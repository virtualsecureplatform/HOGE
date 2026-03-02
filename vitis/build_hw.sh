#!/bin/bash
# Hardware (hw) build script for HomGate 3-kernel FPGA design
# Builds everything from scratch: Chisel→Verilog, XO, xclbin, test binary
#
# Prerequisites:
#   - Xilinx Vitis 2023.2 installed at /home/opt/xilinx/Vitis/2023.2/
#   - Xilinx Vivado 2023.2 installed at /home/opt/xilinx/Vivado/2023.2/
#   - sbt (Scala Build Tool) available in PATH
#   - cmake and make available
#
# Usage:
#   ./vitis/build_hw.sh              # Build xclbin only
#   ./vitis/build_hw.sh --test       # Build xclbin and run nand test on real FPGA
#   ./vitis/build_hw.sh --clean      # Clean all build artifacts and rebuild
#
set -eo pipefail

# Parse arguments
RUN_TEST=false
CLEAN_BUILD=false
for arg in "$@"; do
    case $arg in
        --test) RUN_TEST=true ;;
        --clean) CLEAN_BUILD=true ;;
        *) echo "Unknown argument: $arg"; echo "Usage: $0 [--test] [--clean]"; exit 1 ;;
    esac
done

# Resolve directories
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
VITIS_DIR="${SCRIPT_DIR}"
BUILD_DIR="${VITIS_DIR}/build"
XO_DIR="${BUILD_DIR}/xo"
XCLBIN_DIR="${BUILD_DIR}/xclbin"
CHISEL_DIR="${REPO_DIR}/chisel/HomGate"

PLATFORM="xilinx_u280_gen3x16_xdma_1_202211_1"
TARGET="hw"

export BUILD_TARGET=${TARGET}

# Ensure TMPDIR is on a filesystem with enough space (not /tmp if it's tmpfs)
if [ -z "${TMPDIR}" ] || [ ! -d "${TMPDIR}" ]; then
    export TMPDIR="${BUILD_DIR}/tmp"
    mkdir -p "${TMPDIR}"
fi

ulimit -s unlimited

echo "============================================"
echo "HomGate hw Build (real FPGA)"
echo "============================================"
echo "Repository: ${REPO_DIR}"
echo "Vitis dir:  ${VITIS_DIR}"
echo "Build dir:  ${BUILD_DIR}"
echo "TMPDIR:     ${TMPDIR}"
echo "============================================"

# Clean if requested
if [ "${CLEAN_BUILD}" = true ]; then
    echo "Cleaning previous build artifacts..."
    rm -rf "${BUILD_DIR}"
    rm -f "${VITIS_DIR}/rtl/HomGateWrap.v"
fi

mkdir -p "${XO_DIR}" "${XCLBIN_DIR}"

# ============================================
# Step 1: Source Vitis environment
# ============================================
echo ""
echo "============================================"
echo "Step 1: Sourcing Vitis 2023.2 environment..."
echo "============================================"
if [ -f /home/opt/xilinx/Vitis/2023.2/settings64.sh ]; then
    source /home/opt/xilinx/Vitis/2023.2/settings64.sh
elif [ -n "${XILINX_VITIS}" ]; then
    echo "Using existing Vitis environment: ${XILINX_VITIS}"
else
    echo "ERROR: Cannot find Vitis 2023.2. Set XILINX_VITIS or install at /home/opt/xilinx/Vitis/2023.2/"
    exit 1
fi

# ============================================
# Step 2: Generate Verilog from Chisel
# ============================================
echo ""
echo "============================================"
echo "Step 2: Generating Verilog from Chisel..."
echo "============================================"
# Always regenerate for hw build (Config.scala may have changed)
echo "Running sbt to generate HomGateWrap.v..."
cd "${CHISEL_DIR}"
sbt run
cp "${CHISEL_DIR}/HomGateWrap.v" "${VITIS_DIR}/rtl/HomGateWrap.v"
echo "HomGateWrap.v generated and copied to vitis/rtl/"
cd "${REPO_DIR}"

# ============================================
# Step 3: Build XO files
# ============================================
echo ""
echo "============================================"
echo "Step 3a: Building HomGate XO (with DataMover IPs)..."
echo "============================================"
if [ ! -f "${XO_DIR}/HomGate.xo" ]; then
    vivado -mode batch -source "${VITIS_DIR}/scripts/build_homgate_xo.tcl" \
        -log "${BUILD_DIR}/build_homgate_xo.log" \
        -journal "${BUILD_DIR}/build_homgate_xo.jou"
else
    echo "HomGate.xo already exists, skipping."
fi

echo ""
echo "============================================"
echo "Step 3b: Building BRBack XO..."
echo "============================================"
if [ ! -f "${XO_DIR}/BRBack.xo" ]; then
    vivado -mode batch -source "${VITIS_DIR}/scripts/build_brback_xo.tcl" \
        -log "${BUILD_DIR}/build_brback_xo.log" \
        -journal "${BUILD_DIR}/build_brback_xo.jou"
else
    echo "BRBack.xo already exists, skipping."
fi

echo ""
echo "============================================"
echo "Step 3c: Building BRFront XO..."
echo "============================================"
if [ ! -f "${XO_DIR}/BRFront.xo" ]; then
    vivado -mode batch -source "${VITIS_DIR}/scripts/build_brfront_xo.tcl" \
        -log "${BUILD_DIR}/build_brfront_xo.log" \
        -journal "${BUILD_DIR}/build_brfront_xo.jou"
else
    echo "BRFront.xo already exists, skipping."
fi

# ============================================
# Step 4: Link xclbin
# ============================================
echo ""
echo "============================================"
echo "Step 4: Linking xclbin for hw (with multicore)..."
echo "  This will take 3-5 hours..."
echo "============================================"
XCLBIN_FILE="${XCLBIN_DIR}/HomGate_${TARGET}.xclbin"
if [ ! -f "${XCLBIN_FILE}" ]; then
    v++ -l -t "${TARGET}" \
        --platform "${PLATFORM}" \
        --config "${VITIS_DIR}/cfg/link_config.cfg" \
        --kernel_frequency 300 \
        --vivado.synth.jobs 8 \
        --vivado.impl.jobs 8 \
        -o "${XCLBIN_FILE}" \
        "${XO_DIR}/HomGate.xo" \
        "${XO_DIR}/BRBack.xo" \
        "${XO_DIR}/BRFront.xo" \
        --log_dir "${BUILD_DIR}/vpp_log" \
        --temp_dir "${BUILD_DIR}/vpp_temp" \
        --report_dir "${BUILD_DIR}/vpp_report"
else
    echo "xclbin already exists, skipping."
fi

echo ""
echo "============================================"
echo "hw xclbin build complete!"
echo "xclbin: ${XCLBIN_FILE}"
echo "============================================"

# ============================================
# Step 5: Build and run test (optional, for --test)
# ============================================
if [ "${RUN_TEST}" = true ]; then
    echo ""
    echo "============================================"
    echo "Step 5: Building nand test binary..."
    echo "============================================"
    TEST_BUILD_DIR="${BUILD_DIR}/nand_build"
    mkdir -p "${TEST_BUILD_DIR}"
    cd "${TEST_BUILD_DIR}"
    # Use /usr/bin/cmake to avoid Vitis's bundled cmake 3.3.2 (too old, need >= 3.18)
    /usr/bin/cmake "${REPO_DIR}" -G "Unix Makefiles"
    make -j$(nproc) nand
    cd "${REPO_DIR}"
    NAND_BIN="${TEST_BUILD_DIR}/xcltest/HomGate/nand"

    if [ ! -f "${NAND_BIN}" ]; then
        echo "ERROR: nand binary not found at ${NAND_BIN}"
        exit 1
    fi

    # ============================================
    # Step 6: Run nand test on real FPGA
    # ============================================
    echo ""
    echo "============================================"
    echo "Step 6: Running nand test on real FPGA..."
    echo "============================================"

    # No XCL_EMULATION_MODE for real FPGA
    unset XCL_EMULATION_MODE

    # Run the test with timeout (300s = 5 min, real FPGA is fast)
    cd "${REPO_DIR}"
    timeout 300 "${NAND_BIN}" "${XCLBIN_FILE}" 2>&1 | tee "${BUILD_DIR}/nand_test.log"
    TEST_EXIT=$?

    if [ ${TEST_EXIT} -eq 0 ]; then
        echo ""
        echo "============================================"
        echo "FPGA nand test PASSED!"
        echo "============================================"
    elif [ ${TEST_EXIT} -eq 124 ]; then
        echo ""
        echo "============================================"
        echo "FPGA nand test timed out (300s)"
        echo "Check ${BUILD_DIR}/nand_test.log for partial results"
        echo "============================================"
    else
        echo ""
        echo "============================================"
        echo "FPGA nand test FAILED (exit code: ${TEST_EXIT})"
        echo "Check ${BUILD_DIR}/nand_test.log for details"
        echo "============================================"
        exit 1
    fi
fi
