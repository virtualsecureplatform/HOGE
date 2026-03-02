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

object SwitchTransposeUnitState extends ChiselEnum{
    val WAIT,INIT,RUN,LAST = Value
}

class SwitchTransposeUnit(widthbit : Int, cyclebit : Int) extends Module{
    val width = 1<<widthbit
    val halfcycle = 1<<(cyclebit-1)
    val io = IO(new Bundle{
        val in = Input(Vec(width,UInt(64.W)))
        val out = Output(Vec(width,UInt(64.W)))
        val validin = Input(Bool())
        val validout = Output(Bool())
    })

    val validreg = RegInit(false.B)
    val selreg = RegInit(false.B)
    io.validout := validreg
    for(i <- 0 until width/2){
        val delayedin = ShiftRegister(io.in(i+width/2),width/2)
        io.out(i) := ShiftRegister(Mux(selreg,delayedin,io.in(i)),width/2)
        io.out(i+width/2) := Mux(~selreg,delayedin,io.in(i))
    }

    if(cyclebit > 1){
        val cntreg = RegInit(0.U((cyclebit-1).W))
        val statereg = RegInit(SwitchTransposeUnitState.WAIT)
        switch(statereg){
            is(SwitchTransposeUnitState.WAIT){
                when(io.validin){
                    statereg := SwitchTransposeUnitState.INIT
                    cntreg := cntreg + 1.U
                }
            }
            is(SwitchTransposeUnitState.INIT){
                cntreg := cntreg + 1.U
                when(cntreg === (halfcycle-1).U){
                    selreg := ~selreg
                    cntreg := 0.U
                    validreg := true.B
                    statereg := SwitchTransposeUnitState.RUN
                }
            }
            is(SwitchTransposeUnitState.RUN){
                cntreg := cntreg + 1.U
                when(cntreg === (halfcycle-1).U){
                    selreg := ~selreg
                    cntreg := 0.U
                when((~selreg)&(~io.validin)){
                        validreg := false.B
                        selreg := false.B
                        statereg := SwitchTransposeUnitState.WAIT
                    }
                }
            }
            is(SwitchTransposeUnitState.LAST){
                cntreg := cntreg + 1.U
                when(cntreg === (halfcycle-1).U){
                    cntreg := 0.U
                    validreg := false.B
                    selreg := false.B
                    statereg := SwitchTransposeUnitState.WAIT
                }
            }
        }
    }else{
        validreg := io.validin
        when(io.validin){
            selreg := ~selreg
        }.otherwise{
            selreg := false.B
        }
    }
}

class FormerINTTradixButterflyUnit(radixbit : Int, top : Boolean) extends Module{
    val size = 1<<radixbit
    val lshdelay = 3

    val io = IO(new Bundle{
        val in = Input(Vec(size,UInt(64.W)))
        val out = Output(Vec(size,UInt(64.W)))
        val validin = Input(Bool())
        val validout = Output(Bool())
    })


    val butadder = Module(new ButterflyADD(size))
    val switchtran = Module(new SwitchTransposeUnit(radixbit,radixbit))

    if(top){
        val twistedbus = Wire(Vec(size,UInt(64.W)))
        twistedbus := ShiftRegister(io.in,lshdelay)

        for(i <- 0 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := RegNext(io.in(i + size / 2))
            Lsh.io.l := (3 * 16).U
            twistedbus(i + size / 2) := Lsh.io.Y
        }
        butadder.io.in := RegNext(twistedbus)
        switchtran.io.validin := ShiftRegister(io.validin,lshdelay+1+1+lshdelay)
    }else{
        butadder.io.in := RegNext(io.in)
        switchtran.io.validin := ShiftRegister(io.validin,1+1+lshdelay)
    }
    switchtran.io.in := ShiftRegister(butadder.io.out,lshdelay)
    if(top){
        for(i <- 1 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := RegNext(butadder.io.out(i))
            Lsh.io.l := (3 * (i << (6 - (radixbit+1)))).U
            switchtran.io.in(i) := Lsh.io.Y
        }

        for(i <- 1 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := RegNext(butadder.io.out(i + size / 2))
            Lsh.io.l := (3 * ((i << (6 - radixbit))+(i << (6 - (radixbit+1))))).U
            switchtran.io.in(i + size / 2) := Lsh.io.Y
        }
    }else{
        for(i <- 1 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := RegNext(butadder.io.out(i + size / 2))
            Lsh.io.l := (3 * (i << (6 - radixbit))).U
            switchtran.io.in(i + size / 2) := Lsh.io.Y
        }
    }


    if(radixbit > 1){
        val upper = Module(new FormerINTTradixButterflyUnit(radixbit - 1,false))
        upper.io.in.zip(switchtran.io.out.slice(0,size/2)).foreach { case (a, b) => a:= b }
        upper.io.validin := switchtran.io.validout
        io.out.slice(0,size/2).zip(upper.io.out).foreach { case (a, b) => a:= b }
        val lower = Module(new FormerINTTradixButterflyUnit(radixbit - 1,false))
        lower.io.in.zip(switchtran.io.out.slice(size/2,size)).foreach { case (a, b) => a:= b }
        lower.io.validin := switchtran.io.validout
        io.out.slice(size/2,size).zip(lower.io.out).foreach { case (a, b) => a:= b }
        io.validout := upper.io.validout
    }else{
        io.out := switchtran.io.out
        io.validout := switchtran.io.validout
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

class LaterNTTradixButterflyUnit(radixbit : Int,top: Boolean) extends Module{
    val size = 1<<radixbit
    val lshdelay = 3

    val io = IO(new Bundle {
        val in = Input(Vec(size,UInt(64.W)))
        val out = Output(Vec(size,UInt(64.W)))
        val validin = Input(Bool())
        val validout = Output(Bool())
    })

    val switchtran = Module(new SwitchTransposeUnit(radixbit,radixbit))

    if(radixbit > 1){
        val bus = Wire(Vec(size,UInt(64.W)))
        val upper = Module(new LaterNTTradixButterflyUnit(radixbit - 1, false))
        upper.io.validin := io.validin
        upper.io.in.zip(io.in.slice(0,size/2)).foreach { case (a, b) => a:= b }
        bus.slice(0,size/2).zip(upper.io.out).foreach { case (a, b) => a:= b }
        val lower = Module(new LaterNTTradixButterflyUnit(radixbit - 1, false))
        lower.io.validin := io.validin
        lower.io.in.zip(io.in.slice(size/2,size)).foreach { case (a, b) => a:= b }
        bus.slice(size/2,size).zip(lower.io.out).foreach { case (a, b) => a:= b }
        switchtran.io.in := RegNext(bus)
        switchtran.io.validin := RegNext(upper.io.validout)
    }else{
        switchtran.io.in := RegNext(io.in)
        switchtran.io.validin := RegNext(io.validin)
    }

    val butadder = Module(new ButterflyADD(size))

    butadder.io.in := ShiftRegister(switchtran.io.out,lshdelay)
    if(top){
        for(i <- 1 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := switchtran.io.out(i)
            Lsh.io.l := (3 * (64 - (i << (6 - (radixbit+1))))).U
            butadder.io.in(i) := RegNext(Lsh.io.Y)
        }

        for(i <- 1 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := switchtran.io.out(i + size / 2)
            Lsh.io.l := (3 * (64 - (i << (6 - radixbit)) - (i << (6 - (radixbit+1))))).U
            butadder.io.in(i + size / 2) := RegNext(Lsh.io.Y)
        }
    }else{
        for(i <- 1 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := switchtran.io.out(i + size / 2)
            Lsh.io.l := (3 * (64 - (i << (6 - radixbit)))).U
            butadder.io.in(i + size / 2) := RegNext(Lsh.io.Y)
        }
    }

    if(top){
        io.out := ShiftRegister(butadder.io.out,lshdelay)
        io.validout := ShiftRegister(switchtran.io.validout,lshdelay+1+1+lshdelay)
        for(i <- 0 until 1<< (radixbit-1)){
            val Lsh = Module(new INTorusLSH()(Config()))
            Lsh.io.A := RegNext(butadder.io.out(i + size / 2))
            Lsh.io.l := (3 * (64-16)).U
            io.out(i + size / 2) := Lsh.io.Y
        }
    }else{
        io.out := butadder.io.out
        io.validout := ShiftRegister(switchtran.io.validout,lshdelay+1)
    }
}
