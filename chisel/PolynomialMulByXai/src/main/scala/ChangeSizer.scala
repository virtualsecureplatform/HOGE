import chisel3._
import chisel3.util._

import math.ceil
import math.log

class UpSizer(val outputwidth: Int,implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt(conf.buswidth.W))
		val out = Output(UInt(outputwidth.W))
		val ready = Output(Bool())
	})

	val numreg = ceil(outputwidth.toFloat/conf.buswidth).toInt
	val shiftreg = Reg(Vec(numreg,UInt(conf.buswidth.W)))
	val cntreg = RegInit(0.U(ceil(log(numreg)/log(2)).toInt.W))

	io.out := Cat(shiftreg.reverse)

	shiftreg(numreg-1) := io.in
	for(i<-0 until numreg-1){
		shiftreg(i) := shiftreg(i+1)
	}

	when(cntreg=/=(numreg-1).U){
		cntreg := cntreg + 1.U
		io.ready := false.B
	}.otherwise{
		cntreg := 0.U
		io.ready := true.B
	}
}

class DownSizer(val inputwidth: Int,implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt(inputwidth.W))
		val out = Output(UInt(conf.buswidth.W))
		val req = Input(Bool())
		val ready = Output(Bool())
	})

	val numreg = ceil(inputwidth.toFloat/conf.buswidth).toInt
	val shiftreg = Reg(Vec(numreg,UInt(conf.buswidth.W)))
	val cntreg = RegInit((numreg-1).U(ceil(log(numreg)/log(2)).toInt.W))

	io.out := shiftreg(0)

	when(io.req){
		shiftreg(numreg-1) := io.in(inputwidth-1,conf.buswidth*(numreg-1))
		for(i<-0 until numreg-1){
			shiftreg(i) := io.in(conf.buswidth*(i+1)-1,conf.buswidth*i)
		}
		cntreg := 0.U
	}

	when(cntreg=/=(numreg-1).U){
		cntreg := cntreg + 1.U
		io.ready := false.B
		
		shiftreg(numreg-1) := DontCare
		for(i<-0 until numreg-1){
			shiftreg(i) := shiftreg(i+1)
		}

	}.otherwise{
		io.ready := true.B
	}
}