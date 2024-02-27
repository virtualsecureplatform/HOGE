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
    val validin = Input(Bool())
    val validout = Output(Bool())
}

// Assuming Queue(no bubble)
class INTT(implicit val conf:Config) extends Module{
    val io = IO(new NTTport)

    val formerinttbuts = for(i <- 0 until conf.chunk) yield{
        val inttbut = Module(new FormerINTTradixButterflyUnit(conf.radixbit))
        inttbut
    }
    val laterinttbuts = for(i <- 0 until conf.chunk) yield{
        val inttbut = Module(new INTTradixButterflyUnit(conf.radixbit))
        inttbut
    }

    val inttlbuf = Module(new DoubleLbuffer(64,conf.lshdelay+conf.radixdelay*conf.radixbit+2+conf.muldelay,conf))

    def reverse(in: Int, n: Int, out: Int = 0): Int =
        if (n == 0) out
        else reverse(in >>> 1, n - 1, (out << 1) | (in & 1))

    val twidlebus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                twidlebus(i)(j)(k) := ((((conf.intttable(reverse(k,conf.radixbit)*(i*conf.chunk+j))*conf.intttwist(i*conf.chunk+j)) % conf.P) + conf.P) % conf.P).U
            }
        }
    }

    val cntreg = RegInit(0.U(conf.cyclebit.W))

    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
            formerinttbuts(i).io.in(j) := RegNext(io.in(i)(j))
            val twidlemul = Module(new INTorusMUL)
            twidlemul.io.A := formerinttbuts(i).io.out(j)
            twidlemul.io.B := RegNext(twidlebus(ShiftRegister(cntreg,conf.radixdelay*conf.radixbit+conf.lshdelay))(i)(j))
            inttlbuf.io.in(i)(j) := RegNext(twidlemul.io.Y)
        }
        laterinttbuts(i).io.in := inttlbuf.io.out(i)
        io.out(i) := RegNext(laterinttbuts(i).io.out)
    }

    inttlbuf.io.validin := io.validin && (cntreg === 0.U)
    io.validout := ShiftRegister(inttlbuf.io.validout,conf.radixdelay*conf.radixbit+1)
    when(io.validin){
        cntreg := cntreg + 1.U
        when(cntreg === (conf.numcycle-1).U){
            cntreg := 0.U
        }
    }.otherwise{
        cntreg := 0.U
    }
}

// Assuming MULandACC as Queue(no bubble)
class NTT(implicit val conf:Config) extends Module{
    val io = IO(new NTTport)

    val formernttbuts = for(i <- 0 until conf.chunk) yield{
        val nttbut = Module(new NTTradixButterflyUnit(conf.radixbit))
        nttbut
    }

    val laternttbuts = for(i <- 0 until conf.chunk) yield{
        val nttbut = Module(new LaterNTTradixButterflyUnit(conf.radixbit))
        nttbut
    }

    val nttlbuf = Module(new DoubleLbuffer(64,1+conf.radixdelay*conf.radixbit,conf))

    def reverse(in: Int, n: Int, out: Int = 0): Int =
        if (n == 0) out
        else reverse(in >>> 1, n - 1, (out << 1) | (in & 1))

    val twidlebus = Wire(Vec(conf.numcycle,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
    for(i <- 0 until conf.numcycle){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                twidlebus(i)(j)(k) := ((((conf.ntttable(reverse(k,conf.radixbit)*(i*conf.chunk+j))*conf.ntttwist(i*conf.chunk+j)) % conf.P) + conf.P) % conf.P).U
            }
        }
    }

    val cntreg = RegInit(0.U(conf.cyclebit.W))
    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
            formernttbuts(i).io.in(j) := RegNext(io.in(i)(j))
            nttlbuf.io.in(i)(j) := formernttbuts(i).io.out(j)
            val twidlemul = Module(new INTorusMUL)
            twidlemul.io.A := RegNext(nttlbuf.io.out(i)(j))
            twidlemul.io.B := RegNext(twidlebus(ShiftRegister(cntreg,1+conf.radixdelay*conf.radixbit+conf.numcycle))(i)(j))
            laternttbuts(i).io.in(j) := RegNext(twidlemul.io.Y)
        }
        io.out(i) := RegNext(laternttbuts(i).io.out)
    }

    nttlbuf.io.validin := io.validin && (cntreg === 0.U)
    io.validout := ShiftRegister(nttlbuf.io.validout,2+conf.muldelay+conf.radixdelay*conf.radixbit+conf.lshdelay+1)
    when(io.validin){
        cntreg := cntreg + 1.U
        when(cntreg === (conf.numcycle-1).U){
            cntreg := 0.U
        }
    }.otherwise{
        cntreg := 0.U
    }
}