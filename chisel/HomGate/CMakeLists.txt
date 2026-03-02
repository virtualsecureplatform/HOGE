cmake_minimum_required(VERSION 3.16)
project(HomGateTest)

find_package(verilator QUIET)
if(verilator_FOUND)
    add_subdirectory(src/test/cpp)
endif()