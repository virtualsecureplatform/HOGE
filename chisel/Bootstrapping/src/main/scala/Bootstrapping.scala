import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.log
import math.ceil

object BootstrappingState extends ChiselEnum {
  val WAIT, INIT, RUN = Value
}

class Bootstrapping(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{

		val axi4outcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4iksoutcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4incmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4ikskincmd = Vec(conf.iksknumbus,new AXI4StreamManager(conf.cmdbuswidth))
		val axi4bkincmd = Vec(conf.bknumbus,new AXI4StreamManager(conf.cmdbuswidth))

		val outaddr = Input(UInt(64.W))
		val iksoutaddr = Input(UInt(64.W))
		val inaddr = Input(UInt(64.W))
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
	io.axi4iksoutcmd.TVALID := false.B
	io.axi4incmd.TVALID := false.B

	io.axi4outcmd.TDATA:=Cat(io.outaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*2*conf.N.toFloat/8).toInt.U(23.W))
	io.axi4iksoutcmd.TDATA:=Cat(io.iksoutaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*(conf.n+1).toFloat/8).toInt.U(23.W))
	io.axi4incmd.TDATA:=Cat(io.inaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*(conf.N+1).toFloat/8).toInt.U(23.W))
	for(i <- 0 until conf.iksknumbus){
		io.axi4ikskincmd(i).TVALID := false.B
		io.axi4ikskincmd(i).TDATA:=Cat(io.ikskaddr(i),false.B,true.B,0.U(6.W),true.B,(conf.hbmbuswidth*(conf.totaliksknumbus/conf.iksknumbus)*((1<<conf.basebit)-1)*conf.t*conf.N/8).U(23.W))
	}
	for(i <- 0 until conf.bknumbus){
		io.axi4bkincmd(i).TVALID := false.B
		io.axi4bkincmd(i).TDATA := Cat(io.bkaddr(i),false.B,true.B,0.U(6.W),true.B,(conf.buswidth*conf.numcycle*2*conf.l*conf.n/8).U(23.W))
	}

	val statereg = RegInit(BootstrappingState.WAIT)
	val outcmdreg = RegInit(false.B)
	val iksoutcmdreg = RegInit(false.B)
	val incmdreg = RegInit(false.B)
	val ikskincmdreg = RegInit(VecInit(Seq.fill(conf.iksknumbus)(0.U(1.W))))
	val bkincmdreg = RegInit(VecInit(Seq.fill(conf.bknumbus)(0.U(1.W))))

	switch(statereg){
		is(BootstrappingState.WAIT){
			when(io.ap_start){
				statereg := BootstrappingState.INIT
			}
			io.ap_idle := ~io.ap_start
		}
		is(BootstrappingState.INIT){
			io.ap_idle := false.B

			when(~outcmdreg){
				io.axi4outcmd.TVALID := true.B
				outcmdreg := io.axi4outcmd.TREADY
			}
			when(~iksoutcmdreg){
				io.axi4iksoutcmd.TVALID := true.B
				iksoutcmdreg := io.axi4iksoutcmd.TREADY
			}
			when(~incmdreg){
				io.axi4incmd.TVALID := true.B
				incmdreg := io.axi4incmd.TREADY
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
			when(outcmdreg&&iksoutcmdreg&&incmdreg&&Cat(ikskincmdreg).andR&&Cat(bkincmdreg).andR){
				statereg := BootstrappingState.RUN
			}
		}
	}

	when(~io.brvalid&&RegNext(io.brvalid)){
		io.ap_done := true.B
		statereg := BootstrappingState.WAIT
	}	
}

class BootstrappingWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4out = new AXI4StreamManager(conf.buswidth)
		val axi4in = new AXI4StreamSubordinate(conf.buswidth)
		val axi4ikskin = Vec(conf.iksknumbus,new AXI4StreamSubordinate(conf.hbmbuswidth))
		val axi4bkin = Vec(conf.bknumbus,new AXI4StreamSubordinate(conf.buswidth))

		val axi4outcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4iksoutcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4incmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4ikskincmd = Vec(conf.iksknumbus,new AXI4StreamManager(conf.cmdbuswidth))
		val axi4bkincmd = Vec(conf.bknumbus,new AXI4StreamManager(conf.cmdbuswidth))

		val outaddr = Input(UInt(64.W))
		val iksoutaddr = Input(UInt(64.W))
		val inaddr = Input(UInt(64.W))
		val ikskaddr = Input(Vec(conf.iksknumbus,UInt(64.W)))
		val bkaddr = Input(Vec(conf.bknumbus,UInt(64.W)))

		val ap_start = Input(Bool())
		val ap_done = Output(Bool())
		val ap_idle = Output(Bool())
		val ap_ready = Output(Bool())

		val ikskout = Output(UInt(conf.buswidth.W))
		val ikskvalid = Output(Bool())
		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	val bootstrapping = Module(new Bootstrapping)
	val axisiks = Module(new AXISIKS)
	val axisbrformer = Module(new AXISBRFormer)
	val axisbrlater = Module(new AXISBRLater)
	val top2formerslice = Module(new Top2FormerSlice)
	val bk2formerslices = for(i <- 0 until conf.bknumbus) yield{
        val bk2formerslice = Module(new BK2Formerslice)
        bk2formerslice
    }

	io.ap_done := bootstrapping.io.ap_done
	io.ap_idle := bootstrapping.io.ap_idle
	io.ap_ready := bootstrapping.io.ap_ready
	bootstrapping.io.ap_start := io.ap_start

	bootstrapping.io.axi4outcmd <> io.axi4outcmd
	bootstrapping.io.axi4iksoutcmd <> io.axi4iksoutcmd
	bootstrapping.io.axi4incmd <> io.axi4incmd
	bootstrapping.io.axi4ikskincmd <> io.axi4ikskincmd
	bootstrapping.io.axi4bkincmd <> io.axi4bkincmd
	bootstrapping.io.outaddr := io.outaddr
	bootstrapping.io.iksoutaddr := io.iksoutaddr
	bootstrapping.io.inaddr := io.inaddr
	bootstrapping.io.ikskaddr := io.ikskaddr
	bootstrapping.io.bkaddr := io.bkaddr
	bootstrapping.io.brvalid := top2formerslice.io.globaloutlater.TVALID

	axisiks.io.axi4in <> io.axi4in
	top2formerslice.io.globalintop <> axisiks.io.axi4out
	axisbrformer.io.axi4sglobalin <> top2formerslice.io.globalinlater
	io.ikskout := axisiks.io.axi4out.TDATA
	io.ikskvalid := axisiks.io.axi4out.TVALID
	axisiks.io.axi4ikskin <> io.axi4ikskin

	axisbrformer.io.axi4sglobalout <> top2formerslice.io.globaloutlater
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
object BootstrappingWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new BootstrappingWrap()(Config()))
}