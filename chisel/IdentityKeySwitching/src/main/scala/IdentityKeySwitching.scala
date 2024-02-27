import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

object IdentityKeySwitchingState extends ChiselEnum {
  val WAIT, RUN, LAST = Value
}

class IdentityKeySwitching(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val b = Input(UInt(conf.Qbit.W))
		val a = Input(UInt(conf.Qbit.W))
		val out = Output(Vec(conf.n+1,UInt(conf.qbit.W)))
		val dim = Output(UInt(conf.Nbit.W))
		val digit = Output(UInt(log2Ceil(conf.t).W))
		val addr = Output(UInt(conf.basebit.W))
		val kskin = Input(Vec(conf.n+1,UInt(conf.qbit.W)))
		val enable = Input(Bool())
		val ready = Input(Bool())
		val fin = Output(Bool())
	})

	io.fin := false.B

	val accreg = Reg(Vec(conf.n+1,UInt(conf.qbit.W)))
	io.out := accreg
	val digitreg = RegInit(0.U(log2Ceil(conf.t).W))
	io.digit := digitreg
	val roundoffset = 1L<<(conf.Qbit - conf.t*conf.basebit - 1)
	val decompbus = Wire(Vec(conf.t,UInt(conf.basebit.W)))
	for (i <- 0 until conf.t){
		decompbus(i) := (io.a + roundoffset.U)(conf.Qbit - i * conf.basebit -1, conf.Qbit - (i+1)*conf.basebit)
	}
	io.addr := decompbus(digitreg)
	val dimreg = RegInit(0.U(conf.Nbit.W))
	io.dim := dimreg
	val statereg = RegInit(IdentityKeySwitchingState.WAIT)
	switch(statereg){
		is(IdentityKeySwitchingState.WAIT){
			when(io.enable){
				for(i <- 0 until conf.n){
					accreg(i) := 0.U
				}
				accreg(conf.n) := io.b
				digitreg := digitreg + 1.U
				statereg := IdentityKeySwitchingState.RUN
			}
		}
		is(IdentityKeySwitchingState.RUN){
			when(io.ready){
				for(i <- 0 to conf.n){
					accreg(i) := accreg(i) - io.kskin(i)
				}
			}
			when(io.ready || (RegNext(io.addr)===0.U)){
				when(digitreg =/= (conf.t-1).U){
					digitreg := digitreg + 1.U
				}.otherwise{
					digitreg := 0.U
					when(dimreg =/= (conf.N-1).U){
						dimreg := dimreg + 1.U
					}.otherwise{
						dimreg := 0.U
					}
				}
				when(dimreg === 0.U && digitreg === 0.U){
					dimreg := 0.U
					digitreg := 0.U
					statereg := IdentityKeySwitchingState.WAIT
					io.fin := true.B
				}
			}
		}
	}
}

class IdentityKeySwitchingWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt(((conf.N+1)*conf.Qbit).W))
		val out = Output(UInt(((conf.n+1)*conf.qbit).W))
		val kskin = Input(UInt(((conf.n+1)*conf.qbit).W))
		val fin = Output(Bool())
	})
	val IKS = Module(new IdentityKeySwitching)

	io.out := Cat(IKS.io.out.reverse)
	IKS.io.enable := false.B
	IKS.io.b := io.in((conf.N+1)*conf.Qbit-1,conf.N*conf.Qbit)
	val abus = Wire(Vec(conf.N,UInt(conf.Qbit.W)))
	for(i<-0 until conf.N){
		abus(i) := io.in((i+1)*conf.Qbit-1,i*conf.Qbit)
	}
	IKS.io.a := abus(IKS.io.dim)
	IKS.io.ready := false.B
	io.fin := RegNext(IKS.io.fin)

	val ikskmemaddr = Wire(UInt(log2Ceil(conf.N*conf.t*((1<<conf.basebit)-1)).W))
    ikskmemaddr := DontCare
    val ikskmem = for(i <- 0 until conf.ikskmemnumber) yield{
		val ikskmempart = SyncReadMem(conf.N*conf.t*((1<<conf.basebit)-1),UInt(conf.maxmemwidth.W))
		ikskmempart
	}
    val ikskmemPort = for(i <- 0 until conf.ikskmemnumber) yield{
		val ikskmemportpart = ikskmem(i)(ikskmemaddr)
		ikskmemportpart
	}
	val ikskmemwen = Wire(Bool())
	ikskmemwen := false.B
	val ikskmemWritedata = Wire(UInt(((conf.n+1)*conf.qbit).W))
	val ikskmemReaddataWire = Wire(UInt(((conf.n+1)*conf.qbit).W))
	val ikskmemReaddata = ShiftRegister(ikskmemReaddataWire,6)
	ikskmemReaddataWire := DontCare
	ikskmemWritedata := DontCare
	when(ikskmemwen){
		for(i <- 0 until conf.ikskmemnumber-1){
			ikskmemPort(i) := ikskmemWritedata((i+1)*conf.maxmemwidth-1,i*conf.maxmemwidth)
		}
		ikskmemPort(conf.ikskmemnumber-1) := ikskmemWritedata((conf.n+1)*conf.qbit-1,(conf.ikskmemnumber-1)*conf.maxmemwidth)
	}.otherwise{
		ikskmemReaddataWire := Cat(ikskmemPort.reverse)
	}
	for(i <- 0 to conf.n){
		IKS.io.kskin(i) := ikskmemReaddata((i+1)*conf.qbit-1,i*conf.qbit)
	}

	val initreg = RegInit(false.B)
    val initcntreg = RegInit(0.U(log2Ceil(conf.N*conf.t*((1<<conf.basebit)-1)).W))
	val readyreg = RegInit(false.B)
	val runreg = RegInit(true.B)
	IKS.io.ready := readyreg
    when(!initreg){
		IKS.io.enable := false.B
		ikskmemwen := true.B
        ikskmemWritedata := io.kskin
		ikskmemaddr := initcntreg
        when(initcntreg === (conf.N*conf.t*((1<<conf.basebit)-1)).U){
            initreg := true.B
        }.otherwise{
            initcntreg := initcntreg + 1.U
        }
    }.elsewhen(!IKS.io.fin&&runreg){
        IKS.io.enable := true.B
		when(IKS.io.addr =/= 0.U){
        	ikskmemaddr := IKS.io.dim*(conf.t*((1<<conf.basebit)-1)).U+IKS.io.digit*((1<<conf.basebit)-1).U+IKS.io.addr-1.U
			readyreg := true.B
		}.otherwise{
			readyreg := false.B
		}
    }.otherwise{
		runreg := false.B
	}
}

object IdentityKeySwitchingWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IdentityKeySwitchingWrap()(Config()))
}