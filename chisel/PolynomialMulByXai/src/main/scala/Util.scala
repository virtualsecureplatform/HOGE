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