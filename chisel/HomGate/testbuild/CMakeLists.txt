cmake_minimum_required(VERSION 3.18)
project(HomGateTest CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_FLAGS "-march=native -O3 -g")

find_package(OpenMP)
if(OpenMP_FOUND)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
endif()

add_subdirectory(/home/ubuntu/sources/HOGE-workspace/HOGE/thirdparties/TFHEpp tfhepp_build)

set(HOMGATE_DIR /home/ubuntu/sources/HOGE-workspace/HOGE/chisel/HomGate)

find_package(verilator QUIET)
if(verilator_FOUND)
    add_subdirectory(${HOMGATE_DIR}/src/test/cpp cpptest_build)
endif()
