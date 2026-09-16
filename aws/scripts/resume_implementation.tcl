set CL_DIR $::env(CL_DIR)
set HDK_SHELL_DIR $::env(HDK_SHELL_DIR)
set CL cl_hoge
set SHELL_MODE small_shell
set TAG [lindex $argv 0]
set scripts_dir ${CL_DIR}/build/scripts
set constraints_dir ${CL_DIR}/build/constraints
set checkpoints_dir ${CL_DIR}/build/checkpoints
set reports_dir ${CL_DIR}/build/reports
open_checkpoint ${checkpoints_dir}/${CL}.${TAG}.post_link.dcp
source ${scripts_dir}/implement.tcl
