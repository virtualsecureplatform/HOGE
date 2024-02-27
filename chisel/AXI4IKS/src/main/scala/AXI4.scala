import chisel3._
import chisel3.util._

class AXI4ManagerWrite(val buswidth: Int) extends Bundle{
	// Write Address
	val AWVALID = Output(Bool())
	val AWREADY = Input(Bool())
	val AWADDR = Output(UInt(64.W))
	val AWLEN = Output(UInt(8.W))
	
	// Write Bus
	val WVALID = Output(Bool())
	val WREADY = Input(Bool())
	val WDATA = Output(UInt(buswidth.W))
	val WSTRB = Output(UInt((buswidth/8).W))
	val WLAST = Output(Bool())

	// Write Response
	val BVALID = Input(Bool())
	val BREADY = Output(Bool())
}

class AXI4ManagerRead(val buswidth: Int) extends Bundle{
	// Read Address
	val ARVALID = Output(Bool())
	val ARREADY = Input(Bool())
	val ARADDR = Output(UInt(64.W))
	val ARLEN = Output(UInt(8.W))

	// Read Bus
	val RVALID = Input(Bool())
	val RREADY = Output(Bool())
	val RDATA = Input(UInt(buswidth.W))
	val RLAST = Input(Bool())
}

class AXI4Manager(val buswidth: Int) extends Bundle{
	val write = new AXI4ManagerWrite(buswidth)

	val read = new AXI4ManagerRead(buswidth)
}

class AXI4StreamManager(val buswidth: Int) extends Bundle{
	val TVALID = Output(Bool())
	val TREADY = Input(Bool())
	val TDATA = Output(UInt(buswidth.W))
}

class AXI4StreamSubordinate(val buswidth: Int) extends Bundle{
	val TVALID = Input(Bool())
	val TREADY = Output(Bool())
	val TDATA = Input(UInt(buswidth.W))
}

class AXI4StreamRegisterSlice(val buswidth: Int, val numslice: Int) extends Module{
	val io = IO(new Bundle{
		val subordinate = new AXI4StreamSubordinate(buswidth)
		val manager = new AXI4StreamManager(buswidth)
	})
	val slices = for(i <- 0 until numslice) yield{
        val slice = Module(new Queue(UInt(buswidth.W),2))
        slice
	}
	slices(0).io.enq.bits := io.subordinate.TDATA
	slices(0).io.enq.valid := io.subordinate.TVALID
	io.subordinate.TREADY := slices(0).io.enq.ready
	for(i <- 1 until numslice){
		slices(i).io.enq <> slices(i-1).io.deq
	}
	io.manager.TDATA := slices(numslice-1).io.deq.bits
	io.manager.TVALID := slices(numslice-1).io.deq.valid
	slices(numslice-1).io.deq.ready := io.manager.TREADY
}