import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.log
import math.ceil

object PolynomialMulByXaiState extends ChiselEnum {
  val WAIT, RUN, LAST, FIN = Value
}

class PolynomialMulByXai(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val inaddr = Output(UInt(conf.radixbit.W))
		val insel = Output(UInt(1.W))
		
		val valid = Output(Bool())
		val ready = Input(Bool())
		val out = Output(Vec(conf.block,UInt(conf.Qbit.W)))

		val exponent = Input(UInt((conf.Nbit+1).W))

		val enable = Input(Bool())
	})
	io.valid := false.B
	
	val expabs = RegNext(io.exponent(conf.Nbit-1,0))
	val indexcnt = RegInit(0.U(conf.radixbit.W))
	val explower = expabs(conf.radixbit-1,0)
	val expupper = expabs(2*conf.radixbit-1,conf.radixbit)
	io.inaddr := (indexcnt - explower + 1.U)&(conf.radix-1).U
	// io.inaddr := (indexcnt - expabs(2*conf.radixbit-1,conf.radixbit))(conf.radixbit-1,0) + ~(io.exponent(conf.radixbit-1,0).orR)
	val selreg = RegInit(0.U(1.W))
	io.insel := selreg

	val rotatedqueue = Module(new Queue(Vec(conf.block,UInt(conf.Qbit.W)), 2*conf.radix, useSyncReadMem = true))
	val rotvalidwire = Wire(Bool())
	rotvalidwire := false.B
	rotatedqueue.io.enq.valid := ShiftRegister(rotvalidwire,1)
	io.out := rotatedqueue.io.deq.bits
	io.valid := rotatedqueue.io.deq.valid
	rotatedqueue.io.deq.ready := io.ready
	val shiftamount = conf.Qbit.U*(expupper + Mux(indexcnt<explower,1.U,0.U))
	val rotated = RegNext(io.in.rotateLeft(shiftamount))
	val invrotated = Wire(Vec(conf.block,UInt(conf.Qbit.W)))
	for(i <- 0 until conf.block){
		invrotated(i) := Mux(RegNext(ShiftRegister(io.exponent(conf.Nbit),1)^(((i*conf.block).U+indexcnt)<RegNext(expabs))),-rotated((i+1)*conf.Qbit-1,i*conf.Qbit),rotated((i+1)*conf.Qbit-1,i*conf.Qbit))
	}
	rotatedqueue.io.enq.bits := invrotated
	
	val statereg = RegInit(PolynomialMulByXaiState.WAIT)
	switch(statereg){
		is(PolynomialMulByXaiState.WAIT){
			when(io.enable){
				rotvalidwire := true.B
				statereg := PolynomialMulByXaiState.RUN
				indexcnt := 1.U
			}
		}
		is(PolynomialMulByXaiState.RUN){
			rotvalidwire := true.B
			when(indexcnt =/= (conf.radix-1).U){
				indexcnt := indexcnt + 1.U
			}.otherwise{
				indexcnt := 0.U
				when(selreg=/=1.U){
					selreg := 1.U
					io.insel := 1.U
					statereg := PolynomialMulByXaiState.WAIT
				}.otherwise{
					selreg := 0.U
					statereg := PolynomialMulByXaiState.LAST
				}
			}
		}
		
		is(PolynomialMulByXaiState.LAST){
			statereg := PolynomialMulByXaiState.FIN
		}
	}
	when(~io.enable){
		indexcnt := 0.U
		io.inaddr := -explower
		statereg := PolynomialMulByXaiState.WAIT
	}
}

object PolynomialMulByXaiWrapState extends ChiselEnum {
  val INIT, BUBBLE, RUN, OUT = Value
}

class PolynomialMulByXaiWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val out = Output(UInt((conf.block*conf.Qbit).W))
		val exponent = Input(UInt((conf.Nbit+1).W))
		val valid = Output(Bool())
		val enable = Input(Bool())
	})
	io.valid := false.B
	val mulbyxai = Module(new PolynomialMulByXai)
	
	mulbyxai.io.enable := false.B
	mulbyxai.io.exponent := io.exponent
	io.out := Cat(mulbyxai.io.out.reverse)

	val inmem = Module(new RWSmem(2*conf.radix,conf.block*conf.Qbit))
	mulbyxai.io.in := inmem.io.out
	inmem.io.in := io.in
	inmem.io.wen := false.B
	inmem.io.addr := DontCare
	val initcnt = RegInit(0.U((conf.radixbit+1).W))
	io. valid := mulbyxai.io.valid
	mulbyxai.io.ready := true.B

	val statereg = RegInit(PolynomialMulByXaiWrapState.INIT)
	switch(statereg){
		is(PolynomialMulByXaiWrapState.INIT){
			inmem.io.addr := initcnt
			inmem.io.wen := true.B
			when(initcnt =/= (2*conf.radix-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				statereg := PolynomialMulByXaiWrapState.BUBBLE
			}
		}
		is(PolynomialMulByXaiWrapState.BUBBLE){
			statereg := PolynomialMulByXaiWrapState.RUN
			inmem.io.addr := (mulbyxai.io.insel<<conf.radixbit) + mulbyxai.io.inaddr
		}
		is(PolynomialMulByXaiWrapState.RUN){
			mulbyxai.io.enable := true.B
			inmem.io.addr := (mulbyxai.io.insel<<conf.radixbit) + mulbyxai.io.inaddr
		}
	}
	when(~io.enable){
		statereg := PolynomialMulByXaiWrapState.INIT
		initcnt := 0.U
	}
}

object PolynomialMulByXaiWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new PolynomialMulByXaiWrap()(Config()))
}