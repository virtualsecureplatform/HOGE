import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

class Lbuffer(val width:Int , val delay:Int, implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
        val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(width.W))))
        val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(width.W))))
        val validin = Input(Bool())
        val validout = Output(Bool())
    })
    io.validout := false.B

    val regarray = Reg(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(width.W)))))

    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.numcycle){
            io.out(i)(j) := regarray(j)(i)(0)
        }
    }
    regarray(conf.numcycle-1) := io.in
    for(i <- 0 until conf.numcycle-1){
        regarray(i) := regarray(i+1)
    }
    when(ShiftRegister(io.validin,conf.numcycle+delay)){
        io.validout := true.B
        for(i <- 0 until conf.numcycle){
            for(j <- 0 until conf.chunk){
                for(k <- 0 until conf.radix-1){
                    regarray(i)(j)(k) := regarray(i)(j)(k+1)
                }
            }
        }
    }
}

class DoubleLbuffer(val width:Int , val delay:Int, implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
        val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(width.W))))
        val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(width.W))))
        val validin = Input(Bool())
        val validout = Output(Bool())
    })
    val validregarray = Reg(Vec(conf.numcycle,Bool()))
    val columnregarray = Reg(Vec(conf.numcycle-1,Vec(conf.chunk,Vec(conf.radix,UInt(width.W)))))
    val rowregarray = Reg(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(width.W)))))

    io.validout := validregarray(0)
    io.out := rowregarray(0)

    validregarray(conf.numcycle-1) := false.B
    columnregarray(conf.numcycle-2) := io.in
    for(i <- 0 until conf.radix){
        rowregarray(conf.numcycle-1)(0)(i) := 0.U
    }
    for(i <- 0 until conf.numcycle-1){
        validregarray(i) := validregarray(i+1)
        rowregarray(i) := rowregarray(i+1)
    }
    for(i <- 0 until conf.numcycle-2){
        columnregarray(i) := columnregarray(i+1)
    }

    when(ShiftRegister(io.validin,conf.numcycle+delay-1)){
        for(i <- 0 until conf.numcycle){
            for(j <- 0 until conf.chunk){
                for(k <- 0 until conf.radix-1){
                    rowregarray(i)(j)(k) := columnregarray(k)(j)(i)
                }
                rowregarray(i)(j)(conf.radix-1) := io.in(j)(i)
            }
            validregarray(i) := true.B
        }
    }
}

class NTTport (implicit val conf:Config) extends Bundle{
    val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
    val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
    val enable = Input(Bool())
    val fin = Output(Bool())
    val validout = Output(Bool())
    val ready = Output(Bool())
}

// Assuming Queue(no bubble)
class INTT(implicit val conf:Config) extends Module{
    val io = IO(new NTTport)

    val inttbuts = for(i <- 0 until conf.chunk) yield{
        val inttbut = Module(new INTTradixButterflyUnit(conf.radixbit))
        inttbut
    }

    val cyclereg = RegInit(0.U(conf.cyclebit.W))
    val stepreg = RegInit(0.U(conf.stepbit.W))
    val inttlbuf = Module(new Lbuffer(64,conf.radixdelay*conf.radixbit+2+conf.muldelay,conf))
    for(i <- 0 until conf.chunk){
        inttlbuf.io.in(i) := RegNext(inttbuts(i).io.out)
    }

    io.fin := false.B

    def reverse(in: Int, n: Int, out: Int = 0): Int =
        if (n == 0) out
        else reverse(in >>> 1, n - 1, (out << 1) | (in & 1))

    var twidlelist: Array[BigInt] = Array.fill(conf.N)(0)
    val twistbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                twidlelist(k*conf.block+i*conf.chunk+j) = conf.intttable(reverse(k,conf.radixbit)*(i*conf.chunk+j))
                twistbus(i)(j)(k) := conf.intttwist(k*conf.block+i*conf.chunk+j).U
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

    val cntreg = RegInit(0.U(log2Ceil(conf.radixdelay*conf.radixbit+2+conf.muldelay+conf.numcycle).W))
    val readyreg = RegInit(true.B)
    io.ready := io.enable && readyreg && (~cntreg(conf.cyclebit))
    when(cntreg(conf.cyclebit)&&RegNext(~cntreg(conf.cyclebit))){
        readyreg := false.B
    }

    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
            val mul = Module(new INTorusMUL)
            mul.io.A := Mux(RegNext(stepreg === 1.U), RegNext(inttlbuf.io.out(i)(j)), RegNext(io.in(i)(j)))
            mul.io.B := Mux(RegNext(stepreg === 1.U), twidlebus(RegNext(cntreg))(i)(j), twistbus(RegNext(cntreg))(i)(j))
            inttbuts(i).io.in(j) := mul.io.Y
        }
        io.out(i) := RegNext(inttbuts(i).io.out)
    }

    inttlbuf.io.validin := io.enable && (stepreg === 0.U) && (cntreg < conf.numcycle.U)
    io.validout := stepreg===(conf.numstep-1).U && (cntreg >= (conf.radixdelay*conf.radixbit+2+conf.muldelay).U)
    when(io.enable){
        cntreg := cntreg + 1.U
        when(cntreg === (conf.radixdelay*conf.radixbit+2+conf.muldelay+conf.numcycle-1).U){
            cntreg := 0.U
            when(stepreg === (conf.numstep-1).U){
                stepreg := 0.U
                readyreg := true.B
                io.fin := true.B
            }.otherwise{
                stepreg := stepreg + 1.U
            }
        }
    }.otherwise{
        cntreg := 0.U
        readyreg := true.B
        stepreg := 0.U
    }
}

// Assuming MULandACC as Queue(no bubble)
// outlbuf is merged
class NTT(implicit val conf:Config) extends Module{
    val io = IO(new NTTport)
    io.ready := false.B

    val nttbuts = for(i <- 0 until conf.chunk) yield{
        val nttbut = Module(new NTTradixButterflyUnit(conf.radixbit))
        nttbut
    }

    val muls = for(i <- 0 until conf.fiber) yield{
        val mul = Module(new INTorusMUL)
        mul
    }

    val cyclereg = RegInit(0.U(conf.cyclebit.W))
    val stepreg = RegInit(0.U((conf.stepbit+1).W))
    val nttlbuf = Module(new DoubleLbuffer(64,conf.radixdelay*conf.radixbit+2+conf.muldelay+1,conf))
    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
            nttlbuf.io.in(i)(j) := ShiftRegister(muls(i*conf.radix+j).io.Y,2)
        }
    }

    io.fin := false.B

    def reverse(in: Int, n: Int, out: Int = 0): Int =
        if (n == 0) out
        else reverse(in >>> 1, n - 1, (out << 1) | (in & 1))

    val twidlebus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    val twistbus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    var twidlelist: Array[BigInt] = Array.fill(conf.N)(0)
    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                twistbus(i)(j)(k) := conf.ntttwist(k*conf.block+i*conf.chunk+j).U
                twidlelist(k*conf.block+i*conf.chunk+j) = conf.ntttable(reverse(k,conf.radixbit)*(i*conf.chunk+j))
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

    val cntreg = RegInit(0.U(log2Ceil(conf.radixdelay*conf.radixbit+2+conf.muldelay+conf.numcycle+1).W))
    io.ready := io.enable && (stepreg===0.U) && (cntreg < (2*conf.numcycle).U)

    val shiftcycle = ShiftRegister(cntreg,conf.radixdelay*conf.radixbit)
    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
            nttbuts(i).io.in(j) := Mux(stepreg=/=0.U,nttlbuf.io.out(i)(j),io.in(i)(j))
            muls(i*conf.radix+j).io.A := RegNext(nttbuts(i).io.out(j))
            muls(i*conf.radix+j).io.B :=  RegNext(Mux(ShiftRegister(stepreg=/=0.U,conf.radixdelay*conf.radixbit),twistbus(shiftcycle)(i)(j),twidlebus(shiftcycle)(i)(j)))
        }
    }
    io.out := nttlbuf.io.out

    nttlbuf.io.validin := io.enable && (((cntreg === 0.U) || (cntreg === conf.numcycle.U)&&(stepreg===0.U)) || ((nttlbuf.io.validout && RegNext(~nttlbuf.io.validout)) || ShiftRegister((nttlbuf.io.validout && RegNext(~nttlbuf.io.validout)),conf.numcycle))&&(stepreg===1.U))
    io.validout := ShiftRegister((stepreg===1.U),conf.radixdelay*conf.radixbit+2+conf.muldelay+conf.numcycle) && nttlbuf.io.validout
    io.fin := ~io.validout && RegNext(io.validout)
    when(io.enable){
        cntreg := cntreg + 1.U

        when(cntreg === (conf.radixdelay*conf.radixbit+2+conf.muldelay+conf.numcycle+1-1).U){
            cntreg := 0.U
            when(stepreg =/= (conf.numstep).U){
                stepreg := stepreg + 1.U
            }
        }
    }.otherwise{
        cntreg := 0.U
        stepreg := 0.U
    }
}