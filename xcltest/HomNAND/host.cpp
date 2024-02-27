#include <xcl2.hpp>
#include <tfhe++.hpp>

#define PC_NAME(n) n | XCL_MEM_TOPOLOGY
constexpr std::array <int, 32> pc = {
    PC_NAME(0),  PC_NAME(1),  PC_NAME(2),  PC_NAME(3),  PC_NAME(4),  PC_NAME(5),  PC_NAME(6),  PC_NAME(7),
    PC_NAME(8),  PC_NAME(9),  PC_NAME(10), PC_NAME(11), PC_NAME(12), PC_NAME(13), PC_NAME(14), PC_NAME(15),
    PC_NAME(16), PC_NAME(17), PC_NAME(18), PC_NAME(19), PC_NAME(20), PC_NAME(21), PC_NAME(22), PC_NAME(23),
    PC_NAME(24), PC_NAME(25), PC_NAME(26), PC_NAME(27), PC_NAME(28), PC_NAME(29), PC_NAME(30), PC_NAME(31)};

int main(int argc, char* argv[]) {
	if (argc != 2) {
        printf("Usage: %s <XCLBIN> \n", argv[0]);
        return -1;
    }
    cl_int err;
    cl::Context context;
    cl::CommandQueue q;
    cl::Kernel kernel_bootstrapping;
    std::string binaryFile = argv[1];

	// The get_xil_devices will return vector of Xilinx Devices
    auto devices = xcl::get_xil_devices();

    // read_binary_file() command will find the OpenCL binary file created using
    // the
    // V++ compiler load into OpenCL Binary and return pointer to file buffer.
    auto fileBuf = xcl::read_binary_file(binaryFile);

    cl::Program::Binaries bins{{fileBuf.data(), fileBuf.size()}};
    bool valid_device = false;
    for (unsigned int i = 0; i < devices.size(); i++) {
        auto device = devices[i];
        // Creating Context and Command Queue for selected Device
        OCL_CHECK(err, context = cl::Context(device, nullptr, nullptr, nullptr, &err));
        OCL_CHECK(err, q = cl::CommandQueue(context, device, CL_QUEUE_PROFILING_ENABLE, &err));

        std::cout << "Trying to program device[" << i << "]: " << device.getInfo<CL_DEVICE_NAME>() << std::endl;
        cl::Program program(context, {device}, bins, nullptr, &err);
        if (err != CL_SUCCESS) {
            std::cout << "Failed to program device[" << i << "] with xclbin file!\n";
        } else {
            std::cout << "Device[" << i << "]: program successful!\n";
            OCL_CHECK(err, kernel_bootstrapping = cl::Kernel(program, "HomNAND", &err));
            valid_device = true;
            break; // we break because we found a valid device
        }
    }
    if (!valid_device) {
        std::cout << "Failed to program any device found, exit!\n";
        exit(EXIT_FAILURE);
    }

	// std::cout << "Press Enter to Continue"<<std::endl;
	// std::cin.ignore();

    //generatros
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
	

	//allgned to distribute to module
	constexpr uint buswidthlb = 9;
	constexpr uint buswords = 1U<<(buswidthlb-6);
	constexpr uint iksknumbus = 10;
	constexpr uint totaliksknumbus = 40;
	constexpr uint bknumbus = 8;
	constexpr uint cyclebit = 5;
	constexpr uint numcycle = 1<<cyclebit;
	constexpr uint wordsinbus = (1U<<buswidthlb)/std::numeric_limits<typename TFHEpp::lvl0param::T>::digits;

 	// constexpr uint alignedlenlvl0 = (((std::numeric_limits<TFHEpp::lvl0param::T>::digits*(TFHEpp::lvl0param::n+1)>>buswidthlb)+1)<<buswidthlb)/std::numeric_limits<TFHEpp::lvl0param::T>::digits;
  	alignas(4096) std::array<std::array<std::array<std::array<std::array<std::array<typename TFHEpp::lvl0param::T, wordsinbus>, totaliksknumbus/iksknumbus>, (1 << TFHEpp::lvl10param::basebit) - 1>, TFHEpp::lvl10param::t>,TFHEpp::lvl1param::n>,iksknumbus> ikskaligned = {},ikskdebug = {};
  	for(int i = 0; i<TFHEpp::lvl1param::n; i++) for(int j = 0; j < TFHEpp::lvl10param::t; j++) for(int k = 0; k< (1 << TFHEpp::lvl10param::basebit) - 1; k++) for(int l = 0; l < wordsinbus; l++) for(int m = 0; m < iksknumbus; m++) for(int n = 0; n < totaliksknumbus/iksknumbus; n++) ikskaligned[m][i][j][k][n][l] = (*iksk)[i][j][k][n*iksknumbus*wordsinbus+m*wordsinbus+l];
	for(int m = 0; m <iksknumbus; m++) std::cout<<"IKSK bus "<<m<<":"<<std::hex<<ikskaligned[m][0][0][0][0][0]<<std::endl;

	alignas(4096) std::array<std::array<std::array<std::array<std::array<uint64_t,buswords>,numcycle>,2*TFHEpp::lvl1param::l>,TFHEpp::lvl0param::n>,bknumbus> bknttaligned = {};
	for(int k =0; k < 2; k++) for(int bus = 0; bus < bknumbus/2; bus++) for(int i = 0; i < TFHEpp::lvl0param::n; i++) for(int l = 0; l < 2*TFHEpp::lvl1param::l; l++) for(int cycle = 0; cycle < numcycle; cycle++) for(int word = 0; word<buswords; word++) bknttaligned[k*bknumbus/2+bus][i][l][cycle][word] = (*bkntt)[i][l][k][cycle*bknumbus/2*buswords+bus*buswords+word].value;

	double  kernel_time_in_sec;
	{
		// For Allocating Buffer to specific Global Memory PC, user has to use
		// cl_mem_ext_ptr_t
		// and provide the PCs
		cl_mem_ext_ptr_t inaBuf, inbBuf, resBuf;
		std::array<cl_mem_ext_ptr_t, iksknumbus> ikskBufs;
		std::array<cl_mem_ext_ptr_t, bknumbus> bknttBufs;

		resBuf.obj = kernelres.data();
		resBuf.param = 0;
		resBuf.flags = PC_NAME(0);

		inaBuf.obj = tlwea.data();
		inaBuf.param = 0;
		inaBuf.flags = PC_NAME(1);

		inbBuf.obj = tlweb.data();
		inbBuf.param = 0;
		inbBuf.flags = PC_NAME(2);

		for(int i = 0; i < iksknumbus; i++){
			ikskBufs[i].obj = ikskaligned[i].data();
			ikskBufs[i].param = 0;
			ikskBufs[i].flags = pc[i+3];
		}

		for(int i = 0; i < bknumbus; i++){
			bknttBufs[i].obj = bknttaligned[i].data();
			bknttBufs[i].param = 0;
			bknttBufs[i].flags = pc[20+i];
		}

		// These commands will allocate memory on the FPGA. The cl::Buffer objects can
		// be used to reference the memory locations on the device.
		// Creating Buffers
		OCL_CHECK(err, cl::Buffer buffer_ina(context, CL_MEM_READ_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
												sizeof(tlwea), &inaBuf, &err));
		OCL_CHECK(err, cl::Buffer buffer_inb(context, CL_MEM_READ_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
												sizeof(tlweb), &inbBuf, &err));
		OCL_CHECK(err, cl::Buffer buffer_res(context, CL_MEM_WRITE_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
												sizeof(kernelres), &resBuf, &err));
		std::array<std::shared_ptr<cl::Buffer>, iksknumbus> buffer_iksks;
		for(int i = 0; i < iksknumbus; i++){
			buffer_iksks[i] = std::make_shared<cl::Buffer>(context, CL_MEM_READ_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
													sizeof(ikskaligned[i]), &ikskBufs[i], &err);
		}
		std::array<std::shared_ptr<cl::Buffer>, bknumbus> buffer_bkntts;
		for(int i = 0; i < bknumbus; i++){
			buffer_bkntts[i] = std::make_shared<cl::Buffer>(context, CL_MEM_READ_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
													sizeof(bknttaligned[i]), &bknttBufs[i], &err);
		}

		// Setting the kernel Arguments
		OCL_CHECK(err, err = (kernel_bootstrapping).setArg(0, buffer_res));
		OCL_CHECK(err, err = (kernel_bootstrapping).setArg(1, buffer_ina));
		OCL_CHECK(err, err = (kernel_bootstrapping).setArg(2, buffer_inb));
		for(int i = 0; i < iksknumbus; i++) OCL_CHECK(err, err = (kernel_bootstrapping).setArg(i+3, *(buffer_iksks[i])));
		for(int i = 0; i < bknumbus; i++) OCL_CHECK(err, err = (kernel_bootstrapping).setArg(i+3+iksknumbus, *(buffer_bkntts[i])));

		// Copy input data to Device Global Memory
        std::cout<<"Copy"<<std::endl;
		std::vector<cl::Memory> inputs(iksknumbus+bknumbus);
		for(int i = 0; i < iksknumbus; i++) inputs[i] = *(buffer_iksks[i]);
		for(int i = 0; i < bknumbus; i++) inputs[i+iksknumbus] = *(buffer_bkntts[i]);
		OCL_CHECK(err, err = q.enqueueMigrateMemObjects(inputs, 0 /* 0 means from host*/));
		q.finish();

		std::chrono::duration<double> kernel_time(0);

		OCL_CHECK(err, err = q.enqueueMigrateMemObjects({buffer_ina,buffer_inb}, 0 /* 0 means from host*/));
		q.finish();

        std::cout<<"START"<<std::endl;
		auto kernel_start = std::chrono::high_resolution_clock::now();
		OCL_CHECK(err, err = q.enqueueTask(kernel_bootstrapping));
		// Copy Result from Device Global Memory to Host Local Memory
		q.finish();
		auto kernel_end = std::chrono::high_resolution_clock::now();
        std::cout<<"END"<<std::endl;

		OCL_CHECK(err, err = q.enqueueMigrateMemObjects({buffer_res}, CL_MIGRATE_MEM_OBJECT_HOST));
		q.finish();

		kernel_time = std::chrono::duration<double>(kernel_end - kernel_start);

		kernel_time_in_sec = kernel_time.count();
	}
	for(int i = 0; i <= TFHEpp::lvl1param::n; i++){
		if(kernelres[i] != res[i]){
			std::cout<<"ERROR: "<<i<<" : "<<kernelres[i]<<" : "<<res[i]<<std::endl;
			exit(1);
		}
	}
	std::cout<<"PASS"<<std::endl;
	std::cout<<kernel_time_in_sec<<std::endl;
}