# HDK NAND correctness simulation

This test uses the actual HOGE F2 RTL, DataMovers, SmartConnect converters,
clock IP and AWS shell bus-functional models, with two selectable memory models.
It is separate from U280 Vitis `hw_emu` and from physical timing validation.

The installed HDK's older simulation guide says HBM needs VCS/Questa; its newer
`ERRATA.md` explains that XSIM has a slower, non-cycle-accurate HBM BFM. This
runner defaults to that supplied XSIM model and AWS's `xsim_hbm` configuration
files (DBI disabled). `HOGE_HBM_MODEL=axi` instead selects a functional AXI3
memory model for faster compute/integration checks. It retains the real clock
IP, DataMovers, SmartConnect converters and shell BFMs, and inserts bounded
channel stalls. It does not model HBM training, timing, bank contention or
controller behavior. HBM monitor behavior is not
covered by this HDK release. Passing this test would not establish F2 timing
closure, AFI acceptance, hardware HBM training or performance.

## Prepare

Use the HDK-supported Vivado environment (2025.2 was used during development).
The normal F2 staging directory and generated IP definitions must exist. If
they do not, supply the shared Chisel-generated RTL and prepare only the IPs:

```bash
export HOGE_ROOT="$PWD"
export HOGE_BUILD_ROOT="$PWD/aws/build/timing_sim"
python3 aws/scripts/prepare.py --rtl /absolute/path/to/HomGateWrap.v
(cd "$HOGE_BUILD_ROOT/cl_hoge" && vivado -mode batch -source "$HOGE_ROOT/aws/scripts/create_ip.tcl")
```

This does not synthesize or route the complete CL. The IP preparation script
also synthesizes individual IPs; those checkpoints are not required by XSIM.

Build the reference-vector exporter using the existing F2 host CMake setup:

```bash
cmake -S aws -B aws/build/host -DCMAKE_BUILD_TYPE=Release -DAWS_SDK_DIR="$SDK_DIR"
cmake --build aws/build/host --target nand_vectors -j8
ulimit -s unlimited
HOGE_SIM_CASE=0 aws/build/host/nand_vectors aws/build/vectors/case0
```

`HOGE_SIM_CASE` selects the plaintext inputs: 0=(false,false), 1=(true,false),
2=(false,true), 3=(true,true). Encryption and key generation remain randomized.
The exporter uses an isolated adaptation of `xcltest/HomGate/nand.cpp`; its CPU
reference and key packing remain shared with the hardware test. It verifies
the decrypted CPU result is NAND and exports exact output coefficients. It
exits before any host-test `PASS`; generating vectors is not an RTL test.
Each case occupies roughly 160 MB in hexadecimal files.

## Run

```bash
./aws/run_sim.sh aws/build/vectors/case0
# Alternatively select the supported tool explicitly:
HOGE_VIVADO=/home/opt/xilinx/2025.2/Vivado/bin/vivado ./aws/run_sim.sh
# Faster functional memory model:
HOGE_HBM_MODEL=axi HOGE_VIVADO=/home/opt/xilinx/2025.2/Vivado/bin/vivado ./aws/run_sim.sh
# Compile once and run all four vector sets concurrently in isolated snapshots:
HOGE_HBM_MODEL=axi HOGE_SIM_CASES=4 ./aws/run_sim.sh "$PWD/aws/build/vectors/case0"
# Check the real output DMA/status/completion path with an injected zero stream:
HOGE_HBM_MODEL=axi HOGE_DMA_SMOKE=1 ./aws/run_sim.sh "$PWD/aws/build/vectors/case0"
```

The DMA smoke test uses real RTL slices, DataMover, converter and HDK PCIS/OCL
BFMs, and reads back all 2,050 output words after host completion. It does not
execute NAND arithmetic. Its separate `HOGE_HDK_DMA_SMOKE_PASS` marker and
`dma_smoke.log` archive cannot satisfy the full NAND regression checker.

The test waits for HBM initialization, loads all keys/inputs via PCIS 4 KiB
bursts, checks the first 512-bit word in the first and last 4 KiB block of each buffer, and programs
the original control registers via OCL. It runs the two-ciphertext NAND batch
twice, poisoning output memory before each launch, and compares every output
coefficient with the independently computed TFHEpp reference. In `axi` mode,
key buffers are preloaded directly into modeled memory; their first and last
blocks are still checked through PCIS. Input/output transfers use PCIS in both
modes. The memory model never receives the expected output coefficients. Unknown outputs,
mismatches, protocol errors, missing/truncated vectors and a 100 ms simulation
watchdog cause failure. The wrapper requires an explicit `HOGE_HDK_PASS` marker
and rejects simulator error markers even if Vivado exits successfully.

For a shorter memory/controller integration test without NAND, use:

```bash
HOGE_HBM_SMOKE=1 ./aws/run_sim.sh aws/build/vectors/case0
```

This writes distinct 512-bit patterns at two offsets in each of the 21 used
banks before reading them all back (detecting address aliases), and checks all
64-bit kernel pointer registers over OCL. It also tests a narrow write above
8 GiB without corrupting adjacent bytes, FLR clearing the OCL state, and HBM
transfers after reinitialization. Its success marker is deliberately
separate: `HOGE_HDK_HBM_SMOKE_PASS`. It does not establish NAND correctness.
The default runner still executes the full NAND test.

To cover all truth-table inputs, generate cases 0 through 3 and run them
separately or set `HOGE_SIM_CASES=4` with the `case0` directory as the argument.
XSIM compilation/elaboration and key loading can be expensive; runtime is not
a measurement of FPGA performance. Logs and generated files stay under ignored
`aws/build/hdk_sim/` (AMD) or `aws/build/hdk_sim_axi/` (AXI). The main runtime log is
`project/hoge_hdk.sim/sim_1/behav/xsim/simulate.log`.

Inspect a runtime log without relying on the simulator's exit status:

```bash
python3 aws/sim/check_results.py aws/build/hdk_sim_axi/project/hoge_hdk.sim/sim_1/behav/xsim/simulate.log
python3 aws/sim/check_results.py --kind hbm-smoke /path/to/smoke/simulate.log
```

The checker accepts multiple logs and exits 0 only when all requested tests
have their correct success marker and no simulator error marker. Exit 1 means
failure; exit 2 means incomplete. Memory-only success is never a NAND PASS.

The runner shares the hardware-build lock within its `HOGE_BUILD_ROOT`, because
Vivado generates local IP simulation products. Different build roots can run
independently. The default root remains `aws/build`. The runner does not modify
U280 files or the installed HDK checkout.

## Validation status

The previous integration passed all four cases with two launches each: 16,400
exact reference-coefficient comparisons and zero mismatches, completed at
09:46 UTC on 2026-09-08. Its separate AMD HBM bank/OCL smoke also passed.
These results do not validate the subsequent stream-pipeline revision.
The revised integration is being checked under `build/timing_sim` and
`build/timing_amd`; see [validation status](../VALIDATION.md) for current results.
