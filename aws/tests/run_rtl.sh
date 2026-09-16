#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
BUILD=$(realpath -m "${HOGE_BUILD_ROOT:-$ROOT/aws/build}")
VIVADO_BIN=${HOGE_VIVADO:-$(command -v vivado)}
export XILINX_VIVADO=$(cd "$(dirname "$VIVADO_BIN")/.." && pwd)
export PATH="$XILINX_VIVADO/bin:$PATH"
mkdir -p "$BUILD/rtl_tests"
cd "$BUILD/rtl_tests"
xvlog --sv "$ROOT/aws/rtl/hoge_stream_pipeline.sv" "$ROOT/aws/rtl/hoge_dma_completion.sv" "$ROOT/aws/rtl/hoge_local_reset.sv" "$ROOT/aws/rtl/hoge_ready_tree.sv" \
    "$ROOT/aws/tests/pipeline_tb.sv" "$ROOT/aws/tests/completion_tb.sv" "$ROOT/aws/tests/reset_tb.sv" "$ROOT/aws/tests/reset_skew_tb.sv" "$ROOT/aws/tests/ready_tree_tb.sv" > compile.log 2>&1
for test in pipeline completion reset reset_skew ready_tree; do
    xelab "${test}_tb" -s "${test}_snapshot" > "$test.elaborate.log" 2>&1
    xsim "${test}_snapshot" -runall -log "$test.log" > "$test.console.log" 2>&1
    grep -q "HOGE_${test^^}_PASS:" "$test.log"
    if grep -Eq '^[[:space:]]*(Fatal:|Error:|ERROR:)' "$test.log"; then exit 1; fi
    grep "HOGE_${test^^}_PASS:" "$test.log"
done
