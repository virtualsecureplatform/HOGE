#include <bits/stdint-uintn.h>
#include <verilated.h>
#include <verilated_fst_c.h>
#include <VHomGateWrap.h>
#include <tfhe++.hpp>

void clock(VHomGateWrap *dut, VerilatedFstC* tfp){
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
  TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param> *bkntt = new TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param>();
  TFHEpp::bknttgen<TFHEpp::lvl01param>(*bkntt,*sk);

  //allgned to distribute to module
  constexpr uint iksknumbus = 10;
  constexpr uint totaliksknumbus = 40;
  constexpr uint hbmbuswidthlb = 9;
  constexpr uint hbmbuswords = 1U<<(hbmbuswidthlb-5);
  constexpr uint hbmwordsinbus = (1U<<hbmbuswidthlb)/std::numeric_limits<typename TFHEpp::lvl0param::T>::digits;

  constexpr uint radixbit = 5;
  constexpr uint radix = 1<<radixbit;
  constexpr uint numcycle = 1<<5;
  constexpr uint fiber = TFHEpp::lvl1param::n/numcycle;
  constexpr uint bknumbus = 8;
  constexpr uint buswidthlb = 9;
  constexpr uint buswords = 1U<<(buswidthlb-5);
  constexpr uint wordsinbus = (1U<<buswidthlb)/std::numeric_limits<typename TFHEpp::lvl0param::T>::digits;
  constexpr uint nttwordsinbus = (1U<<buswidthlb)/64;
  constexpr uint alignedlenlvl0 = (((std::numeric_limits<TFHEpp::lvl0param::T>::digits*(TFHEpp::lvl0param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl0param::T>::digits;
  using alignedTLWElvl0 = std::array<TFHEpp::lvl0param::T,alignedlenlvl0>;

  std::array<std::array<std::array<std::array<std::array<std::array<typename TFHEpp::lvl0param::T, hbmwordsinbus>, totaliksknumbus/iksknumbus>, (1 << TFHEpp::lvl10param::basebit) - 1>, TFHEpp::lvl10param::t>,TFHEpp::lvl1param::n>, iksknumbus> ikskaligned = {};
  for(int i = 0; i<TFHEpp::lvl1param::n; i++) for(int j = 0; j < TFHEpp::lvl10param::t; j++) for(int k = 0; k< (1 << TFHEpp::lvl10param::basebit) - 1; k++) for(int l = 0; l < hbmwordsinbus; l++) for(int m = 0; m < iksknumbus; m++) for(int n = 0; n < totaliksknumbus/iksknumbus; n++) ikskaligned[m][i][j][k][n][l] = (*iksk)[i][j][k][n*iksknumbus*hbmwordsinbus+m*hbmwordsinbus+l];

  constexpr uint alignedlenlvl1 = (((std::numeric_limits<TFHEpp::lvl1param::T>::digits*(TFHEpp::lvl1param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl1param::T>::digits;
  using alignedTLWElvl1 = std::array<TFHEpp::lvl1param::T,alignedlenlvl1>;

  const bool pa = (binary(engine) > 0);
  const bool pb = (binary(engine) > 0);
  TFHEpp::TLWE<TFHEpp::lvl1param> tlwea = TFHEpp::tlweSymEncrypt<TFHEpp::lvl1param>(pa,TFHEpp::lvl1param::α,sk->key.lvl1);
  TFHEpp::TLWE<TFHEpp::lvl1param> tlweb = TFHEpp::tlweSymEncrypt<TFHEpp::lvl1param>(pb,TFHEpp::lvl1param::α,sk->key.lvl1);
  TFHEpp::TLWE<TFHEpp::lvl1param> res,tlwelvl1;
  // NAND
  // for(int l = 0; l<= TFHEpp::lvl1param::n; l++) tlwelvl1[l] = - tlwea[l] - tlweb[l];
  // tlwelvl1[TFHEpp::lvl1param::n] += TFHEpp::lvl1param::μ;
  //AND
  for(int l = 0; l<= TFHEpp::lvl1param::n; l++) tlwelvl1[l] = tlwea[l] + tlweb[l];
  tlwelvl1[TFHEpp::lvl1param::n] -= TFHEpp::lvl1param::μ;

  using alignedTLWElvl1 = std::array<TFHEpp::lvl1param::T,alignedlenlvl1>;
  alignedTLWElvl1 tlweaaligned = {},tlwebaligned = {}, resaligned, tlwelvl1aligned={};
  for(int l = 0; l<= TFHEpp::lvl1param::n; l++) tlwelvl1aligned[l] = tlwelvl1[l];
  for(int l = 0; l<= TFHEpp::lvl1param::n; l++) tlweaaligned[l] = tlwea[l];
  for(int l = 0; l<= TFHEpp::lvl1param::n; l++) tlwebaligned[l] = tlweb[l];
  TFHEpp::TLWE<TFHEpp::lvl0param> tlwelvl0 = {};
  TFHEpp::IdentityKeySwitch<TFHEpp::lvl10param>(tlwelvl0,tlwelvl1,*iksk);
  
  TFHEpp::Polynomial<TFHEpp::lvl1param> testvec;
  for (int i = 0; i < TFHEpp::lvl1param::n; i++)
      testvec[i] = TFHEpp::lvl1param::μ;

  TFHEpp::TRLWE<TFHEpp::lvl1param> brres;
  brres[0] = {};
  const uint32_t b̄ =
        2 * TFHEpp::lvl1param::n - (tlwelvl0[TFHEpp::lvl0param::n] >>(std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 1 - TFHEpp::lvl1param::nbit) );


  Verilated::commandArgs(argc, argv);
  VHomGateWrap *dut = new VHomGateWrap();

  Verilated::traceEverOn(true);
  VerilatedFstC* tfp = new VerilatedFstC;
  dut->trace(tfp, 100);  // Trace 100 levels of hierarchy
  tfp->open("simx.fst");

  // Format
  dut->reset = 1;
  dut->clock = 0;
  dut->io_ap_start = 0;

  dut->io_axi4ina_TVALID = 0;
  dut->io_axi4inb_TVALID = 0;
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
  // dut->io_axi4ikskin_10_TVALID = 0;
  // dut->io_axi4ikskin_11_TVALID = 0;
  // dut->io_axi4ikskin_12_TVALID = 0;
  // dut->io_axi4ikskin_13_TVALID = 0;
  // dut->io_axi4ikskin_14_TVALID = 0;
  // dut->io_axi4ikskin_15_TVALID = 0;
  // dut->io_axi4ikskin_16_TVALID = 0;
  // dut->io_axi4ikskin_17_TVALID = 0;
  // dut->io_axi4ikskin_18_TVALID = 0;
  // dut->io_axi4ikskin_19_TVALID = 0;
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
  dut->io_axi4out_TREADY = 1;
  dut->io_axi4outcmd_TREADY = 1;
  dut->io_axi4inacmd_TREADY = 1;
  dut->io_axi4inbcmd_TREADY = 1;
  
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
  // dut->io_axi4ikskincmd_10_TREADY = 1;
  // dut->io_axi4ikskincmd_11_TREADY = 1;
  // dut->io_axi4ikskincmd_12_TREADY = 1;
  // dut->io_axi4ikskincmd_13_TREADY = 1;
  // dut->io_axi4ikskincmd_14_TREADY = 1;
  // dut->io_axi4ikskincmd_15_TREADY = 1;
  // dut->io_axi4ikskincmd_16_TREADY = 1;
  // dut->io_axi4ikskincmd_17_TREADY = 1;
  // dut->io_axi4ikskincmd_18_TREADY = 1;
  // dut->io_axi4ikskincmd_19_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_0_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_1_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_2_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_3_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_4_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_5_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_6_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_7_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_8_TREADY = 1;
  // dut->io_axi4ikskdebugcmd_9_TREADY = 1;

  dut->io_axi4bkincmd_0_TREADY = 1;
  dut->io_axi4bkincmd_1_TREADY = 1;
  dut->io_axi4bkincmd_2_TREADY = 1;
  dut->io_axi4bkincmd_3_TREADY = 1;
  dut->io_axi4bkincmd_4_TREADY = 1;
  dut->io_axi4bkincmd_5_TREADY = 1;
  dut->io_axi4bkincmd_6_TREADY = 1;
  dut->io_axi4bkincmd_7_TREADY = 1;

  for(int test = 0; test < 2; test++){

  std::cout<<"Initialize"<<std::endl;
  dut->io_ap_start = 1;
  //NAND
  // dut->io_scaleaindex = 2;
  // dut->io_scalebindex = 2;
  // dut->io_offsetindex = 0;
  //AND
  dut->io_scaleaindex = 0;
  dut->io_scalebindex = 0;
  dut->io_offsetindex = 2;
  dut->io_axi4ina_TVALID = 1;
  dut->io_axi4inb_TVALID = 1;
  for(int j = 0; j<buswords; j++){
    dut->io_axi4ina_TDATA[j] = tlweaaligned[buswords*0+j];
    dut->io_axi4inb_TDATA[j] = tlwebaligned[buswords*0+j];
  }
  //Initialize Input Buffer
  clock(dut, tfp);
  // for(int i = 0; i<20; i++)clock(dut, tfp);
  // dut->final();
  // tfp->close(); 
  // exit(1);
  if(dut->io_axi4ina_TREADY && dut->io_axi4inb_TREADY != 1){
    std::cout<<"NOT READY!"<<std::endl;
    dut->final();
    tfp->close();
    exit(1);
  }

  std::cout<<"Initialize"<<std::endl;
  //Initialize Input Buffer
  for(int i = 0; i < alignedlenlvl1/buswords-1; i++){
    for(int j = 0; j < buswords; j++){
      dut->io_axi4ina_TDATA[j] = tlweaaligned[buswords*(i+1)+j];
      dut->io_axi4inb_TDATA[j] = tlwebaligned[buswords*(i+1)+j];
    }
    for(int j = 0; j < buswords; j++){
      if(dut->io_debugadd[j] != tlwelvl1aligned[buswords*i+j]){
        std::cout<<"ADD ERROR: "<<i<<":"<<j<<std::endl;
        std::cout<<tlweaaligned[buswords*i+j]<<":"<<tlwebaligned[buswords*i+j]<<":"<<tlwelvl1aligned[buswords*i+j]<<":"<<dut->io_debugadd[j]<<std::endl;
        dut->final();
        tfp->close();
        exit(1);
      }
    }
    clock(dut, tfp);
  }
  dut->io_axi4ina_TVALID = 0;
  dut->io_axi4inb_TVALID = 0;
  // clock(dut, tfp);
  for(int j = 0; j < buswords; j++){
      if(dut->io_debugadd[j] != tlwelvl1aligned[buswords*(alignedlenlvl1/buswords-1)+j]){
        std::cout<<"ADD ERROR2: "<<alignedlenlvl1/buswords-1<<":"<<j<<std::endl;
        std::cout<<tlweaaligned[buswords*(alignedlenlvl1/buswords-1)+j]<<":"<<tlwebaligned[buswords*(alignedlenlvl1/buswords-1)+j]<<":"<<tlwelvl1aligned[buswords*(alignedlenlvl1/buswords-1)+j]<<":"<<dut->io_debugadd[j]<<std::endl;
        dut->final();
        tfp->close();
        exit(1);
      }
    }

  std::cout<<"IKS"<<std::endl;


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
  // dut->io_axi4ikskin_10_TVALID = 1;
  // dut->io_axi4ikskin_11_TVALID = 1;
  // dut->io_axi4ikskin_12_TVALID = 1;
  // dut->io_axi4ikskin_13_TVALID = 1;
  // dut->io_axi4ikskin_14_TVALID = 1;
  // dut->io_axi4ikskin_15_TVALID = 1;
  // dut->io_axi4ikskin_16_TVALID = 1;
  // dut->io_axi4ikskin_17_TVALID = 1;
  // dut->io_axi4ikskin_18_TVALID = 1;
  // dut->io_axi4ikskin_19_TVALID = 1;

  alignedTLWElvl0 reslvl0;
  uint count = 0;
  uint outindex = 0;
  for(int i = 0; i<TFHEpp::lvl1param::n;i++)
    for(int j = 0; j<TFHEpp::lvl10param::t;j++)
      for(int k=0;k<(1<<TFHEpp::lvl10param::basebit)-1;k++){
        for(int l = 0; l < totaliksknumbus/iksknumbus; l++){
          for(int m = 0; m < hbmbuswords; m++){
            while(dut->io_axi4ikskin_0_TREADY==0) clock(dut, tfp);
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
            // dut->io_axi4ikskin_10_TDATA[m] = ikskaligned[10][i][j][k][l][m];
            // dut->io_axi4ikskin_11_TDATA[m] = ikskaligned[11][i][j][k][l][m];
            // dut->io_axi4ikskin_12_TDATA[m] = ikskaligned[12][i][j][k][l][m];
            // dut->io_axi4ikskin_13_TDATA[m] = ikskaligned[13][i][j][k][l][m];
            // dut->io_axi4ikskin_14_TDATA[m] = ikskaligned[14][i][j][k][l][m];
            // dut->io_axi4ikskin_15_TDATA[m] = ikskaligned[15][i][j][k][l][m];
            // dut->io_axi4ikskin_16_TDATA[m] = ikskaligned[16][i][j][k][l][m];
            // dut->io_axi4ikskin_17_TDATA[m] = ikskaligned[17][i][j][k][l][m];
            // dut->io_axi4ikskin_18_TDATA[m] = ikskaligned[18][i][j][k][l][m];
            // dut->io_axi4ikskin_19_TDATA[m] = ikskaligned[19][i][j][k][l][m];
          }
          if(dut->io_ikskvalid){
            for(int n = 0; n < buswords; n++){
              reslvl0[buswords*outindex+n] = dut->io_ikskout[n];
            }
            outindex++;
          }
          clock(dut, tfp);
          std::cout<<i<<":"<<j<<":"<<k<<":"<<l<<std::endl;
          count++;
        }
      }

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
  // dut->io_axi4ikskin_10_TVALID = 0;
  // dut->io_axi4ikskin_11_TVALID = 0;
  // dut->io_axi4ikskin_12_TVALID = 0;
  // dut->io_axi4ikskin_13_TVALID = 0;
  // dut->io_axi4ikskin_14_TVALID = 0;
  // dut->io_axi4ikskin_15_TVALID = 0;
  // dut->io_axi4ikskin_16_TVALID = 0;HomNAND<TFHEpp::lvl1param>(res,tlwea,tlweb,ek);
  // dut->io_axi4ikskin_17_TVALID = 0;
  // dut->io_axi4ikskin_18_TVALID = 0;
  // dut->io_axi4ikskin_19_TVALID = 0;
    
  int watchdog = 0;
  while(dut->io_ikskvalid==0){
    clock(dut, tfp);
    if(watchdog>100){
      dut->final();
      tfp->close();
      exit(1);
    }
    watchdog++;
  }

  while(dut->io_ikskvalid==1){
  for(int n = 0; n < buswords; n++){
            reslvl0[buswords*outindex+n] = dut->io_ikskout[n];
          }
          outindex++;
  clock(dut, tfp);
  }

  for(int j = 0; j<=TFHEpp::lvl0param::n;j++){
    uint32_t trueout = tlwelvl0[j];
    uint32_t circout = reslvl0[j];
    if(trueout != circout){
      std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
      std::cout<<j<<std::endl;
      dut->final();
      tfp->close();
      exit(1);
    }
  }

  clock(dut, tfp);

  std::cout<<"BR"<<std::endl;

  brres[0] = {};
  TFHEpp::PolynomialMulByXai<TFHEpp::lvl1param>(brres[1],TFHEpp::μpolygen<TFHEpp::lvl1param,TFHEpp::lvl1param::μ>(),b̄);

  count = 0;

  while(dut->io_debugvalid==0) clock(dut, tfp);
  while(dut->io_debugvalid==1) clock(dut, tfp);

constexpr typename TFHEpp::lvl0param::T roundoffset = 1ULL << (std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 2 - TFHEpp::lvl1param::nbit);
for (int i = 0; i < TFHEpp::lvl0param::n; i++) {
  const uint32_t ā = (tlwelvl0[i]+roundoffset)>>(std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 1 - TFHEpp::lvl1param::nbit);

  TFHEpp::TRLWE<TFHEpp::lvl1param> pmbx;
  TFHEpp::PolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(pmbx[0],brres[0],ā);
  TFHEpp::PolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(pmbx[1],brres[1],ā);
  while(dut->io_debugvalid==0) clock(dut, tfp);
  for(int l = 0; l < TFHEpp::lvl1param::k+1; l++)
  for(int i = 0; i < radix; i++){
    for(int j = 0; j < radix; j++) {
      if(dut->io_debugout[j]!=pmbx[l][radix*j+i]){
        std::cout<<"PMBXERROR:"<<l<<":"<<i<<":"<<j<<std::endl;
        std::cout<<dut->io_debugout[j]<<":"<<pmbx[l][radix*j+i]<<std::endl;
        dut->final();
        tfp->close(); 
        exit(1);
      }
    }
    clock(dut, tfp);
  }

  dut->io_axi4bkin_0_TVALID = 1;
  dut->io_axi4bkin_1_TVALID = 1;
  dut->io_axi4bkin_2_TVALID = 1;
  dut->io_axi4bkin_3_TVALID = 1;
  dut->io_axi4bkin_4_TVALID = 1;
  dut->io_axi4bkin_5_TVALID = 1;
  dut->io_axi4bkin_6_TVALID = 1;
  dut->io_axi4bkin_7_TVALID = 1;

  for (int j = 0; j < 2*TFHEpp::lvl1param::l; j++){
    watchdog = 0;
   
    for(int cycle = 0; cycle<numcycle;cycle++){
      // if(i==0&&j==0&&cycle==0) continue;
      // if(cycle != 0)
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
      brres, (*bkntt)[i], ā);

  while(dut->io_debugvalid==0) clock(dut, tfp);
  // std::cout<<res[0][0]<<":"<<dut->io_debugout[0]<<std::endl;
  // std::cout<<b̄<<":"<<ā<<":"<<b̄+ā<<std::endl;
  for(int cycle = 0; cycle<numcycle;cycle++){
    for(int m = 0; m<radix;m++)
    if(dut->io_debugout[m]!=brres[0][m*radix+cycle]){
      std::cout<<i<<":0:"<<cycle<<":"<<m<<std::endl;
      std::cout<<"ERROR:"<<brres[0][m*radix+cycle]<<":"<<dut->io_debugout[m]<<std::endl;
      dut->final();
      tfp->close(); 
      exit(1);
    }
    clock(dut, tfp);
  }
  while(dut->io_debugvalid==0) clock(dut, tfp);
  for(int cycle = 0; cycle<numcycle;cycle++){
    for(int m = 0; m<radix;m++)
    if(dut->io_debugout[m]!=brres[1][m*radix+cycle]){
      std::cout<<i<<":1:"<<cycle<<":"<<m<<std::endl;
      std::cout<<brres[1][m*radix+cycle]<<":"<<dut->io_debugout[m]<<std::endl;
      dut->final();
      tfp->close(); 
      exit(1);
    }
    clock(dut, tfp);
  }
}
  dut->io_ap_start = 0;
  
  watchdog = 0;
  dut->io_axi4out_TREADY=1;
  while(dut->io_axi4out_TVALID==0){
    clock(dut, tfp);
    watchdog++;
    if(watchdog>1000){
      dut->final();
      tfp->close(); 
      exit(1);
    }
  }

  for(int i = 0; i <= TFHEpp::lvl1param::k*TFHEpp::lvl1param::n; i++){
    while(dut->io_axi4out_TVALID==0) clock(dut, tfp);
    resaligned[i] = dut->io_axi4out_TDATA;
    clock(dut, tfp);
  }


  TFHEpp::SampleExtractIndex<TFHEpp::lvl1param>(res,brres,0);

  for(int j = 0; j<=TFHEpp::lvl1param::n;j++){
    uint32_t trueout = res[j];
    uint32_t circout = resaligned[j];
    if(trueout != circout){
      std::cout<<j<<std::endl;
      std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
      exit(1);
      dut->final();
      tfp->close(); 
    }
  }

  }

  dut->final();
  tfp->close(); 

  std::cout<<"PASS"<<std::endl;
}