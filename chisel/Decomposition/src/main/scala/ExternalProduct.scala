import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import math.log
import math.ceil

class Decomposition(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val in = Input(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
		val out = Output(Vec(conf.chunk,Vec(conf.radix,UInt(conf.Qbit.W))))
		val digit = Input(UInt(ceil(log(conf.l)/log(2)).toInt.W))
	})

	def offsetgen(implicit conf: Config): Long = {
		var offset :Long = 0
		for(i <- 1 to conf.l){
			offset = offset + conf.Bg/2 * (1L<<(conf.Qbit - i * conf.Bgbit))
		}
		offset
	}

	val offset: Long = offsetgen(conf)
	val raundoffset: Long = 1L << (conf.Qbit - conf.l * conf.Bgbit - 1)

	for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
        val addedoffset = io.in(i)(j) + (offset + raundoffset).U
		val extnum = Wire(Vec(conf.l,UInt(conf.Qbit.W)))
            for(k <- 0 until conf.l){
                extnum(k) := addedoffset(conf.Qbit-k*conf.Bgbit-1,conf.Qbit-(k+1)*conf.Bgbit)
            }
			io.out(i)(j) := extnum(io.digit) - (conf.Bg/2).U
        }
	}
}

class DecompositionWrap(implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
		val in = Input(UInt((conf.fiber*32).W))
		val out = Output(UInt((conf.fiber*32).W))
		val digit = Input(UInt(ceil(log(conf.l)/log(2)).toInt.W))
	})
    val decomp = Module(new Decomposition)
    decomp.io.digit := io.digit
    for(i <- 0 until conf.chunk){
        for(j <- 0 until conf.radix){
			val index = i*conf.radix+j
            decomp.io.in(i)(j) := io.in(index*32+31,index*32)
        }
    }
    io.out := Cat(decomp.io.out.flatten.reverse)
}

object DecompositionTop extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new DecompositionWrap()(Config()))
}