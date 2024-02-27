import chisel3._
import chisel3.util._

class SampleExtractIndex(index : Int, implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(2,Vec(conf.N,UInt(conf.Qbit.W))))
		val out = Output(Vec(conf.N+1,UInt(conf.Qbit.W)))
	})

	for(i <- 0 to index){
		io.out(i) := io.in(0)(index-i)
	}
	for(i <- index+1 until conf.N){
		io.out(i) := -io.in(0)(conf.N+index-i)
	}
	io.out(conf.N) := io.in(1)(index)
}

class SampleExtractIndexWrap(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(UInt((2*conf.N*conf.Qbit).W))
		val out = Output(UInt(((conf.N+1)*conf.Qbit).W))
	})

	val SEI = Module(new SampleExtractIndex(0,conf))
	for(i <- 0 until 2){
		for(j <- 0 until conf.N){
			SEI.io.in(i)(j) := io.in((i*conf.N+j+1)*conf.Qbit-1,(i*conf.N+j)*conf.Qbit)
		}
	}
	io.out := Cat(SEI.io.out.reverse)
}

object  SampleExtractIndexWrapTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new SampleExtractIndexWrap()(Config()))
}