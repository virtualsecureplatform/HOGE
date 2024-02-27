import org.scalatest._
import chiseltest._
import chisel3._
import chiseltest.experimental.TestOptionBuilder._
import chiseltest.internal.VerilatorBackendAnnotation

import scala.util.Random

class ButterflyADDTest extends FlatSpec with ChiselScalatestTester with Matchers {
    behavior of "Testers2"
    val conf = new Config

    it should "test ButterflyADD for random input" in {
        test(new ButterflyADD(conf.N)){c =>
            val P = conf.P
            val N = conf.N
            val in = Seq.fill(N){(Random.nextLong() % P + P) % P}
            for (i<- 0 until N){
                c.io.in(i).poke(in(i).U)
            }
            c.clock.step()
            for(i <- 0 until N/2){
                c.io.out(i).expect((((in(i)+in(i+N/2))%P+ P) % P).U)
                c.io.out(i+N/2).expect((((in(i)-in(i+N/2))%P+ P) % P).U)
            }
        }
    }
}

class NTTidTest extends FlatSpec with ChiselScalatestTester with Matchers {
    behavior of "Testers2"
    val conf = new Config

    it should "test NTTid for random input" in {
        test(new NTTid(conf)).withAnnotations(Seq(VerilatorBackendAnnotation)){c =>
            val P = conf.P
            val in = Seq.fill(conf.N){(Random.nextLong() % P + P) % P}
            for (i<- 0 until conf.N){
                c.io.in(i).poke(in(i).U)
            }
            c.clock.step((1<<(conf.cyclebit+2))+1)
            for (i<- 0 until conf.N){
                c.io.out(i).expect(((in(i)% P + P)%P).U)
            }
        }
    }
}