import org.scalatest._
import chiseltest._
import chisel3._

import scala.util.Random

class KaratsubaTest extends FlatSpec with ChiselScalatestTester with Matchers {
    behavior of "Testers2"

    it should "test Karatsuba for random input" in {
        test(new Karatsuba){c =>
            for (i <- 0 until 1000) {
				val P: BigInt = BigInt(1)<<64
                val inA:BigInt = (Random.nextLong() % P + P) % P
                val inB:BigInt = (Random.nextLong() % P + P) % P
                val res:BigInt = inA*inB
                c.io.A.poke(inA.U)
                c.io.B.poke(inB.U)
                c.clock.step()
                c.clock.step()
                c.io.Y.expect(res.U)
            }
        }
    }
}