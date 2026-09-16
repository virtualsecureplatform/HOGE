// AWS F2 implementation of the C API used by xcltest/HomGate/nand.cpp.
// The AFI must already be loaded; this program never reprograms a slot.
#include <fpga_mgmt.h>
#include <fpga_pci.h>
#include <array>
#include <chrono>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>
#include <limits>

namespace {
constexpr uint64_t bank_size = uint64_t{1} << 29; // 512 MiB / HBM pseudo-channel
constexpr std::array<int, 21> banks{0,1,2,3,4,5,6,7,8,9,10,11,12,20,21,22,23,24,25,26,27};
struct Buffer { int arg; uint64_t address; std::vector<uint8_t> data; };
std::vector<Buffer> buffers;
pci_bar_handle_t control = PCI_BAR_HANDLE_INIT, memory = PCI_BAR_HANDLE_INIT;
bool initialized = false, mgmt_open = false, failed = false, running = false;
int generation = 0, active_run = -1;
void check(int rc) { if (rc) throw std::runtime_error("AWS SDK error " + std::to_string(rc)); }
void require(bool ok, const char* message) { if (!ok) throw std::runtime_error(message); }
template<class F> int guarded(F f) {
    try { return f(); }
    catch (const std::exception& e) { failed = true; std::cerr << "F2: " << e.what() << '\n'; return -1; }
}
void ready() { require(initialized && !failed, "backend unavailable or an earlier operation failed"); }
Buffer& buffer(int i) { ready(); require(i >= 0 && size_t(i) < buffers.size(), "invalid buffer handle"); return buffers[i]; }
void poke(uint32_t offset, uint32_t value) { check(fpga_pci_poke(control, offset, value)); }
uint32_t peek(uint32_t offset) { uint32_t v; check(fpga_pci_peek(control, offset, &v)); return v; }
int transfer(int i, bool to_device) {
    return guarded([&] {
        auto& b = buffer(i); require(!running, "cannot transfer while kernel is running");
        for (size_t off=0; off<b.data.size(); off+=4) {
            uint32_t word;
            if (to_device) { std::memcpy(&word, b.data.data()+off, 4); check(fpga_pci_poke(memory, b.address+off, word)); }
            else { check(fpga_pci_peek(memory, b.address+off, &word)); std::memcpy(b.data.data()+off, &word, 4); }
        }
        // Read-after-write fences posted PCIe writes before a later kernel start.
        if (to_device && !b.data.empty()) { uint32_t word; check(fpga_pci_peek(memory, b.address+b.data.size()-4, &word)); }
        return 0;
    });
}
}
extern "C" {
void fpga_cleanup() {
    if (memory != PCI_BAR_HANDLE_INIT) fpga_pci_detach(memory);
    if (control != PCI_BAR_HANDLE_INIT) fpga_pci_detach(control);
    memory = control = PCI_BAR_HANDLE_INIT;
    if (mgmt_open) fpga_mgmt_close();
    mgmt_open = initialized = running = failed = false;
    active_run = -1; buffers.clear();
}
int fpga_init(const char* target) {
    fpga_cleanup();
    int rc = guarded([&] {
        require(target && std::strncmp(target,"slot:",5)==0, "expected slot:N (load the cl_hoge AFI first)");
        const std::string n(target+5);
        require(!n.empty() && n.find_first_not_of("0123456789")==std::string::npos, "invalid slot number");
        const int slot=std::stoi(n);
        check(fpga_mgmt_init()); mgmt_open=true;
        fpga_mgmt_image_info info{};
        check(fpga_mgmt_describe_local_image(slot,&info,0));
        const auto& id=info.ids.afi_device_ids;
        require(info.status==FPGA_STATUS_LOADED && id.vendor_id==0x1d0f && id.device_id==0xf010 && id.svid==0x1d0f && id.ssid==0x4847,
                "slot does not contain the HOGE F2 image");
        const auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(30);
        for (;;) {
            uint16_t leds=0; check(fpga_mgmt_get_vLED_status(slot,&leds));
            if (leds & 1) break;
            require(std::chrono::steady_clock::now()<deadline,"HBM initialization timed out");
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
        check(fpga_pci_attach(slot,FPGA_APP_PF,APP_PF_BAR0,0,&control));
        check(fpga_pci_attach(slot,FPGA_APP_PF,APP_PF_BAR4,0,&memory));
        initialized=true;
        std::cout << "HOGE F2 initialized in slot " << slot << '\n'; return 0;
    });
    if (rc) fpga_cleanup();
    return rc;
}
int fpga_alloc_buffer(int arg, size_t size) {
    return guarded([&] {
        ready(); require(!running,"kernel is running");
        require(arg>=3 && arg<=23 && size && size<=bank_size && size%4==0,"invalid argument or buffer size");
        for (const auto& b:buffers) require(b.arg!=arg,"argument already allocated");
        buffers.push_back({arg, uint64_t(banks[arg-3])*bank_size, std::vector<uint8_t>(size)});
        return int(buffers.size()-1);
    });
}
int fpga_write_buffer(int i, const void* src, size_t size) {
    return guarded([&] { auto& b=buffer(i); require(!running && src && size<=b.data.size(),"invalid buffer write"); std::memcpy(b.data.data(),src,size); return 0; });
}
int fpga_read_buffer(int i, void* dst, size_t size) {
    return guarded([&] { auto& b=buffer(i); require(!running && dst && size<=b.data.size(),"invalid buffer read"); std::memcpy(dst,b.data.data(),size); return 0; });
}
int fpga_sync_to_device(int i) { return transfer(i,true); }
int fpga_sync_from_device(int i) { return transfer(i,false); }
int fpga_start_kernel(uint16_t a, uint16_t b, uint16_t offset, const int* indices, int count) {
    return guarded([&] {
        ready(); require(!running && indices && count==21,"invalid launch or kernel already running");
        // Validate every buffer before writing any control register.
        for (int i=0;i<count;++i) require(buffer(indices[i]).arg==i+3,"buffer argument order mismatch");
        (void)peek(0); // Clear stale ap_done (clear-on-read).
        poke(0x10,a); poke(0x18,b); poke(0x20,offset);
        for (int i=0;i<count;++i) {
            const auto addr=buffer(indices[i]).address;
            poke(0x28+12*i,uint32_t(addr)); poke(0x2c+12*i,uint32_t(addr>>32));
        }
        (void)peek(0x10); // Flush scalar/pointer writes before AP_START.
        require(generation<std::numeric_limits<int>::max(),"run handle counter exhausted");
        poke(0,1); running=true; active_run=++generation; return active_run;
    });
}
int fpga_wait_kernel(int run) {
    return guarded([&] {
        ready(); require(running && run==active_run,"invalid run handle");
        const auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(120);
        while (!(peek(0)&2)) {
            require(std::chrono::steady_clock::now()<deadline,"kernel timed out; reload AFI before retrying");
            std::this_thread::sleep_for(std::chrono::microseconds(50));
        }
        running=false; active_run=-1; return 0;
    });
}
int fpga_run_kernel(uint16_t a,uint16_t b,uint16_t o,const int* ids,int n) {
    int run=fpga_start_kernel(a,b,o,ids,n); return run<0 ? -1 : fpga_wait_kernel(run);
}
int fpga_read_register(uint32_t offset,uint32_t* value) {
    return guarded([&] { ready(); require(value && offset<4096 && offset%4==0,"invalid register"); *value=peek(offset); return 0; });
}
}
