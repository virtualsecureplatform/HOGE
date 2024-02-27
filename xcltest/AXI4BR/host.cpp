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
    cl::Kernel kernel_br;
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
            OCL_CHECK(err, kernel_br = cl::Kernel(program, "AXI4BRTop", &err));
            valid_device = true;
            break; // we break because we found a valid device
        }
    }
    if (!valid_device) {
        std::cout << "Failed to program any device found, exit!\n";
        exit(EXIT_FAILURE);
    }

      //generatros
    std::random_device seed_gen;
    std::default_random_engine engine(seed_gen());
    std::uniform_int_distribution<uint32_t> binary(0, 1);

	  //Initialize TFHEpp objects
	TFHEpp::SecretKey *sk = new TFHEpp::SecretKey();
	TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param> *bkntt = new TFHEpp::BootstrappingKeyNTT<TFHEpp::lvl01param>();
	TFHEpp::bknttgen<TFHEpp::lvl01param>(*bkntt,*sk);

	bool p = (binary(engine) > 0);
	alignas(4096) TFHEpp::TLWE<TFHEpp::lvl0param> tlwe = TFHEpp::tlweSymEncrypt<TFHEpp::lvl0param>(p,TFHEpp::lvl0param::α,sk->key.lvl0);

	alignas(4096) TFHEpp::TRLWE<TFHEpp::lvl1param> res = {},kernelres = {};
	TFHEpp::BlindRotate<TFHEpp::lvl01param>(res,tlwe,*bkntt,TFHEpp::μpolygen<TFHEpp::lvl1param, TFHEpp::lvl1param::μ>());

	//allgned to distribute to module
	constexpr uint buswidthlb = 9;
	constexpr uint buswords = 1U<<(buswidthlb-6);
	constexpr uint numbus = 8;
	constexpr uint cyclebit = 5;
	constexpr uint numcycle = 1<<cyclebit;

	alignas(4096) std::array<std::array<std::array<std::array<std::array<uint64_t,buswords>,numcycle>,2*TFHEpp::lvl1param::l>,TFHEpp::lvl0param::n>,numbus> bknttaligned;
	for(int k =0; k < 2; k++) for(int bus = 0; bus < numbus/2; bus++) for(int i = 0; i < TFHEpp::lvl0param::n; i++) for(int l = 0; l < 2*TFHEpp::lvl1param::l; l++) for(int cycle = 0; cycle < numcycle; cycle++) for(int word = 0; word<buswords; word++) bknttaligned[k*numbus/2+bus][i][l][cycle][word] = (*bkntt)[i][l][k][cycle*numbus/2*buswords+bus*buswords+word].value;

	double  kernel_time_in_sec;
	{
		// For Allocating Buffer to specific Global Memory PC, user has to use
		// cl_mem_ext_ptr_t
		// and provide the PCs
		cl_mem_ext_ptr_t inBuf, resBuf;
		std::array<cl_mem_ext_ptr_t, numbus> bknttBufs;

		resBuf.obj = kernelres.data();
		resBuf.param = 0;
		resBuf.flags = PC_NAME(0);

		inBuf.obj = tlwe.data();
		inBuf.param = 0;
		inBuf.flags = PC_NAME(0);

		for(int i = 0; i < numbus; i++){
			bknttBufs[i].obj = bknttaligned[i].data();
			bknttBufs[i].param = 0;
			bknttBufs[i].flags = pc[i+1];
		}

		// These commands will allocate memory on the FPGA. The cl::Buffer objects can
		// be used to reference the memory locations on the device.
		// Creating Buffers
		OCL_CHECK(err, cl::Buffer buffer_in(context, CL_MEM_READ_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
												sizeof(tlwe), &inBuf, &err));
		OCL_CHECK(err, cl::Buffer buffer_res(context, CL_MEM_WRITE_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
												sizeof(kernelres), &resBuf, &err));
		std::array<std::shared_ptr<cl::Buffer>, numbus> buffer_bkntts;
		for(int i = 0; i < numbus; i++){
			buffer_bkntts[i] = std::make_shared<cl::Buffer>(context, CL_MEM_READ_ONLY | CL_MEM_EXT_PTR_XILINX | CL_MEM_USE_HOST_PTR,
													sizeof(bknttaligned[i]), &bknttBufs[i], &err);
		}

		// Setting the kernel Arguments
		OCL_CHECK(err, err = (kernel_br).setArg(0, buffer_res));
		OCL_CHECK(err, err = (kernel_br).setArg(1, buffer_in));
		for(int i = 0; i < numbus; i++) OCL_CHECK(err, err = (kernel_br).setArg(i+2, *(buffer_bkntts[i])));

		// Copy input data to Device Global Memory
        std::cout<<"Copy"<<std::endl;
		std::vector<cl::Memory> inputs(numbus+1);
		inputs[0] = buffer_in;
		for(int i = 0; i < numbus; i++) inputs[i+1] = *(buffer_bkntts[i]);
		OCL_CHECK(err, err = q.enqueueMigrateMemObjects(inputs, 0 /* 0 means from host*/));
		q.finish();

		std::chrono::duration<double> kernel_time(0);

        std::cout<<"START"<<std::endl;
		auto kernel_start = std::chrono::high_resolution_clock::now();
		OCL_CHECK(err, err = q.enqueueTask(kernel_br));
		// Copy Result from Device Global Memory to Host Local Memory
		OCL_CHECK(err, err = q.enqueueMigrateMemObjects({buffer_res}, CL_MIGRATE_MEM_OBJECT_HOST));
		q.finish();
		auto kernel_end = std::chrono::high_resolution_clock::now();
        std::cout<<"END"<<std::endl;

		kernel_time = std::chrono::duration<double>(kernel_end - kernel_start);

		kernel_time_in_sec = kernel_time.count();
	}
	for(int k = 0; k < 2; k++)
		for(int i = 0; i < TFHEpp::lvl1param::n; i++){
			if(kernelres[k][i] != res[k][i]){
				std::cout<<"ERROR: "<<k<<":"<<i<<" : "<<kernelres[k][i]<<" : "<<res[k][i]<<std::endl;
				exit(1);
			}
		}
	std::cout<<"PASS"<<std::endl;
	std::cout<<kernel_time_in_sec<<std::endl;
}