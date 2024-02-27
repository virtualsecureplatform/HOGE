import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.ceil

class TLWEADD(val buswidth: Int, val n: Int, qbit: Int, mu: Int) extends Module{
    val io = IO(new Bundle{
		val axi4sout = new AXI4StreamManager(buswidth)
		val axi4sina = new AXI4StreamSubordinate(buswidth)
		val axi4sinb = new AXI4StreamSubordinate(buswidth)

		val scaleaindex = Input(UInt(2.W))
		val scalebindex = Input(UInt(2.W))
		val offsetindex = Input(UInt(2.W))
	})

	val numreg = buswidth/qbit
	val queuedepth = ceil(qbit * (n+1).toFloat / buswidth).toInt
	val posb = n % numreg

	val shiftedaslice = Module(new Queue(UInt(buswidth.W),2, useSyncReadMem = true))
	val shiftedbslice = Module(new Queue(UInt(buswidth.W),2, useSyncReadMem = true))

	val countreg = RegInit(0.U(log2Ceil(queuedepth+1).W))

	val addedvec = Wire(Vec(numreg,UInt(qbit.W)))
	val shiftedavec = Wire(Vec(numreg,UInt(qbit.W)))
	val shiftedbvec = Wire(Vec(numreg,UInt(qbit.W)))
	io.axi4sout.TVALID := shiftedaslice.io.deq.valid && shiftedbslice.io.deq.valid
	shiftedaslice.io.deq.ready := io.axi4sout.TVALID && io.axi4sout.TREADY
	shiftedbslice.io.deq.ready := io.axi4sout.TVALID && io.axi4sout.TREADY
	io.axi4sout.TDATA := Cat(addedvec.reverse)

	shiftedaslice.io.enq.valid := io.axi4sina.TVALID
	io.axi4sina.TREADY := shiftedaslice.io.enq.ready
	shiftedaslice.io.enq.bits := Cat(shiftedavec.reverse)

	shiftedbslice.io.enq.valid := io.axi4sinb.TVALID
	io.axi4sinb.TREADY := shiftedbslice.io.enq.ready
	shiftedbslice.io.enq.bits := Cat(shiftedbvec.reverse)
	for(i<-0 until numreg){
		shiftedavec(i) := io.axi4sina.TDATA((i+1)*qbit-1,i*qbit)<<io.scaleaindex(0)
		shiftedbvec(i) := io.axi4sinb.TDATA((i+1)*qbit-1,i*qbit)<<io.scalebindex(0)
		val scaleda = Mux(io.scaleaindex(1),-shiftedaslice.io.deq.bits((i+1)*qbit-1,i*qbit),shiftedaslice.io.deq.bits((i+1)*qbit-1,i*qbit))
		val scaledb = Mux(io.scalebindex(1),-shiftedbslice.io.deq.bits((i+1)*qbit-1,i*qbit),shiftedbslice.io.deq.bits((i+1)*qbit-1,i*qbit))
		addedvec(i) := scaleda + scaledb
	}

	when(io.axi4sout.TVALID && io.axi4sout.TREADY){
		when(countreg =/= (queuedepth-1).U){
			countreg := countreg + 1.U
		}.otherwise{
			countreg := 0.U
			val scaleda = Mux(io.scaleaindex(1),-shiftedaslice.io.deq.bits((posb+1)*qbit-1,posb*qbit),shiftedaslice.io.deq.bits((posb+1)*qbit-1,posb*qbit))
			val scaledb = Mux(io.scalebindex(1),-shiftedbslice.io.deq.bits((posb+1)*qbit-1,posb*qbit),shiftedbslice.io.deq.bits((posb+1)*qbit-1,posb*qbit))
			val shiftedmu = mu.U(qbit.W)<<io.offsetindex(0)
			val scaledmu = Mux(io.offsetindex(1),-shiftedmu,shiftedmu)
			addedvec(posb) := scaleda + scaledb + scaledmu
		}
	}
}

object HomGateState extends ChiselEnum {
  val WAIT, INIT, RUN, RESET = Value
}

class HomGateTop(implicit val conf:Config) extends Module{
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

		val user_rst = Output(Bool())

		val ap_start = Input(Bool())
		val ap_done = Output(Bool())
		val ap_idle = Output(Bool())
		val ap_ready = Output(Bool())
	})

	io.ap_done := false.B
	io.ap_idle := true.B
	io.ap_ready := io.ap_done

	io.user_rst := false.B

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

	val statereg = RegInit(HomGateState.WAIT)
	val outcmdreg = RegInit(false.B)
	val inacmdreg = RegInit(false.B)
	val inbcmdreg = RegInit(false.B)
	val ikskincmdreg = RegInit(VecInit(Seq.fill(conf.iksknumbus)(0.U(1.W))))
	val bkincmdreg = RegInit(VecInit(Seq.fill(conf.bknumbus)(0.U(1.W))))

	val countreg = RegInit(0.U(3.W))

	switch(statereg){
		is(HomGateState.WAIT){
			when(io.ap_start){
				statereg := HomGateState.RESET
			}
			io.ap_idle := ~io.ap_start
		}
		is(HomGateState.RESET){
			// DataMover needs at least 3 cycles to reset
			io.ap_idle := false.B
			io.user_rst := true.B
			outcmdreg := false.B
			inacmdreg := false.B
			inbcmdreg := false.B
			for(i <- 0 until conf.iksknumbus){
				ikskincmdreg(i) := false.B
			}
			for(i <- 0 until conf.bknumbus){
				bkincmdreg(i) := false.B
			}
			when(countreg=/=7.U){
				countreg := countreg + 1.U
			}.otherwise{
				statereg := HomGateState.INIT
				countreg := 0.U
			}
		}
		is(HomGateState.INIT){
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
				statereg := HomGateState.RUN
			}
		}
		is(HomGateState.RUN){
			io.ap_idle := false.B
			when(~io.brvalid&&RegNext(io.brvalid)){
				io.ap_done := true.B
				statereg := HomGateState.WAIT
			}
		}
	}
}



class HomGateWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4out = new AXI4StreamManager(conf.Qbit)
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

		val scaleaindex = Input(UInt(2.W))
		val scalebindex = Input(UInt(2.W))
		val offsetindex = Input(UInt(2.W))

		val ap_start = Input(Bool())
		val ap_done = Output(Bool())
		val ap_idle = Output(Bool())
		val ap_ready = Output(Bool())

		val user_rst = Output(Bool())

		val ikskout = Output(UInt(conf.buswidth.W))
		val ikskvalid = Output(Bool())
		val debugadd = Output(UInt(conf.buswidth.W))
		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
		val debug_iksenable = Output(Bool())
	})

	val homnand = Module(new HomGateTop)
	val tlweadd = Module(new TLWEADD(conf.buswidth,conf.N,conf.Qbit,conf.mu))
	val axisiks = Module(new AXISIKS)
	val axisbrformer = Module(new AXISBRFormer)
	val axisbrmiddle = Module(new AXISBRMiddle)
	val axisbrlater = Module(new AXISBRLater)
	val globaloutsliceSLR2toSLR1 = Module(new GlobalOutslice)
	val globaloutsliceSLR1toSLR0 = Module(new GlobalOutslice)
	val globalinsliceSLR0toSLR1 = Module(new BK2Formerslice)
	val globalinsliceSLR1toSLR2 = Module(new BK2Formerslice)
	val nttSLR1toSLR0slices = for(i <- 0 until conf.nttnumbus) yield{
        val  nttSLR1toSLR0slice = Module(new NTTdataPipeline)
         nttSLR1toSLR0slice
    }

	io.ap_done := homnand.io.ap_done
	io.ap_idle := homnand.io.ap_idle
	io.ap_ready := homnand.io.ap_ready
	homnand.io.ap_start := io.ap_start

	io.user_rst := homnand.io.user_rst

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
	homnand.io.brvalid := globaloutsliceSLR1toSLR0.io.manager.TVALID

	tlweadd.io.scaleaindex := io.scaleaindex
	tlweadd.io.scalebindex := io.scalebindex
	tlweadd.io.offsetindex := io.offsetindex
	tlweadd.io.axi4sina <> io.axi4ina
	tlweadd.io.axi4sinb <> io.axi4inb
	io.debugadd := tlweadd.io.axi4sout.TDATA
	axisiks.io.axi4in <> tlweadd.io.axi4sout
	axisiks.io.axi4out <> globalinsliceSLR0toSLR1.io.subordinate
	globalinsliceSLR0toSLR1.io.manager <> globalinsliceSLR1toSLR2.io.subordinate
	globalinsliceSLR1toSLR2.io.manager <> axisbrformer.io.axi4sglobalin
	io.ikskout := axisiks.io.axi4out.TDATA
	io.ikskvalid := axisiks.io.axi4out.TVALID
	axisiks.io.axi4ikskin <> io.axi4ikskin
	io.debug_iksenable := axisiks.io.debug_iksenable

	axisbrformer.io.axi4sglobalout <> globaloutsliceSLR2toSLR1.io.subordinate
	globaloutsliceSLR2toSLR1.io.manager <> globaloutsliceSLR1toSLR0.io.subordinate
	globaloutsliceSLR1toSLR0.io.manager <> io.axi4out 

	for(i <- 0 until conf.nttnumbus){
		nttSLR1toSLR0slices(i).io.subordinate <> axisbrformer.io.axi4sout(i)
		axisbrmiddle.io.axi4sin(i) <> nttSLR1toSLR0slices(i).io.manager
	}
	axisbrmiddle.io.axi4bkin <> io.axi4bkin
	axisbrmiddle.io.axi4sout <> axisbrlater.io.axi4sin
	axisbrlater.io.axi4sout <> axisbrformer.io.axi4sin

	io.debugout := axisbrformer.io.debugout
	io.debugvalid := axisbrformer.io.debugvalid
}

object HomGateWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new HomGateWrap()(Config()))
}