
find_package(verilator)
file(COPY DecompositionWrap.v DESTINATION src/test/cpp)
add_subdirectory(src/test/cpp)