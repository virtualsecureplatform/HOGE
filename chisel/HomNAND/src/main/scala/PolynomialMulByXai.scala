import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.log
import math.ceil

object PolynomialMulByXaiState extends ChiselEnum {
  val WAIT, BUBBLE, RUN, LAST, FIN = Value
}

class PolynomialMulByXai(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val inaddr = Output(UInt(conf.radixbit.W))
		val insel = Output(UInt(1.W))
		
		val outaddr = Input(UInt(conf.radixbit.W))
		val outsel = Input(UInt(1.W))
		val out = Output(Vec(conf.block,UInt(conf.Qbit.W)))

		val exponent = Input(UInt((conf.Nbit+1).W))

		val enable = Input(Bool())
		val valid = Output(Bool())
	})
	io.valid := false.B
	
	val expabs = RegNext(io.exponent(conf.Nbit-1,0))
	val indexcnt = RegInit((conf.radix-1).U(conf.radixbit.W))
	io.inaddr := (indexcnt - expabs(2*conf.radixbit-1,conf.radixbit))(conf.radixbit-1,0) + ~(io.exponent(conf.radixbit-1,0).orR)
	val selreg = RegInit(0.U(1.W))
	io.insel := selreg

	val rotatedmem = Module(new RWSmem(2*conf.radix,conf.block*conf.Qbit))
	rotatedmem.io.wen := false.B
	rotatedmem.io.addr := ShiftRegister(RegNext((selreg<<conf.radixbit))+RegNext(indexcnt),2)
	for(i <- 0 until conf.block){
		io.out(i) := rotatedmem.io.out((i+1)*conf.Qbit-1,i*conf.Qbit)
	}
	val tbshifted = Cat(io.in,RegNext(io.in))
	val shiftval = (conf.block.U-RegNext(io.exponent(conf.radixbit-1,0)))(conf.radixbit-1,0)
	val uppershift = RegNext((tbshifted>>((shiftval(conf.radixbit-1,conf.radixbit-2)<<(conf.radixbit-2))*conf.Qbit.U))(((1<<(conf.radixbit-2))+conf.block)*conf.Qbit-1,0))
	val rotated = RegNext((uppershift>>(RegNext(shiftval(conf.radixbit-3,0))*conf.Qbit.U))(conf.block*conf.Qbit-1,0))
	val invrotated = Wire(Vec(conf.block,UInt(conf.Qbit.W)))
	for(i <- 0 until conf.block){
		invrotated(i) := Mux(RegNext(ShiftRegister(io.exponent(conf.Nbit),2)^((ShiftRegister(indexcnt,2)*conf.block.U+i.U)<RegNext(expabs))),-rotated((i+1)*conf.Qbit-1,i*conf.Qbit),rotated((i+1)*conf.Qbit-1,i*conf.Qbit))
	}
	rotatedmem.io.in := Cat(invrotated.reverse)
	
	val statereg = RegInit(PolynomialMulByXaiState.WAIT)
	switch(statereg){
		is(PolynomialMulByXaiState.WAIT){
			when(io.enable){
				statereg := PolynomialMulByXaiState.BUBBLE
				indexcnt := 0.U
			}
		}
		is(PolynomialMulByXaiState.BUBBLE){
			indexcnt := indexcnt + 1.U
			when(indexcnt===2.U){
				statereg := PolynomialMulByXaiState.RUN
			}
		}
		is(PolynomialMulByXaiState.RUN){
			rotatedmem.io.wen := true.B
			when(indexcnt =/= (conf.radix-1).U){
				indexcnt := indexcnt + 1.U
			}.otherwise{
				statereg := PolynomialMulByXaiState.LAST
			}
		}
		
		is(PolynomialMulByXaiState.LAST){
			rotatedmem.io.wen := true.B
			when(ShiftRegister(statereg === PolynomialMulByXaiState.LAST,2)){
				when(selreg=/=1.U){
					selreg := 1.U
					statereg := PolynomialMulByXaiState.WAIT
				}.otherwise{
					selreg := 0.U
					statereg := PolynomialMulByXaiState.FIN
				}
			}
		}
		is(PolynomialMulByXaiState.FIN){
			io.valid := true.B
			rotatedmem.io.addr := io.outsel*conf.radix.U+io.outaddr
		}
	}
	when(~io.enable){
		indexcnt := (conf.radix-1).U
		statereg := PolynomialMulByXaiState.WAIT
	}
}

object PolynomialMulByXaiMinusOneState extends ChiselEnum {
  val WAIT, INIT, RUN = Value
}

class PolynomialMulByXaiMinusOne(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val inaddr = Output(UInt(conf.radixbit.W))
		val insel = Output(UInt(1.W))
		
		val outaddr = Input(UInt(conf.radixbit.W))
		val outsel = Input(UInt(1.W))
		val out = Output(Vec(conf.block,UInt(conf.Qbit.W)))

		val exponent = Input(UInt((conf.Nbit+1).W))

		val enable = Input(Bool())
		val valid = Output(Bool())
	})
	val mulbyxai = Module(new PolynomialMulByXai)
	io.valid := mulbyxai.io.valid
	mulbyxai.io.enable := false.B
	mulbyxai.io.in := io.in
	io.inaddr := mulbyxai.io.inaddr
	io.insel := mulbyxai.io.insel
	mulbyxai.io.exponent := io.exponent
	mulbyxai.io.outaddr := io.outaddr
	mulbyxai.io.outsel := io.outsel

	for(i <- 0 until conf.block){
		io.out(i) := mulbyxai.io.out(i) - io.in((i+1)*conf.Qbit-1,i*conf.Qbit)
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
				statereg := PolynomialMulByXaiMinusOneState.RUN
				io.inaddr := io.outaddr
				io.insel := io.outsel
			}
		}
		is(PolynomialMulByXaiMinusOneState.RUN){
			mulbyxai.io.enable := true.B
			io.inaddr := io.outaddr
			io.insel := io.outsel
		}
	}
	when(~io.enable){
		mulbyxai.io.enable := false.B
		statereg := PolynomialMulByXaiMinusOneState.WAIT
	}
}

object PMBXMOLState extends ChiselEnum {
  val WAIT, OUT, BUBBLE, FIN = Value
}

//Polynomial Mul By Xai Minus One with Lbuffer
class PMBXMOL(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val inaddr = Output(UInt(conf.radixbit.W))
		val insel = Output(UInt(1.W))
		
		val valid = Output(Bool())
		val out = Output(Vec(conf.block,UInt(conf.Qbit.W)))

		val exponent = Input(UInt((conf.Nbit+1).W))

		val enable = Input(Bool())
		val release = Output(Bool())
	})

	io.release := false.B

	val mulbyxaimone = Module(new PolynomialMulByXaiMinusOne)
	mulbyxaimone.io.enable := io.enable
	mulbyxaimone.io.in := io.in
	io.inaddr := mulbyxaimone.io.inaddr
	io.insel := mulbyxaimone.io.insel
	mulbyxaimone.io.exponent := io.exponent

	val inlbuf = Module(new Lbuffer(conf.Qbit,1,conf))
	inlbuf.io.in(0) := RegNext(mulbyxaimone.io.out)

	io.out := inlbuf.io.out(0)

	val initcnt = RegInit(0.U((conf.radixbit+1).W))
	val selreg = RegInit(0.U(1.W))
	mulbyxaimone.io.outaddr := initcnt
	mulbyxaimone.io.outsel := selreg

	io.valid := inlbuf.io.validout

	val statereg = RegInit(PMBXMOLState.WAIT)
	inlbuf.io.validin := (statereg === PMBXMOLState.OUT) && RegNext(~initcnt(conf.radixbit))
	switch(statereg){
		is(PMBXMOLState.WAIT){
			when(mulbyxaimone.io.valid){
				initcnt := 1.U
				statereg := PMBXMOLState.OUT
			}
		}
		is(PMBXMOLState.OUT){
			when(initcnt =/= (2*conf.radix-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				when(selreg=/=1.U){
					selreg := selreg + 1.U
					statereg := PMBXMOLState.BUBBLE
				}.otherwise{
					selreg := 0.U
					statereg := PMBXMOLState.FIN
				}
			}
		}
		is(PMBXMOLState.BUBBLE){
			statereg := PMBXMOLState.WAIT
		}
	}
	when(~io.enable){
		initcnt := 0.U
		selreg := 0.U
		statereg := PMBXMOLState.WAIT
	}
}

object PMBXMOLWrapState extends ChiselEnum {
  val INIT, BUBBLE, RUN, OUT, FIN = Value
}

class PMBXMOLWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val out = Output(UInt((conf.block*conf.Qbit).W))
		val exponent = Input(UInt((conf.Nbit+1).W))
		val valid = Output(Bool())
		val enable = Input(Bool())
	})
	val pbxmol = Module(new PMBXMOL)
	
	pbxmol.io.enable := false.B
	pbxmol.io.exponent := io.exponent
	io.out := Cat(pbxmol.io.out.reverse)
	io.valid := pbxmol.io.valid

	val inmem = Module(new RWSmem(2*conf.radix,conf.block*conf.Qbit))
	pbxmol.io.in := inmem.io.out
	inmem.io.in := io.in
	inmem.io.wen := false.B
	inmem.io.addr := DontCare
	val initcnt = RegInit(0.U((conf.radixbit+1).W))
	val selreg = RegInit(0.U(1.W))

	val statereg = RegInit(PMBXMOLWrapState.INIT)
	switch(statereg){
		is(PMBXMOLWrapState.INIT){
			inmem.io.addr := initcnt
			inmem.io.wen := true.B
			when(initcnt =/= (2*conf.radix-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				statereg := PMBXMOLWrapState.BUBBLE
			}
		}
		is(PMBXMOLWrapState.BUBBLE){
			statereg := PMBXMOLWrapState.RUN
			inmem.io.addr := (pbxmol.io.insel<<conf.radixbit) + pbxmol.io.inaddr
		}
		is(PMBXMOLWrapState.RUN){
			pbxmol.io.enable := true.B
			inmem.io.addr := (pbxmol.io.insel<<conf.radixbit) + pbxmol.io.inaddr
			when(pbxmol.io.valid){
				statereg := PMBXMOLWrapState.OUT
				initcnt := 1.U
			}
		}
		is(PMBXMOLWrapState.OUT){
			pbxmol.io.enable := true.B
			inmem.io.addr := (pbxmol.io.insel<<conf.radixbit) + pbxmol.io.inaddr
			io.valid := true.B
			when(initcnt =/= (conf.radix-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				when(selreg =/= 1.U){
					selreg := selreg + 1.U
					statereg := PMBXMOLWrapState.RUN
				}.otherwise{
					selreg := 0.U
					statereg :=  PMBXMOLWrapState.FIN
				}
			}
		}
	}
	when(~io.enable){
		statereg := PMBXMOLWrapState.INIT
		initcnt := 0.U
		selreg := 0.U
	}
}

object PMBXMOLWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new PMBXMOLWrap()(Config()))
}