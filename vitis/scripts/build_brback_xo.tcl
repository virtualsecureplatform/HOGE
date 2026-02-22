#!/usr/bin/env vivado -mode batch -source
# Build BRBack XO file (stream-only kernel, no DataMover IPs needed)

set script_dir [file dirname [file normalize [info script]]]
set workspace_dir [file dirname $script_dir]
set rtl_dir ${workspace_dir}/rtl
set xml_dir ${workspace_dir}/xml
set build_dir ${workspace_dir}/build
set xo_dir ${build_dir}/xo

set kernel_name BRBack
set project_name ${kernel_name}_kernel
set project_dir ${build_dir}/${project_name}
set ip_repo_dir ${project_dir}/ip_repo

# Clean previous build
file delete -force ${project_dir}
file mkdir ${xo_dir}

# Create Vivado project
create_project ${project_name} ${project_dir} -part xcu280-fsvh2892-2L-e -force

set_property target_language Verilog [current_project]

# Import RTL sources
import_files -norecurse [list ${rtl_dir}/HomGateWrap.v ${rtl_dir}/BRBack_top.v]
set_property top BRBack [current_fileset]

update_compile_order -fileset sources_1

# Package the project as an IP
ipx::package_project -root_dir ${ip_repo_dir} -vendor mycompany.com -library kernel -taxonomy /KernelIP -import_files -set_current true

# Set kernel properties for Vitis
set_property sdx_kernel true [ipx::current_core]
set_property sdx_kernel_type rtl [ipx::current_core]

# Associate all bus interfaces with ap_clk
for {set i 0} {$i <= 18} {incr i} {
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

puts "BRBack XO built successfully: ${xo_file}"
