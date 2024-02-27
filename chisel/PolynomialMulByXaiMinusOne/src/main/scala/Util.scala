import chisel3._
import chisel3.util._

// https://www.chisel-lang.org/chisel3/docs/explanations/memories.html
class RWSmem(depth:Int, width:Int) extends Module {
  val io = IO(new Bundle {
    val wen = Input(Bool())
    val addr = Input(UInt(log2Ceil(depth).W))
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })

  val mem = SyncReadMem(depth, UInt(width.W))
  io.out := DontCare
  val rdwrPort = mem(io.addr)
  when (io.wen) { rdwrPort := io.in }
    .otherwise    { io.out := rdwrPort }
}

// Simple Dual Port
class RWDmem(depth:Int, width:Int) extends Module {
  val io = IO(new Bundle {
    val wen = Input(Bool())
    val waddr = Input(UInt(log2Ceil(depth).W))
    val raddr = Input(UInt(log2Ceil(depth).W))
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
  })

  val mem = SyncReadMem(depth, UInt(width.W))
  io.out := mem(io.raddr)
  when (io.wen) { mem(io.waddr) := io.in }
}

// Simple Dual Port + Additional Read Port
class RWSRmem(depth:Int, width:Int) extends Module {
  val io = IO(new Bundle {
    val wen = Input(Bool())
    val addr = Input(UInt(log2Ceil(depth).W))
    val raddr = Input(UInt(log2Ceil(depth).W))
    val in = Input(UInt(width.W))
    val out = Output(UInt(width.W))
    val rout = Output(UInt(width.W))
  })

  val mem = SyncReadMem(depth, UInt(width.W))
  io.rout := mem(io.raddr)
  io.out := DontCare
  val rdwrPort = mem(io.addr)
  when (io.wen) { rdwrPort := io.in }
    .otherwise    { io.out := rdwrPort }
}