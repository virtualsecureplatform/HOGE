add_executable(xcltest host.cpp)
find_package(OpenCL REQUIRED)
target_include_directories(
	xcltest
	PRIVATE
	${CMAKE_SOURCE_DIR}/thirdparties/xcl2
)
find_package(Threads REQUIRED)
target_link_libraries(xcltest PRIVATE tfhe++ xcl2 rt Threads::Threads )
file(COPY ../emconfig.json DESTINATION .)