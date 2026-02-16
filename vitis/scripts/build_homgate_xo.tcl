#!/usr/bin/env vivado -mode batch -source
# Build HomGate XO file with DataMover IPs

set script_dir [file dirname [file normalize [info script]]]
set workspace_dir [file dirname $script_dir]
set rtl_dir ${workspace_dir}/rtl
set xml_dir ${workspace_dir}/xml
set build_dir ${workspace_dir}/build
set xo_dir ${build_dir}/xo

# Target
set target hw_emu
if {[info exists ::env(BUILD_TARGET)]} {
    set target $::env(BUILD_TARGET)
}

set kernel_name HomGate
set project_name ${kernel_name}_kernel
set project_dir ${build_dir}/${project_name}
set ip_repo_dir ${project_dir}/ip_repo

# Clean previous build
file delete -force ${project_dir}
file mkdir ${xo_dir}

# Create Vivado project
create_project ${project_name} ${project_dir} -part xcu280-fsvh2892-2L-e -force

# Set project properties
set_property target_language Verilog [current_project]

# Import RTL sources
import_files -norecurse [list \
    ${rtl_dir}/HomGateWrap.v \
    ${rtl_dir}/HomGate_top.v \
    ${rtl_dir}/HomGate_control_s_axi.v \
]

set_property top HomGate [current_fileset]

# Create DataMover IPs
# IP0: S2MM only (for output write-back, 32-bit stream -> 512-bit memory)
create_ip -name axi_datamover -vendor xilinx.com -library ip -version 5.1 -module_name axi_datamover_0
set_property -dict [list \
    CONFIG.c_enable_mm2s {0} \
    CONFIG.c_addr_width {64} \
    CONFIG.c_m_axi_s2mm_addr_width {64} \
    CONFIG.c_m_axi_s2mm_data_width {512} \
    CONFIG.c_s_axis_s2mm_tdata_width {32} \
    CONFIG.c_s2mm_btt_used {23} \
    CONFIG.c_include_s2mm_stsfifo {false} \
    CONFIG.c_s2mm_stscmd_is_async {false} \
    CONFIG.c_s2mm_burst_size {16} \
    CONFIG.c_s2mm_support_indet_btt {false} \
    CONFIG.c_m_axi_s2mm_id_width {4} \
    CONFIG.c_include_s2mm_dre {true} \
] [get_ips axi_datamover_0]

# IP1: MM2S only (for memory reads, 512-bit memory -> 512-bit stream)
create_ip -name axi_datamover -vendor xilinx.com -library ip -version 5.1 -module_name axi_datamover_1
set_property -dict [list \
    CONFIG.c_enable_s2mm {0} \
    CONFIG.c_addr_width {64} \
    CONFIG.c_m_axi_mm2s_addr_width {64} \
    CONFIG.c_m_axi_mm2s_data_width {512} \
    CONFIG.c_m_axis_mm2s_tdata_width {512} \
    CONFIG.c_mm2s_btt_used {23} \
    CONFIG.c_include_mm2s_stsfifo {false} \
    CONFIG.c_mm2s_stscmd_is_async {false} \
    CONFIG.c_mm2s_burst_size {4} \
    CONFIG.c_m_axi_mm2s_id_width {1} \
    CONFIG.c_include_mm2s_dre {false} \
] [get_ips axi_datamover_1]

# Generate DataMover IPs
generate_target all [get_ips axi_datamover_0]
generate_target all [get_ips axi_datamover_1]

update_compile_order -fileset sources_1

# Package the project as an IP
ipx::package_project -root_dir ${ip_repo_dir} -vendor mycompany.com -library kernel -taxonomy /KernelIP -import_files -set_current true

# Set kernel properties for Vitis
set_property sdx_kernel true [ipx::current_core]
set_property sdx_kernel_type rtl [ipx::current_core]

# Associate all bus interfaces with ap_clk
ipx::associate_bus_interfaces -busif s_axi_control -clock ap_clk [ipx::current_core]
for {set i 0} {$i <= 20} {incr i} {
    ipx::associate_bus_interfaces -busif [format "m%02d_axi" $i] -clock ap_clk [ipx::current_core]
}
for {set i 0} {$i <= 9} {incr i} {
    ipx::associate_bus_interfaces -busif [format "axis%02d" $i] -clock ap_clk [ipx::current_core]
}

# Save IP
ipx::create_xgui_files [ipx::current_core]
ipx::update_checksums [ipx::current_core]
ipx::save_core [ipx::current_core]

close_project

# Package as XO
set xo_file ${xo_dir}/${kernel_name}.xo
if {[file exists ${xo_file}]} {
    file delete -force ${xo_file}
}

package_xo -xo_path ${xo_file} \
    -kernel_name ${kernel_name} \
    -ip_directory ${ip_repo_dir} \
    -kernel_xml ${xml_dir}/${kernel_name}_kernel.xml

puts "HomGate XO built successfully: ${xo_file}"
