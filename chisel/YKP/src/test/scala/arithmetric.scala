import org.scalatest._
import chiseltest._
import chisel3._

import scala.util.Random

class INTorusADDTest extends FlatSpec with ChiselScalatestTester with Matchers {
    behavior of "Testers2"

    it should "test INTorusADD for random input" in {
        test(new INTorusADD){c =>
            for (i <- 0 until 1000) {
                val P:BigInt = (((BigInt(1)<<32)-1)<<32)+1
                val inA:BigInt = (Random.nextLong() % P + P) % P
                val inB:BigInt = (Random.nextLong() % P + P) % P
                val res:BigInt = ((inA+inB)%P + P) % P
                c.io.A.poke(inA.U)
                c.io.B.poke(inB.U)
                c.clock.step()
                c.io.Y.expect(res.U)
            }
        }
    }
}

class INTorusSUBTest extends FlatSpec with ChiselScalatestTester with Matchers {
    behavior of "Testers2"

    it should "test INTorusSUB for random input" in {
        test(new INTorusSUB){c =>
            for (i <- 0 until 1000) {
                val P:BigInt = (((BigInt(1)<<32)-1)<<32)+1
                val inA:BigInt = (Random.nextLong() % P + P) % P
                val inB:BigInt = (Random.nextLong() % P + P) % P
                val res:BigInt = ((inA-inB)%P + P) % P
                c.io.A.poke(inA.U)
                c.io.B.poke(inB.U)
                c.clock.step()
                c.io.Y.expect(res.U)
            }
        }
    }
}

class INTorusMULTest extends FlatSpec with ChiselScalatestTester with Matchers {
    behavior of "Testers2"

    it should "test INTorusMUL for random input" in {
        test(new INTorusMUL()(Config())){c =>
            for (i <- 0 until 1000) {
                val P:BigInt = (((BigInt(1)<<32)-1)<<32)+1
                val inA:BigInt = (Random.nextLong() % P + P) % P
                val inB:BigInt = (Random.nextLong() % P + P) % P
                val res:BigInt = ((inA*inB)%P + P) % P
                c.io.A.poke(inA.U)
                c.io.B.poke(inB.U)
                c.clock.step()
                c.clock.step()
                c.clock.step()
                c.clock.step()
                c.clock.step()
                c.clock.step()
                c.clock.step()
                c.clock.step()
                c.clock.step()
                c.io.Y.expect(res.U)
            }
        }
    }
}

class INTorusLSHTest extends FlatSpec with ChiselScalatestTester with Matchers {
    behavior of "Testers2"

    it should "test INTorusLSH for random input" in {
        test(new INTorusLSH()(Config())){c =>
            for(inl <- 0 until 192){
                println(inl)
                val P:BigInt = (((BigInt(1)<<32)-1)<<32)+1
                for (i <- 0 until 1000) {
                    val inA:BigInt = (Random.nextLong() % P + P) % P
                    val res:BigInt = ((inA<<inl)%P + P) % P
                    c.io.A.poke(inA.U)
                    c.io.l.poke(inl.U)
                    c.clock.step()
                    c.io.Y.expect(res.U)
                }
            }
        }
    }
}