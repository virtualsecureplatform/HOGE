// Arithmetics over 2^64-2^32+1
import chisel3._
import chisel3.util._

class INTorusArithmeticPort extends Bundle {
    val A = Input(UInt(64.W))
    val B = Input(UInt(64.W))
    val Y = Output(UInt(64.W))
}

class INTorusADD extends Module{
    val io = IO(new INTorusArithmeticPort)

    val sum = RegNext(io.A +& io.B)
    io.Y := Mux(sum(64)||(sum(63,0)>=Config().P.U),sum(63,0) + ((1L<<32)-1).U, sum(63,0))
}

class INTorusSUB extends Module{
    val io = IO(new INTorusArithmeticPort)
    val sub = RegNext(io.A -& io.B)
    io.Y := Mux(sub(64),sub(63,0) - ((1L<<32)-1).U, sub(63,0))
}

class INTorusMUL(implicit val conf:Config) extends Module{
    val io = IO(new INTorusArithmeticPort)
    val mul = Module(new MultUint64)
    mul.io.A := io.A
    mul.io.B := io.B
    val temp0 = mul.io.Y(31,0)
    val temp1 = mul.io.Y(63,32)
    val temp2 = mul.io.Y(95,64)
    val temp3 = mul.io.Y(127,96)

    val posupper = temp1 + temp2
    val pos = Cat(posupper,temp0)
    val res = RegNext(RegNext(pos) - RegNext(temp3 +& temp2))
    val gt = res>ShiftRegister(mul.io.Y(63,0),2)
    val lt = (!gt)&(res=/=ShiftRegister(mul.io.Y(63,0),2))
    io.Y := Mux(ShiftRegister(temp2===0.U,2), Mux(gt,res - ((1L<<32)-1).U,res), Mux(lt, res + ((1L<<32)-1).U, res))
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
        val res = RegNext(templ +& ((tempu<<32)(63,0) - tempu))
        val resmod = RegNext(Mux(res(64),res(63,0)+((1L<<32)-1).U,res(63,0)))
        io.Y := Mux(resmod>=conf.P.asUInt,resmod+((1L<<32)-1).U,resmod)
    }.elsewhen(io.l < 64.U){
        val templ = (io.A << (io.l-32.U))(31,0)
        val tempul = (io.A >> (64.U - io.l))(31,0)
        val tempuu = io.A >> (96.U - io.l)
        val res = RegNext(((templ +& tempul) << 32.U)(63,0) - tempuu - tempul)
        val resmod = RegNext(Mux(RegNext(tempul===0.U),Mux(res > RegNext(templ<<32), res - ((1L<<32)-1).U, res), Mux(res < RegNext(templ<<32), res + ((1L<<32)-1).U, res)))
        io.Y := Mux(resmod>=conf.P.asUInt,resmod+((1L<<32)-1).U,resmod)
    }.elsewhen(io.l < 96.U){
        val temp = (io.A << (io.l-64.U))(31,0)
        val templ = (temp<<32.U)(63,0) - temp
        val tempu = io.A >> (96.U - io.l)
        val res = RegNext(templ -& tempu)
        val resmod = RegNext(Mux(res(64), res(63,0) - ((1L<<32)-1).U, res(63,0)))
        io.Y := Mux(resmod>=conf.P.asUInt,resmod+((1L<<32)-1).U,resmod)
    }.elsewhen(io.l < 128.U){
        val templ = (io.A << (io.l - 96.U))(63,0)
        val tempu = io.A >> (160.U - io.l)
        val res = RegNext(templ + (tempu << 32.U)(63,0) - tempu)
        val resmod = Mux(res < RegNext(templ), res + ((1L<<32)-1).U, res)
        val ressub = RegNext(conf.P.asUInt - Mux(resmod >= conf.P.asUInt,resmod+((1L<<32)-1).U,resmod))
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
        subres.io.B := RegNext(tempul) << 32.U
        io.Y := subres.io.Y
    }.otherwise{
        val templ = RegNext((io.A << (io.l - 160.U))(31,0))
        val tempu = RegNext(io.A >> (192.U - io.l))
        val res = RegNext(tempu -& ((templ << 32.U) - templ))
        io.Y := Mux(res(64), res(63,0) - ((1L<<32)-1).U, res(63,0))
    }
}