add_executable(cpptest cpptest.cpp)
verilate(cpptest SOURCES ../../../AXISBRWrapper.v THREADS 4 TRACE_FST)
target_link_libraries(cpptest PUBLIC tfhe++)