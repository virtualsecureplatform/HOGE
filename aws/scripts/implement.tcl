# Called by the locally staged HDK build immediately after post_link.dcp.
# Both candidates start from the same synthesis and static Shell checkpoint.
source ${constraints_dir}/small_shell_cl_pnr_user.xdc

proc hoge_summary {path} {
    set report [report_timing_summary -delay_type min_max -max_paths 10 -report_unconstrained -return_string]
    set f [open $path w]; puts $f $report; close $f
    set pattern {(?m)^\s*(-?[0-9]+\.[0-9]+)\s+(-?[0-9]+\.[0-9]+)\s+[0-9]+\s+[0-9]+\s+(-?[0-9]+\.[0-9]+)\s+(-?[0-9]+\.[0-9]+)\s+[0-9]+\s+[0-9]+\s+(-?[0-9]+\.[0-9]+)\s+(-?[0-9]+\.[0-9]+)\s+[0-9]+\s+[0-9]+}
    if {![regexp $pattern $report -> wns tns whs ths wpws tpws]} {error "Missing timing summary statistics"}
    return [list $wns $tns $whs $ths $wpws $tpws]
}

proc hoge_capacity {path} {
    set f [open $path w]
    set valid 1
    foreach slr {0 1 2} {
        set report [report_utilization -pblocks [get_pblocks pblock_hoge_slr$slr] -return_string]
        puts $f $report
        # Vivado's Pblock utilization report accounts for hierarchy, packing
        # resources and non-CL occupancy. Reject any resource exceeding 100%.
        foreach line [split $report \n] {
            if {[regexp {\|\s*([0-9]+\.[0-9]+)\s*\|\s*$} $line -> percent] && $percent>100.0} {
                puts "ERROR: F2 SLR$slr capacity exceeded: $line"
                set valid 0
            }
        }
    }
    close $f
    return $valid
}

# One worst setup path per endpoint; aggregate all failures, not only the
# handful printed by report_timing_summary. Include the exact worst pins.
proc hoge_failing_families {path} {
    set groups {}
    set paths [get_timing_paths -delay_type max -slack_lesser_than 0 -max_paths 2000000 -nworst 1]
    foreach p $paths {
        set src [get_property STARTPOINT_PIN $p]
        set dst [get_property ENDPOINT_PIN $p]
        set slack [get_property SLACK $p]
        set origin [join [lrange [split $src /] 0 2] /]
        set family [join [lrange [split $dst /] 0 2] /]
        if {[regexp {WRAPPER/CL/HomGate_1/(datamover[0-9]+|DMA_[A-Z]+_[0-9]+)} $dst -> sub]} {append family /$sub}
        if {[regexp {WRAPPER/CL/HBM_CONVERTER_[0-9]+/.*(m00_exit_pipeline|s00_[rwb]+_node)} $dst -> sub]} {append family /$sub}
        set key "$origin -> $family"
        if {![dict exists $groups $key]} {dict set groups $key [list 0 0.0 0.0 $src $dst]}
        lassign [dict get $groups $key] count total worst ws wd
        if {$slack<$worst} {set worst $slack; set ws $src; set wd $dst}
        dict set groups $key [list [expr {$count+1}] [expr {$total+$slack}] $worst $ws $wd]
    }
    set rows {}
    dict for {key value} $groups {lappend rows [list [lindex $value 2] $key {*}$value]}
    set f [open $path w]
    puts $f "worst_slack family endpoint_count total_negative_slack worst_slack startpoint endpoint"
    foreach row [lsort -real -index 0 $rows] {puts $f $row}
    puts $f "TOTAL_ENDPOINTS [llength $paths]"
    close $f
}

set ranked {}
set failures {}
set first_candidate 1
foreach candidate {B} {
    if {!$first_candidate} {
        close_design
        open_checkpoint ${checkpoints_dir}/${CL}.${TAG}.post_link.dcp
    }
    set first_candidate 0
    if {[llength [get_pblocks -quiet pblock_CL]]==0} {
        read_xdc ${HDK_SHELL_DIR}/build/constraints/${SHELL_MODE}_level_1_fp_cl.xdc
    }
    set hbm [get_cells -quiet WRAPPER/CL/HBM/HBM_CORE_I/inst]
    if {[llength $hbm]!=1} {error "F2 HBM timing workaround target missing"}
    set_property DONT_TOUCH false $hbm
    puts "INFO: F2 applied AWS HBM DONT_TOUCH workaround to $hbm"
    opt_design
    hoge_floorplan $candidate
    set prefix ${reports_dir}/${CL}.${TAG}.${candidate}
    set cdc [report_cdc -cells [get_cells WRAPPER/CL] -severity Critical -details -return_string]
    set f [open ${prefix}.early_cdc.rpt w]; puts $f $cdc; close $f
    if {[regexp -line {^CDC-[0-9]+\s+Critical\s+[1-9]} $cdc]} {
        error "Unresolved critical CL CDC findings before placement: ${prefix}.early_cdc.rpt"
    }
    if {![hoge_capacity ${prefix}.capacity.rpt]} {
        lappend failures "$candidate: capacity exceeded"
        continue
    }
    write_checkpoint -force ${checkpoints_dir}/${CL}.${TAG}.${candidate}.post_opt.dcp
    if {[catch {
        place_design -directive SSI_SpreadLogic_high -no_bufg_opt
        write_checkpoint -force ${checkpoints_dir}/${CL}.${TAG}.${candidate}.post_place.dcp
        hoge_check_hbm_locations
        hoge_summary ${prefix}.post_place_timing.rpt
        report_design_analysis -congestion -file ${prefix}.congestion.rpt
        phys_opt_design -directive AggressiveExplore
        set stats [hoge_summary ${prefix}.post_phys_opt_timing.rpt]
        write_checkpoint -force ${checkpoints_dir}/${CL}.${TAG}.${candidate}.post_phys_opt.dcp
    } reason]} {
        lappend failures "$candidate: $reason"
        set f [open ${prefix}.failed.txt w]; puts $f $reason; close $f
        continue
    }
    lappend ranked [list $candidate {*}$stats]
}

proc hoge_rank {a b} {
    foreach i {1 2} {
        if {[lindex $a $i]>[lindex $b $i]} {return -1}
        if {[lindex $a $i]<[lindex $b $i]} {return 1}
    }
    return [string compare [lindex $a 0] [lindex $b 0]]
}
set ranked [lsort -command hoge_rank $ranked]
set f [open ${reports_dir}/${CL}.${TAG}.candidates.txt w]
puts $f "candidate WNS TNS WHS THS WPWS TPWS"
foreach row $ranked {puts $f $row}
foreach failure $failures {puts $f "REJECTED $failure"}
close $f
set accepted 0
foreach row $ranked {
    set candidate [lindex $row 0]
    close_design
    open_checkpoint ${checkpoints_dir}/${CL}.${TAG}.${candidate}.post_phys_opt.dcp
    set prefix ${reports_dir}/${CL}.${TAG}.${candidate}
    route_design -directive AggressiveExplore -tns_cleanup
    set initial_stats [hoge_summary ${prefix}.initial_route_timing.rpt]
    write_checkpoint -force ${checkpoints_dir}/${CL}.${TAG}.${candidate}.initial_route.dcp
    phys_opt_design -directive Explore
    # Physical optimization can leave new or moved nets needing routing.
    route_design -directive Explore -tns_cleanup
    set stats [hoge_summary ${prefix}.post_route_opt_timing.rpt]
    set initial_clean 1
    set optimized_clean 1
    foreach v $initial_stats {if {$v<0} {set initial_clean 0}}
    foreach v $stats {if {$v<0} {set optimized_clean 0}}
    if {($initial_clean && !$optimized_clean) || ($initial_clean==$optimized_clean && [hoge_rank [list initial {*}$initial_stats] [list optimized {*}$stats]]<0)} {
        write_checkpoint -force ${checkpoints_dir}/${CL}.${TAG}.${candidate}.post_route_opt.dcp
        close_design
        open_checkpoint ${checkpoints_dir}/${CL}.${TAG}.${candidate}.initial_route.dcp
        puts "INFO: Retained better initial routed timing for candidate $candidate"
    }
    set stats [hoge_summary ${prefix}.post_route_timing.rpt]
    hoge_failing_families ${prefix}.failing_families.txt
    report_route_status -file ${prefix}.route_status.rpt
    report_drc -file ${prefix}.drc.rpt
    report_cdc -cells [get_cells WRAPPER/CL] -details -file ${prefix}.cdc.rpt
    report_cdc -cells [get_cells WRAPPER/CL] -waived -details -file ${prefix}.cdc_waived.rpt
    report_bus_skew -file ${prefix}.bus_skew.rpt
    check_timing -verbose -file ${prefix}.check_timing.rpt
    report_utilization -slr -file ${prefix}.utilization.rpt
    write_checkpoint -force ${checkpoints_dir}/${CL}.${TAG}.${candidate}.post_route.dcp
    # A strict external report checker also covers unconstrained paths and CDC.
    if {[catch {exec python3 ${scripts_dir}/check_implementation.py $prefix} result]} {
        puts "WARNING: Candidate $candidate rejected: $result"
        continue
    }
    puts $result
    write_checkpoint -force ${checkpoints_dir}/${CL}.${TAG}.post_route.dcp
    write_debug_probes -no_partial_ltxfile -force ${checkpoints_dir}/${TAG}.debug_probes.ltx
    set f [open ${reports_dir}/${CL}.${TAG}.accepted.txt w]
    puts $f "$candidate $stats"; close $f
    set accepted 1
    break
}
close_design
if {!$accepted} {error "No F2 candidate passed routed implementation checks; see ${reports_dir}"}
