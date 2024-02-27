add_executable(cpptest cpptest.cpp)
# verilate(cpptest SOURCES ../../../AXI4IKS.v THREADS 4 TRACE_FST)
verilate(cpptest SOURCES ../../../AXI4IKS.v TRACE_FST)
target_link_libraries(cpptest PUBLIC tfhe++)