#include <verilated.h>
#include <verilated_fst_c.h>
#include <VPolynomialMulByXaiMinusOneWrap.h>
#include <tfhe++.hpp>

void clock(VPolynomialMulByXaiMinusOneWrap *dut, VerilatedFstC* tfp){
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
  std::uniform_int_distribution<uint32_t> Torus32dist(0, std::numeric_limits<uint32_t>::max());
  

  Verilated::commandArgs(argc, argv);
  VPolynomialMulByXaiMinusOneWrap *dut = new VPolynomialMulByXaiMinusOneWrap();
  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  for(int test = 0 ; test < 100 ; test++){
  //Initialize TFHEpp objects
  TFHEpp::TRLWE<TFHEpp::lvl1param> trlwe,res,circres;
  for (int j = 0; j < 2; j++)
    for (int i = 0; i < TFHEpp::lvl1param::n; i++)
        trlwe[j][i] = Torus32dist(engine);
  const uint32_t exponent = Torus32dist(engine)&(2*TFHEpp::lvl1param::n-1);
  // const uint32_t exponent = 0;
  TFHEpp::PolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(res[0],trlwe[0],exponent);
  TFHEpp::PolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(res[1],trlwe[1],exponent);
  std::cout<<exponent<<std::endl;
  
  // Format
  dut->reset = 1;
  dut->clock = 0;
  dut->io_enable = 0;

  // Reset
  clock(dut, tfp);

  //Release reset
  dut->reset = 0;

  dut->io_exponent = exponent;
  dut->io_enable = 1;

  constexpr int radixbit = 5;
  constexpr int radix = 1<<radixbit;

  for(int l = 0; l < TFHEpp::lvl1param::k+1; l++)
    for(int i = 0; i < radix; i++){
      for(int j = 0; j < radix; j++) dut->io_in[j] = trlwe[l][j*radix+i];
      clock(dut, tfp);
    }
  
  while(dut->io_valid==0) clock(dut, tfp);
  for(int l = 0; l < TFHEpp::lvl1param::k+1; l++)
  for(int i = 0; i < radix; i++){
    for(int j = 0; j < radix; j++) circres[l][j*radix+i] = dut->io_out[j];
    clock(dut, tfp);
  }
  
  for(int i = 0; i < 2; i++)
    for(int j = 0; j<TFHEpp::lvl1param::n;j++){
        uint32_t trueout = res[i][j];
        uint32_t circout = circres[i][j];
        if(trueout != circout){
          std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
          std::cout<<i<<":"<<j<<std::endl;
          dut->final();
          tfp->close(); 
          exit(1);
        }
    }
  }

  dut->final();
  tfp->close(); 

  std::cout<<"PASS"<<std::endl;
}