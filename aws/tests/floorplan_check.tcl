set root [file normalize [file join [file dirname [info script]] ../..]]
set build $root/aws/build
if {[info exists ::env(HOGE_BUILD_ROOT)]} {set build [file normalize $::env(HOGE_BUILD_ROOT)]}
file mkdir $build/floorplan_test
cd $build/floorplan_test
create_project -in_memory -part xcvu47p-fsvh2892-2-e
read_verilog -sv $root/aws/rtl/hoge_local_reset.sv
synth_design -top hoge_local_reset -part xcvu47p-fsvh2892-2-e
# Exercise the exact HDK legal geometry using a small design. Omit only the
# final logical cell binding, because this geometry test has no Shell hierarchy.
set f [open $::env(HDK_SHELL_DIR)/build/constraints/small_shell_level_1_fp_cl.xdc]
set fp [read $f]; close $f
set binding {add_cells_to_pblock pblock_CL [get_cells [list WRAPPER/CL]]}
if {[string first $binding $fp]<0 || [string first $binding $fp]!=[string last $binding $fp]} {error "HDK floorplan binding changed"}
eval [string map [list $binding {}] $fp]
set legal {}
foreach site [get_property NAME [get_sites -of_objects [get_pblocks pblock_CL]]] {dict set legal $site 1}
source $root/aws/rtl/floorplan.xdc
foreach slr {0 1 2} {
 set p [hoge_region pblock_hoge_slr$slr $slr]
 set sites [get_sites -of_objects $p]
 foreach site [get_property NAME $sites] {if {![dict exists $legal $site]} {error "Child contains non-CL site $site"}}
 foreach cr [lsort -unique [get_property CLOCK_REGION $sites]] {
  if {$cr eq ""} {continue}
  regexp {X([0-9]+)Y([0-9]+)} $cr -> x y
  if {$y/4!=$slr} {error "SLR$slr leaks into $cr"}
 }
 puts "HOGE_REGION_PASS: SLR$slr [llength $sites] legal sites"
}
for {set x 0} {$x<8} {incr x} {
 set p [hoge_region pblock_hoge_port$x 0 $x $x 0 0]
 set crs [lsort -unique [get_property CLOCK_REGION [get_sites -of_objects $p]]]
 if {$crs ne "X${x}Y0"} {error "Port group leaks: $crs"}
 puts "HOGE_PORT_REGION_PASS: $crs"
}
foreach location {{1 5 6} {1 5 7} {1 5 4} {0 5 3} {0 5 2} {0 6 1}} {
 lassign $location slr x y
 set p [hoge_region pblock_hoge_pcis_${x}_${y} $slr $x $x $y $y]
 set sites [get_sites -of_objects $p]
 foreach site [get_property NAME $sites] {if {![dict exists $legal $site]} {error "PCIS region contains non-CL site $site"}}
 set crs [lsort -unique [get_property CLOCK_REGION $sites]]
 if {$crs ne "X${x}Y${y}"} {error "PCIS group leaks: $crs"}
 puts "HOGE_PCIS_REGION_PASS: $crs [llength $sites] sites"
}
puts "HOGE_FLOORPLAN_PASS: legal CL subsets, exact SLR and HBM regions"
close_project
exit
