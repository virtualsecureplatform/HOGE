# AWS F2 250 MHz release

This release adds an independent AWS F2 HDK Small Shell target alongside the
existing Alveo U280 target. Compute runs at 250 MHz and HBM AXI at 450 MHz.

## Changes required for F2

- Replace the Vitis platform integration with a generated Small Shell CL,
  separate build tree, AWS SDK host backend, OCL control, PCIS memory access,
  HBM readiness monitoring, and function-level reset handling.
- Adapt 21 accelerator AXI4 ports from 512 bits to 256-bit AXI3 using AWS
  SmartConnect at the HBM clock. Preserve the U280 bank allocation, use F2's
  512 MiB bank windows, and reserve HBM port 31 for host access.
- Insert RTL elastic stream stages between compute, DataMovers, and existing
  FIFOs. Add five-stage PCIS and three-stage OCL channel pipelines. Preserve
  stream framing, backpressure, and required FIFO depths.
- Enable status reporting in the existing F2 output DataMover and assert host
  completion only after both compute and a successful output DMA complete.
- Localize reset release, register the HBM readiness reduction, remove unused
  AXI3 directions, and avoid incoming VALID/global reset driving wide payload
  enables. These changes reduce high-fanout control paths.
- Constrain compute across three SLRs and place DataMovers, converters, key
  capture FIFOs, HBM exit logic, and reset branches near their consumers.
  Retain the better routed result after a post-route optimization trial.
- Add fail-closed implementation checks for timing, routing, DRC, CDC, bus skew,
  and unconstrained paths, plus isolated HDK simulations and source inventories.
- Reuse the NAND reference test through a selectable host backend. Buffer API
  errors now fail immediately; U280 retains its XRT backend and XCLBIN usage.

Shared Chisel compute RTL, U280 wrappers, Vitis build entry points and U280 IP
settings are unchanged. No additional vendor register-slice IP was necessary.

## Validation

Vivado 2025.2 / AWS HDK 2.3.4, checkout b603a81f; candidate B, build tag
2026_09_15-133443, completed September 16, 2026 at 01:45 JST:

- Routed setup WNS +0.027 ns, TNS 0; hold WHS +0.010 ns, THS 0.
- Zero setup, hold, or pulse-width failing endpoints; fully routed with zero
  routing errors. All implementation acceptance checks passed.
- Five XSIM RTL tests, seven Python checks, and the actual XPM FIFO test passed.
- HDK functional-memory NAND regression passed all four cases, two launches
  each: 16 ciphertexts and 16,400 exact coefficient comparisons, zero mismatches.
- DMA completion/readback smoke and AMD HBM-model smoke passed. The latter
  covers 21 banks, OCL, partial writes above 8 GiB, and FLR/restart.
- U280 host compilation/link and command-line usage passed.

The full NAND regression uses a functional AXI3 memory model. AMD HBM-model
coverage is a separate smoke test. Neither physical F2 execution nor a U280
hardware regression has been performed. No AFI has been created.

The generated Developer_CL archive is a checkpoint package for AWS AFI
creation, not an AFI or a hardware-tested image. Build products are excluded
from Git; the source release retains reproducible build instructions.

## Next target: 300 MHz compute

The 300 MHz target is subsequent work; this release preserves the validated
250 MHz baseline and does not claim 300 MHz timing closure. HBM remains 450 MHz.
