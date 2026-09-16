// Export only: deliberately exits before the shared NAND test can report PASS.
#include <array>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <vector>

namespace {
struct Buffer { int arg; std::vector<uint8_t> bytes; };
std::vector<Buffer> buffers;
std::filesystem::path directory;
size_t expected_words;
constexpr std::array<int,21> banks{0,1,2,3,4,5,6,7,8,9,10,11,12,20,21,22,23,24,25,26,27};
}
bool hoge_vector_plain(int bit) {
    const char* value = std::getenv("HOGE_SIM_CASE");
    if (!value || std::strlen(value)!=1 || *value<'0' || *value>'3') {
        std::cerr << "Set HOGE_SIM_CASE=0..3\n"; std::exit(2);
    }
    return ((*value-'0') >> bit) & 1;
}
void hoge_vector_expected(const void* data, size_t size) {
    expected_words=size/4;
    std::ofstream out(directory/"expected.hex");
    const auto* words=static_cast<const uint32_t*>(data);
    for(size_t i=0;i<expected_words;++i) out << std::hex << std::setw(8) << std::setfill('0') << words[i] << '\n';
    if (!out) std::exit(2);
}
extern "C" {
int fpga_init(const char* path) { directory=path; std::filesystem::create_directories(directory); return 0; }
void fpga_cleanup() {}
int fpga_alloc_buffer(int arg,size_t size) { buffers.push_back({arg,std::vector<uint8_t>(size)}); return int(buffers.size()-1); }
int fpga_write_buffer(int id,const void* src,size_t size) { if(size>buffers.at(id).bytes.size()) return -1; std::memcpy(buffers.at(id).bytes.data(),src,size); return 0; }
int fpga_sync_to_device(int) { return 0; }
int fpga_sync_from_device(int) { return -1; }
int fpga_read_buffer(int,void*,size_t) { return -1; }
int fpga_wait_kernel(int) { return -1; }
int fpga_read_register(uint32_t,uint32_t*) { return -1; }
int fpga_start_kernel(uint16_t a,uint16_t b,uint16_t o,const int* ids,int count) {
    if(count!=21 || expected_words==0) std::exit(2);
    std::ofstream manifest(directory/"manifest.txt");
    manifest << a << ' ' << b << ' ' << o << ' ' << expected_words << '\n';
    for(int i=0;i<count;++i) {
        const auto& buffer=buffers.at(ids[i]);
        if(buffer.arg!=i+3) std::exit(2);
        manifest << i << ' ' << std::hex << (uint64_t(banks[i])<<29) << std::dec << ' ' << buffer.bytes.size() << '\n';
        std::ofstream out(directory/("arg"+std::to_string(i)+".hex"));
        for(size_t off=0;off<buffer.bytes.size();off+=64) {
            for(int j=63;j>=0;--j) {
                unsigned v=off+j<buffer.bytes.size()?buffer.bytes[off+j]:0;
                out << std::hex << std::setw(2) << std::setfill('0') << v;
            }
            out << '\n';
        }
        if(!out) std::exit(2);
    }
    manifest.close();
    if(!manifest) std::exit(2);
    std::cout << "VECTORS_GENERATED (CPU reference only; no RTL result checked)\n";
    std::exit(0);
}
}
