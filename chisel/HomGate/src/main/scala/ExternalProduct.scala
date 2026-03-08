import chisel3._
import chisel3.util._

import math.log
import math.ceil

object DecompositionState extends ChiselEnum {
  val WAIT, INIT, RUN, LAST = Value
}

class Decomposition(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
        val validin = Input(Bool())
        val validout = Output(Bool())
        val readyin = Output(Bool())
	})

    val digitreg = RegInit(0.U(log2Ceil(conf.l).W))
    val cyclereg = RegInit(0.U((conf.cyclebit+1).W))

    val decmem = Module(new WFTPSAmem(2*conf.radix,conf.block*conf.Qbit))
    decmem.io.in := io.in
    decmem.io.wen := false.B
    decmem.io.addr := cyclereg

    io.validout := false.B

    val statereg = RegInit(DecompositionState.WAIT)
    io.readyin := statereg === DecompositionState.WAIT || statereg === DecompositionState.INIT
    switch(statereg){
        is(DecompositionState.WAIT){
            io.validout := RegNext(io.validin)
            when(io.validin){
                decmem.io.wen := true.B
                cyclereg := cyclereg + 1.U
                statereg := DecompositionState.INIT
            }
        }
        is(DecompositionState.INIT){
            io.validout := RegNext(io.validin)
            when(io.validin){
                decmem.io.wen := true.B
                when(cyclereg=/=(2*conf.numcycle-1).U){
                    cyclereg := cyclereg + 1.U
                }.otherwise{
                    cyclereg := 0.U
                    digitreg := digitreg + 1.U
                    statereg := DecompositionState.RUN
                }
            }
        }
        is(DecompositionState.RUN){
            io.validout := true.B
            when(cyclereg=/=(2*conf.numcycle-1).U){
                cyclereg := cyclereg + 1.U
            }.otherwise{
                cyclereg := 0.U
                digitreg := digitreg + 1.U
                statereg := DecompositionState.RUN
                when(digitreg =/= (conf.l-1).U){
                    digitreg := digitreg + 1.U
                }.otherwise{
                    digitreg := 0.U
                    statereg := DecompositionState.LAST
                }
            }
        }
        is(DecompositionState.LAST){
            io.validout := true.B
            statereg := DecompositionState.WAIT
        }
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

    for(j <- 0 until conf.radix){
    val addedoffset = decmem.io.out((j+1)*conf.Qbit-1,j*conf.Qbit) + (offset + raundoffset).U
    val extnum = Wire(Vec(conf.l,UInt(conf.Qbit.W)))
        for(k <- 0 until conf.l){
            extnum(k) := addedoffset(conf.Qbit-k*conf.Bgbit-1,conf.Qbit-(k+1)*conf.Bgbit)
        }
        io.out(0)(j) := extnum(RegNext(digitreg)) - (conf.Bg/2).U
    }
}

class TRGSWBatchMemory(implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
        val trgswin = Input(Vec(conf.radix,UInt(64.W)))
        val trgswinvalid = Input(Bool())
        val trgswinready = Output(Bool())
        val trgswout = Output(Vec(conf.radix,UInt(64.W)))
        val trgswoutvalid = Output(Bool())
        val trgswoutready = Input(Bool())
    })

    val depth = 2*conf.l*conf.numcycle
    val width = conf.radix * 64

    val mem = Module(new RWDmem(2*depth, width))
    mem.io.in := Cat(io.trgswin.reverse)
    for(k <- 0 until conf.radix){
        io.trgswout(k) := mem.io.out((k+1)*64-1, k*64)
    }

    val incnt = RegInit(0.U(log2Ceil(depth+1).W))
    val inselreg = RegInit(0.U(1.W))
    val outcnt = RegInit(0.U(log2Ceil(depth).W))
    val outselreg = RegInit(0.U(1.W))
    val batchcnt = RegInit(0.U(log2Ceil(conf.numbatch).W))

    io.trgswinready := (inselreg =/= outselreg || (outcnt <= incnt && batchcnt === 0.U)) && incnt =/= depth.U
    mem.io.wen := io.trgswinready && io.trgswinvalid
    mem.io.waddr := incnt + inselreg * depth.U

    io.trgswoutvalid := outcnt < incnt || inselreg =/= outselreg
    mem.io.raddr := outcnt + outselreg * depth.U

    when(mem.io.wen){
        incnt := incnt + 1.U
    }
    when(incnt === depth.U && inselreg === outselreg){
        inselreg := ~inselreg
        incnt := 0.U
    }

    when(io.trgswoutvalid && io.trgswoutready){
        when(outcnt =/= (depth-1).U){
            mem.io.raddr := outcnt + 1.U + outselreg * depth.U
            outcnt := outcnt + 1.U
        }.otherwise{
            mem.io.raddr := outselreg * depth.U
            outcnt := 0.U
            when(batchcnt =/= (conf.numbatch - 1).U){
                batchcnt := batchcnt + 1.U
            }.otherwise{
                batchcnt := 0.U
                outselreg := ~outselreg
                mem.io.raddr := (~outselreg) * depth.U
            }
        }
    }
}

class MULandACCpolynomial(delay: Int,implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
		val in = Input(UInt((conf.block*64).W))
		val out = Output(UInt((conf.block*64).W))
        val trgswin = Input(Vec(conf.radix,UInt(64.W)))
        val trgswinvalid = Input(Bool())
        val trgswinready = Output(Bool())
        val validin = Input(Bool())
        val validout = Output(Bool())

        val debugvalid = Output(Bool())
        val debugout = Output(UInt((conf.block*64).W))
	})

    // TRGSWBatchMemory for BK replay
    val trgswbatchmem = Module(new TRGSWBatchMemory)
    trgswbatchmem.io.trgswin := io.trgswin
    trgswbatchmem.io.trgswinvalid := io.trgswinvalid
    io.trgswinready := trgswbatchmem.io.trgswinready
    trgswbatchmem.io.trgswoutready := false.B

    val cyclereg = RegInit(0.U(conf.cyclebit.W))
    val digitreg = RegInit(0.U(log2Ceil((conf.k+1)*conf.l).W))
    val wenwire = Wire(Bool())
    val validwire = Wire(Bool())
    wenwire := false.B
    validwire := false.B
    io.validout := RegNext(validwire)

    // ShiftRegister-based accumulation (replaces AccumulateMemory)
    val accbus = Wire(Vec(conf.fiber,UInt(64.W)))
    val outwire = Wire(UInt((conf.block*64).W))
    outwire := ShiftRegister(Cat(accbus.reverse), conf.numcycle*(delay+1) - conf.muldelay - 2)

    for(k <- 0 until conf.radix){
        val mul = Module(new INTorusMUL)
        mul.io.A := io.in((k+1)*64-1,k*64)
        mul.io.B := trgswbatchmem.io.trgswout(k)
        val add = Module(new INTorusADD)
        add.io.A := RegNext(mul.io.Y)
        add.io.B := RegNext(Mux(ShiftRegister(digitreg===0.U,conf.muldelay),0.U,ShiftRegister(add.io.Y,conf.numcycle-2)))
        accbus(k) := add.io.Y
    }
    io.out := RegNext(outwire)
    io.debugout := Cat(accbus.reverse)
    io.debugvalid := ShiftRegister(wenwire,conf.muldelay+1+1)

    val outflag = Wire(Bool())
    outflag := false.B

    // Processing loop: runs continuously when data is available
    when(io.validin && trgswbatchmem.io.trgswoutvalid){
        trgswbatchmem.io.trgswoutready := true.B
        wenwire := true.B
        when(cyclereg=/=(conf.numcycle-1).U){
            cyclereg := cyclereg + 1.U
        }.otherwise{
            cyclereg := 0.U
            when(digitreg =/= (2*conf.l-1).U){
                digitreg := digitreg + 1.U
            }.otherwise{
                digitreg := 0.U
                outflag := true.B
            }
        }
    }

    // Output staging
    val outcnt = RegInit(0.U(conf.cyclebit.W))
    val outflagreg = RegInit(false.B)

    if(delay != 0){
        val delaycnt = RegInit(0.U(log2Ceil(conf.numcycle*delay).W))
        val delaystate = RegInit(false.B)
        when(outflag && !delaystate && !outflagreg){ delaystate := true.B }
        when(delaystate){
            when(delaycnt === (conf.numcycle*delay-1).U){
                delaycnt := 0.U
                delaystate := false.B
                outflagreg := true.B
            }.otherwise{
                delaycnt := delaycnt + 1.U
            }
        }
    }else{
        when(outflag){ outflagreg := true.B }
    }

    when(outflagreg){
        validwire := true.B
        when(outcnt === (conf.numcycle-1).U){
            outcnt := 0.U
            outflagreg := false.B
        }.otherwise{
            outcnt := outcnt + 1.U
        }
    }
}

class MULandACC(implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
		val in = Input(UInt((conf.radix*64).W))
		val out = Output(UInt((conf.block*64).W))
        val trgswin = Input(Vec(conf.k+1,Vec(conf.radix,UInt(64.W))))
        val trgswinvalid = Input(Vec(conf.k+1,Bool()))
        val trgswinready = Output(Vec(conf.k+1,Bool()))
        val validin = Input(Bool())
        val validout = Output(Bool())
        
        val debugvalid = Output(Vec(2,Bool()))
        val debugout = Output(Vec(conf.k+1,UInt((conf.block*64).W)))
	})

    val mulaccpolys = for(i <- 0 until conf.k+1) yield{
        val mulaccpoly = Module(new MULandACCpolynomial(i,conf))
        mulaccpoly
    }
    mulaccpolys(0).io.in := ShiftRegister(io.in,conf.accnumslice)
    mulaccpolys(0).io.validin := ShiftRegister(io.validin,conf.accnumslice)
    mulaccpolys(conf.k).io.in := io.in
    mulaccpolys(conf.k).io.validin := io.validin
    for(i <- 0 until conf.k+1){
        mulaccpolys(i).io.trgswin := io.trgswin(i)
        mulaccpolys(i).io.trgswinvalid := io.trgswinvalid(i)
        io.trgswinready(i) := mulaccpolys(i).io.trgswinready
        io.debugvalid(i) := mulaccpolys(i).io.debugvalid
        io.debugout(i) := mulaccpolys(i).io.debugout
    }
    io.out := Mux(mulaccpolys(0).io.validout,mulaccpolys(0).io.out,ShiftRegister(mulaccpolys(1).io.out,conf.accnumslice))
    io.validout := mulaccpolys(0).io.validout | ShiftRegister(mulaccpolys(1).io.validout,conf.accnumslice)
}

class ExternalProductFormer(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
        val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
        val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
        val readyin = Output(Bool())
	})
    val tdatavec = Wire(Vec(conf.trlwenumbus,UInt(conf.buswidth.W)))
    for(i <- 0 until conf.trlwenumbus){
        io.axi4sin(i).TREADY := true.B
        tdatavec(i) := io.axi4sin(i).TDATA
    }

	val decomp = Module(new Decomposition)
    decomp.io.in := Cat(tdatavec.reverse)
    decomp.io.validin := io.axi4sin(0).TVALID
    io.readyin := decomp.io.readyin


    for(i <- 0 until conf.trlwenumbus){
        io.axi4sout(i).TVALID := decomp.io.validout
        io.axi4sout(i).TDATA :=Cat(decomp.io.out(0).reverse)((i+1)*conf.buswidth-1,i*conf.buswidth)
    }
}

class ExternalProductPreMiddle(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
        val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
        val axi4sout = Vec(conf.nttnumbus,new AXI4StreamManager(conf.buswidth))

        val inttvalidout = Output(Bool())
        val inttout = Output(Vec(conf.radix,UInt(64.W)))
	})

    val intt = Module(new INTT)
    
    io.inttvalidout := intt.io.validout
    io.inttout := intt.io.out(0)

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
		val trgswin = Input(Vec(conf.k+1,UInt((conf.fiber*64).W)))
        val trgswinvalid = Input(Vec(conf.k+1,Bool()))
        val trgswinready = Output(Vec(conf.k+1,Bool()))

        val accvalid = Output(Vec(2,Bool()))
        val accout = Output(UInt((2*conf.block*64).W))
	})

    val mulandacc = Module(new MULandACC)
    mulandacc.io.trgswinvalid := io.trgswinvalid
    io.trgswinready := mulandacc.io.trgswinready
    for(i<-0 until conf.k+1){
        for(k <- 0 until conf.radix){
            mulandacc.io.trgswin(i)(k) := io.trgswin(i)((k+1)*64-1,k*64)
        }
    }

    val tdatavec = Wire(Vec(conf.nttnumbus,UInt(conf.buswidth.W)))
	for(i <- 0 until conf.nttnumbus){
		io.axi4sin(i).TREADY := true.B
		tdatavec(i) :=  ShiftRegister(io.axi4sin(i).TDATA,conf.interslr)
	}
    mulandacc.io.in := Cat(tdatavec.reverse)
    mulandacc.io.validin := ShiftRegister(io.axi4sin(0).TVALID,conf.interslr)

    io.accout := Cat(mulandacc.io.debugout.reverse)
    io.accvalid := mulandacc.io.debugvalid
    
    for(i <- 0 until conf.nttnumbus){
        io.axi4sout(i).TVALID := mulandacc.io.validout
        io.axi4sout(i).TDATA := mulandacc.io.out((i+1)*conf.buswidth-1,i*conf.buswidth)
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
