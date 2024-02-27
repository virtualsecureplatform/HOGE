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

	val rotvalidwire = Wire(Bool())
	rotvalidwire := false.B
	val shiftamount = conf.Qbit.U*(expupper + Mux(indexcnt<explower,1.U,0.U))
	val rotated = RegNext(io.in.rotateLeft(shiftamount))
	val invrotated = Wire(Vec(conf.block,UInt(conf.Qbit.W)))
	for(i <- 0 until conf.block){
		invrotated(i) := Mux(RegNext(ShiftRegister(io.exponent(conf.Nbit),1)^(((i*conf.block).U+indexcnt)<RegNext(expabs))),-rotated((i+1)*conf.Qbit-1,i*conf.Qbit),rotated((i+1)*conf.Qbit-1,i*conf.Qbit))
	}
	io.out := invrotated
	io.valid := ShiftRegister(rotvalidwire,1)
	
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
					statereg := PolynomialMulByXaiState.LAST
				}
			}
		}
		
		is(PolynomialMulByXaiState.LAST){
			selreg := 0.U
			statereg := PolynomialMulByXaiState.FIN
		}
	}
	when(~io.enable){
		indexcnt := 0.U
		io.inaddr := -explower
		selreg := 0.U
		statereg := PolynomialMulByXaiState.WAIT
	}
}

object PolynomialMulByXaiMinusOneState extends ChiselEnum {
  val WAIT, INIT, RUN, FIN = Value
}

class PolynomialMulByXaiMinusOne(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val inaddr = Output(UInt(conf.radixbit.W))
		val insel = Output(UInt(1.W))
		val minusin = Input(UInt((conf.block*conf.Qbit).W))
		val minusaddr = Output(UInt((conf.radixbit+1).W))
		
		val out = Output(Vec(conf.block,UInt(conf.Qbit.W)))

		val exponent = Input(UInt((conf.Nbit+1).W))

		val enable = Input(Bool())
		val valid = Output(Bool())
	})
	val mulbyxai = Module(new PolynomialMulByXai)
	io.valid := false.B
	mulbyxai.io.enable := false.B
	mulbyxai.io.in := io.in
	io.inaddr := mulbyxai.io.inaddr
	io.insel := mulbyxai.io.insel
	mulbyxai.io.exponent := io.exponent

	val cntreg = RegInit(0.U((conf.radixbit+1).W))
	io.minusaddr := 0.U

	for(i <- 0 until conf.block){
		io.out(i) := mulbyxai.io.out(i) - io.minusin((i+1)*conf.Qbit-1,i*conf.Qbit)
	}

	val statereg = RegInit(PolynomialMulByXaiMinusOneState.WAIT)

	switch(statereg){
		is(PolynomialMulByXaiMinusOneState.WAIT){
			when(io.enable){
				statereg := PolynomialMulByXaiMinusOneState.INIT
			}
		}
		is(PolynomialMulByXaiMinusOneState.INIT){
			mulbyxai.io.enable := true.B
			when(mulbyxai.io.valid){
				io.valid := true.B
				statereg := PolynomialMulByXaiMinusOneState.RUN
				io.minusaddr := cntreg + 1.U
				cntreg := cntreg + 1.U
			}
		}
		is(PolynomialMulByXaiMinusOneState.RUN){
			mulbyxai.io.enable := true.B
			io.valid := true.B
			when(cntreg =/= (2*conf.radix-1).U){
				io.minusaddr := cntreg + 1.U
				cntreg := cntreg + 1.U
			}.otherwise{
				statereg := PolynomialMulByXaiMinusOneState.FIN
			}
		}
	}
	when(~io.enable){
		cntreg := 0.U
		statereg := PolynomialMulByXaiMinusOneState.WAIT
	}
}

object PolynomialMulByXaiMinusOneWrapState extends ChiselEnum {
  val INIT, BUBBLE, RUN, OUT = Value
}

class PolynomialMulByXaiMinusOneWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val out = Output(UInt((conf.block*conf.Qbit).W))
		val exponent = Input(UInt((conf.Nbit+1).W))
		val valid = Output(Bool())
		val enable = Input(Bool())
	})
	val mulbyxaimone = Module(new PolynomialMulByXaiMinusOne)
	io.valid := mulbyxaimone.io.valid
	
	mulbyxaimone.io.enable := false.B
	mulbyxaimone.io.exponent := io.exponent
	io.out := Cat(mulbyxaimone.io.out.reverse)

	val inmem = Module(new RWSRmem(2*conf.radix,conf.block*conf.Qbit))
	mulbyxaimone.io.in := inmem.io.out
	mulbyxaimone.io.minusin := inmem.io.rout
	inmem.io.in := io.in
	inmem.io.wen := false.B
	inmem.io.addr := DontCare
	inmem.io.raddr := mulbyxaimone.io.minusaddr
	val initcnt = RegInit(0.U((conf.radixbit+1).W))

	val statereg = RegInit(PolynomialMulByXaiMinusOneWrapState.INIT)
	switch(statereg){
		is(PolynomialMulByXaiMinusOneWrapState.INIT){
			inmem.io.addr := initcnt
			inmem.io.wen := true.B
			when(initcnt =/= (2*conf.radix-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				statereg := PolynomialMulByXaiMinusOneWrapState.BUBBLE
			}
		}
		is(PolynomialMulByXaiMinusOneWrapState.BUBBLE){
			statereg := PolynomialMulByXaiMinusOneWrapState.RUN
			inmem.io.addr := (mulbyxaimone.io.insel<<conf.radixbit) + mulbyxaimone.io.inaddr
		}
		is(PolynomialMulByXaiMinusOneWrapState.RUN){
			mulbyxaimone.io.enable := true.B
			inmem.io.addr := (mulbyxaimone.io.insel<<conf.radixbit) + mulbyxaimone.io.inaddr
			when(mulbyxaimone.io.valid){
				statereg := PolynomialMulByXaiMinusOneWrapState.OUT
			}
		}
		is(PolynomialMulByXaiMinusOneWrapState.OUT){
			mulbyxaimone.io.enable := true.B
			inmem.io.addr := (mulbyxaimone.io.insel<<conf.radixbit) + mulbyxaimone.io.inaddr
		}
	}
	when(~io.enable){
		statereg := PolynomialMulByXaiMinusOneWrapState.INIT
		initcnt := 0.U
	}
}

object PolynomialMulByXaiMinusOneWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new PolynomialMulByXaiMinusOneWrap()(Config()))
}