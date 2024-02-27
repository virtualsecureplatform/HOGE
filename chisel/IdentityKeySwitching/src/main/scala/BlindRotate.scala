import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.log
import math.ceil

class RotatedTestVector(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val exponent = Input(UInt((conf.Nbit+1).W))
		val out = Output(Vec(conf.N,UInt(conf.Qbit.W)))
	})

	for(i <- 0 until conf.N){
		io.out(i) := Mux(io.exponent(conf.Nbit) ^ (i.U<io.exponent(conf.Nbit-1,0)),((1L<<conf.Qbit)-conf.mu).U,conf.mu.U)
	}
}

class PolynomialMulByXai(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.N,UInt(conf.Qbit.W)))
		val out = Output(Vec(conf.N,UInt(conf.Qbit.W)))
		val exponent = Input(UInt((conf.Nbit+1).W))
	})
	val expabs = io.exponent(conf.Nbit-1,0)
	
	// https://github.com/chipsalliance/rocket-chip/blob/4276f17f989b99e18e0376494587fe00cd09079f/src/main/scala/util/package.scala#L178-L187
	def rotateLeft(x: UInt, n: Int): UInt = if (n == 0) x else Cat(x(x.getWidth-1-n,0), x(x.getWidth-1,x.getWidth-n))
	def rotateLeft(x:UInt, n: UInt): UInt = {
      if (x.getWidth <= 1) {
        x
      } else {
        val amt = n.pad(log2Ceil(x.getWidth))
        (0 until log2Ceil(x.getWidth)).foldLeft(x)((r, i) => Mux(amt(i), rotateLeft(r, 1 << i), r))
      }
    }
	val rotated = rotateLeft(Cat(io.in.reverse),expabs*conf.Qbit.U)
	for(i <- 0 until conf.N){
		io.out(i) := Mux(io.exponent(conf.Nbit)^(i.U<expabs),-rotated((i+1)*conf.Qbit-1,i*conf.Qbit),rotated((i+1)*conf.Qbit-1,i*conf.Qbit))
	}
}

class PolynomialMulByXaiMinusOne(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.N,UInt(conf.Qbit.W)))
		val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
		val exponent = Input(UInt((conf.Nbit+1).W))
		val cycle = Input(UInt(conf.cyclebit.W))
	})
	val mulbyxai = Module(new PolynomialMulByXai)
	mulbyxai.io.in := io.in
	mulbyxai.io.exponent := io.exponent
	val inbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W)))))
	val xaibus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W)))))
	for(i <- 0 until conf.numcycle){
		for(j <- 0 until conf.chunk){
			for(k <- 0 until conf.radix){
				inbus(i)(j)(k) := io.in(k*conf.block+i*conf.chunk+j)
				xaibus(i)(j)(k) := mulbyxai.io.out(k*conf.block+i*conf.chunk+j)
			}
		}
	}
	for(i <- 0 until conf.chunk){
		for(j <- 0 until conf.radix){
			io.out(i)(j) := xaibus(io.cycle)(i)(j) - inbus(io.cycle)(i)(j)
		}
	}
}

object BlindRotateState extends ChiselEnum {
  val INIT,RUN,FIN = Value
}

class BlindRotate(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.n+1,UInt(conf.Qbit.W)))
		val out = Output(Vec(2,Vec(conf.N,UInt(conf.Qbit.W))))
		val trgswin = Input(UInt((2*conf.fiber*64).W))
		val trgswaddress  = Output(UInt(log2Ceil(conf.n*2*conf.l*conf.numcycle).W))
		val enable = Input(Bool())
		val extpfin = Output(Bool())
		val fin = Output(Bool())
	})

	io.fin := false.B
	io.extpfin := false.B

	val BlindRotateRegister = Reg(Vec(2,Vec(conf.N,UInt(conf.Qbit.W))))
	io.out := BlindRotateRegister

	val extp = Module(new ExternalProduct)
	extp.io.enable := false.B
	extp.io.trgswin := io.trgswin

	val finreg = RegInit(0.U(2.W))
    when(extp.io.fin){
        finreg := finreg +1.U
    }
	
	val inbus = Wire(Vec(2,Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))))
    val outbus = Wire(Vec(2,Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))))
    for(l <- 0 until 2){
        for(i <- 0 until conf.numcycle){
            for(j <- 0 until conf.chunk){
                for(k <- 0 until conf.radix){
                    inbus(l)(i)(j)(k) := BlindRotateRegister(l)(k*conf.block+i*conf.chunk+j)
                    outbus(l)(i)(j)(k) := BlindRotateRegister(l)(k*conf.block+i*conf.chunk+j)
                    BlindRotateRegister(l)(k*conf.block+i*conf.chunk+j) := outbus(l)(i)(j)(k)
                }
            }
        }
    }

	extp.io.in := inbus(extp.io.sel)(extp.io.cycle)
    when(extp.io.validout){
		for(i <- 0 until conf.chunk){
			for(j <- 0 until conf.radix){
				outbus(extp.io.sel)(extp.io.cycle)(i)(j) := extp.io.out(i)(j) + inbus(extp.io.sel)(extp.io.cycle)(i)(j)
			}
		}
    }

	val brcntreg = RegInit(0.U(log2Ceil(conf.n).W))
	io.trgswaddress := brcntreg*(2*conf.l*conf.numcycle).U + (((conf.l).U*extp.io.sel + extp.io.digit)*conf.numcycle.U + extp.io.cycle)
	
	val xaimone = Module(new PolynomialMulByXaiMinusOne)
	xaimone.io.in := BlindRotateRegister(extp.io.sel)
	val roundoffset = 1L<<(conf.Qbit-conf.Nbit-2)
	xaimone.io.exponent := (io.in(brcntreg) + roundoffset.U)(conf.Qbit-1,conf.Qbit-(conf.Nbit+1))
	xaimone.io.cycle := extp.io.cycle
	extp.io.in := xaimone.io.out

	val statereg = RegInit(BlindRotateState.INIT)
	switch(statereg){
		is(BlindRotateState.INIT){
			for(i<-0 until conf.N){
				BlindRotateRegister(0)(i) := 0.U
			}
			val tvgen = Module(new RotatedTestVector)
			tvgen.io.exponent := (2*conf.N).U - io.in(conf.n)(conf.Qbit-1,conf.Qbit-(conf.Nbit+1))
			BlindRotateRegister(1) := tvgen.io.out
			when(io.enable){
				statereg := BlindRotateState.RUN
			}
		}
		is(BlindRotateState.RUN){
			extp.io.enable := true.B
			when(finreg===2.U){
				finreg := 0.U
				io.extpfin := true.B
				extp.io.enable := false.B
				when(brcntreg =/= (conf.n-1).U){
					brcntreg := brcntreg + 1.U
				}.otherwise{
					statereg := BlindRotateState.FIN
				}
			}
		}
		is(BlindRotateState.FIN){
			io.fin := true.B
		}
	}
	when(!io.enable){
		statereg := BlindRotateState.INIT
	}
}

class BlindRotateWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt(((conf.n+1)*conf.Qbit).W))
		val out = Output(UInt((2*conf.N*conf.Qbit).W))
		val trgswin = Input(UInt((2*conf.fiber*64).W))
		val extpfin = Output(Bool())
		val fin = Output(Bool())
	})

	val br = Module(new BlindRotate)
	br.io.trgswin := DontCare
	io.fin := br.io.fin
	for(i<-0 until conf.n+1){
		br.io.in(i) := io.in((i+1)*conf.Qbit-1,i*conf.Qbit)
	}
	io.out := Cat(br.io.out.flatten.reverse)
	io.extpfin := br.io.extpfin

	val bkmemaddr = Wire(UInt(log2Ceil(conf.n*2*conf.l*conf.numcycle).W))
    bkmemaddr := DontCare
    val bkmem = SyncReadMem(conf.n*2*conf.l*conf.numcycle,UInt((2*conf.fiber*64).W))
    val bkmemPort = bkmem(bkmemaddr)
	val bkmemwen = Wire(Bool())
	bkmemwen := false.B
	val bkmemWritedata = Wire(UInt((2*conf.fiber*64).W))
	val bkmemReaddata = Wire(UInt((2*conf.fiber*64).W))
	bkmemReaddata := DontCare
	bkmemWritedata := DontCare
	when(bkmemwen){
		bkmemPort := bkmemWritedata
	}.otherwise{
		bkmemReaddata := bkmemPort
	}

	val initreg = RegInit(false.B)
    val initcntreg = RegInit(0.U(log2Ceil(conf.n*2*conf.l*conf.numcycle).W))
    when(!initreg){
		br.io.enable := false.B
		bkmemwen := true.B
        bkmemWritedata := io.trgswin
		bkmemaddr := initcntreg
        when(initcntreg === (conf.n*2*conf.l*conf.numcycle - 1).U){
            initreg := true.B
        }.otherwise{
            initcntreg := initcntreg + 1.U
        }
    }.elsewhen(!br.io.fin){
        br.io.enable := true.B
        bkmemaddr := br.io.trgswaddress
        br.io.trgswin := bkmemReaddata
    }.otherwise{
		br.io.enable := true.B
	}
}

