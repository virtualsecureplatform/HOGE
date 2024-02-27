#include <verilated.h>
#include <verilated_fst_c.h>
#include <VINTTWrap.h>
#include <tfhe++.hpp>

void clock(VINTTWrap *dut, VerilatedFstC* tfp){
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
  constexpr uint num_test = 1;

  constexpr uint radix = 32;
  //generatros
  std::random_device seed_gen;
  std::default_random_engine engine(seed_gen());
  std::uniform_int_distribution<uint32_t> Torus32dist(0, std::numeric_limits<uint32_t>::max());

  const std::array<std::array<cuHEpp::INTorus, TFHEpp::lvl1param::n>, 2>
      tablelvl1 = cuHEpp::TableGen<TFHEpp::lvl1param::nbit>();
  const std::array<std::array<cuHEpp::INTorus, TFHEpp::lvl1param::n>, 2>
      twistlvl1 = cuHEpp::TwistGen<TFHEpp::lvl1param::nbit>();
  
  //Initialize TFHEpp objects
  TFHEpp::Polynomial<TFHEpp::lvl1param> poly;
  std::array<cuHEpp::INTorus,TFHEpp::lvl1param::n> res;

  Verilated::commandArgs(argc, argv);
  VINTTWrap *dut = new VINTTWrap();

  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  // Format
  dut->reset = 1;
  dut->clock = 0;
  dut->io_enable = 0;

  // Reset
  dut->eval();

  //Release reset
  dut->reset = 0;

  for(int test = 0; test < num_test; test++){
    for (int i = 0; i < TFHEpp::lvl1param::n; i++)
        poly[i] = Torus32dist(engine);
    cuHEpp::TwistINTT<typename TFHEpp::lvl1param::T,TFHEpp::lvl1param::nbit>(res,poly,tablelvl1[1],twistlvl1[1]);
    for(int j = 0; j<radix; j++){
      dut->io_in[j] = poly[j*radix];
    }
    dut->io_enable=1;
    std::cout<<"Processing"<<std::endl;
    for(int i = 1; i < TFHEpp::lvl1param::n/radix; i++){
      clock(dut,tfp);
      for(int j = 0; j<radix; j++){
        dut->io_in[j] = poly[j*radix+i];
      }
    }
    uint count = 0;
    while(dut->io_validout==0){
      count++;
      clock(dut,tfp);
    }
    for(int i = 0; i < TFHEpp::lvl1param::n/radix; i++){
      uint errocount = 0;
      for(int j = 0; j<radix;j++){
          const uint index = i*radix+j;
          const uint64_t trueout = res[index].value;
          const uint64_t circout = dut->io_out[2*j]+(static_cast<uint64_t>(dut->io_out[2*j+1])<<32);
          if(trueout != circout){
            std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
            std::cout<<index<<":"<<i<<":"<<j<<std::endl;
            errocount++;
          }
      }
      clock(dut,tfp);
      if(errocount != 0){
        dut->final();
        tfp->close();
        exit(1);
      }
    }
    dut->io_enable=0;
    clock(dut,tfp);
  }
    std::cout<<"PASS"<<std::endl;
}