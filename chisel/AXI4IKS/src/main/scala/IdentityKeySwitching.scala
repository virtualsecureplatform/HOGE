import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.ceil

object AddressDecompositionState extends ChiselEnum {
  val WAIT, RUN, BUBBLE, FIN  = Value
}

class AddressDecomposition(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt(conf.Qbit.W))
		val out = Output(UInt(conf.basebit.W))
		val dimready = Output(Bool())
		val enable = Input(Bool())
		val ready = Input(Bool())
	})
	val dimreg = RegInit(0.U(conf.Nbit.W))
	io.dimready := false.B
	val digitreg = RegInit(0.U(log2Ceil(conf.t).W))
	val roundoffset = 1L<<(conf.Qbit - conf.t*conf.basebit - 1)
	val decompbus = Wire(Vec(conf.t,UInt(conf.basebit.W)))
	val areg = Reg(UInt(conf.Qbit.W))
	for (i <- 0 until conf.t){
		decompbus(i) := (areg + roundoffset.U)(conf.Qbit - i * conf.basebit -1, conf.Qbit - (i+1)*conf.basebit)
	}
	val outqueue = Module(new Queue(UInt(conf.basebit.W),2*conf.basebit))
	outqueue.io.enq.bits := decompbus(digitreg)
	outqueue.io.enq.valid := false.B
	outqueue.io.deq.ready := io.ready
	io.out := outqueue.io.deq.bits

	val statereg = RegInit(AddressDecompositionState.WAIT)

	switch(statereg){
		is(AddressDecompositionState.WAIT){
			when(io.enable){
				areg := io.in
				dimreg := 1.U
				io.dimready := true.B
				statereg := AddressDecompositionState.RUN
			}
		}
		is(AddressDecompositionState.RUN){
			when(outqueue.io.enq.ready){
				outqueue.io.enq.valid := true.B
				when(digitreg =/= (conf.t-1).U){
					digitreg := digitreg + 1.U
				}.otherwise{
					digitreg := 0.U
					areg := io.in
					when(dimreg =/= (conf.N-1).U){
						dimreg := dimreg + 1.U
						io.dimready := true.B
					}.otherwise{
						dimreg := 0.U
						statereg := AddressDecompositionState.BUBBLE
					}
				}
			}
		}
		is(AddressDecompositionState.BUBBLE){
			when(outqueue.io.enq.ready){
				outqueue.io.enq.valid := true.B
				when(digitreg =/= (conf.t-1).U){
					digitreg := digitreg + 1.U
				}.otherwise{
					digitreg := 0.U
					statereg := AddressDecompositionState.FIN
				}
			}
		}
	}
	when(~io.enable){
		dimreg := 0.U
		digitreg := 0.U
		statereg := AddressDecompositionState.WAIT
	}
}

object IdentityKeySwitchingState extends ChiselEnum {
  val WAIT, INIT, ADDRBUBBLE, RUN, LASTADD, OUT, LAST  = Value
}

class IdentityKeySwitching(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val b = Input(UInt(conf.Qbit.W))
		val a = Input(UInt(conf.Qbit.W))
		val dimready = Output(Bool())
		val addr = Output(UInt(conf.basebit.W))
		val added = Output(Bool())
		val enable = Input(Bool())
		val fin = Output(Bool())

		val axi4ikskin = new AXI4StreamSubordinate(conf.hbmbuswidth*conf.iksknumbus)
		val axi4out = new AXI4StreamManager(conf.buswidth)
	})
	io.axi4ikskin.TREADY := false.B
	io.added := false.B
	io.fin := false.B
	io.axi4out.TVALID := false.B

	val acc = SyncReadMem(conf.iksknumsegments,UInt((conf.hbmbuswidth*conf.iksknumbus).W))
	val accreadaddr = Wire(UInt(log2Ceil(conf.iksknumsegments).W))
	val accreadport = acc(accreadaddr)
	val accwriteaddr = Wire(UInt(log2Ceil(conf.iksknumsegments).W))
	val accwriteport = acc(accwriteaddr)
	val accbus = Wire(Vec(conf.iksknumbus*conf.hbmbuswidth/conf.qbit,UInt(conf.qbit.W)))
	for(i <- 0 until conf.iksknumbus*conf.hbmbuswidth/conf.qbit){
		accbus(i) := accreadport((i+1)*conf.qbit-1,i*conf.qbit)
	}
	

	val addrdecomp = Module(new AddressDecomposition())
	io.dimready := addrdecomp.io.dimready
	io.addr := addrdecomp.io.out
	addrdecomp.io.in := io.a
	addrdecomp.io.ready := false.B
	addrdecomp.io.enable := io.enable

	val statereg = RegInit(IdentityKeySwitchingState.WAIT)
	val dimreg = RegInit(0.U(conf.Nbit.W))
	val digitreg = RegInit(0.U(log2Ceil(conf.t).W))
	val segreg = RegInit(0.U(log2Ceil(conf.iksknumsegments).W))
	val outnum = ceil((conf.n+1)*conf.qbit.toFloat/conf.buswidth).toInt
	val outbus = Wire(Vec(conf.iksknumbus/2,UInt(conf.buswidth.W)))
	val outsegcount = RegInit(0.U(log2Ceil(conf.iksknumbus/2).W))
	for(i <- 0 until conf.iksknumbus/2){
		outbus(i) := accreadport((i+1)*conf.buswidth-1,i*conf.buswidth)
	}
	val outdimreg = RegInit(0.U(log2Ceil(outnum).W))
	accreadaddr := segreg
	accwriteaddr := DontCare
	io.axi4out.TDATA := outbus(outsegcount)
	switch(statereg){
		is(IdentityKeySwitchingState.WAIT){
			when(io.enable){
				statereg := IdentityKeySwitchingState.INIT
			}
		}
		is(IdentityKeySwitchingState.INIT){
			accwriteaddr := segreg
			when(segreg =/= (conf.iksknumsegments-1).U){
				segreg := segreg + 1.U
				accwriteport := 0.U
			}.otherwise{
				segreg := 0.U
				val wireacc = Wire(Vec(conf.iksknumbus*conf.hbmbuswidth/conf.qbit,UInt(conf.qbit.W)))
				for(i <- 0 until conf.iksknumbus*conf.hbmbuswidth/conf.qbit){
					wireacc(i) := 0.U
				}
				wireacc(conf.n - (conf.iksknumsegments-1)*conf.hbmbuswidth*conf.iksknumbus/conf.qbit) := io.b
				accwriteport := Cat(wireacc.reverse)
				statereg := IdentityKeySwitchingState.ADDRBUBBLE
			}			
		}
		is(IdentityKeySwitchingState.ADDRBUBBLE){
			statereg := IdentityKeySwitchingState.RUN
		}
		is(IdentityKeySwitchingState.RUN){
			io.axi4ikskin.TREADY := true.B
			when(io.axi4ikskin.TVALID){
				when(segreg =/= (conf.iksknumsegments-1).U){
					segreg := segreg + 1.U
				}.otherwise{
					segreg := 0.U
					io.added := true.B
					addrdecomp.io.ready := true.B
					when(digitreg =/= (conf.t-1).U){
						digitreg := digitreg + 1.U
					}.otherwise{
						digitreg := 0.U
						when(dimreg =/= (conf.N-1).U){
							dimreg := dimreg + 1.U
						}.otherwise{
							dimreg := 0.U
							statereg := IdentityKeySwitchingState.LASTADD
						}
					}
				}
			}
			when(RegNext(io.axi4ikskin.TVALID)){
				val wireacc = Wire(Vec(conf.hbmbuswidth*conf.iksknumbus/conf.qbit,UInt(conf.qbit.W)))
				accwriteaddr := RegNext(accreadaddr)
				for(i <- 0 until conf.hbmbuswidth*conf.iksknumbus/conf.qbit){
					wireacc(i) := accbus(i) - RegNext(io.axi4ikskin.TDATA((i+1)*conf.qbit-1,i*conf.qbit))
				}
				accwriteport := Cat(wireacc.reverse)
			}
		}
		is(IdentityKeySwitchingState.LASTADD){
			val wireacc = Wire(Vec(conf.hbmbuswidth*conf.iksknumbus/conf.qbit,UInt(conf.qbit.W)))
			accwriteaddr := RegNext(accreadaddr)
			for(i <- 0 until conf.hbmbuswidth*conf.iksknumbus/conf.qbit){
				wireacc(i) := accbus(i) - RegNext(io.axi4ikskin.TDATA((i+1)*conf.qbit-1,i*conf.qbit))
			}
			accwriteport := Cat(wireacc.reverse)
			statereg := IdentityKeySwitchingState.OUT
		}
		is(IdentityKeySwitchingState.OUT){
			io.axi4out.TVALID := true.B
			when(io.axi4out.TREADY){
				when(outdimreg =/= (outnum-1).U){
					outdimreg := outdimreg + 1.U
				}.otherwise{
					statereg := IdentityKeySwitchingState.LAST
				}
				when(outsegcount =/= (conf.iksknumbus/2-1).U){
					outsegcount := outsegcount + 1.U
				}.otherwise{
					outsegcount := 0.U
					accreadaddr := segreg + 1.U
					segreg := segreg + 1.U
				}
			}
		}
		is(IdentityKeySwitchingState.LAST){
			io.fin := true.B
		}
	}
	when(~io.enable){
		digitreg := 0.U
		dimreg := 0.U
		segreg := 0.U
		outdimreg := 0.U
		outsegcount := 0.U
		statereg := IdentityKeySwitchingState.WAIT
	}
}

object AXI4IKSState extends ChiselEnum {
  val WAIT, INIT, RUN = Value
}

import math.ceil

class AXI4IKS(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4out = new AXI4StreamManager(conf.buswidth)
		val axi4in = new AXI4StreamSubordinate(conf.buswidth)
		val axi4ikskin = Vec(conf.iksknumbus,new AXI4StreamSubordinate(conf.hbmbuswidth))
		val axi4outcmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4incmd = new AXI4StreamManager(conf.cmdbuswidth)
		val axi4ikskincmd = Vec(conf.iksknumbus,new AXI4StreamManager(conf.cmdbuswidth))

		val outaddr = Input(UInt(64.W))
		val inaddr = Input(UInt(64.W))
		val ikskaddr = Input(Vec(conf.iksknumbus,UInt(64.W)))

		val ap_start = Input(Bool())
		val ap_done = Output(Bool())
		val ap_idle = Output(Bool())
		val ap_ready = Output(Bool())
	})
	io.ap_idle := true.B
	io.ap_done := false.B
	io.ap_ready := io.ap_done

	io.axi4outcmd.TVALID := false.B

	io.axi4incmd.TVALID := false.B

	io.axi4outcmd.TDATA:=Cat(io.outaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.qbit*(conf.n+1).toFloat/8).toInt.U(23.W))
	io.axi4incmd.TDATA:=Cat(io.inaddr,false.B,true.B,0.U(6.W),true.B,ceil(conf.Qbit*(conf.N+1).toFloat/8).toInt.U(23.W))

	for(i <- 0 until conf.iksknumbus){
		io.axi4ikskincmd(i).TVALID := false.B
		io.axi4ikskincmd(i).TDATA:=Cat(io.ikskaddr(i),false.B,true.B,0.U(6.W),true.B,(conf.hbmbuswidth*(conf.totaliksknumbus/conf.iksknumbus)*((1<<conf.basebit)-1)*conf.t*conf.N/8).U(23.W))
	}

	val IKS = Module(new IdentityKeySwitching)
	IKS.io.enable := false.B
	
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
	IKS.io.axi4ikskin.TVALID := false.B
	IKS.io.axi4ikskin.TDATA := Cat(tdatavec.reverse)

	val tlwe2index = Module(new TLWE2Index(conf.buswidth,conf.N,conf.Qbit))
	val inslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	io.axi4in <> inslice.io.subordinate
	tlwe2index.io.axi4 <> inslice.io.manager

	tlwe2index.io.ready := IKS.io.dimready
	tlwe2index.io.enable := ~IKS.io.fin
	IKS.io.b := tlwe2index.io.b
	IKS.io.a := tlwe2index.io.a

	val outslice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	io.axi4out <> outslice.io.manager
	outslice.io.subordinate <> IKS.io.axi4out

	val addrreg = RegInit(1.U(conf.basebit.W))
	val added = RegInit(false.B)
	val statereg = RegInit(AXI4IKSState.WAIT)

	val outcmdreg = RegInit(false.B)
	val incmdreg = RegInit(false.B)
	val ikskincmdreg = RegInit(VecInit(Seq.fill(conf.iksknumbus)(0.U(1.W))))
	val segreg = RegInit(0.U(log2Ceil(conf.iksknumsegments).W))

	switch(statereg){
		is(AXI4IKSState.WAIT){
			when(io.ap_start){
				statereg := AXI4IKSState.INIT
			}
		}
		is(AXI4IKSState.INIT){
			io.ap_idle := false.B

			when(~outcmdreg){
				 io.axi4outcmd.TVALID := true.B
				outcmdreg := io.axi4outcmd.TREADY
			}
			when(~incmdreg){
				io.axi4incmd.TVALID := true.B
				incmdreg := io.axi4incmd.TREADY
			}
			when(ShiftRegister(tlwe2index.io.validout,conf.changesizeslice)){
				for(i <- 0 until conf.iksknumbus){
					io.axi4ikskincmd(i).TVALID := ikskincmdreg(i)===0.U
					ikskincmdreg(i):= io.axi4ikskincmd(i).TREADY && (ikskincmdreg(i)===0.U)
				}
			}
			when(outcmdreg && incmdreg && Cat(ikskincmdreg).andR){
				statereg := AXI4IKSState.RUN
			}
		}
		is(AXI4IKSState.RUN){
			io.ap_idle := false.B
			IKS.io.enable := true.B
			when(Cat(tvalidvec).andR){
				treadywire := IKS.io.axi4ikskin.TREADY
				when(~added){
					when(IKS.io.addr===0.U){
						IKS.io.axi4ikskin.TDATA := 0.U
						IKS.io.axi4ikskin.TVALID := true.B
						added := segreg === (conf.iksknumsegments-1).U
					}.elsewhen(IKS.io.addr===addrreg){
						IKS.io.axi4ikskin.TVALID := true.B
						added := segreg === (conf.iksknumsegments-1).U
					}
				}
				when(segreg =/= (conf.iksknumsegments-1).U){
					segreg := segreg + 1.U
				}.otherwise{
					segreg := 0.U
					when(addrreg =/= ((1<<conf.basebit)-1).U){
						addrreg := addrreg + 1.U
					}.otherwise{
						addrreg := 1.U
						added := false.B
					}
				}
			}
			when(IKS.io.fin){
				statereg := AXI4IKSState.WAIT
				IKS.io.enable := false.B
				io.ap_done := true.B
			}
		}
	}
}

object AXI4IKSTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new AXI4IKS()(Config()))
}