import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class Decomposition(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
		val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
		val digit = Input(UInt(log2Ceil(conf.l).W))
	})

	def offsetgen(implicit conf: Config): Long = {
		var offset :Long = 0
		for(i <- 1 to conf.l){
			offset = offset + conf.Bg/2 * (1L<<(conf.Qbit - i * conf.Bgbit))
		}
		offset
	}

	val offset: Long = offsetgen(conf)
	val raundoffset: Long = 1L << (conf.Qbit - conf.l * conf.Bgbit - 1)

	for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
        val addedoffset = io.in(i)(j) + (offset + raundoffset).U
		val extnum = Wire(Vec(conf.l,UInt(conf.Qbit.W)))
            for(k <- 0 until conf.l){
                extnum(k) := addedoffset(conf.Qbit-k*conf.Bgbit-1,conf.Qbit-(k+1)*conf.Bgbit)
            }
			io.out(i)(j) := extnum(io.digit) - (conf.Bg/2).U
        }
	}
}

class MULandACC(implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
		val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
		val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
        val trgswin = Input(Vec(2,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
        val inttvalidout = Input(Bool())
        val enable = Input(Bool())
        val inttcycle = Input(UInt(conf.cyclebit.W))
        val nttcycle = Input(UInt(conf.cyclebit.W))
        val nttsel = Input(UInt(1.W))
	})

    val inttoutbuf = Reg(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
    val acc = Reg(Vec(2,Vec(conf.N,UInt(64.W))))
    val cyclereg = RegInit(0.U(conf.cyclebit.W))

    inttoutbuf := io.in
    cyclereg := io.inttcycle

    val outbus = Wire(Vec(2,Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))))
    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                outbus(0)(i)(j)(k) := acc(0)(i*conf.fiber+j*conf.radix+k)
                outbus(1)(i)(j)(k) := acc(1)(i*conf.fiber+j*conf.radix+k)
            }
        }
    }
    io.out := outbus(io.nttsel)(io.nttcycle)

    when(io.enable){
        when(RegNext(io.inttvalidout)){
            for(i<-0 until 2){
                val inbus = Wire(Vec(conf.numcycle,Vec(conf.fiber,UInt(64.W))))
                val accbus = Wire(Vec(conf.numcycle,Vec(conf.fiber,UInt(64.W))))
                for(j <- 0 until conf.numcycle){
                    inbus(j).zip(acc(i).slice(j*conf.fiber,(j+1)*conf.fiber)).foreach { case (a, b) => a:= b }
                    accbus(j).zip(acc(i).slice(j*conf.fiber,(j+1)*conf.fiber)).foreach { case (a, b) => a:= b }
                    accbus(j).zip(acc(i).slice(j*conf.fiber,(j+1)*conf.fiber)).foreach { case (a, b) => b:= a }
                }

                for(j <- 0 until conf.chunk){
                    for(k <- 0 until conf.radix){    
                        val mul = Module(new INTorusMUL)
                        mul.io.A := inttoutbuf(j)(k)
                        mul.io.B := io.trgswin(i)(j)(k)
                        val add = Module(new INTorusADD)
                        add.io.A := mul.io.Y
                        add.io.B := inbus(cyclereg)(j*conf.radix+k)
                        accbus(cyclereg)(j*conf.radix+k) := add.io.Y
                    }
                }
            }
        }.otherwise{
            acc := acc
        }
    }.otherwise{
        for(i<-0 until 2){
            for(j<-0 until conf.N){
                acc(i)(j) := 0.U
            }
        }
    }
}

object ExternalProductState extends ChiselEnum {
  val WAIT, INTT, NTT = Value
}

class ExternalProduct(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(32.W))))
        val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(32.W))))
		val trgswin = Input(UInt((2*conf.fiber*64).W))
        val enable = Input(Bool())
        val validout = Output(Bool())
        val fin = Output(Bool())
        val digit = Output(UInt(log2Ceil(conf.l).W))
        val cycle = Output(UInt(conf.cyclebit.W))//used for output and TRGSW memory
        val sel = Output(UInt(1.W))
	})

    val selreg = RegInit(0.U(1.W))
    io.sel := selreg

	val decomp = Module(new Decomposition)

    val digitreg = RegInit(0.U(log2Ceil(conf.l).W))

    io.digit := digitreg
    decomp.io.digit := digitreg
    decomp.io.in := io.in

    val intt = Module(new INTT)

    intt.io.in := decomp.io.out
    intt.io.enable := false.B
    io.cycle := DontCare

    val mulandacc = Module(new MULandACC)
    mulandacc.io.in := intt.io.out
    for(i<-0 until 2){
        for(j<-0 until conf.chunk){
            for(k <- 0 until conf.radix){
                val index = i*conf.fiber+j*conf.radix+k
                mulandacc.io.trgswin(i)(j)(k) := io.trgswin(index*64+63,index*64)
            }
        }
    }
    mulandacc.io.inttvalidout := intt.io.validout
    mulandacc.io.inttcycle := intt.io.cycle
    mulandacc.io.enable := false.B
    mulandacc.io.nttsel := selreg

    val ntt = Module(new NTT)

    ntt.io.enable := false.B
    ntt.io.in := mulandacc.io.out
    io.fin := ntt.io.fin
    mulandacc.io.nttcycle := ntt.io.cycle

    for(i<-0 until conf.chunk){
        for(j<-0 until conf.radix){
            io.out(i)(j) := ntt.io.out(i)(j)(31,0)
        }
    }
    io.validout := ntt.io.validout

    val state = RegInit(ExternalProductState.WAIT)
    switch(state){
        is(ExternalProductState.WAIT){
            when(io.enable){
                state := ExternalProductState.INTT
            }
        }

        is(ExternalProductState.INTT){
            intt.io.enable := true.B
            mulandacc.io.enable := true.B
            io.cycle := intt.io.cycle
            when(intt.io.fin){
                when(digitreg === (conf.l - 1).U){
                    digitreg := 0.U
                    when(selreg === 1.U){
                        selreg := 0.U
                        state := ExternalProductState.NTT
                    }.otherwise{
                        selreg := 1.U
                        state := ExternalProductState.INTT
                    }
                }.otherwise{
                    digitreg := digitreg + 1.U
                }
            }
        }

        is(ExternalProductState.NTT){
            ntt.io.enable := true.B
            mulandacc.io.enable := true.B
            io.cycle := ntt.io.cycle
            when(ntt.io.fin){
                when(selreg === 1.U){
                    selreg := 0.U
                    state := ExternalProductState.WAIT
                }.otherwise{
                    selreg := 1.U
                    state := ExternalProductState.NTT
                }
            }
        }
    }
    when(!io.enable){
        state := ExternalProductState.WAIT
    }
}