#include <bits/stdint-uintn.h>
#include <verilated.h>
#include <verilated_fst_c.h>
#include <VHomGateWrap.h>
#include <tfhe++.hpp>

  //allgned to distribute to module
  constexpr uint numbatch = 2;
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

alignas(4096) std::array<std::array<std::array<std::array<std::array<uint64_t,nttwordsinbus>,numcycle>,(TFHEpp::lvl1param::k+1)*TFHEpp::lvl1param::l>,TFHEpp::lvl0param::n>,bknumbus> bknttaligned = {};
std::array<uint,bknumbus> buscycle = {};

void clock(VHomGateWrap *dut, VerilatedFstC* tfp = nullptr){
  static uint time_counter = 0;
  const std::array<uint8_t,bknumbus> treadyarray = {dut->io_axi4bkin_0_TREADY,dut->io_axi4bkin_1_TREADY,dut->io_axi4bkin_2_TREADY,dut->io_axi4bkin_3_TREADY,dut->io_axi4bkin_4_TREADY,dut->io_axi4bkin_5_TREADY,dut->io_axi4bkin_6_TREADY,dut->io_axi4bkin_7_TREADY};
  const std::array<uint8_t*,bknumbus> tvalidarray = {&(dut->io_axi4bkin_0_TVALID),&(dut->io_axi4bkin_1_TVALID),&(dut->io_axi4bkin_2_TVALID),&(dut->io_axi4bkin_3_TVALID),&(dut->io_axi4bkin_4_TVALID),&(dut->io_axi4bkin_5_TVALID),&(dut->io_axi4bkin_6_TVALID),&(dut->io_axi4bkin_7_TVALID)};
  std::array<uint32_t*,bknumbus> bkbusarray = {dut->io_axi4bkin_0_TDATA,dut->io_axi4bkin_1_TDATA,dut->io_axi4bkin_2_TDATA,dut->io_axi4bkin_3_TDATA,dut->io_axi4bkin_4_TDATA,dut->io_axi4bkin_5_TDATA,dut->io_axi4bkin_6_TDATA,dut->io_axi4bkin_7_TDATA};

  for(int i = 0; i < bknumbus; i++){
    if(buscycle[i]<numcycle*(TFHEpp::lvl1param::k+1)*TFHEpp::lvl1param::l*TFHEpp::lvl0param::n){
      *(tvalidarray[i]) = 1;
      for(int j = 0; j < nttwordsinbus; j++){
        bkbusarray[i][2*j] = static_cast<uint32_t>(bknttaligned[i][0][0][buscycle[i]][j]);
        bkbusarray[i][2*j+1] = static_cast<uint32_t>(bknttaligned[i][0][0][buscycle[i]][j]>>32);
      }
      if(treadyarray[i]!=0) buscycle[i]++;
    }else{
      *(tvalidarray[i]) = 0;
    }
  }
  dut->eval();
  if(tfp) tfp->dump(1000*time_counter);
  time_counter++;
  dut->clock = !dut->clock;
  dut->eval();
  if(tfp) tfp->dump(1000*time_counter);
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

  for(int k =0; k < 2; k++) for(int bus = 0; bus < bknumbus/2; bus++) for(int i = 0; i < TFHEpp::lvl0param::n; i++) for(int l = 0; l < TFHEpp::lvl1param::l; l++) for(int kindex = 0; kindex <= TFHEpp::lvl1param::k; kindex++) for(int cycle = 0; cycle < numcycle; cycle++) for(int word = 0; word<nttwordsinbus; word++) bknttaligned[k*bknumbus/2+bus][i][(TFHEpp::lvl1param::k+1)*l+kindex][cycle][word] = (*bkntt)[i][kindex*TFHEpp::lvl1param::l+l][k][cycle*bknumbus/2*nttwordsinbus+bus*nttwordsinbus+word].value;

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

  // Combined input arrays for batch processing (same TLWE for all batches)
  constexpr uint totalInputLen = numbatch * alignedlenlvl1;
  constexpr uint totalInputBusWords = totalInputLen / buswords;
  std::array<TFHEpp::lvl1param::T, totalInputLen> combinedA = {};
  std::array<TFHEpp::lvl1param::T, totalInputLen> combinedB = {};
  std::array<TFHEpp::lvl1param::T, totalInputLen> combinedLvl1 = {};
  for(uint b = 0; b < numbatch; b++){
    for(int l = 0; l <= TFHEpp::lvl1param::n; l++){
      combinedA[b*alignedlenlvl1+l] = tlwea[l];
      combinedB[b*alignedlenlvl1+l] = tlweb[l];
      combinedLvl1[b*alignedlenlvl1+l] = tlwelvl1[l];
    }
  }

  Verilated::commandArgs(argc, argv);
  VHomGateWrap *dut = new VHomGateWrap();

  Verilated::traceEverOn(false);
  VerilatedFstC* tfp = nullptr;

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
  //AND
  dut->io_scaleaindex = 0;
  dut->io_scalebindex = 0;
  dut->io_offsetindex = 2;
  dut->io_axi4ina_TVALID = 1;
  dut->io_axi4inb_TVALID = 1;

  //Initialize Input Buffer (send numbatch TLWEs with proper TREADY handling)
  for(uint i = 0; i < totalInputBusWords; i++){
    for(int j = 0; j < buswords; j++){
      dut->io_axi4ina_TDATA[j] = combinedA[buswords*i+j];
      dut->io_axi4inb_TDATA[j] = combinedB[buswords*i+j];
    }
    int inputwatchdog = 0;
    do {
      clock(dut, tfp);
      inputwatchdog++;
      if(inputwatchdog > 10000){
        std::cout<<"Input TREADY timeout at word "<<i<<" iks_enable="<<(int)dut->io_debug_iksenable<<std::endl;
        dut->final();
        exit(1);
      }
    } while(dut->io_axi4ina_TREADY == 0);
  }
  std::cout<<"Input feeding done ("<<totalInputBusWords<<" words)"<<std::endl;
  dut->io_axi4ina_TVALID = 0;
  dut->io_axi4inb_TVALID = 0;

  std::cout<<"IKS"<<std::endl;

  // Wait for IKS to be ready (loading phase to complete)
  {
    int loadwatchdog = 0;
    while(dut->io_debug_iksenable == 0){
      clock(dut, tfp);
      loadwatchdog++;
      if(loadwatchdog % 10000 == 0){
        std::cout<<"IKS loading wait: "<<loadwatchdog<<" cycles"
                 <<" loadstate="<<(int)dut->io_debug_loadstate
#if numbatch > 1
                 <<" batchcnt="<<(int)dut->io_debug_batchloadcnt
#endif
                 <<" tlwevalidout="<<(int)dut->io_debug_tlwevalidout
                 <<std::endl;
      }
      if(loadwatchdog > 200000){
        std::cout<<"IKS loading timeout after "<<loadwatchdog<<" cycles"<<std::endl;
        dut->final();
        exit(1);
      }
    }
    std::cout<<"IKS enabled after "<<loadwatchdog<<" cycles"<<std::endl;
  }

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

  // IKS output buffer for numbatch TLWElvl0
  std::array<TFHEpp::lvl0param::T, numbatch * alignedlenlvl0> reslvl0 = {};
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
          }
          if(dut->io_ikskvalid && dut->io_ikskready){
            for(int n = 0; n < buswords; n++){
              reslvl0[buswords*outindex+n] = dut->io_ikskout[n];
            }
            outindex++;
          }
          clock(dut, tfp);
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

  int watchdog = 0;
  while(dut->io_ikskvalid==0){
    clock(dut, tfp);
    if(watchdog>100){
      std::cout<<"IKS output watchdog timeout"<<std::endl;
      dut->final();
      if(tfp) tfp->close();
      exit(1);
    }
    watchdog++;
  }

  while(dut->io_ikskvalid==1){
    if(dut->io_ikskready){
      for(int n = 0; n < buswords; n++){
        reslvl0[buswords*outindex+n] = dut->io_ikskout[n];
      }
      outindex++;
    }
    clock(dut, tfp);
  }

  std::cout<<"IKS output words: "<<outindex<<std::endl;

  // Verify IKS output for each batch
  for(uint batch = 0; batch < numbatch; batch++){
    for(int j = 0; j<=TFHEpp::lvl0param::n;j++){
      uint32_t trueout = tlwelvl0[j];
      uint32_t circout = reslvl0[batch * alignedlenlvl0 + j];
      if(trueout != circout){
        std::cout<<"IKS Error batch "<<batch<<": "<<trueout<<":"<<circout<<std::endl;
        std::cout<<j<<std::endl;
        dut->final();
        if(tfp) tfp->close();
        exit(1);
      }
    }
  }

  clock(dut, tfp);

  std::cout<<"BR"<<std::endl;

  brres[0] = {};
  TFHEpp::PolynomialMulByXai<TFHEpp::lvl1param>(brres[1],TFHEpp::μpolygen<TFHEpp::lvl1param,TFHEpp::lvl1param::μ>(),b̄);

  count = 0;

  // Skip initial TV init debug burst
  while(dut->io_debugvalid==0) clock(dut, tfp);
  while(dut->io_debugvalid==1) clock(dut, tfp);

constexpr typename TFHEpp::lvl0param::T roundoffset = 1ULL << (std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 2 - TFHEpp::lvl1param::nbit);
for (int i = 0; i < TFHEpp::lvl0param::n; i++) {
  const uint32_t ā = (tlwelvl0[i]+roundoffset)>>(std::numeric_limits<typename TFHEpp::lvl0param::T>::digits - 1 - TFHEpp::lvl1param::nbit);

  // Compute PMBX from pre-CMUX brres (same for all batches with identical input)
  TFHEpp::TRLWE<TFHEpp::lvl1param> pmbx;
  TFHEpp::PolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(pmbx[0],brres[0],ā);
  TFHEpp::PolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(pmbx[1],brres[1],ā);

  // Software CMUX update (compute post-CMUX brres)
  TFHEpp::CMUXNTTwithPolynomialMulByXaiMinusOne<TFHEpp::lvl1param>(
      brres, (*bkntt)[i], ā);

  // Check debug output for each batch
  for(uint batch = 0; batch < numbatch; batch++){
    // Wait for PMBX debug valid
    watchdog = 0;
    while(dut->io_debugvalid==0){
      clock(dut, tfp);
      watchdog++;
      if(watchdog>1000){
        std::cout<<"PMBX watchdog timeout dim="<<i<<" batch="<<batch<<std::endl;
        dut->final();
        if(tfp) tfp->close();
        exit(1);
      }
    }
    // Check PMBX output (same for all batches)
    for(int l = 0; l < TFHEpp::lvl1param::k+1; l++)
    for(int ii = 0; ii < radix; ii++){
      for(int j = 0; j < radix; j++) {
        if(dut->io_debugout[j]!=pmbx[l][radix*j+ii]){
          std::cout<<"PMBXERROR:"<<l<<":"<<ii<<":"<<j<<" batch="<<batch<<std::endl;
          std::cout<<dut->io_debugout[j]<<":"<<pmbx[l][radix*j+ii]<<std::endl;
          dut->final();
          if(tfp) tfp->close();
          exit(1);
        }
      }
      clock(dut, tfp);
    }

    // Wait for feedback debug valid (a[0])
    watchdog = 0;
    while(dut->io_debugvalid==0){
      clock(dut, tfp);
      watchdog++;
      if(watchdog>1000){
        std::cout<<"Feedback a[0] watchdog timeout dim="<<i<<" batch="<<batch<<std::endl;
        dut->final();
        if(tfp) tfp->close();
        exit(1);
      }
    }
    // Check feedback a[0] (against post-CMUX brres)
    for(int cycle = 0; cycle<numcycle;cycle++){
      for(int m = 0; m<radix;m++)
      if(dut->io_debugout[m]!=brres[0][m*radix+cycle]){
        std::cout<<i<<":0:"<<cycle<<":"<<m<<" batch="<<batch<<std::endl;
        std::cout<<"ERROR:"<<brres[0][m*radix+cycle]<<":"<<dut->io_debugout[m]<<std::endl;
        dut->final();
        if(tfp) tfp->close();
        exit(1);
      }
      clock(dut, tfp);
    }

    // Wait for feedback debug valid (a[1])
    watchdog = 0;
    while(dut->io_debugvalid==0){
      clock(dut, tfp);
      watchdog++;
      if(watchdog>1000){
        std::cout<<"Feedback a[1] watchdog timeout dim="<<i<<" batch="<<batch<<std::endl;
        dut->final();
        if(tfp) tfp->close();
        exit(1);
      }
    }
    // Check feedback a[1] (against post-CMUX brres)
    for(int cycle = 0; cycle<numcycle;cycle++){
      for(int m = 0; m<radix;m++)
      if(dut->io_debugout[m]!=brres[1][m*radix+cycle]){
        std::cout<<i<<":1:"<<cycle<<":"<<m<<" batch="<<batch<<std::endl;
        std::cout<<brres[1][m*radix+cycle]<<":"<<dut->io_debugout[m]<<std::endl;
        dut->final();
        if(tfp) tfp->close();
        exit(1);
      }
      clock(dut, tfp);
    }
  }
  std::cout<<i<<std::endl;
}
  dut->io_ap_start = 0;

  // Capture output for each batch
  watchdog = 0;
  dut->io_axi4out_TREADY=1;
  while(dut->io_axi4out_TVALID==0){
    clock(dut, tfp);
    watchdog++;
    if(watchdog>1000){
      std::cout<<"Output watchdog timeout"<<std::endl;
      dut->final();
      if(tfp) tfp->close();
      exit(1);
    }
  }

  TFHEpp::SampleExtractIndex<TFHEpp::lvl1param>(res,brres,0);

  constexpr uint outputElements = TFHEpp::lvl1param::k*TFHEpp::lvl1param::n + 1;
  std::array<TFHEpp::lvl1param::T, numbatch * outputElements> resout = {};
  for(uint batch = 0; batch < numbatch; batch++){
    for(uint i = 0; i < outputElements; i++){
      while(dut->io_axi4out_TVALID==0) clock(dut, tfp);
      resout[batch * outputElements + i] = dut->io_axi4out_TDATA;
      clock(dut, tfp);
    }
  }

  // Verify output for each batch
  for(uint batch = 0; batch < numbatch; batch++){
    for(uint j = 0; j < outputElements; j++){
      uint32_t trueout = res[j];
      uint32_t circout = resout[batch * outputElements + j];
      if(trueout != circout){
        std::cout<<"Output Error batch "<<batch<<" index "<<j<<std::endl;
        std::cout<<"Error: "<<trueout<<":"<<circout<<std::endl;
        dut->final();
        if(tfp) tfp->close();
        exit(1);
      }
    }
  }
  for(int i = 0; i < bknumbus; i++) buscycle[i] = 0;
  }

  dut->final();
  if(tfp) tfp->close();

  std::cout<<"PASS"<<std::endl;
}
