add_executable(cpptest cpptest.cpp)
verilate(cpptest SOURCES ../../../IdentityKeySwitchingWrap.v TRACE_FST)
target_link_libraries(cpptest PUBLIC tfhe++)