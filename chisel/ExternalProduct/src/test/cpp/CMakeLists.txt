add_executable(cpptest cpptest.cpp)
verilate(cpptest SOURCES ../../../ExternalProductWrap.v THREADS 2 TRACE_FST)
# verilate(cpptest SOURCES ../../../ExternalProductWrap.v)
target_link_libraries(cpptest PUBLIC tfhe++)