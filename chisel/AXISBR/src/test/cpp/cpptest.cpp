#include <bits/stdint-uintn.h>
#include <verilated.h>
#include <verilated_fst_c.h>
#include <VAXISBRWrapper.h>
#include <tfhe++.hpp>

void clock(VAXISBRWrapper *dut, VerilatedFstC* tfp){
  static uint time_counter = 0;
  dut->eval();
  tfp->dump(1000*time_counter);
  time_counter++;
  dut->clock = !dut->clock;
  dut->eval();
  tfp->dump(1000*time_counter);
  time_counter++;
  dut->clock = !dut->clock;
}

int main(int argc, char** argv) {
  //generatros
  std::random_device seed_gen;
  std::default_random_engine engine(seed_gen());
  std::uniform_int_distribution<uint32_t> binary(0, 1);
  std::uniform_int_distribution<uint32_t> bubble(0, 100);
  
  //Initialize TFHEpp objects
  TFHEpp::SecretKey *sk = new TFHEpp::SecretKey();
  TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param> *bkntt = new TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param>();
  TFHEpp::bknttgen<TFHEpp::lvl01param>(*bkntt,*sk);

  //allgned to distribute to module
  constexpr uint radixbit = 5;
  constexpr uint radix = 1<<radixbit;
  constexpr uint numcycle = 1<<5;
  constexpr uint fiber = TFHEpp::lvl1param::n/numcycle;
  constexpr uint bknumbus = 8;
  constexpr uint buswidthlb = 9;
  constexpr uint buswords = 1U<<(buswidthlb-5);
  constexpr uint nttwordsinbus = 512/64;
  constexpr uint alignedlenlvl0 = (((std::numeric_limits<TFHEpp::lvl0param::T>::digits*(TFHEpp::lvl0param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl0param::T>::digits;
  using alignedTLWElvl0 = std::array<TFHEpp::lvl0param::T,alignedlenlvl0>;

  constexpr uint alignedlenlvl1 = (((std::numeric_limits<TFHEpp::lvl1param::T>::digits*(TFHEpp::lvl1param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl1param::T>::digits;
  using alignedTLWElvl1 = std::array<TFHEpp::lvl1param::T,alignedlenlvl1>;
  alignedTLWElvl1 alignedcin = {};
  bool p = (binary(engine) > 0);
  TFHEpp::TLWE<TFHEpp::lvl0param> tlwe = TFHEpp::tlweSymEncrypt<TFHEpp::lvl0param>(p,TFHEpp::lvl0param::α,sk->key.lvl0);
  for(int i = 0; i <= TFHEpp::lvl0param::n; i++) alignedcin[i] = tlwe[i];
  TFHEpp::Polynomial<TFHEpp::lvl1param> testvec;
  for (int i = 0; i < TFHEpp::lvl1param::n; i++)
      testvec[i] = TFHEpp::lvl1param::μ;

  TFHEpp::TRLWE<TFHEpp::lvl1param> trlwe;
  TFHEpp::TLWE<TFHEpp::lvl1param> res, resaligned;
  trlwe[0] = {};
  const uint32_t b̄ =
        2 * TFHEpp::lvl1param::n - (tlwe[TFHEpp::lvl0param::n] >>(std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 1 - TFHEpp::lvl1param::nbit) );
  TFHEpp::PolynomialMulByXai<TFHEpp::lvl1param>(trlwe[1],TFHEpp::μpolygen<TFHEpp::lvl1param,TFHEpp::lvl1param::μ>(),b̄);


  Verilated::commandArgs(argc, argv);
  VAXISBRWrapper *dut = new VAXISBRWrapper();

  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  // Format
  dut->reset = 1;
  dut->clock = 0;
  dut->io_ap_start = 0;
  for(int j = 0; j<buswords; j++){
    dut->io_axi4in_TDATA[j] = alignedcin[j];
  }
  dut->io_axi4in_TVALID = 0;
  dut->io_axi4out_TREADY = 0;
  dut->io_axi4bkin_0_TVALID = 0;
  dut->io_axi4bkin_1_TVALID = 0;
  dut->io_axi4bkin_2_TVALID = 0;
  dut->io_axi4bkin_3_TVALID = 0;
  dut->io_axi4bkin_4_TVALID = 0;
  dut->io_axi4bkin_5_TVALID = 0;
  dut->io_axi4bkin_6_TVALID = 0;
  dut->io_axi4bkin_7_TVALID = 0;

  // Reset
  clock(dut, tfp);

  //Release reset
  dut->reset = 0;
  dut->io_ap_start = 1;
  dut->io_axi4out_TREADY = 1;
  dut->io_axi4outcmd_TREADY = 1;
  dut->io_axi4incmd_TREADY = 1;
  dut->io_axi4bkincmd_0_TREADY = 1;
  dut->io_axi4bkincmd_1_TREADY = 1;
  dut->io_axi4bkincmd_2_TREADY = 1;
  dut->io_axi4bkincmd_3_TREADY = 1;
  dut->io_axi4bkincmd_4_TREADY = 1;
  dut->io_axi4bkincmd_5_TREADY = 1;
  dut->io_axi4bkincmd_6_TREADY = 1;
  dut->io_axi4bkincmd_7_TREADY = 1;

  std::cout<<"Initialize"<<std::endl;
  //Initialize Input Buffer
  while(dut->io_axi4in_TREADY==0) clock(dut, tfp);
  dut->io_axi4in_TVALID = 1;
  for(int i = 0; i < alignedlenlvl0/buswords; i++){
    for(int j = 0; j < buswords; j++){
      dut->io_axi4in_TDATA[j] = alignedcin[buswords*i+j];
    }
    clock(dut, tfp);
  }
  dut->io_axi4in_TVALID = 0;
  clock(dut, tfp);
  for(int i = 0; i<20; i++)clock(dut, tfp);
  // dut->final();
  // tfp->close(); 
  // exit(1);

  std::cout<<"Processing"<<std::endl;
  uint count = 0;
  
constexpr typename TFHEpp::lvl0param::T roundoffset = 1ULL << (std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 2 - TFHEpp::lvl1param::nbit);
for (int i = 0; i < TFHEpp::lvl0param::n; i++) {
  const uint32_t ā = (tlwe[i]+roundoffset)>>(std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 1 - TFHEpp::lvl1param::nbit);

    dut->io_axi4bkin_0_TVALID = 1;
    dut->io_axi4bkin_1_TVALID = 1;
    dut->io_axi4bkin_2_TVALID = 1;
    dut->io_axi4bkin_3_TVALID = 1;
    dut->io_axi4bkin_4_TVALID = 1;
    dut->io_axi4bkin_5_TVALID = 1;
    dut->io_axi4bkin_6_TVALID = 1;
    dut->io_axi4bkin_7_TVALID = 1;

  for (int j = 0; j < 2*TFHEpp::lvl1param::l; j++){
   
    for(int cycle = 0; cycle<numcycle;cycle++){
      // if(i==0&&j==0&&cycle==0) continue;
      // if(cycle != 0)
      int watchdog = 0;
      while(dut->io_axi4bkin_0_TREADY==0){
        clock(dut, tfp);
        watchdog++;
        if(watchdog>1000){
          dut->final();
          tfp->close(); 
          exit(1);
        }
      }
      watchdog = 0;
      for(int m = 0; m < nttwordsinbus; m++){
        dut->io_axi4bkin_0_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+0*nttwordsinbus+m].value);
        dut->io_axi4bkin_0_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+0*nttwordsinbus+m].value>>32);
        dut->io_axi4bkin_1_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+1*nttwordsinbus+m].value);
        dut->io_axi4bkin_1_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+1*nttwordsinbus+m].value>>32);
        dut->io_axi4bkin_2_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+2*nttwordsinbus+m].value);
        dut->io_axi4bkin_2_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+2*nttwordsinbus+m].value>>32);
        dut->io_axi4bkin_3_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+3*nttwordsinbus+m].value);
        dut->io_axi4bkin_3_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][0][cycle*fiber+3*nttwordsinbus+m].value>>32);
        dut->io_axi4bkin_4_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+0*nttwordsinbus+m].value);
        dut->io_axi4bkin_4_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+0*nttwordsinbus+m].value>>32);
        dut->io_axi4bkin_5_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+1*nttwordsinbus+m].value);
        dut->io_axi4bkin_5_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+1*nttwordsinbus+m].value>>32);
        dut->io_axi4bkin_6_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+2*nttwordsinbus+m].value);
        dut->io_axi4bkin_6_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+2*nttwordsinbus+m].value>>32);
        dut->io_axi4bkin_7_TDATA[2*m] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+3*nttwordsinbus+m].value);
        dut->io_axi4bkin_7_TDATA[2*m+1] = static_cast<uint32_t>((*bkntt)[i][j][1][cycle*fiber+3*nttwordsinbus+m].value>>32);
      }
      clock(dut, tfp);
      std::cout<<i<<":"<<j<<":"<<cycle<<std::endl;
    }
  }

  dut->io_axi4bkin_0_TVALID = 0;
  dut->io_axi4bkin_1_TVALID = 0;
  dut->io_axi4bkin_2_TVALID = 0;
  dut->io_axi4bkin_3_TVALID = 0;
  dut->io_axi4bkin_4_TVALID = 0;
  dut->io_axi4bkin_5_TVALID = 0;
  dut->io_axi4bkin_6_TVALID = 0;
  dut->io_axi4bkin_7_TVALID = 0;

  // if (ā == 0) continue;
  // Do not use CMUXNTT to avoid unnecessary copy.
  TFHEpp::CMUXNTTwithPolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(
      trlwe, (*bkntt)[i], ā);
  
  while(dut->io_debugvalid==0) clock(dut, tfp);
  for(int cycle = 0; cycle<numcycle;cycle++){
    for(int m = 0; m<radix;m++)
    if(dut->io_debugout[m]!=trlwe[0][m*radix+cycle]){
      std::cout<<i<<":0:"<<cycle<<":"<<m<<std::endl;
      std::cout<<tlwe[i]<<std::endl;
      std::cout<<"ERROR:"<<trlwe[0][m*radix+cycle]<<":"<<dut->io_debugout[m]<<std::endl;
      dut->final();
      tfp->close(); 
      exit(1);
    }
    clock(dut, tfp);
  }
  while(dut->io_debugvalid==0) clock(dut, tfp);
  for(int cycle = 0; cycle<numcycle;cycle++){
    for(int m = 0; m<radix;m++)
    if(dut->io_debugout[m]!=trlwe[1][m*radix+cycle]){
      std::cout<<i<<":1:"<<cycle<<":"<<m<<std::endl;
      std::cout<<trlwe[1][m*radix+cycle]<<":"<<dut->io_debugout[m]<<std::endl;
      dut->final();
      tfp->close(); 
      exit(1);
    }
    clock(dut, tfp);
  }
}
  
  int watchdog = 0;
  while(dut->io_axi4out_TVALID==0){
    clock(dut, tfp);
    watchdog++;
    if(watchdog>1000){
      dut->final();
      tfp->close(); 
      exit(1);
    }
  }
  
  dut->io_axi4out_TREADY=1;
  for(int i = 0; i <= TFHEpp::lvl1param::k*TFHEpp::lvl1param::n; i++){
    while(dut->io_axi4out_TVALID==0) clock(dut, tfp);
    resaligned[i] = dut->io_axi4out_TDATA;
    clock(dut, tfp);
  }

  TFHEpp::BlindRotate<TFHEpp::lvl01param>(trlwe,tlwe,*bkntt,TFHEpp::μpolygen<TFHEpp::lvl1param, TFHEpp::lvl1param::μ>());
  TFHEpp::SampleExtractIndex<TFHEpp::lvl1param>(res, trlwe, 0);
  dut->final();
  tfp->close(); 
    for(int j = 0; j<=TFHEpp::lvl1param::n;j++){
        uint32_t trueout = res[j];
        uint32_t circout = resaligned[j];
        if(trueout != circout){
          std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
          std::cout<<j<<std::endl;
          exit(1);
        }
    }
  std::cout<<"PASS"<<std::endl;
}