#include <verilated.h>
#include <verilated_fst_c.h>
#include <VSampleExtractIndexWrap.h>
#include <tfhe++.hpp>

void clock(VSampleExtractIndexWrap *dut, VerilatedFstC* tfp){
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
  std::uniform_int_distribution<uint32_t> Torus32dist(0, std::numeric_limits<uint32_t>::max());
  
  //Initialize TFHEpp objects

  TFHEpp::TRLWE<TFHEpp::lvl1param> trlwe;
  for(int i = 0; i < 2*TFHEpp::lvl1param::n; i++) trlwe[0][i] = Torus32dist(engine);

  TFHEpp::TLWE<TFHEpp::lvl1param> res;
  TFHEpp::SampleExtractIndex<TFHEpp::lvl1param>(res,trlwe,0);

  constexpr uint buswidthlb = 9;
  constexpr uint buswords = 1U<<(buswidthlb-5);
  constexpr uint wordsinbus = (1U<<buswidthlb)/std::numeric_limits<typename TFHEpp::lvl0param::T>::digits;
  constexpr uint alignedlenlvl1 = (((std::numeric_limits<TFHEpp::lvl1param::T>::digits*(TFHEpp::lvl1param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl1param::T>::digits;
  using alignedTLWElvl1 = std::array<TFHEpp::lvl1param::T,alignedlenlvl1>;

  alignedTLWElvl1 resaligned = {};

  Verilated::commandArgs(argc, argv);
  VSampleExtractIndexWrap *dut = new VSampleExtractIndexWrap();

  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  // Format
  dut->reset = 1;
  dut->clock = 0;
  dut->io_axi4sout_TREADY = 0;
  for(int j = 0; j<buswords; j++){
    dut->io_axi4sin_TDATA[j] = trlwe[0][j];
  }
  clock(dut, tfp);
  //Release reset
  dut->reset = 0;
  std::cout<<"Initialize"<<std::endl;
  //Initialize Input Buffer
  dut->io_axi4sin_TVALID = 1;
  while(dut->io_axi4sin_TREADY==0) clock(dut, tfp);
  int inindex = 1;
  while(inindex < 2*TFHEpp::lvl1param::n/buswords){
    for(int j = 0; j < buswords; j++){
      dut->io_axi4sin_TDATA[j] = trlwe[0][buswords*inindex+j];
    }
    if(dut->io_axi4sin_TREADY==1) inindex++;
    clock(dut, tfp);
  }
  dut->io_axi4sout_TREADY = 1;
  while(dut->io_axi4sout_TVALID==0) clock(dut, tfp);
  uint outindex = 0;
  while(dut->io_axi4sout_TVALID==1){
    for(int n = 0; n < buswords; n++){
              resaligned[buswords*outindex+n] = dut->io_axi4sout_TDATA[n];
            }
            outindex++;
    clock(dut, tfp);
  }

  dut->final();
  tfp->close(); 

  for(int j = 0; j<=TFHEpp::lvl1param::n;j++){
      uint32_t trueout = res[j];
      uint32_t circout = resaligned[j];
      if(trueout != circout){
        std::cout<<j<<std::endl;
        std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
        // exit(1);
      }
  }

  std::cout<<"PASS"<<std::endl;
}