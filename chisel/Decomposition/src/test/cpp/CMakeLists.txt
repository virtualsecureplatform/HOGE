add_executable(cpptest cpptest.cpp)
verilate(cpptest SOURCES ../../../DecompositionWrap.v TRACE)
target_link_libraries(cpptest PUBLIC tfhe++)