import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.log
import math.ceil

class Decomposition(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
		val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
        val cycle = Output(UInt(conf.cyclebit.W))
        val sel = Output(UInt(1.W))
        val valid = Output(Bool())
        val ready = Input(Bool())
        val start = Input(Bool())
	})
    val startreg = RegInit(false.B)
    val digitreg = RegInit(0.U(log2Ceil(conf.l).W))
    val cyclereg = RegInit(0.U(conf.cyclebit.W))
    val selreg = RegInit(0.U(1.W))
    io.cycle := cyclereg
    io.sel := selreg

    io.valid := false.B

    when(io.start||startreg){
        io.valid := true.B
        when(io.ready){
            when(cyclereg=/=(conf.numcycle-1).U){
                cyclereg := cyclereg + 1.U
                io.cycle := cyclereg + 1.U
            }.otherwise{
                cyclereg := 0.U
                io.cycle := 0.U
                when(digitreg =/= (conf.l-1).U){
                    digitreg := digitreg + 1.U
                }.otherwise{
                    digitreg := 0.U
                    when(selreg =/= 1.U){
                        selreg := 1.U
                        io.sel := 1.U
                    }.otherwise{
                        selreg := 0.U
                        io.sel := 0.U
                        startreg := false.B
                    }
                }
            }
        }
    }
    when(io.start){
        startreg := true.B
    }

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
			io.out(i)(j) := extnum(digitreg) - (conf.Bg/2).U
        }
	}
}

object MULandACCState extends ChiselEnum {
  val RUN, OUT = Value
}

class MULandACC(implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
		val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
		val out = Output(UInt((conf.block*64).W))
        val trgswin = Input(Vec(2,Vec(conf.chunk,Vec(conf.radix,UInt(64.W)))))
        val trgswinvalid = Input(Bool())
        val fifovalid = Input(Bool())
        val enable = Input(Bool())
        val ready = Output(Bool())
        val valid = Output(Bool())
        val readyin = Input(Bool())
        
        val debugout = Output(UInt((2*conf.block*64).W))
	})
    io.ready := false.B

    val accmem = Module(new RWDmem(conf.numcycle,2*conf.block*64))

    val cyclereg = RegInit(0.U((conf.cyclebit+1).W))
    io.out := Mux(RegNext(cyclereg(conf.cyclebit)),accmem.io.out>>(conf.block*64),accmem.io.out)
    val digitreg = RegInit(0.U(log2Ceil(2*conf.l).W))
    val wenwire = Wire(Bool())
    val validwire = Wire(Bool())
    wenwire := false.B
    validwire := false.B
    io.valid := RegNext(validwire)

    accmem.io.wen := ShiftRegister(wenwire,conf.muldelay+1+1)
    accmem.io.raddr := cyclereg
    accmem.io.waddr := ShiftRegister(cyclereg,conf.muldelay+1+1)
    val accbus = Wire(Vec(2,Vec(conf.chunk,Vec(conf.fiber,UInt(64.W)))))
    for(i<-0 until 2){
        for(j <- 0 until conf.chunk){
            for(k <- 0 until conf.radix){
                val mul = Module(new INTorusMUL)
                mul.io.A := io.in(j)(k)
                mul.io.B := io.trgswin(i)(j)(k)
                val add = Module(new INTorusADD)
                add.io.A := RegNext(mul.io.Y)
                add.io.B := RegNext(Mux(ShiftRegister(digitreg===0.U,conf.muldelay),0.U,ShiftRegister(accmem.io.out((i*conf.chunk*conf.radix+j*conf.radix+k+1)*64-1,(i*conf.chunk*conf.radix+j*conf.radix+k)*64),conf.muldelay-1)))
                accbus(i)(j)(k) := add.io.Y
            }
        }
    }
    accmem.io.in := Cat(accbus.flatten.flatten.reverse)
    io.debugout := Cat(accbus.flatten.flatten.reverse)

    val statereg = RegInit(MULandACCState.RUN)

    switch(statereg){
        is(MULandACCState.RUN){
            // Avoid address collision caused by bubbles in HBM.
            when(io.fifovalid && io.trgswinvalid && (~(accmem.io.wen && (accmem.io.raddr === accmem.io.waddr)))){
                io.ready := true.B
                wenwire := true.B
                when(cyclereg=/=(conf.numcycle-1).U){
                    cyclereg := cyclereg + 1.U
                }.otherwise{
                    cyclereg := 0.U
                    when(digitreg =/= (2*conf.l-1).U){
                        digitreg := digitreg + 1.U
                    }.otherwise{
                        digitreg := 0.U
                        statereg := MULandACCState.OUT
                    }
                }
            }
        }
        is(MULandACCState.OUT){
            validwire := true.B
            cyclereg := cyclereg + 1.U
            when(cyclereg===(2*conf.numcycle-1).U){
                    cyclereg := 0.U
                    statereg := MULandACCState.RUN
            }
        }
    }

    when(~io.enable){
        statereg := MULandACCState.RUN
        cyclereg := 0.U
        digitreg := 0.U
    }
}

class ExternalProductFormer(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
        val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
        val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
	})
    val inmem = Module(new RWDmem(2*conf.radix,conf.block*conf.Qbit))
    inmem.io.wen := false.B
    val initreg = RegInit(0.U((conf.radixbit+2).W))
    inmem.io.waddr := initreg
    when(io.axi4sin(0).TVALID&&(~initreg(conf.radixbit+1))){
        initreg := initreg + 1.U
        inmem.io.wen := true.B
    }
    val tdatavec = Wire(Vec(conf.trlwenumbus,UInt(conf.buswidth.W)))
    for(i <- 0 until conf.trlwenumbus){
        io.axi4sin(i).TREADY := io.axi4sin(0).TVALID&&(~initreg(conf.radixbit+1))
        tdatavec(i) := io.axi4sin(i).TDATA
    }
    inmem.io.in := Cat(tdatavec.reverse)

	val decomp = Module(new Decomposition)
    inmem.io.raddr := (decomp.io.sel<<conf.radixbit) + decomp.io.cycle
    decomp.io.start := false.B

    for(i <- 0 until conf.radix){
        decomp.io.in(0)(i) := inmem.io.out((i+1)*conf.Qbit-1,i*conf.Qbit)
    }

    decomp.io.ready := io.axi4sout(0).TREADY
    when(~decomp.io.valid && RegNext(decomp.io.valid)){
                initreg := 0.U
    }
    decomp.io.start := initreg === 1.U

    for(i <- 0 until conf.trlwenumbus){
        io.axi4sout(i).TVALID := decomp.io.valid
        io.axi4sout(i).TDATA :=Cat(decomp.io.out(0).reverse)((i+1)*conf.buswidth-1,i*conf.buswidth)
    }
}

class ExternalProductPreMiddle(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
        val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
        val axi4sout = Vec(conf.nttnumbus,new AXI4StreamManager(conf.buswidth))

        val inttvalidout = Output(Bool())
        val inttout = Output(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
	})

    val intt = Module(new INTT)
    
    io.inttvalidout := intt.io.validout
    io.inttout := intt.io.out

    val tdatavec = Wire(Vec(conf.trlwenumbus,UInt(conf.buswidth.W)))
    for(i <- 0 until conf.trlwenumbus){
        io.axi4sin(i).TREADY := true.B
        tdatavec(i) := io.axi4sin(i).TDATA
    }
    for(i <- 0 until conf.radix){
        intt.io.in(0)(i) :=  Cat(tdatavec.reverse)((i+1)*conf.Qbit-1,i*conf.Qbit)
    }
    intt.io.validin := io.axi4sin(0).TVALID

    for(i <- 0 until conf.nttnumbus){
        io.axi4sout(i).TVALID := ShiftRegister(intt.io.validout,conf.interslr)
        io.axi4sout(i).TDATA := ShiftRegister(Cat(intt.io.out(0).reverse)((i+1)*conf.buswidth-1,i*conf.buswidth),conf.interslr)
    }

}

class ExternalProductMiddle(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
        val axi4sin = Vec(conf.nttnumbus,new AXI4StreamSubordinate(conf.buswidth))
        val axi4sout = Vec(conf.nttnumbus,new AXI4StreamManager(conf.buswidth))
		val trgswin = Input(UInt((2*conf.fiber*64).W))
        val trgswinvalid = Input(Bool())
        val trgswinready = Output(Bool())

        val accout = Output(UInt((2*conf.block*64).W))
	})
    val inttqueue = Module(new Queue(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))), conf.cyclebit, useSyncReadMem = true))
    inttqueue.io.enq.valid := ShiftRegister(io.axi4sin(0).TVALID,conf.interslr)
	val tdatavec = Wire(Vec(conf.nttnumbus,UInt(conf.buswidth.W)))
	for(i <- 0 until conf.nttnumbus){
		io.axi4sin(i).TREADY := true.B
		tdatavec(i) :=  ShiftRegister(io.axi4sin(i).TDATA,conf.interslr)
	}
    for(i <- 0 until conf.radix){
        inttqueue.io.enq.bits(0)(i) := Cat(tdatavec.reverse)((i+1)*64-1,i*64)
    }

    val mulandacc = Module(new MULandACC)
    io.trgswinready := mulandacc.io.ready
    mulandacc.io.trgswinvalid := io.trgswinvalid
    for(i<-0 until 2){
        for(j<-0 until conf.chunk){
            for(k <- 0 until conf.radix){
                val index = i*conf.fiber+j*conf.radix+k
                mulandacc.io.trgswin(i)(j)(k) := io.trgswin((index+1)*64-1,index*64)
            }
        }
    }
    mulandacc.io.in := inttqueue.io.deq.bits
    mulandacc.io.fifovalid :=  inttqueue.io.deq.valid
    inttqueue.io.deq.ready := mulandacc.io.ready

    mulandacc.io.enable := true.B

    io.accout := mulandacc.io.debugout
    
    mulandacc.io.readyin := io.axi4sout(0).TREADY
    for(i <- 0 until conf.nttnumbus){
        io.axi4sout(i).TVALID := ShiftRegister(mulandacc.io.valid,conf.interslr)
        io.axi4sout(i).TDATA := ShiftRegister(mulandacc.io.out((i+1)*conf.buswidth-1,i*conf.buswidth),conf.interslr)
    }
}

class ExternalProductLater(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
        val axi4sin = Vec(conf.nttnumbus,new AXI4StreamSubordinate(conf.buswidth))
        val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
	})

    val ntt = Module(new NTT)
    ntt.io.validin := RegNext(io.axi4sin(0).TVALID)

    val tdatavec = Wire(Vec(conf.nttnumbus,UInt(conf.buswidth.W)))
    for(i <- 0 until conf.nttnumbus){
        io.axi4sin(i).TREADY:= true.B
        tdatavec(i) := io.axi4sin(i).TDATA
    }
    for(i <- 0 until conf.radix){
        ntt.io.in(0)(i) := RegNext(Cat(tdatavec.reverse)((i+1)*64-1,i*64))
    }

    val outdatavec = Wire(Vec(conf.radix,UInt(conf.Qbit.W)))
    for(i <- 0 until conf.radix){
        outdatavec(i) := ntt.io.out(0)(i)
    }
    for(i <- 0 until conf.trlwenumbus){
        io.axi4sout(i).TVALID :=  ShiftRegister(ntt.io.validout,conf.interslr/2)
        io.axi4sout(i).TDATA := ShiftRegister(Cat(outdatavec.reverse)((i+1)*conf.buswidth-1,i*conf.buswidth),conf.interslr/2)
    }
}

class ExternalProduct(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
        val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
		val trgswin = Input(UInt((2*conf.fiber*64).W))
        val trgswinvalid = Input(Bool())
        val trgswinready = Output(Bool())

        val validin = Input(Bool())
        val validout = Output(Bool())
        val fin = Output(Bool())

        val inttvalidout = Output(Bool())
        val inttout = Output(Vec(conf.chunk,Vec(conf.radix,UInt(64.W))))
        val accout = Output(UInt((2*conf.block*64).W))
	})

    val former = Module(new ExternalProductFormer)
    for(i <- 0 until conf.trlwenumbus){
        former.io.axi4sin(i).TVALID := io.validin
        former.io.axi4sin(i).TDATA := Cat(io.in(0).reverse)((i+1)*conf.buswidth-1,i*conf.buswidth)
    }
    
    val premiddle = Module(new ExternalProductPreMiddle)
    premiddle.io.axi4sin <> former.io.axi4sout
    io.inttvalidout := premiddle.io.inttvalidout
    io.inttout := premiddle.io.inttout

    val middle = Module(new ExternalProductMiddle)
    middle.io.axi4sin <> premiddle.io.axi4sout
    middle.io.trgswin := io.trgswin
    middle.io.trgswinvalid := io.trgswinvalid
    io.trgswinready := middle.io.trgswinready
    io.accout := middle.io.accout

    val later = Module(new ExternalProductLater)
    middle.io.axi4sout <> later.io.axi4sin

    val tdatavec = Wire(Vec(conf.trlwenumbus,UInt(conf.buswidth.W)))
	for(i <- 0 until conf.trlwenumbus){
		later.io.axi4sout(i).TREADY := true.B
		tdatavec(i) :=  ShiftRegister(later.io.axi4sout(i).TDATA,conf.interslr/2)
	}
    for(i <- 0 until conf.radix){
        io.out(0)(i) := Cat(tdatavec.reverse)((i+1)*conf.Qbit-1,i*conf.Qbit)
    }

    io.validout := ShiftRegister(later.io.axi4sout(0).TVALID,conf.interslr/2)
    io.fin := ~io.validout && RegNext(io.validout)
}