import chisel3._
import chisel3.util._

class Butterfly extends Module{
    val io = IO(new Bundle {
        val A = Input(UInt(64.W))
        val B = Input(UInt(64.W))
        val ADDY = Output(UInt(64.W))
        val SUBY = Output(UInt(64.W))
    })

    val adder = Module(new INTorusADD)
    adder.io.A := io.A
    adder.io.B := io.B
    io.ADDY := adder.io.Y
    
    val suber = Module(new INTorusSUB)
    suber.io.A := io.A
    suber.io.B := io.B
    io.SUBY := suber.io.Y
}

class ButterflyADD(size: Int) extends Module{
    val io = IO(new Bundle {
        val in = Input(Vec(size,UInt(64.W)))
        val out = Output(Vec(size,UInt(64.W)))
    })
    val butterflys = for (i <- 0 until size/2) yield{
        val but = Module(new Butterfly)
        but
    }
    butterflys.zipWithIndex.map { case (but, i) =>
        but.io.A := io.in(i)
        but.io.B := io.in(i+size/2)
        io.out(i) := but.io.ADDY
        io.out(i+size/2) := but.io.SUBY
    }
}

class INTTradixButterflyUnit(radixbit : Int) extends Module{
    val size = 1<<radixbit
    val lshdelay = 3

    val io = IO(new Bundle{
        val in = Input(Vec(size,UInt(64.W)))
        val out = Output(Vec(size,UInt(64.W)))
    })

    val butadder = Module(new ButterflyADD(size))

    butadder.io.in := RegNext(io.in)

    val bus = Wire(Vec(size,UInt(64.W)))
    bus := ShiftRegister(butadder.io.out,lshdelay)

    for(i <- 1 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := RegNext(butadder.io.out(i + size / 2))
        Lsh.io.l := (3 * (i << (6 - radixbit))).U
        bus(i + size / 2) := Lsh.io.Y
    }

    if(radixbit > 1){
        val upper = Module(new INTTradixButterflyUnit(radixbit - 1))
        upper.io.in.zip(bus.slice(0,size/2)).foreach { case (a, b) => a:= b }
        io.out.slice(0,size/2).zip(upper.io.out).foreach { case (a, b) => a:= b }
        val lower = Module(new INTTradixButterflyUnit(radixbit - 1))
        lower.io.in.zip(bus.slice(size/2,size)).foreach { case (a, b) => a:= b }
        io.out.slice(size/2,size).zip(lower.io.out).foreach { case (a, b) => a:= b }
    }else{
        io.out := bus
    }
}

class FormerINTTradixButterflyUnit(radixbit : Int) extends Module{
    val size = 1<<radixbit
    val lshdelay = 3

    val io = IO(new Bundle{
        val in = Input(Vec(size,UInt(64.W)))
        val out = Output(Vec(size,UInt(64.W)))
    })

    val twistedbus = Wire(Vec(size,UInt(64.W)))
    twistedbus := ShiftRegister(io.in,lshdelay)

    for(i <- 0 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := RegNext(io.in(i + size / 2))
        Lsh.io.l := (3 * 16).U
        twistedbus(i + size / 2) := Lsh.io.Y
    }

    val butadder = Module(new ButterflyADD(size))

    butadder.io.in := RegNext(twistedbus)

    val bus = Wire(Vec(size,UInt(64.W)))
    bus := ShiftRegister(butadder.io.out,lshdelay)

    for(i <- 1 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := RegNext(butadder.io.out(i))
        Lsh.io.l := (3 * (i << (6 - (radixbit+1)))).U
        bus(i) := Lsh.io.Y
    }

    for(i <- 1 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := RegNext(butadder.io.out(i + size / 2))
        Lsh.io.l := (3 * ((i << (6 - radixbit))+(i << (6 - (radixbit+1))))).U
        bus(i + size / 2) := Lsh.io.Y
    }

    if(radixbit > 1){
        val upper = Module(new INTTradixButterflyUnit(radixbit - 1))
        upper.io.in.zip(bus.slice(0,size/2)).foreach { case (a, b) => a:= b }
        io.out.slice(0,size/2).zip(upper.io.out).foreach { case (a, b) => a:= b }
        val lower = Module(new INTTradixButterflyUnit(radixbit - 1))
        lower.io.in.zip(bus.slice(size/2,size)).foreach { case (a, b) => a:= b }
        io.out.slice(size/2,size).zip(lower.io.out).foreach { case (a, b) => a:= b }
    }else{
        io.out := bus
    }
}

class NTTradixButterflyUnit(radixbit : Int) extends Module{
    val size = 1<<radixbit
    val lshdelay = 3

    val io = IO(new Bundle {
        val in = Input(Vec(size,UInt(64.W)))
        val out = Output(Vec(size,UInt(64.W)))
    })

    val bus = Wire(Vec(size,UInt(64.W)))

    bus := io.in

    if(radixbit > 1){
        val upper = Module(new NTTradixButterflyUnit(radixbit - 1))
        upper.io.in.zip(io.in.slice(0,size/2)).foreach { case (a, b) => a:= b }
        bus.slice(0,size/2).zip(upper.io.out).foreach { case (a, b) => a:= b }
        val lower = Module(new NTTradixButterflyUnit(radixbit - 1))
        lower.io.in.zip(io.in.slice(size/2,size)).foreach { case (a, b) => a:= b }
        bus.slice(size/2,size).zip(lower.io.out).foreach { case (a, b) => a:= b }
    }

    val butadder = Module(new ButterflyADD(size))
    val buf = RegNext(bus)
    butadder.io.in := ShiftRegister(buf,lshdelay)

    for(i <- 1 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := buf(i + size / 2)
        Lsh.io.l := (3 * (64 - (i << (6 - radixbit)))).U
        butadder.io.in(i + size / 2) := RegNext(Lsh.io.Y)
    }

    io.out := butadder.io.out
}

class LaterNTTradixButterflyUnit(radixbit : Int) extends Module{
    val size = 1<<radixbit
    val lshdelay = 3

    val io = IO(new Bundle {
        val in = Input(Vec(size,UInt(64.W)))
        val out = Output(Vec(size,UInt(64.W)))
    })

    val bus = Wire(Vec(size,UInt(64.W)))

    bus := io.in

    if(radixbit > 1){
        val upper = Module(new NTTradixButterflyUnit(radixbit - 1))
        upper.io.in.zip(io.in.slice(0,size/2)).foreach { case (a, b) => a:= b }
        bus.slice(0,size/2).zip(upper.io.out).foreach { case (a, b) => a:= b }
        val lower = Module(new NTTradixButterflyUnit(radixbit - 1))
        lower.io.in.zip(io.in.slice(size/2,size)).foreach { case (a, b) => a:= b }
        bus.slice(size/2,size).zip(lower.io.out).foreach { case (a, b) => a:= b }
    }

    val butadder = Module(new ButterflyADD(size))
    val buf = RegNext(bus)
    butadder.io.in := ShiftRegister(buf,lshdelay)

    for(i <- 1 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := buf(i)
        Lsh.io.l := (3 * (64 - (i << (6 - (radixbit+1))))).U
        butadder.io.in(i) := RegNext(Lsh.io.Y)
    }

    for(i <- 1 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := buf(i + size / 2)
        Lsh.io.l := (3 * (64 - (i << (6 - radixbit)) - (i << (6 - (radixbit+1))))).U
        butadder.io.in(i + size / 2) := RegNext(Lsh.io.Y)
    }

    io.out := ShiftRegister(butadder.io.out,lshdelay)

    for(i <- 0 until 1<< (radixbit-1)){
        val Lsh = Module(new INTorusLSH()(Config()))
        Lsh.io.A := RegNext(butadder.io.out(i + size / 2))
        Lsh.io.l := (3 * (64-16)).U
        io.out(i + size / 2) := Lsh.io.Y
    }
}
