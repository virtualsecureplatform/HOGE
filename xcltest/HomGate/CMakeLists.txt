add_executable(nand nand.cpp)
find_package(OpenCL REQUIRED)
target_include_directories(
	nand
	PRIVATE
	${CMAKE_SOURCE_DIR}/thirdparties/xcl2
)
find_package(Threads REQUIRED)
target_link_libraries(nand PRIVATE tfhe++ xcl2 rt Threads::Threads )
file(COPY ../emconfig.json DESTINATION .)
file(COPY ../xrt.ini DESTINATION .)