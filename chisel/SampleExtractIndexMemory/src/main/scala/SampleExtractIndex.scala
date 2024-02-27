import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

object SampleExtractIndexState extends ChiselEnum {
  val WAIT, POSITIVE, NEGATIVE, B, FIN = Value
}

class SampleExtractIndex(index : Int, implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val addr = Output(UInt((conf.radixbit+1).W))
		val axi4sout = new AXI4StreamManager(conf.Qbit)

		val enable = Input(Bool())
	})


	val statereg = RegInit(SampleExtractIndexState.WAIT)
	val cntreg = RegInit(0.U(conf.Nbit.W))
	
	io.addr := index.U
	io.axi4sout.TVALID := false.B
	io.axi4sout.TDATA := DontCare
	val wordwire = Wire(Vec(conf.block,UInt(conf.Qbit.W)))
	for(i <- 0 until conf.block){
		wordwire(i) := io.in((i+1)*conf.Qbit-1,i*conf.Qbit)
	}

	switch(statereg){
		is(SampleExtractIndexState.WAIT){
			when(io.enable){
				statereg := SampleExtractIndexState.POSITIVE
			}
		}
		is(SampleExtractIndexState.POSITIVE){
			val inindex = (index.U - cntreg)(conf.Nbit-1,conf.Nbit-conf.radixbit)
			io.addr := (index.U - cntreg - 1.U)(conf.radixbit-1,0)
			io.axi4sout.TDATA := wordwire(inindex)
			io.axi4sout.TVALID := true.B
			cntreg := cntreg + 1.U
			when(cntreg === index.U){
				io.addr := ((conf.N+index).U - cntreg - 1.U)(conf.radixbit-1,0)
				statereg := SampleExtractIndexState.NEGATIVE
			}
		}
		is(SampleExtractIndexState.NEGATIVE){
			val inindex = ((conf.N+index).U - cntreg)(conf.Nbit-1,conf.Nbit-conf.radixbit)
			io.addr := ((conf.N+index).U - cntreg - 1.U)(conf.radixbit-1,0)
			io.axi4sout.TDATA := - wordwire(inindex)
			io.axi4sout.TVALID := true.B
			when(cntreg === (conf.N-1).U){
				io.addr := 1.U << (conf.radixbit)
				statereg := SampleExtractIndexState.B
			}.otherwise{
				cntreg := cntreg  + 1.U
			}
		}
		is(SampleExtractIndexState.B){
			io.axi4sout.TDATA := io.in((index+1)*conf.Qbit-1,index*conf.Qbit)
			io.axi4sout.TVALID := true.B
			statereg := SampleExtractIndexState.FIN
		}
	}

	when(~io.enable){
		statereg := SampleExtractIndexState.WAIT
		cntreg := 0.U
	}
}

object SampleExtractIndexWrapState extends ChiselEnum {
  val INIT, BUBBLE, RUN = Value
}

class SampleExtractIndexWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((conf.block*conf.Qbit).W))
		val axi4sout = new AXI4StreamManager(conf.Qbit)
		val enable = Input(Bool())
	})
	val sei = Module(new SampleExtractIndex(0,conf))
	io.axi4sout <> sei.io.axi4sout
	
	sei.io.enable := false.B

	val inmem = Module(new RWSmem(2*conf.radix,conf.block*conf.Qbit))
	sei.io.in := inmem.io.out
	inmem.io.in := io.in
	inmem.io.wen := false.B
	inmem.io.addr := DontCare
	val initcnt = RegInit(0.U((conf.radixbit+1).W))

	val statereg = RegInit(SampleExtractIndexWrapState.INIT)
	switch(statereg){
		is(SampleExtractIndexWrapState.INIT){
			inmem.io.addr := initcnt
			inmem.io.wen := true.B
			when(initcnt =/= (2*conf.radix-1).U){
				initcnt := initcnt + 1.U
			}.otherwise{
				initcnt := 0.U
				statereg := SampleExtractIndexWrapState.BUBBLE
			}
		}
		is(SampleExtractIndexWrapState.BUBBLE){
			statereg := SampleExtractIndexWrapState.RUN
			inmem.io.addr := sei.io.addr
		}
		is(SampleExtractIndexWrapState.RUN){
			sei.io.enable := true.B
			inmem.io.addr := sei.io.addr
		}
	}
	when(~io.enable){
		statereg := SampleExtractIndexWrapState.INIT
		initcnt := 0.U
	}
}

object SampleExtractIndexWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new SampleExtractIndexWrap()(Config()))
}