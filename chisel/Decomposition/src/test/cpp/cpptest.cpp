#include <verilated.h>
#include <verilated_vcd_c.h>
#include <VDecompositionWrap.h>
#include <tfhe++.hpp>

int main(int argc, char** argv) {
  constexpr uint num_test = 100;
  //generatros
  std::random_device seed_gen;
  std::default_random_engine engine(seed_gen());
  std::uniform_int_distribution<uint32_t> Torus32dist(0, std::numeric_limits<uint32_t>::max());
  
  //Initialize TFHEpp objects
  TFHEpp::Polynomial<TFHEpp::lvl1param> poly;

  Verilated::commandArgs(argc, argv);
  VDecompositionWrap *dut = new VDecompositionWrap();

  uint time_counter = 0;
  Verilated::traceEverOn(true);
  VerilatedVcdC* tfp = new VerilatedVcdC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.vcd");

  // Format
  dut->reset = 1;
  dut->clock = 0;

  // Reset
  dut->eval();
  tfp->dump(time_counter*1000);
  time_counter++;

  //Release reset
  dut->reset = 0;

  std::cout<<"Processing"<<std::endl;
  constexpr uint numcycle = 1<<4;
  constexpr uint fiber = TFHEpp::lvl1param::n/numcycle;
  for(int test = 0; test<num_test; test++){
    for (int i = 0; i < TFHEpp::lvl1param::n; i++)
        poly[i] = Torus32dist(engine);
    for(int digit = 0; digit<TFHEpp::lvl1param::l; digit++){
      TFHEpp::DecomposedPolynomial<TFHEpp::lvl1param> res;
      TFHEpp::DecompositionPolynomial<TFHEpp::lvl1param>(res,poly,digit);
      dut->io_digit = digit;
      for(int cycle=0; cycle<numcycle;cycle++){
        for(int j = 0; j<fiber;j++){
          dut->io_in[j] = poly[cycle*fiber+j];
        }
        dut->eval();
        tfp->dump(time_counter*1000);
        time_counter++;
        for(int j = 0; j<fiber;j++){
          if(dut->io_out[j] != res[cycle*fiber+j]){
            std::cout<<"Error: "<<digit<<":"<<cycle<<":"<<j<<":"<<dut->io_out[j]<<":"<<res[cycle*fiber+j]<<std::endl;
            exit(1);
          }
        }
      }
    }
  }
  std::cout<<"PASS"<<std::endl;
}