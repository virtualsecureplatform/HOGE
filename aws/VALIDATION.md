# F2 bring-up validation — 2026-09-08

Environment: AWS FPGA HDK 2.3.4 (`b603a81f`), Vivado 2025.2,
target `xcvu47p-fsvh2892-2-e`, Small Shell.

## Completed checks

* Generated the shared Chisel RTL in the isolated F2 build directory.
* Built `nand_f2` against the real AWS SDK and TFHEpp. Usage and invalid-input
  checks pass; requesting local slot 0 fails because this machine has no F2.
* Compiled the shared NAND test with the original XRT backend and checked its
  original `<XCLBIN>` command line. This does not constitute a U280 hardware test.
* Backend mock tests pass: HBM addressing above 4 GiB, register programming,
  transfers, repeated runs, image checks, invalid arguments and SDK failures.
* Wiring checks pass for all kernel ports, stream depths, HBM converters,
  unused AXI directions and HBM monitor connections.
* Actual XPM FIFO simulation passes with backpressure, wraparound, reset,
  TKEEP and TLAST preservation (200 words, 116 blocked input cycles).
* Clean generation and synthesis of both DataMovers and the corrected internal
  HBM clock IP pass with no errors or critical warnings.
* Full CL synthesis passes with no errors or critical warnings. Its warnings
  include unused upstream wrapper ports and memory inference messages.
* The CL links into the AWS shell and passes pre-optimization DRC with zero
  errors. Logic optimization completes and produces a checkpoint.
* Full placement completes. The unconstrained baseline has estimated worst
  setup slack of **-4.365 ns** and high congestion. Its worst path crosses an
  SLR boundary between HBM and the host SmartConnect adapter.
* Constraining HBM and all 22 adapters to SLR0, explicitly unplacing the adapters
  and HBM reset pipeline, then running a diagnostic `Quick` placement completes
  with zero errors and zero critical warnings. Worst setup slack improves to
  **-3.062 ns**, but timing still fails. This is an incremental placement
  experiment, not a clean build or routed timing result.

CL synthesis resource counts (before physical implementation):

| Resource | Count |
| --- | ---: |
| LUTs | 507,002 |
| Registers | 454,669 |
| RAMB36 / RAMB18 | 343 / 27 |
| URAM | 64 |
| DSP | 2,032 |

The checked clock periods are 4 ns (compute), 2.222 ns (HBM AXI) and
10 ns (HBM reference). The validation checkpoint was updated with the separately
rebuilt internal clock IP using Vivado `update_design`; the build script includes
that corrected configuration for subsequent clean builds.

Logs and reports are under ignored `aws/build/cl_hoge/`: `build/reports/synthesis.log`,
`build/reports/hoge_utilization.rpt`, `build/reports/hoge_clocks.rpt` and
`build/scripts/synth_tied.vivado.log`. The installed AWS checkout remains unchanged.
The constrained experiment is in `hbm_replaced.log` and
`build/reports/cl_hoge.hbm_replaced.{post_place_timing,timing_summary,congestion}.rpt`.
Its checkpoint is `build/checkpoints/cl_hoge.hbm_replaced.post_place.dcp`.

## HDK correctness simulation

The isolated simulation runner is documented in [sim/README.md](sim/README.md).
TFHEpp reference generation succeeds for all four NAND plaintext combinations.
Both configurations compile and elaborate with Vivado 2025.2 and unchanged
AWS shell BFMs, HOGE compute RTL, DataMovers and SmartConnect converters.

* AMD HBM XSIM model: initialization and PCIS transfer checks for the output
  and both input buffers have passed. The full-key-loading attempt was stopped
  because of its runtime. The separate AMD HBM smoke test **passes** all 21
  bank-address readback checks, all 64-bit OCL-pointer checks and the HDK shell
  protocol-error check. It finishes at 63.872 microseconds with a clean XSIM
  exit (code 0). The Tcl loop now stops at the test completion marker instead
  of resuming after `$finish`. Log: `aws/build/hdk_hbm_smoke/simulate.log`.
* Functional AXI3 memory model: all 21 buffers pass the first/last-block PCIS
  checks, and all four cases have launched NAND. Keys are preloaded directly
  into modeled memory; input/output transfers still use PCIS. Bounded AXI
  stalls exercise backpressure. This model does not validate the AMD HBM
  controller or physical HBM behavior.

**The previous integration passed the complete NAND regression.** All four
plaintext combinations completed two launches each at 09:46 UTC on 2026-09-08:
16 ciphertexts, 16,400 exact coefficient comparisons, and zero mismatches.
The archived [results](build/hdk_sim_results.md) identify the logs and the
supervised termination used by those older simulation snapshots.

## RTL stream-pipeline revision

The F2 wrapper now adds RTL elastic pipelines around DataMover command/data
streams and at shell/inter-kernel interfaces. No additional vendor IP is used.
Output completion waits for successful S2MM status, and FLR acknowledgement is
held off for 31 clocks. HBM reset branches release locally after four clocks.
The U280 source wrappers and shared Chisel arithmetic remain unchanged.

Completed checks while implementing this revision:

* Generator/implementation-report unit checks pass.
* XSIM RTL tests pass for payload/sideband ordering, backpressure, full-rate
  throughput, reset flushing, delayed write completion, status errors, repeated
  launches, and asynchronous reset assertion/four-clock release.
* Both HDK memory configurations compile and elaborate before the status fix.
* The initial pipeline revision's AMD HBM smoke passes all 21 banks, OCL pointer readback, partial
  writes above 8 GiB, FLR clearing OCL state, and transfers after HBM
  reinitialization. It finishes at 70.848 microseconds with exit code 0.
  Log: `aws/build/timing_amd/hdk_sim/project/hoge_hdk.sim/sim_1/behav/xsim/simulate.log`.
* The initial pipeline revision's full CL synthesis completes with no errors. The HDK emits constraints warnings
  for unused GT terminals and disabled DDR; its empty DDR clock-group constraint
  is rejected rather than cutting any clocks. Routed checks remain required.
* Floorplan geometry checks pass against the actual Small Shell legal sites.
  Optimized candidate A passes hierarchy/reset checks, SLR capacity checks and
  pre-placement DRC. LUT use is about 50%, 43%, 46% in SLR0–2.

The first pipeline NAND regression (`timing_sim`) exposed a completion blocker:
the inherited DataMover configuration disabled its status FIFO, which ties
status-valid low. Output production completed but the host waited indefinitely
for write status. These runs were stopped for diagnosis; **they did not pass**.
The F2 IP configuration now enables status inside the existing S2MM DataMover.
The U280 configuration remains unchanged.

Early CDC analysis also identified the unregistered HBM reset-ready reduction;
the existing XPM now enables its source register. A separate reviewed CDC-10
waiver is scoped to the asynchronous reset/lock-combination entry, whose release
is synchronized by four XPM stages. No data crossing or timing path is waived.

The corrected revision passes the short HDK output-DMA test at 11:20 UTC:
2,050 injected output words, successful write status, host completion, exact
memory readback and no AXI protocol errors. It finishes at 51.970 microseconds.
Log: `aws/build/timing_sim2/dma_smoke.log`. This is not a NAND arithmetic pass.

The corrected revision also passes the AMD HBM smoke at 11:46 UTC: all 21
banks, OCL, partial writes above 8 GiB, FLR and post-reset transfers, with no
protocol errors and a clean simulator exit at 70.848 microseconds.
Log: `aws/build/timing_amd2/hdk_sim/project/hoge_hdk.sim/sim_1/behav/xsim/simulate.log`.

The corrected optimized CL passes its early critical CDC check at 11:51 UTC:
no unresolved critical findings, and exactly one waived asynchronous-reset
entry. Report: `aws/build/timing_hw3/cl_hoge/build/reports/cl_hoge.2026_09_08-111709.A.early_cdc.rpt`.

The status-corrected pipeline revision (`timing_sim2`) passed all four NAND
cases, two launches each, by 13:21 UTC: 16 ciphertexts, 16,400 coefficient
comparisons, and zero mismatches. Every simulator exited cleanly and the
strict log checker accepted all four results. Logs are in
`aws/build/timing_sim2/hdk_sim_axi/` (case 0 under `project/hoge_hdk.sim/sim_1/behav/xsim`,
cases 1–3 under `case1`–`case3`); `regression.console.log` records the aggregate
pass. This revision precedes the per-stage local reset change below.

## Outstanding acceptance checks

### Placement findings and local-stage-reset revision

Candidate A of `timing_hw3` completed placement with zero errors and zero
critical warnings. Its WNS is **-1.445 ns**, TNS **-61,029.777 ns**, and WHS
**-0.368 ns**. This improves the older constrained WNS of -3.062 ns, but is not
timing closure. The worst setup path is global core reset to an RTL read-slice
payload enable: 5.195 ns data delay, including 4.959 ns routing. Reset recovery
also fails by 0.688 ns. A separate HBM-to-SmartConnect read-upsizer enable path
fails by 0.577 ns at 450 MHz. Congestion remains significant.

The next revision adds four-clock local reset release to each RTL slice stage
and constrains the HBM-side read upsizers near their physical ports. It changes
neither the U280 design nor the vendor-IP instance count. Its pipeline,
completion, reset and staggered-reset-release unit tests pass. Its HDK DMA
smoke also passes at 13:11 UTC, including 2,050 words, write status, host
completion and exact memory readback (`aws/build/timing_sim3/dma_smoke.log`).
The current revision passed its complete functional-memory NAND regression by
15:17 UTC: all four cases, two launches each, 16 ciphertexts and 16,400 exact
coefficient comparisons with zero mismatches. All four simulators exited cleanly
and the strict result checker passed. Logs: `aws/build/timing_sim3/hdk_sim_axi/`;
aggregate result: `aws/build/timing_sim3/regression.console.log`.

The same revision passed the AMD HBM smoke at 14:05 UTC, including all 21 banks,
OCL, partial writes above 8 GiB, FLR, post-reset transfers and protocol checks.
Simulation finished at 70.848 microseconds with a clean exit. Log:
`aws/build/timing_amd3/hdk_sim/project/hoge_hdk.sim/sim_1/behav/xsim/simulate.log`.
The AMD smoke does not execute NAND; the full NAND test uses functional AXI3 memory.

The `timing_hw4` build finished at 18:45 UTC. Both candidates completed
placement, physical optimization and routing, but both were rejected for setup
timing. Candidate B was ranked first and routed first. No accepted F2 package
was produced, and this build is no longer running.

| Candidate / stage | WNS (ns) | TNS (ns) | WHS (ns) |
| --- | ---: | ---: | ---: |
| A / placement | -0.656 | -8,238.911 | -0.320 |
| A / physical optimization | -0.577 | -3,476.387 | -0.250 |
| A / routed | -0.513 | -3,450.691 | +0.003 |
| B / physical optimization | -0.439 | -460.888 | -0.250 |
| B / routed | -0.408 | -833.750 | +0.006 |

Candidate B has 9,400 setup-failing endpoints; candidate A has 27,983. Both
are fully routed with zero routing errors, zero hold-failing endpoints and no
pulse-width failures. The strict acceptance checker rejects both for setup
violations only: it finds no additional blocker in the DRC-error, critical-CDC,
bus-skew or constrained-path checks. DRC and CDC warnings still exist; this is
not a claim of warning-free reports. The single reviewed asynchronous-reset
CDC waiver remains present.

Candidate B's worst setup path is the host adapter's AXI3 write-address state
register to HBM port 31 AWADDR, with -0.408 ns slack and 2.107 ns routing delay.
Its worst 250 MHz path is shell PCIS to the first write-stream slice enable,
with -0.225 ns slack and 3.613 ns routing delay. Candidate A's worst path is
also in the host HBM write adapter (-0.513 ns); its worst 250 MHz path is core
reset to a stream FIFO enable (-0.376 ns).

Reports and candidate-specific routed checkpoints are retained under
`aws/build/timing_hw4/cl_hoge/build/`. The final driver message about a missing
post-route DCP means no *accepted canonical* checkpoint was produced; it does
not mean routing was skipped. These results were checked at 23:47 UTC.

The preceding `timing_sim2` regression also passed but did not include local
stage resets. The preceding `timing_hw3` A optimized checkpoint is retained
(WNS -1.215 ns, TNS -55,229.660 ns, WHS -0.250 ns); that run was intentionally
stopped before B/routing when the latest reset changes required new synthesis.

**Timing closure is not achieved; there is no deployable F2 image.**
The constrained experiment's worst path is HBM RVALID-to-RREADY handshake logic
with 4.297 ns of estimated routing delay. The baseline also flags the OCL control
path and HBM reset recovery. Congestion remains around BRBack's NTT logic.
The new build flow evaluates two compute floorplans from one synthesized CL,
constrains memory adapters and HBM feedback gates locally, and checks routed
setup/hold, CDC/bus skew, pulse width and design rules before accepting a build.
Those checks must finish successfully before AFI submission.

The baseline implementation was stopped after saving its placed checkpoint and
timing report; the diagnostic constrained placement finished normally. The clean implementation and HDK simulations described above have finished.
Both latest physical candidates completed routing but failed setup timing.
AFI creation/acceptance, HBM transfers, repeated NAND comparisons against TFHEpp
and performance measurements on an actual F2 instance have not been run.
No cloud resources have been created and no FPGA has been reprogrammed.

The U280 RTL, Vitis configuration/build scripts, root CMake setup and XRT backend
are unchanged. The shared NAND host test adds buffer-error checking and selects
the F2 usage text only when built with `HOGE_AWS_F2`. U280 hardware regression
remains a separate acceptance check.

## Follow-up timing revision — 2026-09-09

The first implementation pass after the routed candidate B failure changes only
F2 integration: speculative capture into free RTL slice storage, five-stage PCIS,
local FIFO/kernel/converter reset branches, registered HBM-ready reduction,
explicitly inactive AXI3 directions, bank-local BK FIFOs, and tighter adapter
placement. Candidate B is the default; post-route physical optimization and
complete failing-endpoint family reports are now included. HBM data-channel
slices and vendor slice IP remain conditional follow-ups, not implemented yet.

Five XSIM RTL tests pass, including five-stage pipelines, invalid input payload
changes, staggered reset release, and all 32 missing-port cases for the readiness
tree. Seven generator/report checks pass. The actual XPM FIFO simulation passes
200 words with 116 blocked input cycles after adding local reset release.
Results are under `aws/build/timing_sim4/rtl_tests/` and
`aws/build/timing_sim4/fifo_sim/`. Full HDK integration and routed timing for this
revision are not yet established; earlier regression passes do not validate it.

The current HDK DMA smoke passes at 00:44 UTC (56.810 microseconds): 2,050
output words, successful S2MM status, host completion, exact memory readback,
and protocol checks. Log: `aws/build/timing_sim4/dma_smoke.log`.
The first attempt stopped before simulation because IP generation ran from the
wrong directory; IP generation now honors `CL_DIR`, and products were rebuilt
inside the isolated simulation root.

The original XRT host sources compile and link successfully. Their `<XCLBIN>`
usage check passes using the existing Boost 1.71 compatibility library and
unlimited stack. Logs: `aws/build/timing_sim4/u280_host/`. This is not a U280
hardware regression. Shared U280/Chisel source hashes and the installed HDK
checkout remain unchanged.

The supervised sequence starts the four-case NAND regression, then the AMD HBM
smoke, then a fresh candidate B build under `aws/build/timing_hw5`. It verifies
source hashes between phases and waits for sufficient available memory before
AMD elaboration and hardware synthesis. Status is recorded in
`aws/build/timing_sim4/sequence_status.json`. The hardware build is queued, not
yet a timing result. The PCIS geometry check rejected X6Y2 (no legal CL sites);
the intermediate stage was moved to X5Y2 and geometry validation was restarted.

The corrected placement-region checks passed at 00:46 UTC: all three SLR
regions, all eight HBM columns, and all six PCIS stage regions are legal CL
subsets. Report: `aws/build/timing_sim4/floorplan2.console.log`. The queued
sequence's geometry gate now uses this passing report; the rejected original
report is retained as `floorplan.invalid_pcis.console.log`. The full NAND
regression has launched. All queued source fingerprints and staged RTL source
hashes match the current revision.

### Status checked at 04:55 UTC

The latest `timing_sim4` revision passes the complete functional-memory NAND
regression: all four cases, two launches each, 16,400 coefficient comparisons,
and zero mismatches. The AMD HBM smoke also passes, finishing at 03:00 UTC
(72.540 microseconds simulated time), including all 21 banks, OCL, partial writes,
FLR and post-reset transfers. Both test runners report clean completion.

The fresh `timing_hw5` build has **not started**. Its supervisor is waiting for
45 GiB available memory; the machine currently has about 30 GiB available while
another FPGA implementation uses about 58 GiB. The queue remains active and all
source fingerprints match. It will launch automatically when the memory gate is
satisfied. There is no new synthesis or timing result for this revision.

### Hardware build cancelled at 2026-09-09T11:12:18.628785+00:00

The user requested cancellation. The memory gate had released and `timing_hw5`
had started IP generation before cancellation was processed. Its supervisor and
all identified descendant build processes were stopped; automatic launch is no
longer active. Completed `timing_sim4` simulation results and source changes are
preserved. No new routed timing result is available.

### Build restarted on the new machine — 2026-09-15 13:27 UTC

User requested a fresh hardware build. Vivado 2025.2 is executable and listed
as supported by HDK checkout `b603a81f`; configured HDK paths are present.
The machine has 62 GiB RAM, about 58 GiB available at launch, and 2 GiB swap.
All saved `timing_sim4` source fingerprints match. The isolated `timing_hw6`
build is running under supervisor PID 52152 and has launched Vivado IP
generation. Status: `aws/build/timing_hw6/status.json`; output:
`aws/build/timing_hw6/console.log`. The supervisor records final pass/failure.
No new timing result is available yet. U280 sources and prior simulations
are preserved.

### F2 routed build passed — 2026-09-16 01:45 JST

`timing_hw6` completed successfully (exit 0) at 2026-09-15 16:45 UTC.
Candidate B passes the implementation acceptance checks at 250 MHz compute /
450 MHz HBM: setup WNS +0.027 ns, TNS 0; hold WHS +0.010 ns, THS 0;
zero pulse-width violations and zero routing errors. The better initial routed
checkpoint was retained after comparing post-route optimization.
The accepted canonical checkpoint and `2026_09_15-133443.Developer_CL.tar`
were generated under `aws/build/timing_hw6/cl_hoge/build/checkpoints/`.
Acceptance record: `aws/build/timing_hw6/cl_hoge/build/reports/`
`cl_hoge.2026_09_15-133443.accepted.txt`. No AFI has been created and no
physical F2 hardware execution has been performed.
