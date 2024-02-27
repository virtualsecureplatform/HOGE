#include <verilated.h>
#include <verilated_fst_c.h>
#include <VAXI4IKS.h>
#include <tfhe++.hpp>

void clock(VAXI4IKS *dut, VerilatedFstC* tfp){
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

  TFHEpp::TLWE<TFHEpp::lvl0param> res = {};
  TFHEpp::IdentityKeySwitch<TFHEpp::lvl10param>(res,tlwe,*iksk);

  //allgned to distribute to module
  constexpr uint iksknumbus = 20;
  constexpr uint totaliksknumbus = 80;
  constexpr uint hbmbuswidthlb = 8;
  constexpr uint hbmbuswords = 1U<<(hbmbuswidthlb-5);
  constexpr uint hbmwordsinbus = (1U<<hbmbuswidthlb)/std::numeric_limits<typename TFHEpp::lvl0param::T>::digits;
  constexpr uint buswidthlb = 9;
  constexpr uint buswords = 1U<<(buswidthlb-5);
  constexpr uint wordsinbus = (1U<<buswidthlb)/std::numeric_limits<typename TFHEpp::lvl0param::T>::digits;
  constexpr uint alignedlenlvl0 = (((std::numeric_limits<TFHEpp::lvl0param::T>::digits*(TFHEpp::lvl0param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl0param::T>::digits;
  // static_assert(iksknumbus==(alignedlenlvl0*std::numeric_limits<TFHEpp::lvl0param::T>::digits>>buswidthlb)/4, "");
  using alignedTLWElvl0 = std::array<TFHEpp::lvl0param::T,alignedlenlvl0>;
  std::array<std::array<std::array<std::array<std::array<std::array<typename TFHEpp::lvl0param::T, hbmwordsinbus>, totaliksknumbus/iksknumbus>, (1 << TFHEpp::lvl10param::basebit) - 1>, TFHEpp::lvl10param::t>,TFHEpp::lvl1param::n>, iksknumbus> ikskaligned = {};
  for(int i = 0; i<TFHEpp::lvl1param::n; i++) for(int j = 0; j < TFHEpp::lvl10param::t; j++) for(int k = 0; k< (1 << TFHEpp::lvl10param::basebit) - 1; k++) for(int l = 0; l < hbmwordsinbus; l++) for(int m = 0; m < iksknumbus; m++) for(int n = 0; n < totaliksknumbus/iksknumbus; n++) ikskaligned[m][i][j][k][n][l] = (*iksk)[i][j][k][n*iksknumbus*hbmwordsinbus+m*hbmwordsinbus+l];

  constexpr uint alignedlenlvl1 = (((std::numeric_limits<TFHEpp::lvl1param::T>::digits*(TFHEpp::lvl1param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl1param::T>::digits;
  using alignedTLWElvl1 = std::array<TFHEpp::lvl1param::T,alignedlenlvl1>;
  alignedTLWElvl1 tlwealigned = {};
  for(int l = 0; l<= TFHEpp::lvl1param::n; l++) tlwealigned[l] = tlwe[l];
  alignedTLWElvl0 resaligned;

  Verilated::commandArgs(argc, argv);
  VAXI4IKS *dut = new VAXI4IKS();

  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  // Format
  dut->reset = 1;
  dut->clock = 0;
  dut->io_ap_start = 0;
  for(int j = 0; j<buswords; j++){
    dut->io_axi4in_TDATA[j] = tlwealigned[j];
  }
  dut->io_axi4in_TVALID = 0;
  dut->io_axi4out_TREADY = 0;
  dut->io_axi4ikskin_0_TVALID = 0;
  dut->io_axi4ikskin_1_TVALID = 0;
  dut->io_axi4ikskin_2_TVALID = 0;
  dut->io_axi4ikskin_3_TVALID = 0;
  dut->io_axi4ikskin_4_TVALID = 0;
  dut->io_axi4ikskin_5_TVALID = 0;
  dut->io_axi4ikskin_6_TVALID = 0;
  dut->io_axi4ikskin_7_TVALID = 0;
  dut->io_axi4ikskin_8_TVALID = 0;
  dut->io_axi4ikskin_9_TVALID = 0;
  dut->io_axi4ikskin_10_TVALID = 0;
  dut->io_axi4ikskin_11_TVALID = 0;
  dut->io_axi4ikskin_12_TVALID = 0;
  dut->io_axi4ikskin_13_TVALID = 0;
  dut->io_axi4ikskin_14_TVALID = 0;
  dut->io_axi4ikskin_15_TVALID = 0;
  dut->io_axi4ikskin_16_TVALID = 0;
  dut->io_axi4ikskin_17_TVALID = 0;
  dut->io_axi4ikskin_18_TVALID = 0;
  dut->io_axi4ikskin_19_TVALID = 0;

  // Reset
  clock(dut, tfp);

  //Release reset
  dut->reset = 0;
  dut->io_ap_start = 1;
  dut->io_axi4out_TREADY = 1;
  dut->io_axi4outcmd_TREADY = 1;
  dut->io_axi4incmd_TREADY = 1;
  dut->io_axi4ikskincmd_0_TREADY = 1;
  dut->io_axi4ikskincmd_1_TREADY = 1;
  dut->io_axi4ikskincmd_2_TREADY = 1;
  dut->io_axi4ikskincmd_3_TREADY = 1;
  dut->io_axi4ikskincmd_4_TREADY = 1;
  dut->io_axi4ikskincmd_5_TREADY = 1;
  dut->io_axi4ikskincmd_6_TREADY = 1;
  dut->io_axi4ikskincmd_7_TREADY = 1;
  dut->io_axi4ikskincmd_8_TREADY = 1;
  dut->io_axi4ikskincmd_9_TREADY = 1;
  dut->io_axi4ikskincmd_10_TREADY = 1;
  dut->io_axi4ikskincmd_11_TREADY = 1;
  dut->io_axi4ikskincmd_12_TREADY = 1;
  dut->io_axi4ikskincmd_13_TREADY = 1;
  dut->io_axi4ikskincmd_14_TREADY = 1;
  dut->io_axi4ikskincmd_15_TREADY = 1;
  dut->io_axi4ikskincmd_16_TREADY = 1;
  dut->io_axi4ikskincmd_17_TREADY = 1;
  dut->io_axi4ikskincmd_18_TREADY = 1;
  dut->io_axi4ikskincmd_19_TREADY = 1;

  clock(dut, tfp);

  if(dut->io_axi4in_TREADY != 1){
    std::cout<<"NOT READY!"<<std::endl;
    dut->final();
    tfp->close();
    exit(1);
  }

  std::cout<<"Initialize"<<std::endl;
  //Initialize Input Buffer
  dut->io_axi4in_TVALID = 1;
  for(int i = 0; i < alignedlenlvl1/buswords; i++){
    for(int j = 0; j < buswords; j++){
      dut->io_axi4in_TDATA[j] = tlwealigned[buswords*i+j];
    }
    clock(dut, tfp);
  }
  dut->io_axi4in_TVALID = 0;
{
  int i = 0;
  int j = 0;
  int k = 0;
  int l = 0;
  for(int m = 0; m < hbmbuswords; m++){
            dut->io_axi4ikskin_0_TDATA[m] = ikskaligned[0][i][j][k][l][m];
            dut->io_axi4ikskin_1_TDATA[m] = ikskaligned[1][i][j][k][l][m];
            dut->io_axi4ikskin_2_TDATA[m] = ikskaligned[2][i][j][k][l][m];
            dut->io_axi4ikskin_3_TDATA[m] = ikskaligned[3][i][j][k][l][m];
            dut->io_axi4ikskin_4_TDATA[m] = ikskaligned[4][i][j][k][l][m];
            dut->io_axi4ikskin_5_TDATA[m] = ikskaligned[5][i][j][k][l][m];
            dut->io_axi4ikskin_6_TDATA[m] = ikskaligned[6][i][j][k][l][m];
            dut->io_axi4ikskin_7_TDATA[m] = ikskaligned[7][i][j][k][l][m];
            dut->io_axi4ikskin_8_TDATA[m] = ikskaligned[8][i][j][k][l][m];
            dut->io_axi4ikskin_9_TDATA[m] = ikskaligned[9][i][j][k][l][m];
            dut->io_axi4ikskin_10_TDATA[m] = ikskaligned[10][i][j][k][l][m];
            dut->io_axi4ikskin_11_TDATA[m] = ikskaligned[11][i][j][k][l][m];
            dut->io_axi4ikskin_12_TDATA[m] = ikskaligned[12][i][j][k][l][m];
            dut->io_axi4ikskin_13_TDATA[m] = ikskaligned[13][i][j][k][l][m];
            dut->io_axi4ikskin_14_TDATA[m] = ikskaligned[14][i][j][k][l][m];
            dut->io_axi4ikskin_15_TDATA[m] = ikskaligned[15][i][j][k][l][m];
            dut->io_axi4ikskin_16_TDATA[m] = ikskaligned[16][i][j][k][l][m];
            dut->io_axi4ikskin_17_TDATA[m] = ikskaligned[17][i][j][k][l][m];
            dut->io_axi4ikskin_18_TDATA[m] = ikskaligned[18][i][j][k][l][m];
            dut->io_axi4ikskin_19_TDATA[m] = ikskaligned[19][i][j][k][l][m];
          }
}

  //Bubble
  for (int i = 0; i < 30; i++) clock(dut, tfp);

  dut->io_axi4ikskin_0_TVALID = 1;
  dut->io_axi4ikskin_1_TVALID = 1;
  dut->io_axi4ikskin_2_TVALID = 1;
  dut->io_axi4ikskin_3_TVALID = 1;
  dut->io_axi4ikskin_4_TVALID = 1;
  dut->io_axi4ikskin_5_TVALID = 1;
  dut->io_axi4ikskin_6_TVALID = 1;
  dut->io_axi4ikskin_7_TVALID = 1;
  dut->io_axi4ikskin_8_TVALID = 1;
  dut->io_axi4ikskin_9_TVALID = 1;
  // while(dut->io_axi4ikskin_0_TREADY==0) clock(dut, tfp);

  std::cout<<"Processing"<<std::endl;

  dut->io_axi4ikskin_10_TVALID = 1;
  dut->io_axi4ikskin_11_TVALID = 1;
  dut->io_axi4ikskin_12_TVALID = 1;
  dut->io_axi4ikskin_13_TVALID = 1;
  dut->io_axi4ikskin_14_TVALID = 1;
  dut->io_axi4ikskin_15_TVALID = 1;
  dut->io_axi4ikskin_16_TVALID = 1;
  dut->io_axi4ikskin_17_TVALID = 1;
  dut->io_axi4ikskin_18_TVALID = 1;
  dut->io_axi4ikskin_19_TVALID = 1;
  uint count = 0;
  uint outindex = 0;
  for(int i = 0; i<TFHEpp::lvl1param::n;i++)
    for(int j = 0; j<TFHEpp::lvl10param::t;j++)
      for(int k=0;k<(1<<TFHEpp::lvl10param::basebit)-1;k++){
        for(int l = 0; l < totaliksknumbus/iksknumbus; l++){
          for(int m = 0; m < hbmbuswords; m++){
            dut->io_axi4ikskin_0_TDATA[m] = ikskaligned[0][i][j][k][l][m];
            dut->io_axi4ikskin_1_TDATA[m] = ikskaligned[1][i][j][k][l][m];
            dut->io_axi4ikskin_2_TDATA[m] = ikskaligned[2][i][j][k][l][m];
            dut->io_axi4ikskin_3_TDATA[m] = ikskaligned[3][i][j][k][l][m];
            dut->io_axi4ikskin_4_TDATA[m] = ikskaligned[4][i][j][k][l][m];
            dut->io_axi4ikskin_5_TDATA[m] = ikskaligned[5][i][j][k][l][m];
            dut->io_axi4ikskin_6_TDATA[m] = ikskaligned[6][i][j][k][l][m];
            dut->io_axi4ikskin_7_TDATA[m] = ikskaligned[7][i][j][k][l][m];
            dut->io_axi4ikskin_8_TDATA[m] = ikskaligned[8][i][j][k][l][m];
            dut->io_axi4ikskin_9_TDATA[m] = ikskaligned[9][i][j][k][l][m];
            dut->io_axi4ikskin_10_TDATA[m] = ikskaligned[10][i][j][k][l][m];
            dut->io_axi4ikskin_11_TDATA[m] = ikskaligned[11][i][j][k][l][m];
            dut->io_axi4ikskin_12_TDATA[m] = ikskaligned[12][i][j][k][l][m];
            dut->io_axi4ikskin_13_TDATA[m] = ikskaligned[13][i][j][k][l][m];
            dut->io_axi4ikskin_14_TDATA[m] = ikskaligned[14][i][j][k][l][m];
            dut->io_axi4ikskin_15_TDATA[m] = ikskaligned[15][i][j][k][l][m];
            dut->io_axi4ikskin_16_TDATA[m] = ikskaligned[16][i][j][k][l][m];
            dut->io_axi4ikskin_17_TDATA[m] = ikskaligned[17][i][j][k][l][m];
            dut->io_axi4ikskin_18_TDATA[m] = ikskaligned[18][i][j][k][l][m];
            dut->io_axi4ikskin_19_TDATA[m] = ikskaligned[19][i][j][k][l][m];
          }
          if(dut->io_axi4out_TVALID){
            dut->io_axi4out_TREADY = 1;
            for(int n = 0; n < buswords; n++){
              resaligned[buswords*outindex+n] = dut->io_axi4out_TDATA[n];
            }
            outindex++;
          }
          clock(dut, tfp);
          std::cout<<i<<":"<<j<<":"<<k<<":"<<l<<std::endl;
          count++;
        }
      }

  int watchdog = 0;
  while(dut->io_axi4out_TVALID==0){
    clock(dut, tfp);
    if(watchdog>100){
      dut->final();
      tfp->close();
      exit(1);
    }
    watchdog++;
  }
  dut->io_axi4out_TREADY = 1;
    
  while(dut->io_axi4out_TVALID==1){
    for(int n = 0; n < buswords; n++){
              resaligned[buswords*outindex+n] = dut->io_axi4out_TDATA[n];
            }
            outindex++;
    clock(dut, tfp);
  }


  dut->final();
  tfp->close(); 

  for(int j = 0; j<=TFHEpp::lvl0param::n;j++){
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