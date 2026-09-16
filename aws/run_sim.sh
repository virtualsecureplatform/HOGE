#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
export HOGE_BUILD_ROOT=$(realpath -m "${HOGE_BUILD_ROOT:-$ROOT/aws/build}")
: "${HDK_DIR:?Source the AWS HDK environment first}"
: "${AWS_FPGA_REPO_DIR:?Source the AWS HDK environment first}"
export HDK_COMMON_DIR="$HDK_DIR/common"
export HDK_SHELL_DESIGN_DIR="${HDK_SHELL_DIR:-$HDK_COMMON_DIR/shell_stable}/design"
VIVADO_BIN=${HOGE_VIVADO:-$(command -v vivado)}
version=$("$VIVADO_BIN" -version | head -n1 | tr '[:upper:]' '[:lower:]')
if ! grep -Fxq "$version" "$AWS_FPGA_REPO_DIR/supported_vivado_versions.txt"; then
    echo "Unsupported Vivado: $version" >&2; exit 1
fi
export XILINX_VIVADO=$(cd "$(dirname "$VIVADO_BIN")/.." && pwd)
export PATH="$XILINX_VIVADO/bin:$PATH"
export HOGE_VECTORS=$(realpath "${1:-$HOGE_BUILD_ROOT/vectors/case0}")
test -s "$HOGE_VECTORS/manifest.txt" || { echo 'Generate NAND vectors first (see aws/sim/README.md)' >&2; exit 1; }
test -s "$HOGE_BUILD_ROOT/cl_hoge/design/HomGateWrap.v" || { echo 'Stage the F2 RTL/IPs first (see aws/sim/README.md)' >&2; exit 1; }
export HOGE_HBM_MODEL=${HOGE_HBM_MODEL:-amd}
case "$HOGE_HBM_MODEL" in
  amd) SIM_WORK="$HOGE_BUILD_ROOT/hdk_sim" ;;
  axi) SIM_WORK="$HOGE_BUILD_ROOT/hdk_sim_axi" ;;
  *) echo 'HOGE_HBM_MODEL must be amd or axi' >&2; exit 1 ;;
esac
mkdir -p "$SIM_WORK"
exec 9>"$HOGE_BUILD_ROOT/.hardware.lock"
flock -n 9 || { echo 'Another F2 hardware/simulation build is running' >&2; exit 1; }
python3 "$ROOT/aws/sim/prepare_sources.py"
cd "$SIM_WORK"
SDK_USERS="$AWS_FPGA_REPO_DIR/sdk/userspace"
xsc "$SDK_USERS/utils/sh_dpi_tasks.c" "$HDK_COMMON_DIR/software/src/fpga_pci_sv.c" \
  --gcc_compile_options "-I$SDK_USERS/include" \
  --gcc_compile_options "-I$SDK_USERS/utils" \
  --gcc_compile_options "-I$HDK_COMMON_DIR/software/include" \
  --gcc_compile_options "-I$HDK_COMMON_DIR/software/src" \
  --gcc_compile_options '-DVIVADO_SIM' --gcc_compile_options '-DSV_TEST' \
  --gcc_compile_options '-DDMA_TEST' > xsc.log 2>&1
sim_args=()
pass_marker=HOGE_HDK_PASS:
if [[ ${HOGE_HBM_SMOKE:-0} == 1 ]]; then
    sim_args+=(-testplusarg hbm_smoke)
    pass_marker=HOGE_HDK_HBM_SMOKE_PASS:
fi
if [[ ${HOGE_DMA_SMOKE:-0} == 1 ]]; then
    [[ ${HOGE_HBM_SMOKE:-0} != 1 ]] || { echo 'Choose only one smoke test' >&2; exit 1; }
    sim_args+=(-testplusarg dma_smoke)
fi
simlog="$PWD/project/hoge_hdk.sim/sim_1/behav/xsim/simulate.log"
rm -f "$simlog"
vivado -mode batch -source "$ROOT/aws/sim/run.tcl" -log hdk_sim.log -journal hdk_sim.jou
cases=${HOGE_SIM_CASES:-1}
[[ $cases == 1 || $cases == 4 ]] || { echo 'HOGE_SIM_CASES must be 1 or 4' >&2; exit 1; }
[[ ${HOGE_HBM_SMOKE:-0} != 1 || $cases == 1 ]] || { echo 'Smoke uses one case' >&2; exit 1; }
[[ ${HOGE_DMA_SMOKE:-0} != 1 || $cases == 1 ]] || { echo 'DMA smoke uses one case' >&2; exit 1; }
pids=(); logs=(); dirs=(); vector_dirs=()
for ((i=0;i<cases;i++)); do
    case_dir=$(dirname "$simlog")
    vectors=$HOGE_VECTORS
    if [[ $cases == 4 ]]; then vectors="$(dirname "$HOGE_VECTORS")/case$i"; fi
    test -s "$vectors/manifest.txt" || { echo "Missing vectors: $vectors" >&2; exit 1; }
    if ((i>0)); then
        case_dir="$SIM_WORK/case$i"
        mkdir -p "$case_dir"
        cp --reflink=auto -a "$(dirname "$simlog")/." "$case_dir/"
    fi
    rm -f "$case_dir/simulate.log"
    dirs+=("$case_dir"); vector_dirs+=("$vectors")
done
for ((i=0;i<cases;i++)); do
    case_dir=${dirs[i]}; vectors=${vector_dirs[i]}
    (
        cd "$case_dir"
        xsim tb_behav -testplusarg "vectors=$vectors" \
            "${sim_args[@]}" -tclbatch "$ROOT/aws/sim/progress.tcl" -log simulate.log > simulation.console.log 2>&1
    ) &
    pids+=("$!"); logs+=("$case_dir/simulate.log")
done
failed=0
for pid in "${pids[@]}"; do wait "$pid" || failed=1; done
kind=nand
if [[ ${HOGE_HBM_SMOKE:-0} == 1 ]]; then kind=hbm-smoke; fi
if [[ ${HOGE_DMA_SMOKE:-0} == 1 ]]; then kind=dma-smoke; fi
python3 "$ROOT/aws/sim/check_results.py" --kind "$kind" "${logs[@]}" || failed=1
((failed==0)) || { echo "HDK simulation failed; inspect $SIM_WORK" >&2; exit 1; }
if [[ $kind == dma-smoke ]]; then
    cp "${logs[0]}" "$HOGE_BUILD_ROOT/dma_smoke.log"
fi
echo "HDK simulation PASS (memory model: $HOGE_HBM_MODEL, cases: $cases): $SIM_WORK"
