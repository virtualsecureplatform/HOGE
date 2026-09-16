# Run inside the F2 staging directory, with Vivado supported by the installed HDK.
if {[info exists ::env(CL_DIR)]} {cd $::env(CL_DIR)}
create_project hoge_ip ./ip_project -part xcvu47p-fsvh2892-2-e -force
set_property target_language Verilog [current_project]
# Reuse the U280 DataMover settings, enabling output status only for F2.
set f [open $::env(HOGE_ROOT)/vitis/scripts/build_homgate_xo.tcl]
set original [read $f]
close $f
set first [string first {# Create DataMover IPs} $original]
set last [string first {# Generate DataMover IPs} $original $first]
if {$first < 0 || $last < $first} {error "Cannot locate DataMover configuration"}
eval [string range $original $first [expr {$last-1}]]
# With STSFIFO disabled the core ties status-valid low. F2 waits for actual
# write completion, so enable status in the existing S2MM DataMover instance.
set_property CONFIG.c_include_s2mm_stsfifo true [get_ips axi_datamover_0]
generate_target all [get_ips {axi_datamover_0 axi_datamover_1}]
create_ip -name clk_wiz -vendor xilinx.com -library ip -module_name hoge_hbm_clock
set_property -dict [list CONFIG.PRIM_SOURCE {No_buffer} CONFIG.PRIM_IN_FREQ {250.000} CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {450.000} CONFIG.RESET_TYPE {ACTIVE_LOW} CONFIG.USE_LOCKED {true}] [get_ips hoge_hbm_clock]
generate_target all [get_ips hoge_hbm_clock]
close_project
# synth_ip is a non-project command. Reopen the generated definitions in that
# flow so their checkpoints can also be consumed by the HDK non-project build.
foreach ip {axi_datamover_0 axi_datamover_1 hoge_hbm_clock} {
    read_ip ./ip_project/hoge_ip.srcs/sources_1/ip/${ip}/${ip}.xci
}
synth_ip [get_ips]
close_project
