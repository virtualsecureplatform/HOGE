add_library(xcl2 xcl2.cpp)
find_package(OpenCL REQUIRED)
target_include_directories(
	xcl2
	PUBLIC
	/opt/xilinx/xrt/include
	${OpenCL_INCLUDE_DIRS}
)
target_link_directories(
	xcl2
	PUBLIC
	/opt/xilinx/xrt/lib
)
target_link_libraries(
	xcl2
	PUBLIC
	${OpenCL_LIBRARIES}
)