add_executable(cpptest cpptest.cpp)
verilate(cpptest SOURCES ../../../INTTWrap.v TRACE_FST)
target_link_libraries(cpptest PUBLIC tfhe++)