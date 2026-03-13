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
  val WAIT,INIT,BUBBLE,PMBXMOWAIT,PMBXGAP,FINWAIT,OUT = Value
}

class BlindRotate(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val bvalue = Input(UInt((conf.Nbit+1).W))
		val avalue = Input(UInt(conf.qbit.W))
		val batchidx = Output(UInt(log2Ceil(conf.numbatch).W))
		val dimidx = Output(UInt(log2Ceil(conf.n).W))
		val axi4sglobalout = new AXI4StreamManager(conf.Qbit, withTLast=true)
		val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.trlwenumbus,new AXI4StreamManager(conf.buswidth))
		val enable = Input(Bool())
		val extpfin = Output(Bool())
		val done = Output(Bool())
		val decreadyin = Input(Bool())

		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	io.extpfin := false.B
	io.done := false.B

	// 3R+1W memory: rdata1=PMBX main/SEI/INIT, rdata2=PMBX minus, rdata3=feedback pre-read
	val BRmem = Module(new BRMem(conf.numbatch*2*conf.numcycle,conf.block*conf.Qbit))

	val initcnt = RegInit(0.U(log2Ceil(conf.numbatch*2*conf.numcycle).W))
	val pmbxmo = Module(new PolynomialMulByXaiMinusOne())
	pmbxmo.io.in := BRmem.io.rdata1
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

	// raddr2: PMBX minus read only
	BRmem.io.raddr2 := pmbxmo.io.minusaddr + batchreg * (2*conf.numcycle).U
	pmbxmo.io.minusin := BRmem.io.rdata2

	BRmem.io.raddr3 := 0.U

	// Feedback from ExternalProduct
	val feedbackbatch = RegInit(0.U(log2Ceil(conf.numbatch).W))
	when(ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2)){
		BRmem.io.raddr3 := feedbackbatch * (2*conf.numcycle).U + initcnt
		initcnt := initcnt + 1.U
		when(initcnt === (2*conf.numcycle-1).U){
			initcnt := 0.U
			when(feedbackbatch =/= (conf.numbatch-1).U){
				feedbackbatch := feedbackbatch + 1.U
			}.otherwise{
				feedbackbatch := 0.U
			}
		}
	}
	BRmem.io.wen := ShiftRegister(io.axi4sin(0).TVALID,conf.interslr/2+2)
	BRmem.io.waddr := ShiftRegister(feedbackbatch * (2*conf.numcycle).U + initcnt,2)
	val tdatavec = Wire(Vec(conf.trlwenumbus,UInt(conf.buswidth.W)))
	for(i <- 0 until conf.trlwenumbus){
		io.axi4sin(i).TREADY := true.B
		tdatavec(i) :=  ShiftRegister(io.axi4sin(i).TDATA,conf.interslr/2)
	}
	// Total feedback completion counter (counts all feedback across all dimensions)
	// Count burst completions (initcnt wrapping at 2*numcycle-1) rather than TVALID falling edges,
	// because pmbxgap < 2*numcycle means consecutive dispatches' feedback bursts overlap (no gap).
	val finreg = RegInit(0.U(log2Ceil(conf.n * conf.numbatch + 1).W))
	when(ShiftRegister(io.axi4sin(0).TVALID, conf.interslr/2) && initcnt === (2*conf.numcycle-1).U){
		finreg := finreg + 1.U
	}
	val addedres = Wire(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
	for(i <- 0 until conf.chunk){
		for(j <- 0 until conf.radix){
			addedres(i)(j) := ShiftRegister(Cat(tdatavec.reverse)((i*conf.radix+j+1)*conf.Qbit-1,(i*conf.radix+j)*conf.Qbit),2) + RegNext(BRmem.io.rdata3((i*conf.radix+j+1)*conf.Qbit-1,(i*conf.radix+j)*conf.Qbit))
		}
	}
	BRmem.io.wdata := Cat(addedres.flatten.reverse)
	io.debugout := BRmem.io.wdata
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

	// raddr1: default read address (for PMBX main read, SEI read)
	BRmem.io.raddr1 := 0.U
	sei.io.in := BRmem.io.rdata1
	sei.io.enable := false.B
	// Connect SEI output to global out, adding TLAST counter
	io.axi4sglobalout.TVALID := sei.io.axi4sout.TVALID
	io.axi4sglobalout.TDATA  := sei.io.axi4sout.TDATA
	sei.io.axi4sout.TREADY   := io.axi4sglobalout.TREADY
	val totalBeats = conf.numbatch * (conf.N + 1)
	val outCounter = RegInit(0.U(log2Ceil(totalBeats).W))
	val outLastBeat = outCounter === (totalBeats - 1).U
	io.axi4sglobalout.TLAST.get := outLastBeat
	when(io.axi4sglobalout.TVALID && io.axi4sglobalout.TREADY) {
		when(outLastBeat) { outCounter := 0.U }
		.otherwise        { outCounter := outCounter + 1.U }
	}

	val gapWaitCnt = RegInit(0.U(log2Ceil(conf.numcycle * (conf.k + 1)).W))
	val dispatchCnt = RegInit(0.U(log2Ceil(conf.n * conf.numbatch + 1).W))
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
			BRmem.io.waddr := initAddrPipe
			BRmem.io.wdata := initDataPipe
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
			BRmem.io.waddr := initAddrPipe
			BRmem.io.wdata := initDataPipe
			pmbxmoenablewire := true.B
			dispatchCnt := dispatchCnt + 1.U
			amemBatchIdx := 0.U
			amemDimIdx := 0.U
			feedbackbatch := 0.U
			statereg := BlindRotateState.PMBXMOWAIT
		}
		is(BlindRotateState.PMBXMOWAIT){
			pmbxmoenablewire := true.B
			BRmem.io.raddr1 := batchreg * (2*conf.numcycle).U + (pmbxmo.io.insel<<conf.radixbit)+pmbxmo.io.inaddr
			when(~pmbxmo.io.valid && RegNext(pmbxmo.io.valid)){
				pmbxmoenablewire := false.B
				when(batchreg =/= (conf.numbatch-1).U){
					batchreg := batchreg + 1.U
					statereg := BlindRotateState.PMBXGAP
				}.otherwise{
					batchreg := 0.U
					when(brcntreg =/= (conf.n-1).U){
						brcntreg := brcntreg + 1.U
						statereg := BlindRotateState.PMBXGAP
					}.otherwise{
						statereg := BlindRotateState.FINWAIT
					}
				}
			}
			io.debugout := Cat(pmbxmo.io.out.reverse)
			io.debugvalid := pmbxmo.io.valid
		}
		is(BlindRotateState.PMBXGAP){
			io.debugvalid := false.B
			amemBatchIdx := batchreg
			amemDimIdx := brcntreg
			when(io.decreadyin){
				when(gapWaitCnt === (conf.pmbxgap - 1).U){
					when(dispatchCnt - finreg < conf.numbatch.U){
						gapWaitCnt := 0.U
						dispatchCnt := dispatchCnt + 1.U
						pmbxmoenablewire := true.B
						statereg := BlindRotateState.PMBXMOWAIT
					}
				}.otherwise{
					gapWaitCnt := gapWaitCnt + 1.U
				}
			}
		}
		is(BlindRotateState.FINWAIT){
			io.debugvalid := false.B
			when(finreg === (conf.n * conf.numbatch).U){
				batchreg := 0.U
				statereg := BlindRotateState.OUT
			}
		}
		is(BlindRotateState.OUT){
			BRmem.io.raddr1 := batchreg * (2*conf.numcycle).U + sei.io.addr
			sei.io.enable := true.B
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
		dispatchCnt := 0.U
		brcntreg := 0.U
		batchreg := 0.U
		feedbackbatch := 0.U
		statereg := BlindRotateState.WAIT
	}
}

class AXISBRFormer(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4sglobalin = new AXI4StreamSubordinate(conf.buswidth)
		val axi4sglobalout = new AXI4StreamManager(conf.Qbit, withTLast=true)
		val axi4sin = Vec(conf.trlwenumbus,new AXI4StreamSubordinate(conf.buswidth))
		val axi4sout = Vec(conf.nttnumbus,new AXI4StreamManager(conf.buswidth))

		val debugout = Output(UInt((conf.block*conf.Qbit).W))
		val debugvalid = Output(Bool())
	})

	val tlwe2index = Module(new TLWE2Index(conf.buswidth,conf.n,conf.qbit))
	val inslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	val outslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice,withTLast=true))
	val br = Module(new BlindRotate)
	val extpformer = Module(new ExternalProductFormer)
	val extppremiddle = Module(new ExternalProductPreMiddle)

	io.axi4sglobalin <> inslice.io.subordinate

	io.debugvalid := br.io.debugvalid
	io.debugout := br.io.debugout

	io.axi4sglobalout <> outslice.io.manager

	br.io.axi4sout <> extpformer.io.axi4sin
	br.io.axi4sin <> io.axi4sin
	br.io.decreadyin := extpformer.io.readyin

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
					tlweResetCnt := 2.U
					loadState := LoadState.IDLE
				}
			}.otherwise{
				dimLoadCnt := dimLoadCnt + 1.U
			}
		}
		is(LoadState.DONE){
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
		val allValid = Cat(tvalidvec).andR
		val slices = for(i <- 0 until conf.bknumbus/2) yield {
			val slice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
			slice.io.subordinate <> io.axi4bkin(k*conf.bknumbus/2+i)
			tvalidvec(i) := slice.io.manager.TVALID
			tdatavec(i) := slice.io.manager.TDATA
			slice
		}
		// Slices must advance atomically: only when ALL 4 buses are simultaneously valid
		// AND TRGSWBatchMemory is ready to accept. Without this, a momentary stall on any
		// one HBM bus causes the other 3 slices to consume their data without writing it to
		// TRGSWBatchMemory (silently discarding those BK beats → corrupted accumulation).
		for(i <- 0 until conf.bknumbus/2){
			slices(i).io.manager.TREADY := extpmiddle.io.trgswinready(k) && allValid
		}
		extpmiddle.io.trgswinvalid(k) := allValid
		extpmiddle.io.trgswin(k) := Cat(tdatavec.reverse)
	}

	extplater.io.axi4sout <> io.axi4sout
}
