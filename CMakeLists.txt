project(yata-test CXX)
cmake_minimum_required(VERSION 3.18)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_FLAGS "-march=native -O3 -g")

find_package(OpenMP)
if(OpenMP_FOUND)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
endif()

# option(USE_80BIT_SECURITY "Use 80bit security parameter(faster)" ON)
# add_compile_definitions(USE_80BIT_SECURITY)
# add_subdirectory(chisel/YKP)

add_subdirectory(thirdparties/TFHEpp)

# For testing with Verilator, you can commnet out this line if you just want to run xclbin with a real FPGA.
add_subdirectory(chisel/HomGate)

# For developments
# add_subdirectory(chisel/Decomposition)
# add_subdirectory(chisel/INTT)
# add_subdirectory(chisel/ExternalProduct)
# add_subdirectory(chisel/PolynomialMulByXai)
# add_subdirectory(chisel/PolynomialMulByXaiMinusOne)
# add_subdirectory(chisel/PMBXMOL)
# add_subdirectory(chisel/BlindRotate)
# add_subdirectory(chisel/IdentityKeySwitching)
# add_subdirectory(chisel/SampleExtractIndex)
# add_subdirectory(chisel/SampleExtractIndexMemory)
# add_subdirectory(chisel/AXI4IKS)
# add_subdirectory(chisel/RAIKS)
# add_subdirectory(chisel/AXI4BR)
# add_subdirectory(chisel/AXI4BRmultiSLR)
# add_subdirectory(chisel/AXISBR)
# add_subdirectory(chisel/Bootstrapping)
# add_subdirectory(chisel/HomNAND)

# xcltest contains the codes to run with real FPGA
# Uncomment the following 
# add_subdirectory(thirdparties/xcl2)
# add_subdirectory(xcltest/HomGate)

# For developments
# add_subdirectory(xcltest/AXI4IKS)
# add_subdirectory(xcltest/AXISIKS)
# add_subdirectory(xcltest/AXI4BR)
# add_subdirectory(xcltest/AXISBR)
# add_subdirectory(xcltest/Bootstrapping)
# add_subdirectory(xcltest/HomNAND)
