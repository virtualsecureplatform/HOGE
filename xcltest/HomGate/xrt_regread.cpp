// All XRT/FPGA operations in separate compilation unit
// Avoids xcl2.hpp / XRT header conflicts
#define INCLUDE_XRT_DETAIL_XCLBIN_H_
#include <xrt/xrt_device.h>
#include <xrt/xrt_kernel.h>
#include <xrt/xrt_bo.h>
#include <iostream>
#include <exception>
#include <vector>
#include <cstring>
#include <chrono>
#include <thread>

static xrt::device* s_dev = nullptr;
static xrt::kernel* s_kern = nullptr;

// Track buffer objects
struct bo_entry { xrt::bo* bo; };
static std::vector<bo_entry> s_bos;

extern "C" {

int fpga_init(const char* xclbin_path) {
    try {
        s_dev = new xrt::device(0);
        auto uuid = s_dev->load_xclbin(xclbin_path);
        s_kern = new xrt::kernel(*s_dev, uuid, "HomGate:{HomGate_1}",
                                 xrt::kernel::cu_access_mode::exclusive);
        std::cout << "FPGA initialized (XRT native)" << std::endl;
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_init FAILED: " << e.what() << std::endl;
        return -1;
    }
}

// Allocate a device buffer for a given kernel argument index
// Returns buffer index (>=0) or -1 on error
int fpga_alloc_buffer(int arg_index, size_t size) {
    try {
        auto grp = s_kern->group_id(arg_index);
        auto* bo = new xrt::bo(*s_dev, size, xrt::bo::flags::normal, grp);
        int idx = (int)s_bos.size();
        s_bos.push_back({bo});
        return idx;
    } catch (const std::exception& e) {
        std::cerr << "fpga_alloc_buffer FAILED (arg " << arg_index << "): " << e.what() << std::endl;
        return -1;
    }
}

// Write host data into a buffer object
int fpga_write_buffer(int bo_idx, const void* src, size_t size) {
    try {
        s_bos[bo_idx].bo->write(src, size, 0);
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_write_buffer FAILED: " << e.what() << std::endl;
        return -1;
    }
}

// Read device buffer into host memory
int fpga_read_buffer(int bo_idx, void* dst, size_t size) {
    try {
        s_bos[bo_idx].bo->read(dst, size, 0);
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_read_buffer FAILED: " << e.what() << std::endl;
        return -1;
    }
}

// Sync buffer to device
int fpga_sync_to_device(int bo_idx) {
    try {
        s_bos[bo_idx].bo->sync(XCL_BO_SYNC_BO_TO_DEVICE);
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_sync_to_device FAILED: " << e.what() << std::endl;
        return -1;
    }
}

// Sync buffer from device
int fpga_sync_from_device(int bo_idx) {
    try {
        s_bos[bo_idx].bo->sync(XCL_BO_SYNC_BO_FROM_DEVICE);
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_sync_from_device FAILED: " << e.what() << std::endl;
        return -1;
    }
}

// Run the kernel with given scalar args and buffer args
// scalea, scaleb, offset are the 3 scalar args (uint16_t)
// bo_indices: array of buffer object indices for args 3..3+n_bufs-1
int fpga_run_kernel(uint16_t scalea, uint16_t scaleb, uint16_t offset,
                    const int* bo_indices, int n_bufs) {
    try {
        auto run = xrt::run(*s_kern);
        run.set_arg(0, scalea);
        run.set_arg(1, scaleb);
        run.set_arg(2, offset);
        for (int i = 0; i < n_bufs; i++) {
            run.set_arg(3 + i, *(s_bos[bo_indices[i]].bo));
        }
        run.start();
        run.wait();
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_run_kernel FAILED: " << e.what() << std::endl;
        return -1;
    }
}

// Start kernel (non-blocking) - returns run handle index
static std::vector<xrt::run*> s_runs;

int fpga_start_kernel(uint16_t scalea, uint16_t scaleb, uint16_t offset,
                      const int* bo_indices, int n_bufs) {
    try {
        auto* run = new xrt::run(*s_kern);
        run->set_arg(0, scalea);
        run->set_arg(1, scaleb);
        run->set_arg(2, offset);
        for (int i = 0; i < n_bufs; i++) {
            run->set_arg(3 + i, *(s_bos[bo_indices[i]].bo));
        }
        run->start();
        int idx = (int)s_runs.size();
        s_runs.push_back(run);
        return idx;
    } catch (const std::exception& e) {
        std::cerr << "fpga_start_kernel FAILED: " << e.what() << std::endl;
        return -1;
    }
}

int fpga_wait_kernel(int run_idx) {
    try {
        auto t0 = std::chrono::steady_clock::now();
        const int poll_interval_ms = 5000;
        const int timeout_s = 120;
        while (true) {
            auto st = s_runs[run_idx]->wait(std::chrono::milliseconds(poll_interval_ms));
            auto elapsed = std::chrono::duration_cast<std::chrono::seconds>(
                std::chrono::steady_clock::now() - t0).count();
            // ert_cmd_state: 4=completed, 6=timeout, others=error/busy
            if (st == ERT_CMD_STATE_COMPLETED) {
                if (elapsed >= poll_interval_ms / 1000)
                    std::cout << "fpga_wait_kernel: done after " << elapsed << "s" << std::endl;
                break;
            }
            // Print AP control register and debug register diagnostics
            uint32_t ap_ctrl = 0xDEAD, dbg0 = 0xDEAD, dbg1 = 0xDEAD, dbg2 = 0xDEAD;
            if (s_kern) {
                try { ap_ctrl = s_kern->read_register(0x000); } catch (...) {}
                try { dbg0   = s_kern->read_register(0x200); } catch (...) {}
                try { dbg1   = s_kern->read_register(0x204); } catch (...) {}
                try { dbg2   = s_kern->read_register(0x208); } catch (...) {}
            }
            std::cout << "fpga_wait_kernel: " << elapsed << "s elapsed, ap_ctrl=0x"
                      << std::hex << ap_ctrl << std::dec
                      << " (ap_start=" << ((ap_ctrl>>0)&1)
                      << " ap_done="  << ((ap_ctrl>>1)&1)
                      << " ap_idle="  << ((ap_ctrl>>2)&1)
                      << " ap_ready=" << ((ap_ctrl>>3)&1) << ")"
                      << " iksout_beats=" << dbg0
                      << " axis01_beats=" << dbg1
                      << " misc=0x" << std::hex << dbg2 << std::dec
                      << std::endl;
            if (elapsed >= timeout_s) {
                std::cerr << "fpga_wait_kernel: TIMEOUT after " << elapsed << "s" << std::endl;
                return -1;
            }
        }
        delete s_runs[run_idx];
        s_runs[run_idx] = nullptr;
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_wait_kernel FAILED: " << e.what() << std::endl;
        return -1;
    }
}

int fpga_read_register(uint32_t offset, uint32_t* value) {
    if (!s_kern) return -1;
    try {
        *value = s_kern->read_register(offset);
        return 0;
    } catch (const std::exception& e) {
        std::cerr << "fpga_read_register FAILED: " << e.what() << std::endl;
        return -1;
    }
}

void fpga_cleanup() {
    for (auto& r : s_runs) delete r;
    s_runs.clear();
    for (auto& b : s_bos) delete b.bo;
    s_bos.clear();
    delete s_kern; s_kern = nullptr;
    delete s_dev;  s_dev  = nullptr;
}

} // extern "C"
