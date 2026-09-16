set root [file normalize [file join [file dirname [info script]] ../..]]
set build $root/aws/build
if {[info exists ::env(HOGE_BUILD_ROOT)]} {set build [file normalize $::env(HOGE_BUILD_ROOT)]}
set work $build/hdk_sim
set fast [expr {[info exists ::env(HOGE_HBM_MODEL)] && $::env(HOGE_HBM_MODEL) eq "axi"}]
if {$fast} {set work $build/hdk_sim_axi}
set cl $build/cl_hoge
create_project hoge_hdk $work/project -part xcvu47p-fsvh2892-2-e -force
cd $work
set_property XPM_LIBRARIES {XPM_CDC XPM_MEMORY XPM_FIFO} [current_project]
foreach ip {axi_datamover_0 axi_datamover_1 hoge_hbm_clock} {
    read_ip $cl/ip_project/hoge_ip.srcs/sources_1/ip/$ip/$ip.xci
}
if {!$fast} {read_ip $cl/aws_ip/cl_ip.srcs/sources_1/ip/cl_hbm/cl_hbm.xci}
read_bd $cl/aws_ip/cl_ip.srcs/sources_1/bd/cl_axi_sc_1x1/cl_axi_sc_1x1.bd
generate_target simulation [get_ips -quiet {axi_datamover_0 axi_datamover_1 hoge_hbm_clock cl_hbm}]
generate_target simulation [get_files cl_axi_sc_1x1.bd]
add_files $cl/aws_ip/cl_ip.gen/sources_1/bd/cl_axi_sc_1x1/hdl/cl_axi_sc_1x1_wrapper.v
foreach f [glob $cl/design/*.{v,sv}] {
    if {$fast && [file tail $f] eq "cl_mem_hbm_wrapper.sv"} {continue}
    add_files $f
}
if {$fast} {add_files $work/cl_mem_hbm_wrapper.sv}
source $work/hdk_sources.tcl
add_files -fileset sim_1 $root/aws/sim/test_hoge.sv
set_property include_dirs [concat $hdk_includes [list $cl/design]] [get_filesets sim_1]
set_property verilog_define {CL_NAME=cl_hoge VIVADO_SIM CARD_1=card SIMULATION AWS_SIM DISABLE_VJTAG_DEBUG NO_DDR} [get_filesets sim_1]
if {$fast} {set_property verilog_define [concat [get_property verilog_define [get_filesets sim_1]] HOGE_AXI_MEMORY_MODEL] [get_filesets sim_1]}
set_property top tb [get_filesets sim_1]
set_property xsim.elaborate.xelab.more_options "xil_defaultlib.test_hoge -sv_root $work/xsim.dir/work/xsc -sv_lib dpi" [get_filesets sim_1]
set_property -dict [list xsim.simulate.xsim.more_options "-testplusarg vectors=$::env(HOGE_VECTORS)"] [get_filesets sim_1]
set_property xsim.simulate.runtime all [get_filesets sim_1]
update_compile_order -fileset sim_1
launch_simulation -step compile
set simdir $work/project/hoge_hdk.sim/sim_1/behav/xsim
# The HDK uses two simulation roots. Vivado prunes the uninstantiated test
# from its tb compile order, so compile that second root explicitly afterward.
cd $simdir
set test_args [list --sv --work xil_defaultlib]
if {$fast} {lappend test_args --define HOGE_AXI_MEMORY_MODEL}
exec $::env(XILINX_VIVADO)/bin/xvlog {*}$test_args $root/aws/sim/test_hoge.sv > test_compile.log 2>@1
cd $work
launch_simulation -step elaborate
# AWS supplies XSIM-specific HBM configuration (DBI disabled). Copy after
# Vivado exports IP files so its default configuration cannot overwrite these.
foreach f [glob $::env(HDK_COMMON_DIR)/verif/include/xsim_hbm/*.mem] {
    file copy -force $f $simdir
}
close_project
