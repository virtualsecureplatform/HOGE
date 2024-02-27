#include <verilated.h>
#include <verilated_fst_c.h>
#include <VExternalProductWrap.h>
#include <tfhe++.hpp>

constexpr uint numcycle = 1<<5;
constexpr uint fiber = TFHEpp::lvl1param::n/numcycle;
constexpr int radixbit = 5;
constexpr int radix = 1<<radixbit;
constexpr int muldelay = 9;
constexpr int accdelay = muldelay+1+1+1;
constexpr int interslr = 8;
TFHEpp::TRGSWNTT<TFHEpp::lvl1param> trgswntt;
uint trgswcycle = 0;

void clock(VExternalProductWrap *dut, VerilatedFstC* tfp){
  static uint time_counter = 0;
  dut->eval();
  tfp->dump(1000*time_counter);
  time_counter++;
  dut->clock = !dut->clock;
  dut->io_trgswinvalid = (trgswcycle/numcycle)!=2*TFHEpp::lvl1param::l;
  if(dut->io_trgswinready!=0){
    for(int j = 0; j<2;j++)
        for(int k=0;k<fiber;k++){
          dut->io_trgswin[2*(j*fiber+k)] = static_cast<uint32_t>(trgswntt[trgswcycle/numcycle][j][(trgswcycle%numcycle)*fiber+k].value);
          dut->io_trgswin[2*(j*fiber+k)+1] = static_cast<uint32_t>(trgswntt[trgswcycle/numcycle][j][(trgswcycle%numcycle)*fiber+k].value>>32);
        }
  }

  dut->eval();
  tfp->dump(1000*time_counter);
  time_counter++;
  dut->clock = !dut->clock;
  if(dut->io_trgswinready!=0){
    for(int j = 0; j<2;j++)
        for(int k=0;k<fiber;k++){
          dut->io_trgswin[2*(j*fiber+k)] = static_cast<uint32_t>(trgswntt[trgswcycle/numcycle][j][(trgswcycle%numcycle)*fiber+k].value);
          dut->io_trgswin[2*(j*fiber+k)+1] = static_cast<uint32_t>(trgswntt[trgswcycle/numcycle][j][(trgswcycle%numcycle)*fiber+k].value>>32);
        }
    if((trgswcycle/numcycle)!=2*TFHEpp::lvl1param::l) trgswcycle++;
  }
}

int main(int argc, char** argv) {
  constexpr uint num_test = 100;
  //generatros
  std::random_device seed_gen;
  std::default_random_engine engine(seed_gen());
  // std::uniform_int_distribution<uint32_t> binary(0, 1);
  std::uniform_int_distribution<uint32_t> Torus32dist(0, std::numeric_limits<uint32_t>::max());
  std::uniform_int_distribution<uint64_t> INTorusdist(0, cuHEpp::P-1);
  
  //Initialize TFHEpp objects
  // TFHEpp::lweKey key;

  TFHEpp::TRLWE<TFHEpp::lvl1param> c,res,circres;

  for(int i = 0; i < 2*TFHEpp::lvl1param::l*2*TFHEpp::lvl1param::n; i++) trgswntt[0][0][i] = cuHEpp::INTorus(INTorusdist(engine),false);

  const std::unique_ptr<std::array<std::array<cuHEpp::INTorus, TFHEpp::lvl1param::n>, 2>>
      tablelvl1 = cuHEpp::TableGen<TFHEpp::lvl1param::nbit>();
  const std::unique_ptr<std::array<std::array<cuHEpp::INTorus, TFHEpp::lvl1param::n>, 2>>
      twistlvl1 = cuHEpp::TwistGen<TFHEpp::lvl1param::nbit>();

  Verilated::commandArgs(argc, argv);
  VExternalProductWrap *dut = new VExternalProductWrap();

  uint time_counter = 0;
  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  // Format
  dut->reset = 1;
  dut->clock = 1;
  dut->io_trgswinvalid = 1;
  dut->io_validin = 0;

  // Reset
  clock(dut,tfp);

  //Release reset
  dut->reset = 0;

  for(int test = 0; test < num_test; test++){
    using P = TFHEpp::lvl1param;
    uint count = 0;
    uint errorcount = 0;
    uint middleerrorcount = 0;
    
    clock(dut,tfp);

    for(int i = 0; i < 2*TFHEpp::lvl1param::n; i++) c[0][i] = Torus32dist(engine);
    dut->io_validin = 1;
    for(int i = 0; i < radix; i++){
      for(int j = 0; j < radix; j++) dut->io_in[j] = c[0][j*fiber+i];
      clock(dut, tfp);
    }
    dut->io_validin = 0;
    for(int i = 0; i< radix; i++) clock(dut, tfp);
    dut->io_validin = 1;
    for(int i = 0; i < radix; i++){
      for(int j = 0; j < radix; j++) dut->io_in[j] = c[1][j*fiber+i];
      clock(dut, tfp);
    }
    dut->io_validin = 0;
  

    TFHEpp::DecomposedPolynomial<TFHEpp::lvl1param> decpoly;
    TFHEpp::PolynomialNTT<TFHEpp::lvl1param> pre,middle;
    TFHEpp::DecomposedPolynomialNTT<P> decpolyntt;
    TFHEpp::DecompositionPolynomial<P>(decpoly, c[0], 0);
    cuHEpp::TwistMulInvert<typename TFHEpp::lvl1param::T, TFHEpp::lvl1param::nbit>(pre, decpoly, (*twistlvl1)[1]);
    middle = pre;
    cuHEpp::INTTradix<TFHEpp::lvl1param::nbit, 5>(&middle[0], TFHEpp::lvl1param::n, 1, (*tablelvl1)[1]);    // decpolyntt = middle;
    // constexpr uint radix = 1<<5;
    // for (uint32_t block = 0; block < radix; block++)
    //     cuHEpp::INTTradixButterfly<5>(&decpolyntt[radix * block], radix);
    TFHEpp::DecompositionPolynomialNTT<P>(decpolyntt, c[0], 0);
    
    TFHEpp::TRLWENTT<P> restrlwentt = {};

    int watchdog = 0;
    while(dut->io_inttvalidout==0){
      count++;
      clock(dut,tfp);
      std::cout<<"count:"<<count<<std::endl;
      watchdog++;
      if(watchdog > 1000){
        dut->final();
        tfp->close();
        exit(1);
      }
    }
    errorcount = 0;
    int mulandacccycle = accdelay;
    for(int i = 0; i < 2*interslr+accdelay; i++)  clock(dut,tfp);
    
    for (int i = 0; i < P::l; i++) {
      TFHEpp::DecompositionPolynomial<P>(decpoly, c[0], i);
      cuHEpp::TwistMulInvert<typename TFHEpp::lvl1param::T, TFHEpp::lvl1param::nbit>(pre, decpoly, (*twistlvl1)[1]);
      middle = pre;
      cuHEpp::INTTradix<TFHEpp::lvl1param::nbit, 5>(&middle[0], TFHEpp::lvl1param::n, 1, (*tablelvl1)[1]);
      TFHEpp::DecompositionPolynomialNTT<P>(decpolyntt, c[0], i);
      for (int j = 0; j < P::n; j++)
          restrlwentt[0][j] += decpolyntt[j] * trgswntt[i][0][j];
      for (int j = 0; j < P::n; j++)
          restrlwentt[1][j] += decpolyntt[j] * trgswntt[i][1][j];
      watchdog = 0;

      errorcount = 0;
      int mulandaccerror = 0;
      for(int j = 0; j<numcycle; j++){
        //MULandACC
        for(int j = 0; j<2;j++)
          for(int k=0;k<fiber;k++){
            uint64_t trueout = restrlwentt[j][((mulandacccycle-accdelay)%numcycle)*fiber+k].value;
            uint64_t circout = dut->io_accout[2*(j*fiber+k)]+(static_cast<uint64_t>(dut->io_accout[2*(j*fiber+k)+1])<<32);
            if(trueout != circout){
              mulandaccerror++;
              std::cout<<mulandacccycle<<":"<<((mulandacccycle-accdelay)%numcycle)<<":"<<j<<":"<<k<<std::endl;
              std::cout<<"ACC Error: "<<trueout<<":"<<circout<<std::endl;
              clock(dut,tfp);
              dut->final();
              tfp->close();
              exit(1);
            }
          }
        mulandacccycle++;
        clock(dut,tfp);
      }
      if(errorcount!=0){
        std::cout<<errorcount<<std::endl;
        std::cout<<test<<std::endl;
        dut->final();
        tfp->close();
        exit(1);
      }
      if(mulandaccerror!=0){
          std::cout<<i<<std::endl; 
          std::cout<<"ACC Error Count:"<< mulandaccerror<<std::endl;
          dut->final();
          tfp->close();
          exit(1);
        }
    }
    std::cout<<"First Half"<<std::endl;
    for (int i = 0; i < P::l; i++) {
        TFHEpp::DecompositionPolynomialNTT<P>(decpolyntt, c[1], i);
        for (int j = 0; j < P::n; j++)
            restrlwentt[0][j] += decpolyntt[j] * trgswntt[i + P::l][0][j];
        for (int j = 0; j < P::n; j++)
            restrlwentt[1][j] += decpolyntt[j] * trgswntt[i + P::l][1][j];
        while(dut->io_inttvalidout==0){
          count++;
          clock(dut,tfp);
          std::cout<<"FH:"<<count<<std::endl;
        }
        errorcount = 0;
        int mulandaccerror = 0;
        for(int j = 0; j<numcycle; j++){
          //MULandACC
          if(mulandacccycle/numcycle<2*P::l)
            for(int j = 0; j<2;j++)
              for(int k=0;k<fiber;k++){
                uint64_t trueout = restrlwentt[j][((mulandacccycle-accdelay)%numcycle)*fiber+k].value;
                uint64_t circout = dut->io_accout[2*(j*fiber+k)]+(static_cast<uint64_t>(dut->io_accout[2*(j*fiber+k)+1])<<32);
                if(trueout != circout){
                  mulandaccerror++;
                  std::cout<<mulandacccycle<<":"<<((mulandacccycle-accdelay)%numcycle)<<":"<<j<<":"<<k<<std::endl;
                  std::cout<<"ACC Error: "<<trueout<<":"<<circout<<std::endl;
                  clock(dut,tfp);
                  dut->final();
                  tfp->close();
                  exit(1);
                }
              }
          mulandacccycle++;
          clock(dut,tfp);
        }
        if(mulandaccerror!=0){
          std::cout<<i<<std::endl; 
          std::cout<<"ACC Error Count:"<< mulandaccerror<<std::endl;
          dut->final();
          tfp->close();
          exit(1);
        }
        if(errorcount!=0){
          std::cout<<i<<std::endl; 
          std::cout<<"INTT Error Count:"<< errorcount<<std::endl;
          dut->final();
          tfp->close();
          exit(1);
        }
    }
    std::cout<<mulandacccycle-accdelay<<std::endl;
    while(mulandacccycle<2*P::l*numcycle+accdelay){
      // if(dut->io_trgswinready==1){
              for(int j = 0; j<2;j++)
                for(int k=0;k<fiber;k++){
                  uint64_t trueout = restrlwentt[j][k*fiber+((mulandacccycle-accdelay)%numcycle)].value;
                  uint64_t circout = dut->io_accout[2*(j*fiber+k)]+(static_cast<uint64_t>(dut->io_accout[2*(j*fiber+k)+1])<<32);
                  std::cout<<mulandacccycle<<":"<<((mulandacccycle-accdelay)%numcycle)<<":"<<j<<":"<<k<<std::endl;
                  if(trueout != circout){
                    // mulandaccerror++;
                    std::cout<<"ACC Error: "<<trueout<<":"<<circout<<std::endl;
                    clock(dut,tfp);
                    dut->final();
                    tfp->close();
                    exit(1);
                  }
                }
            mulandacccycle++;
          // }
          clock(dut,tfp);
    }
    TFHEpp::TwistNTT<P>(res[0], restrlwentt[0]);
    TFHEpp::TwistNTT<P>(res[1], restrlwentt[1]);

    TFHEpp::trgswnttExternalProduct<P>(res,c,trgswntt);

    std::cout<<"Processing"<<std::endl;
    watchdog = 0;
    while(dut->io_validout==0){
      count++;
      watchdog++;
      clock(dut,tfp);
      std::cout<<"WaitFin:"<<count<<std::endl;
      if(watchdog>1000){
        dut->final();
        tfp->close();
        exit(1);
      }
    }
    for(int k = 0; k < 2; k++)
      for(int i = 0; i < radix; i++){
        while(dut->io_validout==0) clock(dut, tfp);
        for(int j = 0; j < radix; j++) circres[k][j*radix+i] = dut->io_out[j];
        clock(dut, tfp);
      }

    errorcount = 0;
    for(int i = 0; i<2;i++){
      for(int j = 0; j<TFHEpp::lvl1param::n;j++){
          uint32_t trueout = res[i][j];
          uint32_t circout = circres[i][j];
          if(trueout != circout){
            std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
            std::cout<<i<<":"<<j<<std::endl;
            errorcount++;
          }
          if(errorcount > 512){
            dut->final();
            tfp->close();
            exit(1);
          }
          // else std::cout<<"Correct:"<<i<<":"<< j << std::endl;
      }
      std::cout<<errorcount<<std::endl;
    }
    if(errorcount!=0){
      dut->final();
      tfp->close();
      exit(1);
    }
    trgswcycle = 0;
  }
  std::cout<<"PASS"<<std::endl;
}