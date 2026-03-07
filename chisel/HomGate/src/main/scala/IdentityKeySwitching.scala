import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.ceil

object IdentityKeySwitchingState extends ChiselEnum {
  val WAIT, INIT, ADDRBUBBLE, RUN, LASTADD, OUT, LAST  = Value
}

class IdentityKeySwitching(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val b = Input(Vec(conf.numbatch, UInt(conf.Qbit.W)))
		val a = Input(Vec(conf.numbatch, UInt(conf.Qbit.W)))
		val dimidx = Output(UInt(conf.Nbit.W))
		val enable = Input(Bool())
		val fin = Output(Bool())

		val axi4ikskin = new AXI4StreamSubordinate(conf.hbmbuswidth*conf.iksknumbus)
		val axi4out = new AXI4StreamManager(conf.buswidth)
	})
	io.axi4ikskin.TREADY := false.B
	io.fin := false.B
	io.axi4out.TVALID := false.B

	val elementsPerBus = conf.hbmbuswidth / conf.qbit

	// numbatch × iksknumbus sub-accumulators
	val accs = for (b <- 0 until conf.numbatch) yield
		for (k <- 0 until conf.iksknumbus) yield
			SyncReadMem(conf.iksknumsegments, UInt(conf.hbmbuswidth.W))

	val accreadaddr = Wire(UInt(log2Ceil(conf.iksknumsegments).W))
	val accwriteaddr = Wire(UInt(log2Ceil(conf.iksknumsegments).W))
	accwriteaddr := DontCare

	// Read ports (per batch, per bus)
	val accreadport = for (b <- 0 until conf.numbatch) yield
		for (k <- 0 until conf.iksknumbus) yield
			accs(b)(k).read(accreadaddr, true.B)

	// Write ports (per batch, per bus)
	val accwriteport = for (b <- 0 until conf.numbatch) yield
		for (k <- 0 until conf.iksknumbus) yield
			accs(b)(k)(accwriteaddr)

	// Split read data into qbit-width elements per batch, per bus
	val accbus = Wire(Vec(conf.numbatch, Vec(conf.iksknumbus, Vec(elementsPerBus, UInt(conf.qbit.W)))))
	for (b <- 0 until conf.numbatch) {
		for (k <- 0 until conf.iksknumbus) {
			for (j <- 0 until elementsPerBus) {
				accbus(b)(k)(j) := accreadport(b)(k)((j+1)*conf.qbit-1, j*conf.qbit)
			}
		}
	}

	// Address decomposition (inline, for all batches)
	// Koga's optimization: offset + roundoffset added to a, then decomp - halfbase gives signed digit
	val areg = Reg(Vec(conf.numbatch, UInt(conf.Qbit.W)))
	val roundoffset:Long = if (conf.basebit * conf.t < conf.Qbit) 1L<<(conf.Qbit - conf.t*conf.basebit - 1) else 0L
	val halfbase = 1 << (conf.basebit - 1)
	val iksoffset:Long = (1 to conf.t).map(i => (1L << (conf.basebit - 1)) * (1L << (conf.Qbit - i*conf.basebit))).sum & ((1L << conf.Qbit) - 1)
	val combinedoffset:Long = (iksoffset + roundoffset) & ((1L << conf.Qbit) - 1)

	// State machine
	val statereg = RegInit(IdentityKeySwitchingState.WAIT)
	val dimreg = RegInit(0.U(conf.Nbit.W))
	val digitreg = RegInit(0.U(log2Ceil(conf.t).W))
	val addrreg = RegInit(1.U(conf.basebit.W))
	val segreg = RegInit(0.U(log2Ceil(conf.iksknumsegments).W))
	val dimbubblecnt = RegInit(0.U(2.W))

	// Output state
	val outnum = ceil((conf.n+1)*conf.qbit.toFloat/conf.buswidth).toInt
	val outbus = Wire(Vec(conf.iksknumbus, UInt(conf.buswidth.W)))
	val outsegcount = RegInit(0.U(log2Ceil(conf.iksknumbus).W))
	val outdimreg = RegInit(0.U(log2Ceil(outnum).W))
	val batchoutreg = RegInit(0.U(log2Ceil(conf.numbatch).W))
	val accreadportVec = VecInit(for (b <- 0 until conf.numbatch) yield VecInit(for (k <- 0 until conf.iksknumbus) yield accreadport(b)(k)))
	for (k <- 0 until conf.iksknumbus) {
		outbus(k) := accreadportVec(batchoutreg)(k)
	}

	// Compute decomposed address for all batches (with Koga's offset)
	val decomp = Wire(Vec(conf.numbatch, UInt(conf.basebit.W)))
	for (b <- 0 until conf.numbatch) {
		val aibar = areg(b) + combinedoffset.U(conf.Qbit.W)
		val decompbus = Wire(Vec(conf.t, UInt(conf.basebit.W)))
		for (i <- 0 until conf.t) {
			decompbus(i) := aibar(conf.Qbit - i*conf.basebit - 1, conf.Qbit - (i+1)*conf.basebit)
		}
		decomp(b) := decompbus(digitreg)
	}

	io.dimidx := dimreg
	io.axi4out.TDATA := outbus(outsegcount)
	accreadaddr := segreg

	// b-value placement constants
	val totalElementsPerSegment = conf.iksknumbus * elementsPerBus
	val bFlatIdx = conf.n - (conf.iksknumsegments - 1) * totalElementsPerSegment
	val bBusIdx = bFlatIdx / elementsPerBus
	val bLocalOffset = bFlatIdx % elementsPerBus

	switch(statereg) {
		is(IdentityKeySwitchingState.WAIT) {
			when(io.enable) {
				statereg := IdentityKeySwitchingState.INIT
			}
		}
		is(IdentityKeySwitchingState.INIT) {
			// Initialize all accumulators in parallel
			for (b <- 0 until conf.numbatch) {
				when(segreg =/= (conf.iksknumsegments-1).U) {
					for (k <- 0 until conf.iksknumbus) {
						accwriteport(b)(k) := 0.U
					}
				}.otherwise {
					for (k <- 0 until conf.iksknumbus) {
						accwriteport(b)(k) := 0.U
					}
					// Place b value in the correct bus and position
					val wireacc = Wire(Vec(elementsPerBus, UInt(conf.qbit.W)))
					for (j <- 0 until elementsPerBus) {
						wireacc(j) := 0.U
					}
					if(conf.Qbit > conf.qbit)
						wireacc(bLocalOffset) := (io.b(b) +& (1L << (conf.Qbit - conf.qbit - 1)).U) >> (conf.Qbit - conf.qbit)
					else
						wireacc(bLocalOffset) := io.b(b)
					accwriteport(b)(bBusIdx) := Cat(wireacc.reverse)
				}
			}
			accwriteaddr := segreg
			when(segreg =/= (conf.iksknumsegments-1).U) {
				segreg := segreg + 1.U
			}.otherwise {
				segreg := 0.U
				statereg := IdentityKeySwitchingState.ADDRBUBBLE
			}
		}
		is(IdentityKeySwitchingState.ADDRBUBBLE) {
			// Latch a-values for dim 0 (amem data available from AXISIKS)
			for (b <- 0 until conf.numbatch) {
				areg(b) := io.a(b)
			}
			statereg := IdentityKeySwitchingState.RUN
		}
		is(IdentityKeySwitchingState.RUN) {
			when(dimbubblecnt =/= 0.U) {
				// Wait for amem data to be available after dim change
				dimbubblecnt := dimbubblecnt - 1.U
				when(dimbubblecnt === 1.U) {
					for (b <- 0 until conf.numbatch) {
						areg(b) := io.a(b)
					}
				}
			}.otherwise {
				io.axi4ikskin.TREADY := true.B
				when(io.axi4ikskin.TVALID) {
					// Counter iteration: seg -> addr -> digit -> dim
					when(segreg =/= (conf.iksknumsegments-1).U) {
						segreg := segreg + 1.U
					}.otherwise {
						segreg := 0.U
						when(addrreg =/= halfbase.U) {
							addrreg := addrreg + 1.U
						}.otherwise {
							addrreg := 1.U
							when(digitreg =/= (conf.t-1).U) {
								digitreg := digitreg + 1.U
							}.otherwise {
								digitreg := 0.U
								when(dimreg =/= (conf.N-1).U) {
									dimreg := dimreg + 1.U
									dimbubblecnt := 2.U
								}.otherwise {
									dimreg := 0.U
									statereg := IdentityKeySwitchingState.LASTADD
								}
							}
						}
					}
				}
			}
			// Writeback (1 cycle delayed read-modify-write) with Koga's signed decomposition
			when(RegNext(io.axi4ikskin.TVALID && dimbubblecnt === 0.U && statereg === IdentityKeySwitchingState.RUN)) {
				accwriteaddr := RegNext(accreadaddr)
				for (b <- 0 until conf.numbatch) {
					// Koga's: decomp - halfbase gives signed digit
					// decomp == halfbase + addr → positive → subtract
					// decomp == halfbase - addr → negative → add
					val doSub = RegNext(decomp(b) === (halfbase.U +& addrreg))
					val doAdd = RegNext(decomp(b) === (halfbase.U - addrreg))
					for (k <- 0 until conf.iksknumbus) {
						val wireacc = Wire(Vec(elementsPerBus, UInt(conf.qbit.W)))
						for (j <- 0 until elementsPerBus) {
							val flatIdx = k * elementsPerBus + j
							val ikskdata = RegNext(io.axi4ikskin.TDATA((flatIdx+1)*conf.qbit-1, flatIdx*conf.qbit))
							wireacc(j) := Mux(doSub, accbus(b)(k)(j) - ikskdata,
								Mux(doAdd, accbus(b)(k)(j) + ikskdata, accbus(b)(k)(j)))
						}
						accwriteport(b)(k) := Cat(wireacc.reverse)
					}
				}
			}
		}
		is(IdentityKeySwitchingState.LASTADD) {
			// Catch the last writeback from the final RUN cycle
			accwriteaddr := RegNext(accreadaddr)
			for (b <- 0 until conf.numbatch) {
				val doSub = RegNext(decomp(b) === (halfbase.U +& addrreg))
				val doAdd = RegNext(decomp(b) === (halfbase.U - addrreg))
				for (k <- 0 until conf.iksknumbus) {
					val wireacc = Wire(Vec(elementsPerBus, UInt(conf.qbit.W)))
					for (j <- 0 until elementsPerBus) {
						val flatIdx = k * elementsPerBus + j
						val ikskdata = RegNext(io.axi4ikskin.TDATA((flatIdx+1)*conf.qbit-1, flatIdx*conf.qbit))
						wireacc(j) := Mux(doSub, accbus(b)(k)(j) - ikskdata,
							Mux(doAdd, accbus(b)(k)(j) + ikskdata, accbus(b)(k)(j)))
					}
					accwriteport(b)(k) := Cat(wireacc.reverse)
				}
			}
			statereg := IdentityKeySwitchingState.OUT
		}
		is(IdentityKeySwitchingState.OUT) {
			io.axi4ikskin.TREADY := true.B
			io.axi4out.TVALID := true.B
			when(io.axi4out.TREADY) {
				when(outdimreg === (outnum-1).U) {
					// Last output word of current batch
					outdimreg := 0.U
					outsegcount := 0.U
					segreg := 0.U
					accreadaddr := 0.U
					when(batchoutreg =/= (conf.numbatch-1).U) {
						batchoutreg := batchoutreg + 1.U
					}.otherwise {
						statereg := IdentityKeySwitchingState.LAST
					}
				}.otherwise {
					outdimreg := outdimreg + 1.U
					when(outsegcount =/= (conf.iksknumbus-1).U) {
						outsegcount := outsegcount + 1.U
					}.otherwise {
						outsegcount := 0.U
						accreadaddr := segreg + 1.U
						segreg := segreg + 1.U
					}
				}
			}
		}
		is(IdentityKeySwitchingState.LAST) {
			io.fin := true.B
		}
	}
	when(~io.enable) {
		digitreg := 0.U
		dimreg := 0.U
		addrreg := 1.U
		segreg := 0.U
		outdimreg := 0.U
		outsegcount := 0.U
		batchoutreg := 0.U
		dimbubblecnt := 0.U
		statereg := IdentityKeySwitchingState.WAIT
	}
}


class AXISIKS(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4out = new AXI4StreamManager(conf.buswidth)
		val axi4in = new AXI4StreamSubordinate(conf.buswidth)
		val axi4ikskin = Vec(conf.iksknumbus,new AXI4StreamSubordinate(conf.hbmbuswidth))

		val debug_iksenable = Output(Bool())
		val debug_loadstate = Output(UInt(2.W))
		val debug_batchloadcnt = Output(UInt(log2Ceil(conf.numbatch).W))
		val debug_tlwevalidout = Output(Bool())
		val debug_bvalue = Output(UInt(conf.Qbit.W))
		val debug_a0value = Output(UInt(conf.Qbit.W))
	})

	val IKS = Module(new IdentityKeySwitching)

	// IKSK bus handling with register slices
	val tvalidvec = Wire(Vec(conf.iksknumbus,Bool()))
	val tdatavec = Wire(Vec(conf.iksknumbus,UInt(conf.hbmbuswidth.W)))
	val treadywire = Wire(Bool())
	treadywire := false.B
	for(i <- 0 until conf.iksknumbus){
		val slice = Module(new AXI4StreamRegisterSlice(conf.hbmbuswidth,conf.axi4snumslice))
		slice.io.subordinate <> io.axi4ikskin(i)
		slice.io.manager.TREADY := treadywire
		tdatavec(i) := slice.io.manager.TDATA
		tvalidvec(i) := slice.io.manager.TVALID
	}

	// Forward all IKSK data to IKS (no addr matching - IKS handles it internally)
	IKS.io.axi4ikskin.TVALID := Cat(tvalidvec).andR
	IKS.io.axi4ikskin.TDATA := Cat(tdatavec.reverse)
	when(Cat(tvalidvec).andR) {
		treadywire := IKS.io.axi4ikskin.TREADY
	}

	// TLWE2Index for input extraction
	val tlwe2index = Module(new TLWE2Index(conf.buswidth,conf.N,conf.Qbit))
	val inslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	io.axi4in <> inslice.io.subordinate
	// Gate TLWE2Index input: only accept data when in IDLE and not resetting
	// This prevents second TLWE data from entering the queue during LOADING,
	// which would be lost when the queue is flushed between batches.
	val acceptInput = Wire(Bool())
	tlwe2index.io.axi4.TVALID := inslice.io.manager.TVALID && acceptInput
	tlwe2index.io.axi4.TDATA := inslice.io.manager.TDATA
	inslice.io.manager.TREADY := tlwe2index.io.axi4.TREADY && acceptInput

	// Batch TLWE buffering
	val amem = for (i <- 0 until conf.numbatch) yield
		SyncReadMem(conf.N, UInt(conf.Qbit.W))
	val bqueue = Reg(Vec(conf.numbatch, UInt(conf.Qbit.W)))

	// Loading state machine
	object LoadState extends ChiselEnum {
		val IDLE, LOADING, DONE = Value
	}
	val loadState = RegInit(LoadState.IDLE)
	val batchLoadCnt = RegInit(0.U(log2Ceil(conf.numbatch).W))
	val dimLoadCnt = RegInit(0.U(conf.Nbit.W))
	val tlweResetCnt = RegInit(0.U(2.W))

	acceptInput := loadState === LoadState.IDLE && tlweResetCnt === 0.U && !tlwe2index.io.validout
	tlwe2index.io.enable := loadState =/= LoadState.DONE
	tlwe2index.io.ready := false.B

	// IKS enable and debug
	val iksEnable = loadState === LoadState.DONE
	IKS.io.enable := iksEnable
	io.debug_iksenable := iksEnable
	io.debug_loadstate := loadState.asUInt
	io.debug_batchloadcnt := batchLoadCnt
	io.debug_tlwevalidout := tlwe2index.io.validout
	io.debug_bvalue := bqueue(0)
	val debug_a0_latch = Reg(UInt(conf.Qbit.W))
	io.debug_a0value := debug_a0_latch

	// Connect b values from buffer
	IKS.io.b := bqueue

	// Connect a values from per-batch amem (SyncReadMem, 1 cycle latency)
	for (b <- 0 until conf.numbatch) {
		IKS.io.a(b) := amem(b).read(IKS.io.dimidx)
	}

	// Output
	val outslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	io.axi4out <> outslice.io.manager
	outslice.io.subordinate <> IKS.io.axi4out

	// Loading FSM
	switch(loadState) {
		is(LoadState.IDLE) {
			when(tlweResetCnt =/= 0.U) {
				tlwe2index.io.enable := false.B
				tlweResetCnt := tlweResetCnt - 1.U
			}.elsewhen(tlwe2index.io.validout) {
				bqueue(batchLoadCnt) := tlwe2index.io.b
				dimLoadCnt := 0.U
				loadState := LoadState.LOADING
			}
		}
		is(LoadState.LOADING) {
			tlwe2index.io.ready := true.B
			for (b <- 0 until conf.numbatch) {
				when(batchLoadCnt === b.U) {
					amem(b).write(dimLoadCnt, tlwe2index.io.a)
				}
			}
			when(dimLoadCnt === 0.U && batchLoadCnt === 0.U) {
				debug_a0_latch := tlwe2index.io.a
			}
			when(dimLoadCnt === (conf.N-1).U) {
				dimLoadCnt := 0.U
				when(batchLoadCnt === (conf.numbatch-1).U) {
					batchLoadCnt := 0.U
					loadState := LoadState.DONE
				}.otherwise {
					batchLoadCnt := batchLoadCnt + 1.U
					tlweResetCnt := 2.U
					loadState := LoadState.IDLE
				}
			}.otherwise {
				dimLoadCnt := dimLoadCnt + 1.U
			}
		}
		is(LoadState.DONE) {
			when(IKS.io.fin) {
				loadState := LoadState.IDLE
				batchLoadCnt := 0.U
			}
		}
	}
}
