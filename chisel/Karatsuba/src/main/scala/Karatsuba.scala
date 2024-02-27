import chisel3._

class MultPort extends Bundle {
    val A = Input(UInt(64.W))
    val B = Input(UInt(64.W))
    val Y = Output(UInt(128.W))
}

class Karatsuba extends Module{
    val io = IO(new MultPort)
    
	val z0 = RegNext(io.A(31,0) * io.B(31,0))
	val z2 =  RegNext(io.A(63,32) * io.B(63,32))
	val temp = RegNext(((io.A(63,32) -& io.A(31,0)).asSInt) * ((io.B(63,32)-&io.B(31,0)).asSInt))
	val z1 = ((z2+&z0).zext - temp).asUInt
	io.Y := (z2<<64)+(z1<<32)+z0
}