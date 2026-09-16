set root [file normalize [file join [file dirname [info script]] ../..]]
set build ${root}/aws/build
if {[info exists ::env(HOGE_BUILD_ROOT)]} {set build [file normalize $::env(HOGE_BUILD_ROOT)]}
create_project fifo_test ${build}/fifo_sim -part xcvu47p-fsvh2892-2-e -force
cd $build
set_property XPM_LIBRARIES {XPM_FIFO} [current_project]
add_files ${root}/aws/rtl/hoge_axis_fifo.sv
add_files ${root}/aws/rtl/hoge_local_reset.sv
add_files -fileset sim_1 ${root}/aws/tests/axis_fifo_tb.sv
set_property top axis_fifo_tb [get_filesets sim_1]
set_property xsim.simulate.runtime all [get_filesets sim_1]
launch_simulation
close_sim
set f [open ${build}/fifo_sim/fifo_test.sim/sim_1/behav/xsim/simulate.log]
set result [read $f]
close $f
if {![string match {*F2 AXIS FIFO test passed*} $result] || [string match {*Fatal:*} $result]} {
    error "FIFO simulation failed; inspect simulate.log"
}
close_project
