import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.ceil

class TLWEADD(val buswidth: Int, val n: Int, qbit: Int, mu: Int) extends Module{
    val io = IO(new Bundle{
		val axi4sout = new AXI4StreamManager(buswidth)
		val axi4sina = new AXI4StreamSubordinate(buswidth)
		val axi4sinb = new AXI4StreamSubordinate(buswidth)
	})

	val numreg = buswidth/qbit
	val queuedepth = ceil(qbit * (n+1).toFloat / buswidth).toInt
	val posb = n % numreg

	val countreg = RegInit(0.U(log2Ceil(queuedepth).W))

	val addedvec = Wire(Vec(numreg,UInt(qbit.W)))
	io.axi4sout.TVALID := io.axi4sina.TVALID&&io.axi4sinb.TVALID
	io.axi4sina.TREADY := io.axi4sina.TVALID&&io.axi4sinb.TVALID&&io.axi4sout.TREADY
	io.axi4sinb.TREADY := io.axi4sina.TVALID&&io.axi4sinb.TVALID&&io.axi4sout.TREADY
	for(i<-0 until numreg){
		addedvec(i) := - io.axi4sina.TDATA((i+1)*qbit-1,i*qbit) - io.axi4sinb.TDATA((i+1)*qbit-1,i*qbit)
	}

	when(io.axi4sina.TVALID&&io.axi4sinb.TVALID&&io.axi4sout.TREADY){
		when(countreg =/= (queuedepth - 1).U){
			countreg := countreg + 1.U
		}.otherwise{
			countreg := 0.U
			addedvec(posb) := - io.axi4sina.TDATA((posb+1)*qbit-1,posb*qbit) - io.axi4sinb.TDATA((posb+1)*qbit-1,posb*qbit) + mu.U
		}
	}
	io.axi4sout.TDATA := Cat(addedvec.reverse)
}

object HomNANDState extends ChiselEnum {
  val WAIT, INIT, RUN = Value
}

class HomNANDTop(implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
		val axi4outcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4inacmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4inbcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4ikskincmd = Vec(conf.iksknumbus,new AXI4StreamManager(conf.cmdbuswidth))
		val axi4bkincmd = Vec(conf.bknumbus,new AXI4StreamManager(conf.cmdbuswidth))

		val outaddr = Input(UInt(64.W))
		val inaaddr = Input(UInt(64.W))
		val inbaddr = Input(UInt(64.W))
		val ikskaddr = Input(Vec(conf.iksknumbus,UInt(64.W)))
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
	io.axi4inacmd.TVALID := false.B
	io.axi4inbcmd.TVALID := false.B

	io.axi4outcmd.TDATA:=Cat(io.outaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*(conf.N+1).toFloat/8).toInt.U(23.W))
	io.axi4inacmd.TDATA:=Cat(io.inaaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*(conf.N+1).toFloat/8).toInt.U(23.W))
	io.axi4inbcmd.TDATA:=Cat(io.inbaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*(conf.N+1).toFloat/8).toInt.U(23.W))
	for(i <- 0 until conf.iksknumbus){
		io.axi4ikskincmd(i).TVALID := false.B
		io.axi4ikskincmd(i).TDATA:=Cat(io.ikskaddr(i),false.B,true.B,0.U(6.W),true.B,(conf.hbmbuswidth*(conf.totaliksknumbus/conf.iksknumbus)*((1<<conf.basebit)-1)*conf.t*conf.N/8).U(23.W))
	}
	for(i <- 0 until conf.bknumbus){
		io.axi4bkincmd(i).TVALID := false.B
		io.axi4bkincmd(i).TDATA := Cat(io.bkaddr(i),false.B,true.B,0.U(6.W),true.B,(conf.buswidth*conf.numcycle*2*conf.l*conf.n/8).U(23.W))
	}

	val statereg = RegInit(HomNANDState.WAIT)
	val outcmdreg = RegInit(false.B)
	val inacmdreg = RegInit(false.B)
	val inbcmdreg = RegInit(false.B)
	val ikskincmdreg = RegInit(VecInit(Seq.fill(conf.iksknumbus)(0.U(1.W))))
	val bkincmdreg = RegInit(VecInit(Seq.fill(conf.bknumbus)(0.U(1.W))))

	switch(statereg){
		is(HomNANDState.WAIT){
			when(io.ap_start){
				statereg := HomNANDState.INIT
			}
			io.ap_idle := ~io.ap_start
		}
		is(HomNANDState.INIT){
			io.ap_idle := false.B

			when(~outcmdreg){
				io.axi4outcmd.TVALID := true.B
				outcmdreg := io.axi4outcmd.TREADY
			}
			when(~inacmdreg){
				io.axi4inacmd.TVALID := true.B
				inacmdreg := io.axi4inacmd.TREADY
			}
			when(~inbcmdreg){
				io.axi4inbcmd.TVALID := true.B
				inbcmdreg := io.axi4inbcmd.TREADY
			}
			for(i <- 0 until conf.iksknumbus){
				when(ikskincmdreg(i) === 0.U){
					ikskincmdreg(i):=  io.axi4ikskincmd(i).TREADY
					io.axi4ikskincmd(i).TVALID := true.B
				}
			}
			for(i <- 0 until conf.bknumbus){
				when(bkincmdreg(i)===0.U){
					bkincmdreg(i) := io.axi4bkincmd(i).TREADY
					io.axi4bkincmd(i).TVALID := true.B
				}
			}
			when(outcmdreg&&inacmdreg&&inbcmdreg&&Cat(ikskincmdreg).andR&&Cat(bkincmdreg).andR){
				statereg := HomNANDState.RUN
			}
		}
		is(HomNANDState.RUN){
			io.ap_idle := false.B
		}
	}

	when(~io.brvalid&&RegNext(io.brvalid)){
		io.ap_done := true.B
		outcmdreg := false.B
		inacmdreg := false.B
		inbcmdreg := false.B
		for(i <- 0 until conf.iksknumbus){
			ikskincmdreg(i) := false.B
		}
		for(i <- 0 until conf.bknumbus){
			bkincmdreg(i) := false.B
		}
		statereg := HomNANDState.WAIT
	}
}

class HomNANDWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4out = new AXI4StreamManager(conf.buswidth)
		val axi4ina = new AXI4StreamSubordinate(conf.buswidth)
		val axi4inb = new AXI4StreamSubordinate(conf.buswidth)
		val axi4ikskin = Vec(conf.iksknumbus,new AXI4StreamSubordinate(conf.hbmbuswidth))
		val axi4bkin = Vec(conf.bknumbus,new AXI4StreamSubordinate(conf.buswidth))

		val axi4outcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4inacmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4inbcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4ikskincmd = Vec(conf.iksknumbus,new AXI4StreamManager(conf.cmdbuswidth))
		val axi4bkincmd = Vec(conf.bknumbus,new AXI4StreamManager(conf.cmdbuswidth))

		val outaddr = Input(UInt(64.W))
		val inaaddr = Input(UInt(64.W))
		val inbaddr = Input(UInt(64.W))
		val ikskaddr = Input(Vec(conf.iksknumbus,UInt(64.W)))
		val bkaddr = Input(Vec(conf.bknumbus,UInt(64.W)))

		val ap_start = Input(Bool())
		val ap_done = Output(Bool())
		val ap_idle = Output(Bool())
		val ap_ready = Output(Bool())

		val ikskout = Output(UInt(conf.buswidth.W))
		val ikskvalid = Output(Bool())
		val debugadd = Output(UInt((conf.block*conf.Qbit).W))
		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	val homnand = Module(new HomNANDTop)
	val tlweadd = Module(new TLWEADD(conf.buswidth,conf.N,conf.Qbit,conf.mu))
	val axisiks = Module(new AXISIKS)
	val axisbrformer = Module(new AXISBRFormer)
	val axisbrlater = Module(new AXISBRLater)
	val sei = Module(new SampleExtractIndex(0,conf))
	val top2formerslice = Module(new Top2FormerSlice)
	val bk2formerslices = for(i <- 0 until conf.bknumbus) yield{
        val bk2formerslice = Module(new BK2Formerslice)
        bk2formerslice
    }

	io.ap_done := homnand.io.ap_done
	io.ap_idle := homnand.io.ap_idle
	io.ap_ready := homnand.io.ap_ready
	homnand.io.ap_start := io.ap_start

	homnand.io.axi4outcmd <> io.axi4outcmd
	homnand.io.axi4inacmd <> io.axi4inacmd
	homnand.io.axi4inbcmd <> io.axi4inbcmd
	homnand.io.axi4ikskincmd <> io.axi4ikskincmd
	homnand.io.axi4bkincmd <> io.axi4bkincmd
	homnand.io.outaddr := io.outaddr
	homnand.io.inaaddr := io.inaaddr
	homnand.io.inbaddr := io.inbaddr
	homnand.io.ikskaddr := io.ikskaddr
	homnand.io.bkaddr := io.bkaddr
	homnand.io.brvalid := top2formerslice.io.globaloutlater.TVALID

	tlweadd.io.axi4sina <> io.axi4ina
	tlweadd.io.axi4sinb <> io.axi4inb
	io.debugadd := tlweadd.io.axi4sout.TDATA
	axisiks.io.axi4in <> tlweadd.io.axi4sout
	top2formerslice.io.globalintop <> axisiks.io.axi4out
	axisbrformer.io.axi4sglobalin <> top2formerslice.io.globalinlater
	io.ikskout := axisiks.io.axi4out.TDATA
	io.ikskvalid := axisiks.io.axi4out.TVALID
	axisiks.io.axi4ikskin <> io.axi4ikskin

	axisbrformer.io.axi4sglobalout <> sei.io.axi4sin
	top2formerslice.io.globaloutlater <> sei.io.axi4sout
	top2formerslice.io.globalouttop <> io.axi4out 

	axisbrlater.io.axi4sin <> axisbrformer.io.axi4sout
	for(i <- 0 until conf.bknumbus){
		bk2formerslices(i).io.subordinate <> io.axi4bkin(i)
		axisbrlater.io.axi4bkin(i) <> bk2formerslices(i).io.manager
	}
	axisbrlater.io.axi4sout <> axisbrformer.io.axi4sin

	io.debugout := axisbrformer.io.debugout
	io.debugvalid := axisbrformer.io.debugvalid
}

object HomNANDWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new HomNANDWrap()(Config()))
}