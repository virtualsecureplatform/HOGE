#include <verilated.h>
#include <verilated_fst_c.h>
#include <VIdentityKeySwitchingWrap.h>
#include <tfhe++.hpp>

void clock(VIdentityKeySwitchingWrap *dut, VerilatedFstC* tfp){
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
  
  //Initialize TFHEpp objects
  TFHEpp::SecretKey *sk = new TFHEpp::SecretKey();
  TFHEpp::KeySwitchingKey<TFHEpp::lvl10param> *iksk = new TFHEpp::KeySwitchingKey<TFHEpp::lvl10param>();
  TFHEpp::ikskgen<TFHEpp::lvl10param>(*iksk,*sk);

  bool p = (binary(engine) > 0);
  TFHEpp::TLWE<TFHEpp::lvl1param> tlwe = TFHEpp::tlweSymEncrypt<TFHEpp::lvl1param>(p,TFHEpp::lvl1param::α,sk->key.lvl1);

  TFHEpp::TLWE<TFHEpp::lvl0param> res;
  TFHEpp::IdentityKeySwitch<TFHEpp::lvl10param>(res,tlwe,*iksk);


  Verilated::commandArgs(argc, argv);
  VIdentityKeySwitchingWrap *dut = new VIdentityKeySwitchingWrap();

  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  // Format
  dut->reset = 1;
  dut->clock = 0;
  for(int j = 0; j<=TFHEpp::lvl1param::n; j++){
    dut->io_in[j] = tlwe[j];
  }

  // Reset
  clock(dut, tfp);

  //Release reset
  dut->reset = 0;

  std::cout<<"Initialize"<<std::endl;
  //Initialize Memory
  for(int i = 0; i<TFHEpp::lvl1param::n;i++)
    for(int j = 0; j<TFHEpp::lvl11param::t;j++)
      for(int k=0;k<(1<<TFHEpp::lvl11param::basebit)-1;k++){
        for(int l = 0; l <=TFHEpp::lvl0param::n; l++)
          dut->io_kskin[l] = static_cast<uint32_t>((*iksk)[i][j][k][l]);
        clock(dut, tfp);
      }
  // if(!(dut->io_init)){
  //     std::cout<<"Init Error. Not Finish."<<std::endl;
  //     exit(1);
  //   }
  std::cout<<"Processing"<<std::endl;
  uint count = 0;
  while(dut->io_fin==0){
    clock(dut, tfp);
  }
  clock(dut, tfp);
  dut->final();
  tfp->close(); 

  for(int j = 0; j<=TFHEpp::lvl0param::n;j++){
      uint32_t trueout = res[j];
      uint32_t circout = dut->io_out[j];
      if(trueout != circout){
        std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
        std::cout<<j<<std::endl;
        exit(1);
      }
  }

  std::cout<<"PASS"<<std::endl;
}