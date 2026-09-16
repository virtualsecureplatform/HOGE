#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
export HOGE_BUILD_ROOT=$(realpath -m "${HOGE_BUILD_ROOT:-$ROOT/aws/build}")
mkdir -p "$HOGE_BUILD_ROOT"
exec 9>"$HOGE_BUILD_ROOT/.hardware.lock"
flock -n 9 || { echo 'Another F2 hardware build is running' >&2; exit 1; }
: "${HDK_DIR:?Source aws-fpga/hdk_setup.sh with a supported Vivado first}"
: "${AWS_FPGA_REPO_DIR:?Source aws-fpga/hdk_setup.sh first}"
# Explicit checking prevents silently using an unsupported newer Vivado.
VIVADO_BIN=${HOGE_VIVADO:-$(command -v vivado)}
export XILINX_VIVADO=$(cd "$(dirname "$VIVADO_BIN")/.." && pwd)
export PATH="$XILINX_VIVADO/bin:$PATH"
version=$(vivado -version | head -n 1 | tr '[:upper:]' '[:lower:]')
if ! grep -Fxq "$version" "$AWS_FPGA_REPO_DIR/supported_vivado_versions.txt"; then
    echo "Unsupported Vivado: $version. See $AWS_FPGA_REPO_DIR/supported_vivado_versions.txt" >&2
    exit 1
fi
if [[ ${1:-} == --resume && $# == 2 ]]; then
    python3 "$ROOT/aws/scripts/resume_implementation.py" "$2"
    exit 0
fi
if [[ ${1:-} == --rtl && $# == 2 ]]; then
    rtl=$(realpath "$2")
elif [[ $# == 0 ]]; then
    command -v sbt >/dev/null || { echo 'Install sbt, or pass --rtl /path/to/HomGateWrap.v' >&2; exit 1; }
    python3 - "$ROOT" <<'PY'
from pathlib import Path
import shutil
import os
import sys
root = Path(sys.argv[1])
dest = Path(os.environ['HOGE_BUILD_ROOT'])/'chisel'
dest.mkdir(parents=True, exist_ok=True)
if (dest/'src').exists():
    shutil.rmtree(dest/'src')
shutil.copytree(root/'chisel/HomGate/src', dest/'src')
shutil.copy2(root/'chisel/HomGate/build.sbt', dest/'build.sbt')
(dest/'project').mkdir(exist_ok=True)
(dest/'project/build.properties').write_text('sbt.version=1.9.9\n')
PY
    (cd "$HOGE_BUILD_ROOT/chisel" && sbt run)
    rtl="$HOGE_BUILD_ROOT/chisel/HomGateWrap.v"
else
    echo "Usage: $0 [--rtl /path/to/HomGateWrap.v | --resume SOURCE_TAG]" >&2; exit 1
fi
python3 "$ROOT/aws/scripts/prepare.py" --rtl "$rtl"
export CL=cl_hoge CL_DIR="$HOGE_BUILD_ROOT/cl_hoge" HOGE_ROOT="$ROOT"
cd "$CL_DIR"
vivado -mode batch -source "$ROOT/aws/scripts/create_ip.tcl" -log create_ip.log -journal create_ip.jou
for ip in axi_datamover_0 axi_datamover_1 hoge_hbm_clock; do
    test -s "ip_project/hoge_ip.gen/sources_1/ip/$ip/$ip.dcp" || {
        echo "IP synthesis failed: $ip. See $CL_DIR/create_ip.log" >&2; exit 1;
    }
done
cd build/scripts
tag=$(date -u +%Y_%m_%d-%H%M%S)
python3 aws_build_dcp_from_cl.py --cl cl_hoge --tag "$tag" --no-encrypt
# The upstream driver can return success even when Vivado reports an error.
test -s "../checkpoints/cl_hoge.$tag.post_route.dcp" &&
test ! -e "../checkpoints/cl_hoge.$tag.post_route.VIOLATED.dcp" &&
test -s "../reports/cl_hoge.$tag.accepted.txt" &&
test -s "../checkpoints/$tag.Developer_CL.tar" || {
    echo "F2 build failed or did not meet timing. See $CL_DIR/build/scripts/$tag.vivado.log" >&2
    exit 1
}
echo "F2 routed implementation passed: $CL_DIR/build/reports/cl_hoge.$tag.accepted.txt"
