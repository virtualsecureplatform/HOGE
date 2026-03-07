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

	// numbatch accumulators
	val accs = for (i <- 0 until conf.numbatch) yield
		SyncReadMem(conf.iksknumsegments, UInt((conf.hbmbuswidth*conf.iksknumbus).W))

	val accreadaddr = Wire(UInt(log2Ceil(conf.iksknumsegments).W))
	val accwriteaddr = Wire(UInt(log2Ceil(conf.iksknumsegments).W))
	accwriteaddr := DontCare

	// Read from all accumulators simultaneously
	val accreaddata = Wire(Vec(conf.numbatch, UInt((conf.hbmbuswidth*conf.iksknumbus).W)))
	for (i <- 0 until conf.numbatch) {
		accreaddata(i) := accs(i).read(accreadaddr)
	}

	// Split read data into qbit-width elements per batch
	val accbus = Wire(Vec(conf.numbatch, Vec(conf.hbmbuswidth*conf.iksknumbus/conf.qbit, UInt(conf.qbit.W))))
	for (b <- 0 until conf.numbatch) {
		for (i <- 0 until conf.hbmbuswidth*conf.iksknumbus/conf.qbit) {
			accbus(b)(i) := accreaddata(b)((i+1)*conf.qbit-1, i*conf.qbit)
		}
	}

	// Address decomposition (inline, for all batches)
	val areg = Reg(Vec(conf.numbatch, UInt(conf.Qbit.W)))
	val roundoffset = 1L<<(conf.Qbit - conf.t*conf.basebit - 1)

	// State machine
	val statereg = RegInit(IdentityKeySwitchingState.WAIT)
	val dimreg = RegInit(0.U(conf.Nbit.W))
	val digitreg = RegInit(0.U(log2Ceil(conf.t).W))
	val addrreg = RegInit(1.U(conf.basebit.W))
	val segreg = RegInit(0.U(log2Ceil(conf.iksknumsegments).W))
	val dimbubblecnt = RegInit(0.U(2.W))

	// Output state
	val outnum = ceil((conf.n+1)*conf.qbit.toFloat/conf.buswidth).toInt
	val outsegcount = RegInit(0.U(log2Ceil(conf.iksknumbus*conf.hbmbuswidth/conf.buswidth).W))
	val outdimreg = RegInit(0.U(log2Ceil(outnum).W))
	val batchoutreg = RegInit(0.U(log2Ceil(conf.numbatch).W))

	// Compute decomposed address for all batches
	val decomp = Wire(Vec(conf.numbatch, UInt(conf.basebit.W)))
	for (b <- 0 until conf.numbatch) {
		val decompbus = Wire(Vec(conf.t, UInt(conf.basebit.W)))
		for (i <- 0 until conf.t) {
			decompbus(i) := (areg(b) + roundoffset.U)(conf.Qbit - i*conf.basebit - 1, conf.Qbit - (i+1)*conf.basebit)
		}
		decomp(b) := decompbus(digitreg)
	}

	// Output bus (selects from the batch being output)
	val outbus = Wire(Vec(conf.iksknumbus*conf.hbmbuswidth/conf.buswidth, UInt(conf.buswidth.W)))
	for (i <- 0 until conf.iksknumbus*conf.hbmbuswidth/conf.buswidth) {
		outbus(i) := accreaddata(batchoutreg)((i+1)*conf.buswidth-1, i*conf.buswidth)
	}

	io.dimidx := dimreg
	io.axi4out.TDATA := outbus(outsegcount)
	accreadaddr := segreg

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
					accs(b).write(segreg, 0.U)
				}.otherwise {
					val wireacc = Wire(Vec(conf.iksknumbus*conf.hbmbuswidth/conf.qbit, UInt(conf.qbit.W)))
					for (i <- 0 until conf.iksknumbus*conf.hbmbuswidth/conf.qbit) {
						wireacc(i) := 0.U
					}
					wireacc(conf.n - (conf.iksknumsegments-1)*conf.hbmbuswidth*conf.iksknumbus/conf.qbit) := io.b(b)
					accs(b).write(segreg, Cat(wireacc.reverse))
				}
			}
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
						when(addrreg =/= ((1<<conf.basebit)-1).U) {
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
			// Writeback (1 cycle delayed read-modify-write)
			when(RegNext(io.axi4ikskin.TVALID && dimbubblecnt === 0.U && statereg === IdentityKeySwitchingState.RUN)) {
				accwriteaddr := RegNext(accreadaddr)
				for (b <- 0 until conf.numbatch) {
					val wireacc = Wire(Vec(conf.hbmbuswidth*conf.iksknumbus/conf.qbit, UInt(conf.qbit.W)))
					for (i <- 0 until conf.hbmbuswidth*conf.iksknumbus/conf.qbit) {
						wireacc(i) := Mux(RegNext(decomp(b) === addrreg),
							accbus(b)(i) - RegNext(io.axi4ikskin.TDATA((i+1)*conf.qbit-1, i*conf.qbit)),
							accbus(b)(i))
					}
					accs(b).write(accwriteaddr, Cat(wireacc.reverse))
				}
			}
		}
		is(IdentityKeySwitchingState.LASTADD) {
			// Catch the last writeback from the final RUN cycle
			accwriteaddr := RegNext(accreadaddr)
			for (b <- 0 until conf.numbatch) {
				val wireacc = Wire(Vec(conf.hbmbuswidth*conf.iksknumbus/conf.qbit, UInt(conf.qbit.W)))
				for (i <- 0 until conf.hbmbuswidth*conf.iksknumbus/conf.qbit) {
					wireacc(i) := Mux(RegNext(decomp(b) === addrreg),
						accbus(b)(i) - RegNext(io.axi4ikskin.TDATA((i+1)*conf.qbit-1, i*conf.qbit)),
						accbus(b)(i))
				}
				accs(b).write(accwriteaddr, Cat(wireacc.reverse))
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
					when(outsegcount === (conf.iksknumbus*conf.hbmbuswidth/conf.buswidth-1).U) {
						outsegcount := 0.U
						accreadaddr := segreg + 1.U
						segreg := segreg + 1.U
					}.otherwise {
						outsegcount := outsegcount + 1.U
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
