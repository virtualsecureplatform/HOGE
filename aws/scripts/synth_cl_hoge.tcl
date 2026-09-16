source ${HDK_SHELL_DIR}/build/scripts/synth_cl_header.tcl
read_verilog -sv [glob ${src_post_enc_dir}/*.{sv,v}]
set local_ip ${CL_DIR}/aws_ip/cl_ip.srcs/sources_1
set local_gen ${CL_DIR}/aws_ip/cl_ip.gen/sources_1
read_ip ${local_ip}/ip/cl_hbm/cl_hbm.xci
set_property generate_synth_checkpoint false [get_files cl_hbm.xci]
generate_target synthesis [get_ips cl_hbm]
foreach ip {axi_datamover_0 axi_datamover_1 hoge_hbm_clock} {
    read_ip ${CL_DIR}/ip_project/hoge_ip.srcs/sources_1/ip/${ip}/${ip}.xci
}
add_files ${local_ip}/bd/cl_axi_sc_1x1/cl_axi_sc_1x1.bd
set_property generate_synth_checkpoint false [get_files cl_axi_sc_1x1.bd]
generate_target synthesis [get_files cl_axi_sc_1x1.bd]
read_verilog ${local_gen}/bd/cl_axi_sc_1x1/hdl/cl_axi_sc_1x1_wrapper.v
read_xdc ${constraints_dir}/cl_timing_user.xdc
update_compile_order -fileset sources_1
synth_design -mode out_of_context -top ${CL} -part ${DEVICE_TYPE} -keep_equivalent_registers
report_utilization -hierarchical -file ${CL_DIR}/build/reports/hoge_utilization.rpt
source ${HDK_SHELL_DIR}/build/scripts/synth_cl_footer.tcl
