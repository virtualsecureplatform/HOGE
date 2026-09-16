// Unit tests exercise the actual backend with an in-memory AWS SDK replacement.
#include <fpga_mgmt.h>
#include <fpga_pci.h>
#include <cassert>
#include <map>
#include <tuple>
#include <vector>
static std::map<std::pair<int,uint64_t>,uint32_t> regs;
static std::vector<std::tuple<int,uint64_t,uint32_t>> writes;
static bool wrong_image=false, read_error=false;
static int attachments=0;
extern "C" {
int fpga_mgmt_init() { return 0; }
int fpga_mgmt_close() { return 0; }
int fpga_mgmt_describe_local_image(int, fpga_mgmt_image_info* i, uint32_t) {
    *i={}; i->status=FPGA_STATUS_LOADED;
    i->ids.afi_device_ids={0x1d0f,0xf010,0x1d0f,uint16_t(wrong_image?0:0x4847)}; return 0;
}
int fpga_mgmt_get_vLED_status(int,uint16_t* s) { *s=1; return 0; }
int fpga_pci_attach(int,int,int bar,uint32_t,pci_bar_handle_t* h) { *h=bar+1; ++attachments; return 0; }
int fpga_pci_detach(pci_bar_handle_t) { --attachments; return 0; }
int fpga_pci_poke(pci_bar_handle_t h,uint64_t a,uint32_t v) {
    writes.emplace_back(h,a,v); regs[{h,a}]=(h==1 && a==0 && v==1)?2:v; return 0;
}
int fpga_pci_peek(pci_bar_handle_t h,uint64_t a,uint32_t* v) {
    if(read_error) return -5;
    *v=regs[{h,a}]; if(h==1 && a==0) regs[{h,a}]=0; return 0;
}
}
#include "../software/fpga_backend.cpp"
int main() {
    assert(fpga_init("bad")==-1 && attachments==0);
    wrong_image=true; assert(fpga_init("slot:0")==-1 && attachments==0); wrong_image=false;
    assert(fpga_init("slot:0")==0 && attachments==2);
    int ids[21];
    for(int i=0;i<21;++i) { ids[i]=fpga_alloc_buffer(i+3,64); assert(ids[i]>=0); }
    uint32_t pattern[16]; for(int i=0;i<16;++i) pattern[i]=0xdead0000+i;
    assert(fpga_write_buffer(ids[20],pattern,sizeof(pattern))==0);
    assert(fpga_sync_to_device(ids[20])==0);
    assert((regs[{5,27ULL*(1ULL<<29)+60}]==pattern[15]));
    assert(fpga_sync_from_device(ids[20])==0);
    uint32_t result[16]{}; assert(fpga_read_buffer(ids[20],result,sizeof(result))==0);
    assert(std::memcmp(result,pattern,sizeof(result))==0);
    int run=fpga_start_kernel(2,3,4,ids,21); assert(run>=0);
    assert((writes.back()==std::make_tuple(1,uint64_t{0},uint32_t{1})));
    assert((regs[{1,0x10}]==2 && regs[{1,0x18}]==3 && regs[{1,0x20}]==4));
    for(int i=0;i<21;++i) {
        uint64_t address=uint64_t(i<13?i:i+7)<<29;
        assert((regs[{1,0x28+12*i}]==uint32_t(address)));
        assert((regs[{1,0x2c+12*i}]==uint32_t(address>>32)));
    }
    assert(fpga_wait_kernel(run)==0);
    int next=fpga_start_kernel(2,2,0,ids,21); assert(next>run);
    assert(fpga_wait_kernel(next)==0);
    // A failed DMA-equivalent read must poison future launches, never silently reuse data.
    read_error=true; assert(fpga_sync_from_device(ids[0])==-1); read_error=false;
    auto n=writes.size(); assert(fpga_start_kernel(2,2,0,ids,21)==-1 && writes.size()==n);
    fpga_cleanup(); assert(attachments==0);
    assert(fpga_init("slot:0")==0);
    assert(fpga_alloc_buffer(24,64)==-1);
    fpga_cleanup(); assert(attachments==0);
    assert(fpga_init("slot:0")==0);
    int b=fpga_alloc_buffer(3,4); assert(b>=0);
    assert(fpga_write_buffer(b,pattern,8)==-1);
    fpga_cleanup(); assert(attachments==0);
    std::cout << "F2 backend tests passed\n";
}
