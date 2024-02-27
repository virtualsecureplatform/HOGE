import chisel3._
import chisel3.util._
import chisel3.util.HasBlackBoxInline

// https://www.chisel-lang.org/chisel3/docs/explanations/memories.html
// Simple Single Port
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

// Simple Single Port + Additional Read Port
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

class BK2Formerslice(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val subordinate = new AXI4StreamSubordinate(conf.buswidth)
		val manager = new AXI4StreamManager(conf.buswidth)
	})
	val slice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	io.subordinate <> slice.io.subordinate
	io.manager <> slice.io.manager
}

class NTTdataPipeline(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val subordinate = new AXI4StreamSubordinate(conf.buswidth)
		val manager = new AXI4StreamManager(conf.buswidth)
	})
	io.subordinate.TREADY := true.B
	io.manager.TDATA := ShiftRegister(io.subordinate.TDATA,conf.axi4snumslice)
	io.manager.TVALID := ShiftRegister(io.subordinate.TVALID,conf.axi4snumslice)
}

class GlobalInslice(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val subordinate = new AXI4StreamSubordinate(conf.buswidth)
		val manager = new AXI4StreamManager(conf.buswidth)
	})
	val slice = Module(new AXI4StreamRegisterSlice(conf.buswidth,conf.axi4snumslice))
	io.subordinate <> slice.io.subordinate
	io.manager <> slice.io.manager
}

class GlobalOutslice(implicit val conf:Config) extends Module{
	val io = IO(new Bundle{
		val subordinate = new AXI4StreamSubordinate(conf.Qbit)
		val manager = new AXI4StreamManager(conf.Qbit)
	})
	val slice = Module(new AXI4StreamRegisterSlice(conf.Qbit,conf.axi4snumslice))
	io.subordinate <> slice.io.subordinate
	io.manager <> slice.io.manager
}

class MultPort extends Bundle {
    val A = Input(UInt(64.W))
    val B = Input(UInt(64.W))
    val Y = Output(UInt(128.W))
}

//64-bit multipleir for simulation
class MultUint64Verilate(implicit val conf:Config) extends Module{
  val io = IO(new MultPort)

  // io.Y := ShiftRegister(io.A * io.B,conf.multiplierpipestage)
  val z0 = RegNext(io.A(31,0) * io.B(31,0))
	val z2 =  RegNext(io.A(63,32) * io.B(63,32))
	val temp = RegNext(((io.A(63,32) -& io.A(31,0)).asSInt) * ((io.B(63,32)-&io.B(31,0)).asSInt))
	val z1 = (z2+&z0).zext -& temp
	io.Y := RegNext((z2.zext<<64)+(z1<<32)+z0.zext).asUInt
}

//64-bit multiplier
class MultUint64(implicit val conf:Config) extends Module{
  val io = IO(new MultPort)

  io.Y := ShiftRegister(ShiftRegister(io.A,2) * ShiftRegister(io.B,2),5)
  // val z0 = RegNext(io.A(31,0) * io.B(31,0))
	// val z2 =  RegNext(io.A(63,32) * io.B(63,32))
	// val temp = RegNext(RegNext((io.A(63,32) -& io.A(31,0)).asSInt) * RegNext((io.B(63,32)-&io.B(31,0)).asSInt))
	// val z1 = RegNext(z2+&z0).zext -& temp
	// io.Y := RegNext((RegNext(z2).zext<<64)+(z1<<32)+RegNext(z0).zext).asUInt
}