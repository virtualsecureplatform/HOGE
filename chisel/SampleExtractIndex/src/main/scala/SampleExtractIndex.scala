import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

object SEIState extends ChiselEnum {
  val WAIT, POSITIVE, NEGATIVE, LAST, B, BUBBLE, OUT  = Value
}

class SampleExtractIndex(index : Int, implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4sin = new AXI4StreamSubordinate(conf.buswidth)
		val axi4sout = new AXI4StreamManager(conf.buswidth)
	})

	val numreg = conf.buswidth/conf.Qbit
	val queuedepth = conf.N/ numreg + 1
	val shiftamount = ((index+1) % numreg)*conf.Qbit

 	val seimem = Module(new RWSmem(queuedepth,conf.buswidth))

	val indexreg = Reg(UInt(log2Ceil(queuedepth).W))

	val buffereg = Reg(UInt(shiftamount.W))

	val posbreg = RegInit(0.U(log2Ceil(queuedepth-1).W))

	seimem.io.addr := indexreg
	seimem.io.wen := false.B
	seimem.io.in := DontCare
	io.axi4sin.TREADY := false.B
	io.axi4sout.TDATA := seimem.io.out
	io.axi4sout.TVALID := false.B

	val catted = Cat(io.axi4sin.TDATA,RegNext(io.axi4sin.TDATA>>shiftamount))

	val statereg = RegInit(SEIState.WAIT)
	switch(statereg){
		is(SEIState.WAIT){
			when(io.axi4sin.TVALID){
				io.axi4sin.TREADY := true.B
				buffereg := io.axi4sin.TDATA(shiftamount-1,0)
				if(index/numreg == 0){
					statereg := SEIState.NEGATIVE
					indexreg := (conf.N / numreg - 1).U
				}else{
					statereg := SEIState.POSITIVE
					indexreg := (index / numreg - 1).U
				}
			}
		}
		is(SEIState.POSITIVE){
			when(io.axi4sin.TVALID){
				io.axi4sin.TREADY := true.B
				seimem.io.wen := true.B
				when(indexreg =/= 0.U){
					indexreg := indexreg - 1.U
				}.otherwise{
					statereg := SEIState.NEGATIVE
					indexreg := (conf.N / numreg - 1).U
				}
				val revec = Wire(Vec(numreg,UInt(conf.Qbit.W)))
				for(i <- 0 until numreg){
					revec(i) := catted((i+1)*conf.Qbit-1,i*conf.Qbit)
				}
				seimem.io.in := Cat(revec)
			}
		}
		is(SEIState.NEGATIVE){
			when(io.axi4sin.TVALID){
				io.axi4sin.TREADY := true.B
				seimem.io.wen := true.B
				indexreg := indexreg - 1.U
				when(indexreg === (index / numreg + 1).U){
					statereg := SEIState.LAST
				}
				val revec = Wire(Vec(numreg,UInt(conf.Qbit.W)))
				for(i <- 0 until numreg){
					revec(i) := -catted((i+1)*conf.Qbit-1,i*conf.Qbit)
				}
				seimem.io.in := Cat(revec)
			}
		}
		is(SEIState.LAST){
			seimem.io.wen := true.B
			val revec = Wire(Vec(numreg,UInt(conf.Qbit.W)))
			val shiftelem = shiftamount/conf.Qbit
			val shifted = RegNext(io.axi4sin.TDATA>>shiftamount)
			for(i <- 0 until numreg - shiftelem){
				revec(i) := -(shifted((i+1)*conf.Qbit-1,i*conf.Qbit))
			}
			for(i <- 0 until shiftelem){
				revec(i + numreg - shiftelem) := buffereg((i+1)*conf.Qbit-1,i*conf.Qbit)
			}
			seimem.io.in := Cat(revec)
			indexreg := (queuedepth - 1).U
			statereg := SEIState.B
		}
		is(SEIState.B){
			when(io.axi4sin.TVALID){
				io.axi4sin.TREADY := true.B
				posbreg := posbreg+1.U
				when(posbreg === (index/numreg).U){	
					seimem.io.wen := true.B
					seimem.io.in := io.axi4sin.TDATA((1+index%numreg)*conf.Qbit -1, (index%numreg)*conf.Qbit)
					indexreg := 0.U
					statereg := SEIState.BUBBLE
				}
			}
		}
		is(SEIState.BUBBLE){
			statereg := SEIState.OUT
		}
		is(SEIState.OUT){
			io.axi4sin.TREADY := true.B
			io.axi4sout.TVALID := true.B
			when(io.axi4sout.TREADY){
				when(indexreg =/= (queuedepth-1).U){
					seimem.io.addr := indexreg + 1.U
					indexreg := indexreg + 1.U
				}.otherwise{
					statereg := SEIState.WAIT
				}
			}
		}
	}
}

class SampleExtractIndexWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val axi4sin = new AXI4StreamSubordinate(conf.buswidth)
		val axi4sout = new AXI4StreamManager(conf.buswidth)
	})

	val SEI = Module(new SampleExtractIndex(0,conf))
	io.axi4sin<>SEI.io.axi4sin
	io.axi4sout<>SEI.io.axi4sout
}

object  SampleExtractIndexWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new SampleExtractIndexWrap()(Config()))
}