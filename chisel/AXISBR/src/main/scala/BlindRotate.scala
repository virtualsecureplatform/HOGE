import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.log
import math.ceil

class RotatedTestVector(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val exponent = Input(UInt((conf.Nbit+1).W))
		val out = Output(Vec(conf.numcycle,Vec(conf.radix,UInt(conf.Qbit.W))))
	})

	for(i <- 0 until conf.numcycle){
		for(j <- 0 until conf.radix){
			io.out(i)(j) := Mux(io.exponent(conf.Nbit) ^ (((j<<conf.cyclebit)+i).U<io.exponent(conf.Nbit-1,0)),((1L<<conf.Qbit)-conf.mu).U,conf.mu.U)
		}
	}
}

object BlindRotateState extends ChiselEnum {
  val WAIT,INIT,BUBBLE,PMBXMOWAIT,RUN,OUT = Value
}

class BlindRotate(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val b = Input(UInt(conf.qbit.W))
		val a = Input(UInt(conf.qbit.W))
		val dimready = Output(Bool())
		val axi4sglobalout = new AXI4StreamManager(conf.Qbit)
		val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
		val enable = Input(Bool())
		val extpfin = Output(Bool())

		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	io.extpfin := false.B
	val dimreadywire = Wire(Bool())
	io.dimready := RegNext(dimreadywire)
	dimreadywire := false.B

	val BRmem = Module(new RWSRmem(2*conf.numcycle,conf.block*conf.Qbit))

	val initcnt = RegInit(0.U((conf.cyclebit+1).W))
	val pmbxmo = Module(new PolynomialMulByXaiMinusOne())
	pmbxmo.io.in := BRmem.io.out
	val pmbxmoenablewire = Wire(Bool())
	pmbxmo.io.enable := RegNext(pmbxmoenablewire)
	pmbxmoenablewire := false.B

	val roundoffset = 1L<<(conf.Qbit-conf.Nbit-2)
	pmbxmo.io.exponent := (io.a + roundoffset.U)(conf.Qbit-1,conf.Qbit-(conf.Nbit+1))
	
	BRmem.io.raddr := pmbxmo.io.minusaddr
	pmbxmo.io.minusin := BRmem.io.rout
	//Write Result
	when(ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2)){
		BRmem.io.raddr := initcnt
		initcnt := initcnt + 1.U
	}
	BRmem.io.wen := ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2+2)
	BRmem.io.addr := ShiftRegister(initcnt,2)
	val tdatavec = Wire(Vec(conf.trlwenumbus,UInt(conf.buswidth.W)))
	for(i <- 0 until conf.trlwenumbus){
		io.axi4sin(i).TREADY := true.B
		tdatavec(i) :=  ShiftRegister(io.axi4sin(i).TDATA,conf.interslr/2)
	}
	val finreg = RegInit(0.U(2.W))
    when(RegNext(~ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2)&&ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2+1))){
        finreg := finreg +1.U
    }
	val addedres = Wire(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
	for(i <- 0 until conf.chunk){
		for(j <- 0 until conf.radix){
			addedres(i)(j) := ShiftRegister(Cat(tdatavec.reverse)((i*conf.radix+j+1)*conf.Qbit-1,(i*conf.radix+j)*conf.Qbit),2) + RegNext(BRmem.io.rout((i*conf.radix+j+1)*conf.Qbit-1,(i*conf.radix+j)*conf.Qbit))
		}
	}
	BRmem.io.in := Cat(addedres.flatten.reverse)
	io.debugout := BRmem.io.in
	io.debugvalid := BRmem.io.wen

	for(i <- 0 until conf.trlwenumbus){
		io.axi4sout(i).TVALID := pmbxmo.io.valid
		io.axi4sout(i).TDATA := Cat(pmbxmo.io.out.reverse)((i+1)*conf.buswidth-1,i*conf.buswidth)
	}

	val brcntreg = RegInit(0.U(log2Ceil(conf.n).W))

	val tvgen = Module(new RotatedTestVector)
	tvgen.io.exponent := (2*conf.N).U - io.b(conf.qbit-1,conf.qbit-(conf.Nbit+1))

	val sei = Module(new SampleExtractIndex(0,conf))

	sei.io.in := BRmem.io.out
	sei.io.enable := false.B
	sei.io.axi4sout <> io.axi4sglobalout

	val statereg = RegInit(BlindRotateState.WAIT)
	switch(statereg){
		is(BlindRotateState.WAIT){
			when(io.enable){
				statereg := BlindRotateState.INIT
			}
		}
		is(BlindRotateState.INIT){
			BRmem.io.wen := true.B
			BRmem.io.addr := RegNext(initcnt)
			BRmem.io.in := RegNext(Mux(initcnt(conf.radixbit),Cat(tvgen.io.out(initcnt).reverse),0.U))
			io.debugout := RegNext(Mux(initcnt(conf.radixbit),Cat(tvgen.io.out(initcnt).reverse),0.U))
			when(initcnt =/= (2*conf.numcycle-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				statereg := BlindRotateState.BUBBLE
			}
		}
		is(BlindRotateState.BUBBLE){
			BRmem.io.wen := true.B
			BRmem.io.addr := RegNext(initcnt)
			BRmem.io.in := RegNext(Mux(initcnt(conf.radixbit),Cat(tvgen.io.out(initcnt).reverse),0.U))
			pmbxmoenablewire := true.B
			statereg := BlindRotateState.PMBXMOWAIT
		}
		is(BlindRotateState.PMBXMOWAIT){
			pmbxmoenablewire := true.B
			BRmem.io.addr := (pmbxmo.io.insel<<conf.radixbit)+pmbxmo.io.inaddr
			when(~pmbxmo.io.valid && RegNext(pmbxmo.io.valid)){
				statereg := BlindRotateState.RUN
				dimreadywire := RegNext(brcntreg =/= (conf.n-1).U)
			}
			io.debugout := Cat(pmbxmo.io.out.reverse)
			io.debugvalid := pmbxmo.io.valid
		}
		is(BlindRotateState.RUN){
			pmbxmoenablewire := false.B
			when(finreg===1.U){
				finreg := 0.U
				io.extpfin := true.B
				when(RegNext(brcntreg =/= (conf.n-1).U)){
					pmbxmoenablewire := true.B
					brcntreg := brcntreg + 1.U
					statereg := BlindRotateState.PMBXMOWAIT
				}.otherwise{
					statereg := BlindRotateState.OUT
				}
			}
		}
		is(BlindRotateState.OUT){
			BRmem.io.addr := sei.io.addr
			sei.io.enable := true.B
		}
	}
	when(!io.enable){
		initcnt := 0.U
		finreg := 0.U
		brcntreg := 0.U
		statereg := BlindRotateState.WAIT
	}
}

class AXISBRFormer(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4sglobalin = new AXI4StreamSubordinate(conf.buswidth)
		val axi4sglobalout = new AXI4StreamManager(conf.Qbit)
		val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.nttnumbus,new AXI4StreamManager(conf.buswidth))

		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	val tlwe2index = Module(new TLWE2Index(conf.buswidth,conf.n,conf.qbit))
	val inslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	val outslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	val br = Module(new BlindRotate)
	val extpformer = Module(new ExternalProductFormer)
	val extppremiddle = Module(new ExternalProductPreMiddle)

	io.axi4sglobalin <> inslice.io.subordinate

	io.debugvalid := br.io.debugvalid
	io.debugout := br.io.debugout

	io.axi4sglobalout <> outslice.io.manager

	br.io.axi4sout <> extpformer.io.axi4sin
	br.io.axi4sin <> io.axi4sin
	br.io.enable := RegNext(tlwe2index.io.validout)
	br.io.b := tlwe2index.io.b
	br.io.a := tlwe2index.io.a
	
	br.io.axi4sglobalout <> outslice.io.subordinate

	tlwe2index.io.axi4 <> inslice.io.manager
	tlwe2index.io.ready := br.io.dimready
	tlwe2index.io.enable := true.B

	extpformer.io.axi4sout <> extppremiddle.io.axi4sin

	extppremiddle.io.axi4sout <> io.axi4sout

	when(~br.io.axi4sglobalout.TVALID&&RegNext(br.io.axi4sglobalout.TVALID)){
		tlwe2index.io.enable := false.B
	}
}

class AXISBRMiddle(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4bkin = Vec(conf.bknumbus,new AXI4StreamSubordinate(conf.buswidth))

		val axi4sin = Vec(conf.nttnumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.nttnumbus,new AXI4StreamManager(conf.buswidth))
	})

	val extpmiddle = Module(new ExternalProductMiddle)

	extpmiddle.io.axi4sin <> io.axi4sin
	extpmiddle.io.axi4sout <> io.axi4sout
	val tvalidvec = Wire(Vec(conf.bknumbus,Bool()))
	val tdatavec = Wire(Vec(conf.bknumbus,UInt(conf.buswidth.W)))
	for(i <- 0 until conf.bknumbus){
		val slice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
		slice.io.subordinate <> io.axi4bkin(i)
		slice.io.manager.TREADY := extpmiddle.io.trgswinready
		tvalidvec(i) := slice.io.manager.TVALID
		tdatavec(i) := slice.io.manager.TDATA
	}
	extpmiddle.io.trgswinvalid := Cat(tvalidvec).andR
	extpmiddle.io.trgswin := Cat(tdatavec.reverse)
}

class AXISBRLater(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4sin = Vec(conf.nttnumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
	})

	val extplater = Module(new ExternalProductLater)
	io.axi4sin <> extplater.io.axi4sin
	extplater.io.axi4sout <> io.axi4sout
}

object AXISBRState extends ChiselEnum {
  val WAIT, INIT, RUN = Value
}

class AXISBRTop(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{

		val axi4outcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4incmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4bkincmd = Vec(conf.bknumbus,new AXI4StreamManager(conf.cmdbuswidth))

		val outaddr = Input(UInt(64.W))
		val inaddr = Input(UInt(64.W))
		val bkaddr = Input(Vec(conf.bknumbus,UInt(64.W)))
		
		val brvalid = Input(Bool())

		val ap_start = Input(Bool())
		val ap_done = Output(Bool())
		val ap_idle = Output(Bool())
		val ap_ready = Output(Bool())

	})

	io.ap_done := false.B
	io.ap_idle := true.B
	io.ap_ready := io.ap_done

	io.axi4outcmd.TVALID := false.B
	io.axi4incmd.TVALID := false.B

	io.axi4outcmd.TDATA:=Cat(io.outaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*2*conf.N.toFloat/8).toInt.U(23.W))
	io.axi4incmd.TDATA:=Cat(io.inaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.qbit*(conf.n+1).toFloat/8).toInt.U(23.W))
	for(i <- 0 until conf.bknumbus){
		io.axi4bkincmd(i).TVALID := false.B
		io.axi4bkincmd(i).TDATA := Cat(io.bkaddr(i),false.B,true.B,0.U(6.W),true.B,(conf.buswidth*conf.numcycle*2*conf.l*conf.n/8).U(23.W))
	}


	val statereg = RegInit(AXISBRState.WAIT)
	val outcmdreg = RegInit(false.B)
	val incmdreg = RegInit(false.B)
	val bkincmdreg = RegInit(0.U(conf.bknumbus.W))

	switch(statereg){
		is(AXISBRState.WAIT){
			when(io.ap_start){
				statereg := AXISBRState.INIT
			}
			io.ap_idle := ~io.ap_start
		}
		is(AXISBRState.INIT){
			io.ap_idle := false.B

			when(~outcmdreg){
				io.axi4outcmd.TVALID := true.B
				outcmdreg := io.axi4outcmd.TREADY
			}
			when(~incmdreg){
				io.axi4incmd.TVALID := true.B
				incmdreg := io.axi4incmd.TREADY
			}
			val bkincmdvec = Wire(Vec(conf.bknumbus,Bool()))
			for(i <- 0 until conf.bknumbus){
				bkincmdvec(i) := io.axi4bkincmd(i).TREADY && (bkincmdreg(i)===0.U)
				io.axi4bkincmd(i).TVALID := bkincmdreg(i)===0.U
			}
			bkincmdreg := Cat(bkincmdvec.reverse)
			when(outcmdreg&&incmdreg&&bkincmdreg.andR){
				statereg := AXISBRState.RUN
			}
		}
	}

	when(~io.brvalid&&RegNext(io.brvalid)){
		io.ap_done := true.B
		statereg := AXISBRState.WAIT
	}
}

class AXISBRWrapper(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4out = new AXI4StreamManager(conf.qbit)
		val axi4in = new AXI4StreamSubordinate(conf.buswidth)
		val axi4bkin = Vec(conf.bknumbus,new AXI4StreamSubordinate(conf.buswidth))

		val axi4outcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4incmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4bkincmd = Vec(conf.bknumbus,new AXI4StreamManager(conf.cmdbuswidth))

		val outaddr = Input(UInt(64.W))
		val inaddr = Input(UInt(64.W))
		val bkaddr = Input(Vec(conf.bknumbus,UInt(64.W)))

		val ap_start = Input(Bool())
		val ap_done = Output(Bool())
		val ap_idle = Output(Bool())
		val ap_ready = Output(Bool())

		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	val axisbr = Module(new AXISBRTop)
	val axisbrformer = Module(new AXISBRFormer)
	val axisbrmiddle = Module(new AXISBRMiddle)
	val axisbrlater = Module(new AXISBRLater)
	val globaloutsliceSLR1toMem = Module(new GlobalOutslice)
	val globalinsliceMemtoSLR1 = Module(new GlobalInslice)
	val nttSLR0toSLR2slices = for(i <- 0 until conf.nttnumbus) yield{
        val  nttSLR0toSLR2slice = Module(new NTTdataPipeline)
         nttSLR0toSLR2slice
    }

	io.ap_done := axisbr.io.ap_done
	io.ap_idle := axisbr.io.ap_idle
	io.ap_ready := axisbr.io.ap_ready
	axisbr.io.ap_start := io.ap_start

	axisbr.io.axi4outcmd <> io.axi4outcmd
	axisbr.io.axi4incmd <> io.axi4incmd
	axisbr.io.axi4bkincmd <> io.axi4bkincmd
	axisbr.io.outaddr := io.outaddr
	axisbr.io.inaddr := io.inaddr
	axisbr.io.bkaddr := io.bkaddr
	axisbr.io.brvalid := globaloutsliceSLR1toMem.io.manager.TVALID

	axisbrformer.io.axi4sglobalout <> globaloutsliceSLR1toMem.io.subordinate
	globaloutsliceSLR1toMem.io.manager <> io.axi4out 
	
	io.axi4in <> globalinsliceMemtoSLR1.io.subordinate
	globalinsliceMemtoSLR1.io.manager <> axisbrformer.io.axi4sglobalin

	axisbrformer.io.axi4sout <> axisbrmiddle.io.axi4sin
	axisbrmiddle.io.axi4bkin <> io.axi4bkin
	for(i <- 0 until conf.nttnumbus){
		nttSLR0toSLR2slices(i).io.subordinate <> axisbrmiddle.io.axi4sout(i)
		axisbrlater.io.axi4sin(i) <> nttSLR0toSLR2slices(i).io.manager
	}
	axisbrlater.io.axi4sout <> axisbrformer.io.axi4sin

	io.debugout := axisbrformer.io.debugout
	io.debugvalid := axisbrformer.io.debugvalid
}
object AXISBRWrapperTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new AXISBRWrapper()(Config()))
}