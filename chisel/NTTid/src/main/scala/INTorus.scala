import chisel3._
import chisel3.util._

class INTorusArithmeticPort extends Bundle {
    val A = Input(UInt(64.W))
    val B = Input(UInt(64.W))
    val Y = Output(UInt(64.W))
}

class INTorusADD extends Module{
    val io = IO(new INTorusArithmeticPort)

    val sum = io.A +& io.B
    io.Y := Mux(sum(64),sum(63,0) + ((1L<<32)-1).U, sum(63,0))
}

class INTorusSUB extends Module{
    val io = IO(new INTorusArithmeticPort)
    val sub = io.A -& io.B
    io.Y := Mux(sub(64),sub(63,0) - ((1L<<32)-1).U, sub(63,0))
}

class INTorusMUL extends Module{
    val io = IO(new INTorusArithmeticPort)
    val mul = io.A * io.B
    val temp0 = mul(31,0)
    val temp1 = mul(63,32)
    val temp2 = mul(95,64)
    val temp3 = mul(127,96)

    val temp2iszero = temp2.andR

    val posupper = temp1 +& temp2
    val pos = Cat(posupper(31,0),temp0)
    val sub = pos -& (temp3 +& temp2)
    io.Y := Mux(temp2.andR, Mux(!sub(64),sub(63,0) - ((1L<<32)-1).U,sub(63,0)), Mux(posupper(32), sub(63,0) + ((1L<<32)-1).U, sub(63,0)))
}

class INTorusLSH(implicit val conf:Config) extends Module{
    val io = IO(new Bundle{
        val A = Input(UInt(64.W))
        val l = Input(UInt(8.W))
        val Y = Output(UInt(64.W))
    })
    io.Y := DontCare
    when(io.l<32.U){
        val templ = (io.A<<io.l)(63,0)
        val tempu = io.A>>(64.U-io.l)
        val res = templ +& ((tempu<<32) - tempu)
        val resmod = Mux(res(64),res(63,0)+((1L<<32)-1).U,res(63,0))
        io.Y := Mux(resmod>=conf.P.asUInt,resmod+((1L<<32)-1).U,resmod)
    }.elsewhen(io.l < 64.U){
        val templ = (io.A << (io.l-32.U))(31,0)
        val tempul = (io.A >> (64.U - io.l))(31,0)
        val tempuu = io.A >> (96.U - io.l)
        val res = ((templ +& tempul) << 32.U) - tempuu - tempul
        val resmod = Mux(tempul===0.U,Mux(res > templ, res - ((1L<<32)-1).U, res), Mux(res < templ, res + ((1L<<32)-1).U, res))
        io.Y := Mux(resmod>=conf.P.asUInt,resmod+((1L<<32)-1).U,resmod)
    }.elsewhen(io.l < 96.U){
        val temp = (io.A << (io.l-64.U))(31,0)
        val templ = (temp<<32.U) - temp
        val tempu = io.A >> (96.U - io.l)
        val res = templ -& tempu
        val resmod = Mux(res(64), res(63,0) - ((1L<<32)-1).U, res(63,0))
        io.Y := Mux(resmod>=conf.P.asUInt,resmod+((1L<<32)-1).U,resmod)
    }.elsewhen(io.l < 128.U){
        val templ = (io.A << (io.l - 96.U))(63,0)
        val tempu = io.A >> (160.U - io.l)
        val res = templ + (tempu << 32.U)(63,0) - tempu
        val resmod = Mux(res < templ, res + ((1L<<32)-1).U, res)
        val ressub = conf.P.asUInt - Mux(resmod >= conf.P.asUInt,resmod+((1L<<32)-1).U,resmod)
        io.Y := Mux(ressub>=conf.P.asUInt,ressub+((1L<<32)-1).U,ressub)
    }.elsewhen(io.l < 160.U){
        val templ = (io.A << (io.l-128.U))(31,0)
        val tempul = (io.A >> (160.U - io.l))(31,0)
        val tempuu = io.A >> (192.U - io.l)
        val sub = Module(new INTorusSUB)
        sub.io.A := tempul+tempuu
        sub.io.B := templ << 32.U
        val subres = Module(new INTorusSUB)
        subres.io.A := sub.io.Y
        subres.io.B := tempul << 32.U
        io.Y := subres.io.Y
    }.otherwise{
        val templ = (io.A << (io.l - 160.U))(31,0)
        val tempu = io.A >> (192.U - io.l)
        val res = tempu -& ((templ << 32.U) - templ)
        io.Y := Mux(res(64), res(63,0) - ((1L<<32)-1).U, res(63,0))
    }
}