# HOGE: Homomorphic Gate on An FPGA

FPGA implementation of TFHE presented at ASP-DAC 2024.

## Directory Structure

- `chisel/HomGate/src/main/scala/` - Chisel source (main RTL)
- `vitis/` - Vitis build scripts, wrapper RTL, and configuration
  - `rtl/` - Verilog wrappers (HomGate_top.v, BRBack_top.v, BRFront_top.v, etc.)
  - `scripts/` - Vivado Tcl scripts for XO packaging
  - `cfg/` - Vitis linker configuration
  - `xml/` - Kernel XML descriptors
  - `build_hw.sh` - Hardware build script
  - `build_hw_emu.sh` - Hardware emulation build script
- `xcltest/HomGate/` - FPGA test program (nand gate test)
- `thirdparties/TFHEpp/` - TFHEpp library (submodule)

## Prerequisites

- Xilinx Vitis/Vivado 2023.2
- sbt (Scala Build Tool)
- cmake >= 3.18 and g++ with C++20 support
- XRT (Xilinx Runtime) for FPGA execution
- Platform: `xilinx_u280_gen3x16_xdma_1_202211_1`

## Architecture

The design uses 3 kernels mapped to different SLRs on the Alveo U280:

| Kernel   | SLR  | Function |
|----------|------|----------|
| HomGate  | SLR0 | FSM, IKS, DataMovers (HBM access) |
| BRBack   | SLR1 | NTT pipelines, MulAndAcc |
| BRFront  | SLR2 | BlindRotate, INTT, ExternalProduct |

## Building

### Hardware Emulation (hw_emu)

Hardware emulation simulates the full design including DataMover IPs. Useful for functional verification.

```bash
git clone --recursive https://github.com/virtualsecureplatform/HOGE.git
cd HOGE

# Build xclbin only (~1 hour)
./vitis/build_hw_emu.sh

# Build and run test (~1 hour build + ~1 hour test)
./vitis/build_hw_emu.sh --test
```

The hw_emu test runs the nand gate test for 2 gates (~26 min each in emulation).

### Hardware (real FPGA)

Builds the xclbin for deployment on a real Alveo U280.

```bash
# Build xclbin only (~3 hours)
./vitis/build_hw.sh

# Build and run nand test on FPGA
./vitis/build_hw.sh --test

# Clean rebuild from scratch
./vitis/build_hw.sh --clean --test
```

The build script automatically:
1. Generates `HomGateWrap.v` from Chisel via `sbt run`
2. Builds 3 XO files (HomGate, BRBack, BRFront) using Vivado
3. Links the xclbin with `v++`
4. (with `--test`) Builds and runs the nand gate test

### Running the Test Manually

```bash
# Build test binary
mkdir build && cd build
cmake3 .. -DCMAKE_BUILD_TYPE=Release
make -j$(nproc) nand
cd ..

# Run on real FPGA
ulimit -s unlimited
build/xcltest/HomGate/nand vitis/build/xclbin/HomGate_hw.xclbin

# Run on hw_emu
export XCL_EMULATION_MODE=hw_emu
export EMCONFIG_PATH=vitis/build/xclbin
ulimit -s unlimited
build/xcltest/HomGate/nand vitis/build/xclbin/HomGate_hw_emu.xclbin
```

### Verilator Simulation

For RTL-level simulation without Vitis (tests Chisel logic only, no DataMovers):

```bash
docker-test.bash
```

## Performance

- Kernel time: ~1.55 ms/gate at 292 MHz on Alveo U280
- The nand test runs 10 gates and verifies output against a software reference
