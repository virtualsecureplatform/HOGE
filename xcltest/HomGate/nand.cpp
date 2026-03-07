#include <tfhe++.hpp>
#include <unistd.h>
#include <chrono>
#include <iostream>
#include <random>

// FPGA operations via separate compilation unit (avoids XRT/xcl2 header conflicts)
extern "C" {
  int fpga_init(const char* xclbin_path);
  int fpga_alloc_buffer(int arg_index, size_t size);
  int fpga_write_buffer(int bo_idx, const void* src, size_t size);
  int fpga_read_buffer(int bo_idx, void* dst, size_t size);
  int fpga_sync_to_device(int bo_idx);
  int fpga_sync_from_device(int bo_idx);
  int fpga_start_kernel(uint16_t scalea, uint16_t scaleb, uint16_t offset,
                        const int* bo_indices, int n_bufs);
  int fpga_wait_kernel(int run_idx);
  int fpga_read_register(uint32_t offset, uint32_t* value);
  void fpga_cleanup();
}

int main(int argc, char* argv[]) {
	constexpr uint16_t scaleaindex = 2;
	constexpr uint16_t scalebindex = 2;
	constexpr uint16_t offsetindex = 0;

	if (argc != 2) {
        printf("Usage: %s <XCLBIN> \n", argv[0]);
        return -1;
    }
    std::string binaryFile = argv[1];

    // Initialize FPGA via XRT native API
    if (fpga_init(binaryFile.c_str()) != 0) {
        std::cout << "Failed to initialize FPGA, exit!" << std::endl;
        return -1;
    }

    std::cout << "FPGA programmed." << std::endl;

    //generators
    std::random_device seed_gen;
    std::default_random_engine engine(seed_gen());
    std::uniform_int_distribution<uint32_t> binary(0, 1);

	//Initialize TFHEpp objects
	TFHEpp::SecretKey *sk = new TFHEpp::SecretKey();
	TFHEpp::KeySwitchingKey<TFHEpp::lvl10param> *iksk = new TFHEpp::KeySwitchingKey<TFHEpp::lvl10param>();
	TFHEpp::ikskgen<TFHEpp::lvl10param>(*iksk,*sk);

	const bool pa = (binary(engine) > 0);
	const bool pb = (binary(engine) > 0);
	alignas(4096) TFHEpp::TLWE<TFHEpp::lvl1param> tlwea = TFHEpp::tlweSymEncrypt<TFHEpp::lvl1param>(pa,TFHEpp::lvl1param::α,sk->key.lvl1);
	alignas(4096) TFHEpp::TLWE<TFHEpp::lvl1param> tlweb = TFHEpp::tlweSymEncrypt<TFHEpp::lvl1param>(pb,TFHEpp::lvl1param::α,sk->key.lvl1);
	TFHEpp::TLWE<TFHEpp::lvl1param> tlweadded;
	for(int l = 0; l<= TFHEpp::lvl1param::n; l++) tlweadded[l] = - tlwea[l] - tlweb[l];
	tlweadded[TFHEpp::lvl1param::n] += TFHEpp::lvl1param::μ;


	TFHEpp::TLWE<TFHEpp::lvl0param> tlwelvl0;
	TFHEpp::IdentityKeySwitch<TFHEpp::lvl10param>(tlwelvl0,tlweadded,*iksk);

	TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param> *bkntt = new TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param>();
	TFHEpp::bknttgen<TFHEpp::lvl01param>(*bkntt,*sk);


	TFHEpp::TRLWE<TFHEpp::lvl1param> brres = {};
	TFHEpp::BlindRotate<TFHEpp::lvl01param>(brres,tlwelvl0,*bkntt,TFHEpp::μpolygen<TFHEpp::lvl1param, TFHEpp::lvl1param::μ>());

	alignas(4096) TFHEpp::TLWE<TFHEpp::lvl1param> res = {},kernelres = {};
  	TFHEpp::SampleExtractIndex<TFHEpp::lvl1param>(res,brres,0);


	//aligned to distribute to module
	constexpr uint buswidthlb = 9;
	constexpr uint buswords = 1U<<(buswidthlb-6);
	constexpr uint iksknumbus = 10;
	constexpr uint wordsinbus = (1U<<buswidthlb)/std::numeric_limits<typename TFHEpp::lvl0param::T>::digits;
	constexpr uint elementsPerSegment = iksknumbus * wordsinbus;
	constexpr uint iksknumsegments = (TFHEpp::lvl0param::n + elementsPerSegment) / elementsPerSegment;
	constexpr uint totaliksknumbus = iksknumsegments * iksknumbus;
	constexpr uint bknumbus = 8;
	constexpr uint cyclebit = 5;
	constexpr uint numcycle = 1<<cyclebit;

  	alignas(4096) std::array<std::array<std::array<std::array<std::array<std::array<typename TFHEpp::lvl0param::T, wordsinbus>, totaliksknumbus/iksknumbus>, (1 << TFHEpp::lvl10param::basebit) - 1>, TFHEpp::lvl10param::t>,TFHEpp::lvl1param::n>,iksknumbus> ikskaligned = {},ikskdebug = {};
  	for(int i = 0; i<TFHEpp::lvl1param::n; i++) for(int j = 0; j < TFHEpp::lvl10param::t; j++) for(int k = 0; k< (1 << TFHEpp::lvl10param::basebit) - 1; k++) for(int l = 0; l < wordsinbus; l++) for(int m = 0; m < iksknumbus; m++) for(int n = 0; n < totaliksknumbus/iksknumbus; n++) ikskaligned[m][i][j][k][n][l] = (*iksk)[i][j][k][n*iksknumbus*wordsinbus+m*wordsinbus+l];
	for(int m = 0; m <iksknumbus; m++) std::cout<<"IKSK bus "<<m<<":"<<std::hex<<ikskaligned[m][0][0][0][0][0]<<std::endl;
	std::cout <<"IKSK head :"<< std::hex << ikskaligned[0][0][0][0][0][0]<<std::endl;
	std::cout << "IKSK tail :" << std::hex <<  ikskaligned[0][TFHEpp::lvl1param::n-1][TFHEpp::lvl10param::t-1][(1 << TFHEpp::lvl10param::basebit) - 2][totaliksknumbus/iksknumbus-1][0] <<std::endl;

	alignas(4096) std::array<std::array<std::array<std::array<std::array<uint64_t,buswords>,numcycle>,2*TFHEpp::lvl1param::l>,TFHEpp::lvl0param::n>,bknumbus> bknttaligned = {};
	for(int k =0; k < 2; k++) for(int bus = 0; bus < bknumbus/2; bus++) for(int i = 0; i < TFHEpp::lvl0param::n; i++) for(int l = 0; l < TFHEpp::lvl1param::l; l++) for(int kindex = 0; kindex <= TFHEpp::lvl1param::k; kindex++) for(int cycle = 0; cycle < numcycle; cycle++) for(int word = 0; word<buswords; word++) bknttaligned[k*bknumbus/2+bus][i][(TFHEpp::lvl1param::k+1)*l+kindex][cycle][word] = (*bkntt)[i][kindex*TFHEpp::lvl1param::l+l][k][cycle*bknumbus/2*buswords+bus*buswords+word].value;
	std::cout <<"BK head :"<<std::hex<<bknttaligned[bknumbus-1][0][0][0][0]<<std::endl;
	std::cout <<"BK tail :"<<std::hex<<bknttaligned[bknumbus-1][TFHEpp::lvl0param::n-1][2*TFHEpp::lvl1param::l-1][numcycle-1][0]<<std::endl;

	double kernel_time_in_sec;
	{
		// Allocate device buffers
		// arg 3: output (res), arg 4: ina, arg 5: inb, args 6-15: iksk[0-9], args 16-23: bk[0-7]
		constexpr int total_bufs = 1 + 2 + iksknumbus + bknumbus; // 21

		int bo_res = fpga_alloc_buffer(3, sizeof(kernelres));
		int bo_ina = fpga_alloc_buffer(4, sizeof(tlwea));
		int bo_inb = fpga_alloc_buffer(5, sizeof(tlweb));

		std::array<int, iksknumbus> bo_iksks;
		for(int i = 0; i < iksknumbus; i++)
			bo_iksks[i] = fpga_alloc_buffer(6 + i, sizeof(ikskaligned[i]));

		std::array<int, bknumbus> bo_bks;
		for(int i = 0; i < bknumbus; i++)
			bo_bks[i] = fpga_alloc_buffer(16 + i, sizeof(bknttaligned[i]));

		// Write key data to buffers and sync to device
		for(int i = 0; i < iksknumbus; i++) {
			fpga_write_buffer(bo_iksks[i], ikskaligned[i].data(), sizeof(ikskaligned[i]));
			fpga_sync_to_device(bo_iksks[i]);
		}
		for(int i = 0; i < bknumbus; i++) {
			fpga_write_buffer(bo_bks[i], bknttaligned[i].data(), sizeof(bknttaligned[i]));
			fpga_sync_to_device(bo_bks[i]);
		}
		std::cout<<"Copy"<<std::endl;

		// Build buffer index array for kernel args: res, ina, inb, iksk[0..9], bk[0..7]
		int bo_indices[total_bufs];
		bo_indices[0] = bo_res;
		bo_indices[1] = bo_ina;
		bo_indices[2] = bo_inb;
		for(int i = 0; i < iksknumbus; i++) bo_indices[3 + i] = bo_iksks[i];
		for(int i = 0; i < bknumbus; i++) bo_indices[3 + iksknumbus + i] = bo_bks[i];

		for(int test = 0; test < 10; test++){
			// Write input data and sync
			fpga_write_buffer(bo_ina, tlwea.data(), sizeof(tlwea));
			fpga_write_buffer(bo_inb, tlweb.data(), sizeof(tlweb));
			fpga_sync_to_device(bo_ina);
			fpga_sync_to_device(bo_inb);

			std::cout<<"START gate "<<test<<std::endl;
			std::cout.flush();

			auto kernel_start = std::chrono::high_resolution_clock::now();

			// Start kernel (non-blocking)
			int run_idx = fpga_start_kernel(scaleaindex, scalebindex, offsetindex,
			                                bo_indices, total_bufs);

			// Wait for kernel completion
			fpga_wait_kernel(run_idx);
			auto kernel_end = std::chrono::high_resolution_clock::now();
			std::cout<<"END gate "<<test<<std::endl;

			// Read result from device
			fpga_sync_from_device(bo_res);
			fpga_read_buffer(bo_res, kernelres.data(), sizeof(kernelres));

			kernel_time_in_sec = std::chrono::duration<double>(kernel_end - kernel_start).count();
			std::cout<<"Gate "<<test<<" kernel time: "<<kernel_time_in_sec*1000.0<<" ms"<<std::endl;

			for(int i = 0; i <= TFHEpp::lvl1param::n; i++){
				if(kernelres[i] != res[i]){
					std::cout<<"ERROR: "<<i<<" : "<<kernelres[i]<<" : "<<res[i]<<std::endl;
				}
			}
		}
	}
	std::cout<<"PASS"<<std::endl;
	fpga_cleanup();
}
