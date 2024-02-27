import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.ceil
import math.log

class UpSizer(val outputwidth: Int, val buswidth: Int) extends Module{
	val io = IO(new Bundle{
		val axi4 = new AXI4StreamSubordinate(buswidth)
		val out = Output(UInt(outputwidth.W))
		val ready = Output(Bool())
		val req = Input(Bool())
	})

	val numreg = ceil(outputwidth.toFloat/buswidth).toInt
	val shiftreg = Reg(Vec(numreg,UInt(buswidth.W)))
	val cntreg = RegInit(0.U(log2Ceil(numreg).W))

	io.out := Cat(shiftreg.reverse)
	io.axi4.TREADY := true.B
	val readyreg = RegInit(false.B)
	io.ready := readyreg
	
	when(readyreg & io.req){
		readyreg := false.B
	}

	when((~readyreg)&io.axi4.TVALID){
		shiftreg(numreg-1) := io.axi4.TDATA
		for(i<-0 until numreg-1){
			shiftreg(i) := shiftreg(i+1)
		}
		when(cntreg=/=(numreg-1).U){
			cntreg := cntreg + 1.U
			readyreg := false.B
		}.otherwise{
			readyreg  := true.B
		}
	}
}

class MultiIFUpSizer(val outputwidth: Int, val buswidth: Int, val numbus: Int) extends Module{
	val io = IO(new Bundle{
		val axi4 = Vec(numbus,new AXI4StreamSubordinate(buswidth))
		val out = Output(UInt(outputwidth.W))
		val ready = Output(Bool())
		val req = Input(Bool())
	})

	val numreg = ceil(outputwidth.toFloat/(buswidth*numbus)).toInt
	val shiftreg = Reg(Vec(numreg,UInt((numbus*buswidth).W)))
	val cntreg = RegInit(0.U(log2Ceil(numreg).W))

	io.out := Cat(shiftreg.reverse)
	val tvalidvec = Wire(Vec(numbus,Bool()))
	val tdatavec = Wire(Vec(numbus,UInt(buswidth.W)))
	val readyreg = RegInit(false.B)
	io.ready := readyreg

	when(readyreg & io.req){
		readyreg := false.B
	}

	for(i <- 0 until numbus){
		io.axi4(i).TREADY := ~readyreg | io.req
		tvalidvec(i) := io.axi4(i).TVALID
		tdatavec(i) := io.axi4(i).TDATA
	}

	when((~readyreg | io.req)&Cat(tvalidvec).andR){
		shiftreg(numreg-1) := Cat(tdatavec.reverse)
		for(i<-0 until numreg-1){
			shiftreg(i) := shiftreg(i+1)
		}
		when(cntreg=/=(numreg-1).U){
			cntreg := cntreg + 1.U
			readyreg := false.B
		}.otherwise{
			cntreg := 0.U
			readyreg  := true.B
		}
	}
}

class TLWE2Index(val buswidth: Int, val n: Int, qbit: Int) extends Module{
	val io = IO(new Bundle{
		val axi4 = new AXI4StreamSubordinate(buswidth)
		val a = Output(UInt(qbit.W))
		val b = Output(UInt(qbit.W))
		val ready = Input(Bool())
		val validout = Output(Bool())

		val enable = Input(Bool())
	})
	val numreg = buswidth/qbit
	val queuedepth = ceil(qbit * (n+1).toFloat / buswidth).toInt
	val posb = n % numreg

	val validreg = RegInit(false.B)
	val breg = Reg(UInt(qbit.W))
	val shiftreg = Reg(Vec(numreg,UInt(qbit.W)))
	val cntreg = Reg(UInt(log2Ceil(numreg).W))
	val axiqueue = Module(new Queue(UInt(buswidth.W),queuedepth, useSyncReadMem = true, hasFlush = true))
	axiqueue.io.enq.bits := io.axi4.TDATA
	axiqueue.io.enq.valid := io.axi4.TVALID
	io.axi4.TREADY := axiqueue.io.enq.ready
	axiqueue.io.deq.ready := false.B
	axiqueue.io.flush.get := false.B

	io.validout := validreg
	when(~axiqueue.io.enq.ready){
		validreg := true.B
		for(i<-0 until numreg){
			shiftreg(i) := axiqueue.io.deq.bits((i+1)*qbit-1,i*qbit)
		}
		axiqueue.io.deq.ready := true.B
	}
	
	io.b := breg
	io.a := shiftreg(0)
	when(io.ready){
		when(cntreg =/= (numreg-1).U){
			cntreg := cntreg + 1.U
			for(i<-0 until numreg-1){
				shiftreg(i) := shiftreg(i+1)
			}
		}.otherwise{
			cntreg := 0.U
			for(i<-0 until numreg){
				shiftreg(i) := axiqueue.io.deq.bits((i+1)*qbit-1,i*qbit)
			}
			axiqueue.io.deq.ready := true.B
		}
	}
	when(RegNext(io.axi4.TVALID)){
		cntreg := 0.U
		breg := RegNext(io.axi4.TDATA((posb+1)*qbit-1,posb*qbit))
	}

	when(~io.enable){
		validreg := false.B
		axiqueue.io.flush.get := true.B
	}
}

class DownSizer(val inputwidth: Int, val buswidth: Int) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt(inputwidth.W))
		val req = Input(Bool())
		val done = Output(Bool())
		val axi4 = new AXI4StreamManager(buswidth)
	})

	val numreg = ceil(inputwidth.toFloat/buswidth).toInt
	val shiftreg = Reg(Vec(numreg,UInt(buswidth.W)))
	val cntreg = RegInit((numreg-1).U(log2Ceil(numreg).W))
	val validreg = RegInit(false.B)

	io.axi4.TDATA := shiftreg(0)
	io.axi4.TVALID := validreg
	io.done := false.B

	when(validreg){
		when(io.axi4.TREADY){
			shiftreg(numreg-1) := DontCare
			for(i<-0 until numreg-1){
				shiftreg(i) := shiftreg(i+1)
			}
			when(cntreg=/=(numreg-1).U){
				cntreg := cntreg + 1.U
			}.otherwise{
				cntreg := 0.U
				io.done := true.B
				when(io.req){
					shiftreg(numreg-1) := io.in(inputwidth-1,buswidth*(numreg-1))
					for(i<-0 until numreg-1){
						shiftreg(i) := io.in(buswidth*(i+1)-1,buswidth*i)
					}
					cntreg := 0.U
					validreg := true.B
				}.otherwise{
					validreg := false.B
				}
			}
		}
	}.elsewhen(io.req){
		shiftreg(numreg-1) := io.in(inputwidth-1,buswidth*(numreg-1))
		for(i<-0 until numreg-1){
			shiftreg(i) := io.in(buswidth*(i+1)-1,buswidth*i)
		}
		cntreg := 0.U
		validreg := true.B
	}
}