import chisel3._
import chisel3.util._

import math.log
import math.ceil

class RotatedTestVector(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val exponent = Input(UInt((conf.Nbit+1).W))
		val out = Output(Vec(conf.numcycle,Vec(conf.radix,UInt(conf.Qbit.W))))
	})

	for(i <- 0 until conf.numcycle){
		for(j <- 0 until conf.radix){
			io.out(i)(j) := Mux(io.exponent(conf.Nbit) ^ (((j<<conf.cyclebit)+i).U<io.exponent(conf.Nbit-1,0)),((1L<<conf.Qbit)-conf.mu).U,conf.mu.U)
		}
	}
}

object BlindRotateState extends ChiselEnum {
  val WAIT,INIT,BUBBLE,PMBXMOWAIT,RUN,OUT = Value
}

class BlindRotate(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val bvalue = Input(UInt((conf.Nbit+1).W))
		val avalue = Input(UInt(conf.qbit.W))
		val batchidx = Output(UInt(log2Ceil(conf.numbatch).W))
		val dimidx = Output(UInt(log2Ceil(conf.n).W))
		val axi4sglobalout = new AXI4StreamManager(conf.Qbit)
		val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
		val enable = Input(Bool())
		val extpfin = Output(Bool())
		val done = Output(Bool())

		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	io.extpfin := false.B
	io.done := false.B

	val BRmem = Module(new RWSRmem(conf.numbatch*2*conf.numcycle,conf.block*conf.Qbit))

	val initcnt = RegInit(0.U(log2Ceil(conf.numbatch*2*conf.numcycle).W))
	val pmbxmo = Module(new PolynomialMulByXaiMinusOne())
	pmbxmo.io.in := BRmem.io.out
	val pmbxmoenablewire = Wire(Bool())
	pmbxmo.io.enable := RegNext(pmbxmoenablewire)
	pmbxmoenablewire := false.B

	// Batch processing registers
	val batchreg = RegInit(0.U(log2Ceil(conf.numbatch).W))
	val brcntreg = RegInit(0.U(log2Ceil(conf.n).W))

	// Default amem read address outputs
	val amemBatchIdx = Wire(UInt(log2Ceil(conf.numbatch).W))
	val amemDimIdx = Wire(UInt(log2Ceil(conf.n).W))
	amemBatchIdx := batchreg
	amemDimIdx := brcntreg
	io.batchidx := amemBatchIdx
	io.dimidx := amemDimIdx

	val roundoffset = 1L<<(conf.qbit-conf.Nbit-2)
	pmbxmo.io.exponent := (io.avalue + roundoffset.U)(conf.qbit-1,conf.qbit-(conf.Nbit+1))

	BRmem.io.raddr := pmbxmo.io.minusaddr + batchreg * (2*conf.numcycle).U
	pmbxmo.io.minusin := BRmem.io.rout

	// Feedback from ExternalProduct
	val feedbackbatch = RegInit(0.U(log2Ceil(conf.numbatch).W))
	when(ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2)){
		BRmem.io.raddr := feedbackbatch * (2*conf.numcycle).U + initcnt
		initcnt := initcnt + 1.U
		when(initcnt === (2*conf.numcycle-1).U){
			initcnt := 0.U
		}
	}
	BRmem.io.wen := ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2+2)
	BRmem.io.addr := ShiftRegister(feedbackbatch * (2*conf.numcycle).U + initcnt,2)
	val tdatavec = Wire(Vec(conf.trlwenumbus,UInt(conf.buswidth.W)))
	for(i <- 0 until conf.trlwenumbus){
		io.axi4sin(i).TREADY := true.B
		tdatavec(i) :=  ShiftRegister(io.axi4sin(i).TDATA,conf.interslr/2)
	}
	val finreg = RegInit(0.U(2.W))
    when(RegNext(~ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2)&&ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2+1))){
        finreg := finreg +1.U
    }
	val addedres = Wire(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
	for(i <- 0 until conf.chunk){
		for(j <- 0 until conf.radix){
			addedres(i)(j) := ShiftRegister(Cat(tdatavec.reverse)((i*conf.radix+j+1)*conf.Qbit-1,(i*conf.radix+j)*conf.Qbit),2) + RegNext(BRmem.io.rout((i*conf.radix+j+1)*conf.Qbit-1,(i*conf.radix+j)*conf.Qbit))
		}
	}
	BRmem.io.in := Cat(addedres.flatten.reverse)
	io.debugout := BRmem.io.in
	io.debugvalid := BRmem.io.wen

	for(i <- 0 until conf.trlwenumbus){
		io.axi4sout(i).TVALID := pmbxmo.io.valid
		io.axi4sout(i).TDATA := Cat(pmbxmo.io.out.reverse)((i+1)*conf.buswidth-1,i*conf.buswidth)
	}

	val tvgen = Module(new RotatedTestVector)
	tvgen.io.exponent := io.bvalue

	val initAddrPipe = RegNext(batchreg * (2*conf.numcycle).U + initcnt)
	val initDataPipe = RegNext(Mux(initcnt(conf.radixbit),Cat(tvgen.io.out(initcnt).reverse),0.U))

	val sei = Module(new SampleExtractIndex(0,conf))

	sei.io.in := BRmem.io.out
	sei.io.enable := false.B
	sei.io.axi4sout <> io.axi4sglobalout

	val statereg = RegInit(BlindRotateState.WAIT)
	switch(statereg){
		is(BlindRotateState.WAIT){
			when(io.enable){
				batchreg := 0.U
				initcnt := 0.U
				statereg := BlindRotateState.INIT
			}
		}
		is(BlindRotateState.INIT){
			BRmem.io.wen := true.B
			BRmem.io.addr := initAddrPipe
			BRmem.io.in := initDataPipe
			io.debugout := initDataPipe
			amemBatchIdx := batchreg
			when(initcnt =/= (2*conf.numcycle-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				when(batchreg === (conf.numbatch-1).U){
					batchreg := 0.U
					statereg := BlindRotateState.BUBBLE
				}.otherwise{
					batchreg := batchreg + 1.U
				}
			}
		}
		is(BlindRotateState.BUBBLE){
			BRmem.io.wen := true.B
			BRmem.io.addr := initAddrPipe
			BRmem.io.in := initDataPipe
			pmbxmoenablewire := true.B
			// Issue amem read for first batch, first dim
			amemBatchIdx := 0.U
			amemDimIdx := 0.U
			feedbackbatch := 0.U
			statereg := BlindRotateState.PMBXMOWAIT
		}
		is(BlindRotateState.PMBXMOWAIT){
			pmbxmoenablewire := true.B
			BRmem.io.addr := batchreg * (2*conf.numcycle).U + (pmbxmo.io.insel<<conf.radixbit)+pmbxmo.io.inaddr
			when(~pmbxmo.io.valid && RegNext(pmbxmo.io.valid)){
				statereg := BlindRotateState.RUN
			}
			io.debugout := Cat(pmbxmo.io.out.reverse)
			io.debugvalid := pmbxmo.io.valid
		}
		is(BlindRotateState.RUN){
			pmbxmoenablewire := false.B
			// Pre-read next iteration's a value from amem (SyncReadMem has 1-cycle latency)
			// so that io.avalue is correct when PMBXMOWAIT starts
			when(batchreg =/= (conf.numbatch-1).U){
				amemBatchIdx := batchreg + 1.U
				amemDimIdx := brcntreg
			}.otherwise{
				amemBatchIdx := 0.U
				amemDimIdx := brcntreg + 1.U
			}
			when(finreg===1.U){
				finreg := 0.U
				io.extpfin := true.B
				when(batchreg =/= (conf.numbatch-1).U){
					// More batches for this dimension
					batchreg := batchreg + 1.U
					feedbackbatch := batchreg + 1.U
					pmbxmoenablewire := true.B
					// Issue amem read for next batch, same dim
					amemBatchIdx := batchreg + 1.U
					amemDimIdx := brcntreg
					statereg := BlindRotateState.PMBXMOWAIT
				}.elsewhen(RegNext(brcntreg =/= (conf.n-1).U)){
					// Last batch, more dimensions
					batchreg := 0.U
					feedbackbatch := 0.U
					brcntreg := brcntreg + 1.U
					pmbxmoenablewire := true.B
					// Issue amem read for first batch, next dim
					amemBatchIdx := 0.U
					amemDimIdx := brcntreg + 1.U
					statereg := BlindRotateState.PMBXMOWAIT
				}.otherwise{
					// Last batch, last dimension
					batchreg := 0.U
					statereg := BlindRotateState.OUT
				}
			}
		}
		is(BlindRotateState.OUT){
			BRmem.io.addr := batchreg * (2*conf.numcycle).U + sei.io.addr
			sei.io.enable := true.B
			// Detect SEI completion (falling edge of TVALID)
			when(~sei.io.axi4sout.TVALID && RegNext(sei.io.axi4sout.TVALID)){
				sei.io.enable := false.B
				when(batchreg =/= (conf.numbatch-1).U){
					batchreg := batchreg + 1.U
				}.otherwise{
					io.done := true.B
				}
			}
		}
	}
	when(!io.enable){
		initcnt := 0.U
		finreg := 0.U
		brcntreg := 0.U
		batchreg := 0.U
		feedbackbatch := 0.U
		statereg := BlindRotateState.WAIT
	}
}

class AXISBRFormer(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4sglobalin = new AXI4StreamSubordinate(conf.buswidth)
		val axi4sglobalout = new AXI4StreamManager(conf.Qbit)
		val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.nttnumbus,new AXI4StreamManager(conf.buswidth))

		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	val tlwe2index = Module(new TLWE2Index(conf.buswidth,conf.n,conf.qbit))
	val inslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	val outslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	val br = Module(new BlindRotate)
	val extpformer = Module(new ExternalProductFormer)
	val extppremiddle = Module(new ExternalProductPreMiddle)

	io.axi4sglobalin <> inslice.io.subordinate

	io.debugvalid := br.io.debugvalid
	io.debugout := br.io.debugout

	io.axi4sglobalout <> outslice.io.manager

	br.io.axi4sout <> extpformer.io.axi4sin
	br.io.axi4sin <> io.axi4sin

	br.io.axi4sglobalout <> outslice.io.subordinate

	// Gate TLWE2Index input: only accept data when in IDLE and not resetting
	val acceptInput = Wire(Bool())
	tlwe2index.io.axi4.TVALID := inslice.io.manager.TVALID && acceptInput
	tlwe2index.io.axi4.TDATA := inslice.io.manager.TDATA
	inslice.io.manager.TREADY := tlwe2index.io.axi4.TREADY && acceptInput

	extpformer.io.axi4sout <> extppremiddle.io.axi4sin
	extppremiddle.io.axi4sout <> io.axi4sout

	// Batch TLWE buffering
	val amem = SyncReadMem(conf.numbatch * conf.n, UInt(conf.qbit.W))
	val bqueue = Reg(Vec(conf.numbatch, UInt((conf.Nbit+1).W)))

	// Loading state machine
	object LoadState extends ChiselEnum {
		val IDLE, LOADING, DONE = Value
	}
	val loadState = RegInit(LoadState.IDLE)
	val batchLoadCnt = RegInit(0.U(log2Ceil(conf.numbatch).W))
	val dimLoadCnt = RegInit(0.U(log2Ceil(conf.n).W))
	val tlweResetCnt = RegInit(0.U(2.W))

	acceptInput := loadState === LoadState.IDLE && tlweResetCnt === 0.U && !tlwe2index.io.validout
	tlwe2index.io.enable := loadState =/= LoadState.DONE
	tlwe2index.io.ready := false.B

	// Connect to BlindRotate
	br.io.enable := loadState === LoadState.DONE
	br.io.bvalue := bqueue(br.io.batchidx)
	br.io.avalue := amem.read(br.io.batchidx * conf.n.U + br.io.dimidx)

	switch(loadState){
		is(LoadState.IDLE){
			when(tlweResetCnt =/= 0.U){
				tlwe2index.io.enable := false.B
				tlweResetCnt := tlweResetCnt - 1.U
			}.elsewhen(tlwe2index.io.validout){
				// TLWE ready: capture b, start loading a values
				bqueue(batchLoadCnt) := (2*conf.N).U - tlwe2index.io.b(conf.qbit-1,conf.qbit-(conf.Nbit+1))
				dimLoadCnt := 0.U
				loadState := LoadState.LOADING
			}
		}
		is(LoadState.LOADING){
			tlwe2index.io.ready := true.B
			amem.write(batchLoadCnt * conf.n.U + dimLoadCnt, tlwe2index.io.a)
			when(dimLoadCnt === (conf.n-1).U){
				dimLoadCnt := 0.U
				when(batchLoadCnt === (conf.numbatch-1).U){
					batchLoadCnt := 0.U
					loadState := LoadState.DONE
				}.otherwise{
					batchLoadCnt := batchLoadCnt + 1.U
					// Reset TLWE2Index to load next TLWE
					tlweResetCnt := 2.U
					loadState := LoadState.IDLE
				}
			}.otherwise{
				dimLoadCnt := dimLoadCnt + 1.U
			}
		}
		is(LoadState.DONE){
			// BlindRotate is processing
			when(br.io.done){
				loadState := LoadState.IDLE
				batchLoadCnt := 0.U
			}
		}
	}
}

class AXISBRLater(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4bkin = Vec(conf.bknumbus,new AXI4StreamSubordinate(conf.buswidth))

		val axi4sin = Vec(conf.nttnumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
	})

	val extpmiddle = Module(new ExternalProductMiddle)
	val extplater = Module(new ExternalProductLater)

	for(i<-0 until conf.nttnumbus){
		extpmiddle.io.axi4sin(i).TVALID := ShiftRegister(io.axi4sin(i).TVALID,4)
		extpmiddle.io.axi4sin(i).TDATA := ShiftRegister(io.axi4sin(i).TDATA,4)
		io.axi4sin(i).TREADY := true.B
	}
	extpmiddle.io.axi4sout <> extplater.io.axi4sin
	for(k <- 0 until conf.k+1){
		val tvalidvec = Wire(Vec(conf.bknumbus/2,Bool()))
		val tdatavec = Wire(Vec(conf.bknumbus/2,UInt(conf.buswidth.W)))
		for(i <- 0 until conf.bknumbus/2){
			val slice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
			slice.io.subordinate <> io.axi4bkin(k*conf.bknumbus/2+i)
			slice.io.manager.TREADY := extpmiddle.io.trgswinready(k)
			tvalidvec(i) := slice.io.manager.TVALID
			tdatavec(i) := slice.io.manager.TDATA
		}
		extpmiddle.io.trgswinvalid(k) := Cat(tvalidvec).andR
		extpmiddle.io.trgswin(k) := Cat(tdatavec.reverse)
	}

	extplater.io.axi4sout <> io.axi4sout
}
