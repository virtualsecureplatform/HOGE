
find_package(verilator QUIET)
if(verilator_FOUND)
    add_subdirectory(src/test/cpp)
endif()