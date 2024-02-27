
case class Config() {
    //Simulate Mode
    val velirate = true

    //security parameters
    val n = 636
    val Nbit = 10
    val N = 1<<Nbit
    val Bgbit = 6
    val Bg = 1<<Bgbit
    val l = 3
    val t = 7
    val basebit = 2
    val Qbit = 32
    val qbit = 32
    val mu = 1<<29

    // implementation specific parameters
    val buswidth = 512
    val hbmbuswidth = 512
    val cmdbuswidth = 104
    val changesizeslice = 6
    
    val iksknumbus = 10
    val totaliksknumbus = 40
    val iksknumsegments = totaliksknumbus/iksknumbus

    val axi4snumslice = 10
    val cyclebit = 5
    val nttnumbus = 1<<(Nbit-cyclebit+6-9)
    val trlwenumbus = 1<<(Nbit-cyclebit+5-9)
    val bknumbus = 8
    val stepbit = 1
    val numstep = 1 << stepbit
    val radixbit = Nbit >> stepbit
    val radix = 1 << radixbit
    val numcycle = 1 << cyclebit
    val block = N >> radixbit
    val chunk = block >> cyclebit
    val fiber = N >> cyclebit
    val multiplierpipestage = 7
    val muldelay = multiplierpipestage + 2
    val lshdelay = 3
    val interslr = 8
    val radixdelay = 5


    //Constants
    val P:BigInt = (((BigInt(1)<<32)-1)<<32)+1
    val W:BigInt = BigInt("12037493425763644479")

    def NTTtableGen(): List[BigInt] = {
        // defined on [1,31]
        def InvPow2(nbit: Int): BigInt = 
        {
            val low = (1 << (32 - nbit)) + 1;
            val high = BigInt(((1L<<32)-low)&((1L<<32)-1));
            ((((high << 32) + low)%P) + P) % P
        }
        val invN = InvPow2(Nbit)

        val twist = (W.modPow(1L << (32 - Nbit),P) + P) % P
        var twists:List[BigInt] = List(((twist*invN%P) + P) % P)

        for (i <- 2 until N){
            twists = twists :+ ((twist.modPow(i,P)*invN % P)+ P) % P
        }
        twists = invN::(twists.reverse)
        twists
    }

    def INTTtableGen(): List[BigInt] = {
        val twist = (W.modPow(1L << (32 - Nbit),P) + P) % P
        var twists:List[BigInt] = List(twist)
        for (i <- 2 until N){
            twists = twists :+ (twist.modPow(i,P) + P) % P
        }
        twists = 1::twists
        twists
    }

    val intttable:List[BigInt] = INTTtableGen()
    val ntttable:List[BigInt] = NTTtableGen()

    def NTTtwistGen(): List[BigInt] = {
        val twist = (W.modPow(1L << (32 - Nbit - 1),P) + P) % P
        var twists:List[BigInt] = List(1)
        for (i <- 1 until N){
            twists = twists :+ (twist.modPow(-i,P) + P) % P
        }
        twists
    }

    def INTTtwistGen(): List[BigInt] = {
        val twist = (W.modPow(1L << (32 - Nbit - 1),P) + P) % P
        var twists:List[BigInt] = List(1)
        for (i <- 1 until N){
            twists = twists :+ (twist.modPow(i,P) + P) % P
        }
        twists
    }
    
    val intttwist:List[BigInt] = INTTtwistGen()
    val ntttwist:List[BigInt] = NTTtwistGen()
}