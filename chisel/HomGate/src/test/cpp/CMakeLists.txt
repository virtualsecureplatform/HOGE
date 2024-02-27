add_executable(cpptest cpptest.cpp)
verilate(cpptest SOURCES ../../../HomGateWrap.v THREADS 8 TRACE_FST)
target_link_libraries(cpptest PUBLIC tfhe++)