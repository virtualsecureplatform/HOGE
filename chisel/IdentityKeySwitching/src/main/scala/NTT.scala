import chisel3._
import chisel3.util._

class NTTport (implicit val conf:Config) extends Bundle{
    val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
    val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
    val cycle = Output(UInt(conf.cyclebit.W))
    val enable = Input(Bool())
    val fin = Output(Bool())
    val validout = Output(Bool())
}

class INTT(implicit val conf:Config) extends Module{
    val io = IO(new NTTport)

    val inttbuts = for(i <- 0 until conf.chunk) yield{
        val inttbut = Module(new INTTradixButterflyUnit(conf.radixbit))
        inttbut
    }

    val muls = for(i <- 0 until conf.fiber) yield{
        val mul = Module(new INTorusMUL)
        mul
    }

    val cyclereg = RegInit(0.U(conf.cyclebit.W))
    val stepreg = RegInit(0.U(conf.stepbit.W))
    val buf = Reg(Vec(conf.N,UInt(64.W)))

    io.validout := stepreg===(conf.numstep-1).U
    io.fin := false.B
    io.cycle := cyclereg

    def reverse(in: Int, n: Int, out: Int = 0): Int =
        if (n == 0) out
        else reverse(in >>> 1, n - 1, (out << 1) | (in & 1))

    val inbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    val outbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    var twidlelist: Array[BigInt] = Array.fill(conf.N)(0)
    val twistbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                inbus(i)(j)(k) := buf(i*conf.fiber+j*conf.radix+k)
                outbus(i)(j)(k) := buf(k*conf.block+i*conf.chunk+j)
                twidlelist(k*conf.block+i*conf.chunk+j) = conf.intttable(reverse(k,conf.radixbit)*(i*conf.chunk+j))
                twistbus(i)(j)(k) := conf.intttwist(k*conf.block+i*conf.chunk+j).U
                buf(k*conf.block+i*conf.chunk+j) := outbus(i)(j)(k)
            }
        }
    }

    val twidlebus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))

    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                twidlebus(i)(j)(k) := twidlelist(i*conf.fiber+j*conf.radix+k).U
            }
        }
    }

    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
            muls(i*conf.radix+j).io.A := Mux(stepreg === 1.U, inbus(cyclereg)(i)(j), io.in(i)(j))
            muls(i*conf.radix+j).io.B := Mux(stepreg === 1.U, twidlebus(cyclereg)(i)(j), twistbus(cyclereg)(i)(j))
            inttbuts(i).io.in(j) := muls(i*conf.radix+j).io.Y
        }
        io.out(i) := inttbuts(i).io.out
    }

    when(io.enable){
        when(stepreg===0.U){
            for(i <- 0 until conf.chunk){
                outbus(cyclereg)(i) := inttbuts(i).io.out
            }
        }

        when(cyclereg =/= (conf.numcycle - 1).U){
            cyclereg := cyclereg + 1.U
            stepreg := stepreg
        }.otherwise{
            cyclereg := 0.U
            when(stepreg === (conf.numstep-1).U){
                stepreg := 0.U
                io.fin := true.B
            }.otherwise{
                stepreg := stepreg + 1.U
            }
        }
    }.otherwise{
        cyclereg := 0.U
        stepreg := 0.U
    }
}

class NTT(implicit val conf:Config) extends Module{
    val io = IO(new NTTport)

    val nttbuts = for(i <- 0 until conf.chunk) yield{
        val nttbut = Module(new NTTradixButterflyUnit(conf.radixbit))
        nttbut
    }

    val muls = for(i <- 0 until conf.fiber) yield{
        val mul = Module(new INTorusMUL)
        mul
    }

    val cyclereg = RegInit(0.U(conf.cyclebit.W))
    val stepreg = RegInit(0.U(conf.stepbit.W))
    val buf = Reg(Vec(conf.N,UInt(64.W)))

    io.validout := stepreg===(conf.numstep-1).U
    io.fin := false.B
    io.cycle := cyclereg

    def reverse(in: Int, n: Int, out: Int = 0): Int =
        if (n == 0) out
        else reverse(in >>> 1, n - 1, (out << 1) | (in & 1))

    val inbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    val outbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    val twidlebus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    val twistbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    var twidlelist: Array[BigInt] = Array.fill(conf.N)(0)
    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                inbus(i)(j)(k) := buf(k*conf.block+i*conf.chunk+j)
                outbus(i)(j)(k) := buf(i*conf.fiber+j*conf.radix+k)
                twistbus(i)(j)(k) := conf.ntttwist(k*conf.block+i*conf.chunk+j).U
                twidlelist(k*conf.block+i*conf.chunk+j) = conf.ntttable(reverse(k,conf.radixbit)*(i*conf.chunk+j))
                buf(i*conf.fiber+j*conf.radix+k) := outbus(i)(j)(k)
            }
        }
    }

    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                twidlebus(i)(j)(k) := twidlelist(i*conf.fiber+j*conf.radix+k).U
            }
        }
    }

    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
            nttbuts(i).io.in(j) := Mux(stepreg===1.U,inbus(cyclereg)(i)(j),io.in(i)(j))
            muls(i*conf.radix+j).io.A := nttbuts(i).io.out(j)
            muls(i*conf.radix+j).io.B :=  Mux(stepreg===1.U,twistbus(cyclereg)(i)(j),twidlebus(cyclereg)(i)(j))
            io.out(i)(j) := muls(i*conf.radix+j).io.Y
        }
    }

    when(io.enable){
        when(stepreg===0.U){
            for(i <- 0 until conf.chunk){
                for(j <- 0 until conf.radix){
                    outbus(cyclereg)(i)(j) := muls(i*conf.radix+j).io.Y
                }
            }
        }

        when(cyclereg =/= (conf.numcycle - 1).U){
            cyclereg := cyclereg + 1.U
            stepreg := stepreg
        }.otherwise{
            cyclereg := 0.U
            when(stepreg === (conf.numstep-1).U){
                stepreg := 0.U
                io.fin := true.B
            }.otherwise{
                stepreg := stepreg + 1.U
            }
        }
    }.otherwise{
        cyclereg := 0.U
        stepreg := 0.U
    }
}

class NTTid(conf:Config) extends Module{
    val io = IO(new Bundle{
        val in = Input(Vec(conf.N,UInt(64.W)))
        val out = Output(Vec(conf.N,UInt(64.W)))
    })
    val intt = Module(new INTT()(conf))
    val ntt = Module(new NTT()(conf))
    val buf = Reg(Vec(conf.N,UInt(64.W)))
    val invreg = RegInit(true.B)
    val cyclereg = RegInit(0.U((conf.cyclebit+1).W))
    
    io.out := buf

    intt.io.in := DontCare
    ntt.io.in := DontCare

    intt.io.enable := invreg
    ntt.io.enable := !invreg
    invreg := invreg
    when(invreg){
        val inbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
        val outbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
        for(i <- 0 until conf.numcycle){
            for(j <- 0 until conf.chunk){
                for(k <- 0 until conf.radix){
                    inbus(i)(j)(k) := io.in(k*conf.block+i*conf.chunk+j)
                    outbus(i)(j)(k) := buf(i*conf.fiber+j*conf.radix+k)
                    buf(i*conf.fiber+j*conf.radix+k) := outbus(i)(j)(k)
                }
            }
        }

        intt.io.in := inbus(intt.io.cycle)
        when(intt.io.fin){
            outbus(intt.io.cycle) := intt.io.out
        }
        when(!intt.io.fin && RegNext(intt.io.fin)){
            invreg := false.B
        }
    }.otherwise{
        val inbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
        val outbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
        for(i <- 0 until conf.numcycle){
            for(j <- 0 until conf.chunk){
                for(k <- 0 until conf.radix){
                    inbus(i)(j)(k) := buf(i*conf.fiber+j*conf.radix+k)
                    outbus(i)(j)(k) := buf(k*conf.block+i*conf.chunk+j)
                    buf(k*conf.block+i*conf.chunk+j) := outbus(i)(j)(k)
                }
            }
        }

        ntt.io.in := inbus(ntt.io.cycle)
        when(ntt.io.fin){
            outbus(ntt.io.cycle) := ntt.io.out
        }
        when(!ntt.io.fin && RegNext(ntt.io.fin)){
            invreg := true.B
        }
    }
}