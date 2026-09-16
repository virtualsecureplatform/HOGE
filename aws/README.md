# AWS F2 target

This is an experimental HDK Small Shell port. It shares the Chisel computation,
arithmetic RTL, directional DataMover configuration, and NAND reference test with Alveo
U280. The original `vitis/build_hw.sh`, `vitis/build_hw_emu.sh`, root CMake
configuration, and XRT backend remain the U280 entry points.

F2 uses a separate `aws/build/` tree (override with `HOGE_BUILD_ROOT`) and
`nand_f2` executable. No U280 XO,
xclbin, generated Verilog, or installed AWS HDK files are overwritten.

For end-to-end RTL correctness testing, see [HDK simulation](sim/README.md).

See [VALIDATION.md](VALIDATION.md) for the checks completed and remaining
hardware acceptance work.
The 250 MHz compute / 450 MHz HBM revision passed routed implementation with
Vivado 2025.2 and HDK 2.3.4: setup WNS +0.027 ns and hold WHS +0.010 ns,
with no failing timing endpoints or routing errors. The AWS checkpoint package
was generated; AFI creation and execution on physical F2 hardware remain untested.
See [release notes](RELEASE_NOTES.md) for the adaptation and validation details.

## Architecture

* HomGate, BRBack, and BRFront run at the shell's 250 MHz.
* `generate.py` recreates the streams in `vitis/cfg/link_config.cfg`, including
  TKEEP/TLAST and explicit FIFO depths. Streams without a specified depth get
  a 32-entry FIFO. These pipelines require the same buffering discipline as U280.
  Every inter-kernel FIFO feeds three F2 RTL elastic stages. Producers that
  ignore TREADY have simulation overflow assertions at their capture FIFOs.
* `HomGate_f2.sv` is generated from the unchanged U280 wrapper. Three-stage RTL
  pipelines separate the 21 DataMover command streams and the 12 HomGate read
  streams from compute; bootstrapping-key streams use their existing deep FIFO
  followed by the inter-kernel pipeline. The output DMA has a corresponding
  write pipeline. Each stage buffers two beats and breaks the READY path.
  Payload enables do not depend on incoming VALID or global reset; only free
  storage captures invalid payload. No additional vendor IP is introduced in
  this first pass.
* Twenty-one independent AWS SmartConnect converters adapt the existing
  512-bit AXI4 ports to 256-bit AXI3 at 450 MHz. HBM channels 0–12 and 20–27
  retain the U280 allocation. Each F2 channel has a 512 MiB address window.
* PCIS/BAR4 accesses HBM through channel 31 and its internal global crossbar.
  The host loads keys before launching computation. The initial backend uses
  32-bit MMIO transfers and is intended for correctness bring-up, not fast loading.
* OCL/BAR0 preserves HomGate's control map: scalars at 0x10/0x18/0x20,
  21 64-bit pointers at 0x28 + 12*i, and AP control at 0x00.
  PCIS and OCL use independent RTL channel pipelines: five stages for PCIS
  along a fixed path between the shell and SLR0, and three for OCL.
  AP_DONE waits for both the controller and successful output DMA status;
  F2 enables the status FIFO inside its existing S2MM DataMover (the U280
  configuration disables status reporting and is unchanged).
  Output framing/counting remains at the actual DMA input. A failed DMA status
  holds the operation busy and sets the existing debug error bit until reset.
  Each RTL pipeline stage synchronizes reset release locally through four
  registers, so a global reset net does not directly drive wide payload enables.
* The AWS HBM wrapper connects both mandatory HBM monitor interfaces.
  Virtual LED bit 0 reports HBM ready. The host waits for readiness before
  touching the accelerator. PCI IDs identify this CL as F010:1D0F,
  subsystem 4847:1D0F.
* DDR, PCIM, SDA, interrupts and virtual JTAG are unused. HomGate and memory
  adapters stay in SLR0. The build now uses candidate B: BRBack in SLR1 and
  BRFront in SLR2. Bank-local groups include DataMovers, their bootstrapping-key
  capture FIFOs, and converters. HBM handshake gates, AXI3 exit logic and local
  reset branches are placed near their ports. The converters' 450 MHz read upsizers
  have tighter placement at the HBM ports. Unused AXI3 directions are explicitly
  disconnected so their handshake logic can be optimized away. Child regions are subsets of the actual
  Small Shell CL region. U280 placement constraints are not inherited.
* FLR is acknowledged after 31 compute clocks, allowing clocked reset paths to
  clear. Interfaces remain held in reset until HBM initialization completes.

## Hardware build

Use an HDK-supported Vivado version and source `hdk_setup.sh` with that version.
Use separate shell sessions for the U280 and F2 toolchains.
The development environment used here has HDK 2.3.4 and Vivado 2025.2. Its default
Vivado 2026.1 is not in that HDK's supported-version list.

```bash
source /home/opt/xilinx/2025.2/Vivado/settings64.sh
source "$AWS_FPGA_REPO_DIR/hdk_setup.sh"
./aws/build_hw.sh
```

Requires Python 3.10+, sbt/Java, the HDK IP downloads, and a Vivado synthesis
license. Chisel is built in an isolated copy under `aws/build/chisel`. An
already-generated compatible RTL file can instead be supplied explicitly:

```bash
./aws/build_hw.sh --rtl /absolute/path/to/HomGateWrap.v
# Keep timing experiments separate from simulations and previous artifacts:
HOGE_BUILD_ROOT="$PWD/aws/build/timing_hw" \
  HOGE_VIVADO=/home/opt/xilinx/2025.2/Vivado/bin/vivado \
  ./aws/build_hw.sh --rtl /absolute/path/to/HomGateWrap.v
# Repeat placement/routing after changing only physical implementation scripts:
HOGE_BUILD_ROOT="$PWD/aws/build/timing_hw" \
  HOGE_VIVADO=/home/opt/xilinx/2025.2/Vivado/bin/vivado \
  ./aws/build_hw.sh --resume SOURCE_TAG
```

`SOURCE_TAG` identifies an existing `cl_hoge.SOURCE_TAG.post_link.dcp` in that
build root. Resume verifies the staged design and original RTL/IP source hashes,
copies the linked checkpoint to a fresh run tag, and records the physical input
hashes. RTL changes require a full build. Previous run reports remain available.

The build stages `aws/build/cl_hoge`, creates and synthesizes the DataMover and
clock IPs, and invokes the HDK build flow. AWS IP files are copied into the
staging directory before regenerating output products. A completed HDK build
produces a `*.Developer_CL.tar` under the staged CL's `build/checkpoints`.
Use AWS's documented S3/AFI creation and load workflow with that archive.
The script does not upload anything, create cloud resources, or load a slot.
Optional source encryption is disabled for this open-source design. The script
fails if the routed checkpoint or implementation acceptance report is absent,
even if the upstream HDK driver returns a successful process exit status.
Acceptance checks include setup/hold, pulse width, unconstrained paths, routing,
DRC, CDC and bus skew. Reports needing review are rejected. Each staged tree has
`source_inventory.json` with source/design hashes and the fixed clock targets.
CDC is checked on CL crossings, including crossings to the static shell.
One documented CDC-10 waiver covers only the asynchronous reset entry that
combines reset with MMCM lock; four XPM stages synchronize its release.
The HBM ready reduction is registered before synchronization. Waived crossings
are reported separately, and unresolved critical crossings stop the build before
placement.

Each kernel, converter and stream FIFO has a local four-clock reset-release
branch. HBM reset readiness uses a physically grouped, four-level registered
reduction tree before its existing registered CDC. Generated metadata enumerates
every reset branch and PCIS stage, and physical checks fail on missing targets.
After initial routing, the build tries post-route physical optimization and
keeps the better timing result; complete failing-endpoint family reports accompany
the final routed checks. Adding HBM-side RTL slices, or replacing an insufficient
RTL slice with AMD register-slice IP, remains conditional on these results.

## Host build and execution

Build/install the AWS SDK userspace library first (`libfpga_mgmt`, including PCI
access). This host target does not require XRT or Vitis.
TFHEpp and the executable use the same `TFHEPP_MARCH` setting (default
`x86-64-v3`); matching these is necessary for SIMD alignment and ABI compatibility.

```bash
cmake -S aws -B aws/build/host -DCMAKE_BUILD_TYPE=Release \
  -DAWS_SDK_DIR="$SDK_DIR"
cmake --build aws/build/host --target nand_f2 -j8
ulimit -s unlimited
# After explicitly loading the cl_hoge AFI into slot 0:
aws/build/host/nand_f2 slot:0 --iterations 20
```

`nand_f2` refuses a slot with different PCI image IDs. The test uses two ciphertexts
per invocation and compares every output coefficient against TFHEpp. Transfer,
launch, timeout and comparison failures return failure. The shared NAND test
also checks buffer API errors on U280.

## Local checks

```bash
mkdir -p aws/build
python3 -m unittest discover -s aws/tests -v
HOGE_VIVADO=/home/opt/xilinx/2025.2/Vivado/bin/vivado bash aws/tests/run_rtl.sh
g++ -std=c++20 -Wall -Wextra -Werror -I "$SDK_DIR/userspace/include" \
  aws/tests/backend_test.cpp -o aws/build/backend_test
aws/build/backend_test
vivado -mode batch -source aws/tests/fifo_sim.tcl \
  -log aws/build/fifo_sim.log -journal aws/build/fifo_sim.jou
```

Backend tests use a mock SDK, and the FIFO test uses AMD's actual XPM model.
They do not establish full TFHE correctness, timing closure, AFI acceptance,
or performance on F2. Hardware NAND testing remains necessary before calling
this target production ready. The U280 performance number is not an F2 result.
