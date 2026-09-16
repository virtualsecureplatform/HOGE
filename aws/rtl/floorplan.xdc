# Executed as Tcl after opt_design. Clone the actual Small Shell legal region;
# never expand a child into static Shell resources.
# Build fabric regions from the parent's actual site set. GRID_RANGES alone
# misses snapping, and cutting CLOCKREGION ranges can add virtual clock sites
# at new boundaries. Hard HBM/clocking primitives retain their parent/LOC rules.
proc hoge_region {name slr {xmin 0} {xmax 7} {ymin 0} {ymax 11}} {
    global hoge_legal_sites
    puts "INFO: Building legal fabric region $name (SLR$slr)"
    if {![info exists hoge_legal_sites]} {
        set hoge_legal_sites {}
        set sites [get_sites -of_objects [get_pblocks pblock_CL] -filter {SITE_TYPE =~ SLICE* || SITE_TYPE =~ RAMB* || SITE_TYPE =~ DSP* || SITE_TYPE =~ URAM* || SITE_TYPE =~ LAGUNA*}]
        foreach site [get_property NAME $sites] cr [get_property CLOCK_REGION $sites] {
            if {$cr eq ""} {error "Fabric site lacks clock region: $site"}
            dict lappend hoge_legal_sites $cr $site
        }
    }
    set low [expr {max(4*$slr,$ymin)}]
    set high [expr {min(4*$slr+3,$ymax)}]
    set columns {}
    dict for {cr sites} $hoge_legal_sites {
        regexp {X([0-9]+)Y([0-9]+)} $cr -> x y
        if {$x<$xmin || $x>$xmax || $y<$low || $y>$high} {continue}
        foreach site $sites {
            if {![regexp {^(.+_X[0-9]+)Y([0-9]+)$} $site -> column row]} {error "Unknown fabric site $site"}
            dict lappend columns $column $row
        }
    }
    set strips {}
    dict for {column rows} $columns {
        regexp {^(.+)_X([0-9]+)$} $column -> type x
        set first -1; set last -1
        foreach row [lsort -integer -unique $rows] {
            if {$first<0} {set first $row}
            if {$last>=0 && $row!=$last+1} {
                dict lappend strips ${type}:${first}:${last} $x
                set first $row
            }
            set last $row
        }
        if {$first>=0} {dict lappend strips ${type}:${first}:${last} $x}
    }
    set ranges {}
    dict for {strip xs} $strips {
        lassign [split $strip :] type y0 y1
        set first -1; set last -1
        foreach x [lsort -integer -unique $xs] {
            if {$first<0} {set first $x}
            if {$last>=0 && $x!=$last+1} {
                lappend ranges ${type}_X${first}Y${y0}:${type}_X${last}Y${y1}
                set first $x
            }
            set last $x
        }
        if {$first>=0} {lappend ranges ${type}_X${first}Y${y0}:${type}_X${last}Y${y1}}
    }
    if {[llength $ranges]==0} {error "No legal CL resources for $name"}
    set p [create_pblock $name]
    resize_pblock $p -add $ranges
    set parent pblock_CL
    if {![string match pblock_hoge_slr* $name]} {set parent pblock_hoge_slr$slr}
    set_property PARENT $parent $p
    set_property SNAPPING_MODE OFF $p
    set_property IS_SOFT false $p
    puts "INFO: Completed $name with [llength $ranges] resource ranges"
    return $p
}

proc hoge_add {p cells} {
    if {[llength $cells]==0} {error "Empty F2 placement group for $p"}
    add_cells_to_pblock $p $cells
}

proc hoge_stages {prefix source dest} {
    set middle [expr {($source+$dest)/2}]
    foreach stage {0 1 2} slr [list $source $middle $dest] {
        set cells [get_cells -quiet -hier -regexp [format {%s/STAGE\[%d\][./].*} $prefix $stage] -filter {IS_PRIMITIVE}]
        hoge_add [get_pblocks pblock_hoge_slr$slr] $cells
    }
}

proc hoge_check_hbm_locations {} {
    foreach c [get_cells -hier -filter {REF_NAME == HBM_SNGLBLI_INTF_AXI}] {
        if {![regexp {HBM_ONE_STACK_INTF<([01])>_INST/HBM_SNGLBLI_INTF_AXI<([0-9]+)>_INST$} $c -> stack port]} {
            error "Unexpected placed HBM primitive $c"
        }
        set bank [expr {16*$stack+$port}]
        set loc [get_property LOC $c]
        if {$loc ne "BLI_HBM_AXI_INTF_X${bank}Y0"} {
            error "Placed HBM interface $bank at $loc differs from its fabric floorplan"
        }
    }
}

proc hoge_floorplan {candidate} {
    global CL_DIR
    if {$candidate ni {A B}} {error "Unknown F2 floorplan $candidate"}
    source $CL_DIR/design/physical_metadata.tcl
    foreach s {0 1 2} {hoge_region pblock_hoge_slr$s $s}
    set back [expr {$candidate eq "A" ? 2 : 1}]
    set front [expr {3-$back}]
    hoge_add pblock_hoge_slr0 [get_cells WRAPPER/CL/HomGate_1]
    hoge_add pblock_hoge_slr$back [get_cells WRAPPER/CL/BRBack_1]
    hoge_add pblock_hoge_slr$front [get_cells WRAPPER/CL/BRFront_1]
    foreach kernel {HomGate BRBack BRFront} slr [list 0 $back $front] {
        hoge_add pblock_hoge_slr$slr [get_cells WRAPPER/CL/${kernel}_RESET]
    }
    # Constrain HBM fabric logic, leaving its fixed hard interfaces in pblock_CL.
    hoge_add pblock_hoge_slr0 [get_cells -hier -filter {NAME =~ WRAPPER/CL/HBM/* && IS_PRIMITIVE && (REF_NAME =~ LUT* || REF_NAME =~ FD* || REF_NAME =~ LD* || REF_NAME =~ CARRY* || REF_NAME =~ MUXF* || REF_NAME =~ RAM* || REF_NAME =~ SRL*)}]
    set slrs [dict create HomGate 0 BRBack $back BRFront $front]
    source $CL_DIR/design/stream_floorplan.tcl
    foreach connection $hoge_streams {
        lassign $connection index producer consumer
        set src [dict get $slrs $producer]
        set dst [dict get $slrs $consumer]
        hoge_add pblock_hoge_slr$src [get_cells WRAPPER/CL/STREAM_$index]
        hoge_stages WRAPPER/CL/STREAM_PIPE_$index $src $dst
    }
    foreach entry $hoge_shell_stages {
        lassign $entry name count forward
        if {[string match PCIS_* $name]} {
            # CL regions adjacent to fixed shell PCIS registers (X6Y6/Y7).
            set shell_y [expr {$name eq "PCIS_R" ? 7 : 6}]
            set locations [list [list 1 5 $shell_y] {1 5 4} {0 5 3} {0 5 2} {0 6 1}]
            if {!$forward} {set locations [lreverse $locations]}
            if {$count!=[llength $locations]} {error "PCIS placement metadata mismatch"}
            for {set stage 0} {$stage<$count} {incr stage} {
                lassign [lindex $locations $stage] slr x y
                set p [hoge_region pblock_hoge_${name}_$stage $slr $x $x $y $y]
                hoge_add $p [get_cells -hier -regexp [format {WRAPPER/CL/%s/STAGE\[%d\][./].*} $name $stage] -filter {IS_PRIMITIVE}]
            }
        } else {
            hoge_stages WRAPPER/CL/$name [expr {$forward ? 1 : 0}] [expr {$forward ? 0 : 1}]
        }
    }
    set control [hoge_region pblock_hoge_control 0 3 4 2 2]
    hoge_add $control [get_cells WRAPPER/CL/HomGate_1/inst_control_s_axi]
    hoge_add $control [get_cells WRAPPER/CL/HomGate_1/DMA_COMPLETION]
    foreach channel {AW W AR B R} stage {2 2 2 0 0} {
        hoge_add $control [get_cells -hier -regexp [format {WRAPPER/CL/OCL_%s/STAGE\[%d\][./].*} $channel $stage] -filter {IS_PRIMITIVE}]
    }
    set banks {0 1 2 3 4 5 6 7 8 9 10 11 12 20 21 22 23 24 25 26 27 31}
    for {set i 0} {$i<22} {incr i} {
        set bank [lindex $banks $i]
        # Adjacent CR columns leave room for DMA BRAM near each HBM port.
        set x [expr {$bank/4}]
        set left [expr {$x==7 ? 6 : $x}]
        set p [hoge_region pblock_hoge_bank$bank 0 $left [expr {$left+1}]]
        hoge_add $p [get_cells WRAPPER/CL/HBM_CONVERTER_$i]
        hoge_add $p [get_cells WRAPPER/CL/CONVERTER_RESET_$i]
        foreach entry $hoge_bk_fifos {
            lassign $entry fifo fifo_bank
            if {$fifo_bank==$bank} {hoge_add $p [get_cells WRAPPER/CL/STREAM_$fifo]}
        }
        set exit_port [hoge_region pblock_hoge_exitport$bank 0 $x $x 0 0]
        set_property PARENT $p $exit_port
        hoge_add $exit_port [get_cells -hier -regexp [format {WRAPPER/CL/HBM_CONVERTER_%d/.*m00_exit_pipeline/.*} $i] -filter {IS_PRIMITIVE}]
        # The host's write/response FIFO endpoints also need a short HBM path.
        if {$i==21 || $i==0} {
            hoge_add $exit_port [get_cells -hier -regexp [format {WRAPPER/CL/HBM_CONVERTER_%d/.*s00_[wb]_node/.*} $i] -filter {IS_PRIMITIVE}]
        }
        if {$i>0} {
            # HBM RVALID drives the 450 MHz read upsizer's wide enables.
            # Keep this fabric-only logic at the port, while adapter RAMs and
            # the 250 MHz side retain the larger two-column bank region.
            set read_port [hoge_region pblock_hoge_readport$bank 0 $x $x 0 0]
            set_property PARENT $p $read_port
            hoge_add $read_port [get_cells -hier -regexp [format {WRAPPER/CL/HBM_CONVERTER_%d/.*s00_r_node/.*gen_upsizer\.inst_upsizer/.*} $i] -filter {IS_PRIMITIVE}]
        }
        if {$i<21} {
            hoge_add $p [get_cells [format {WRAPPER/CL/HomGate_1/datamover%02d} $i]]
            hoge_add $p [get_cells WRAPPER/CL/HomGate_1/DMA_RESET_$i]
            hoge_add $p [get_cells -hier -regexp [format {WRAPPER/CL/HomGate_1/DMA_CMD_%d/STAGE\[2\][./].*} $i] -filter {IS_PRIMITIVE}]
            if {$i>=1 && $i<=12} {
                hoge_add $p [get_cells -hier -regexp [format {WRAPPER/CL/HomGate_1/DMA_READ_%d/STAGE\[0\][./].*} $i] -filter {IS_PRIMITIVE}]
            }
            if {$i==0} {
                hoge_add $p [get_cells -hier -regexp {WRAPPER/CL/HomGate_1/DMA_WRITE_0/STAGE\[2\][./].*} -filter {IS_PRIMITIVE}]
                hoge_add $p [get_cells -hier -regexp {WRAPPER/CL/HomGate_1/DMA_STATUS_0/STAGE\[0\][./].*} -filter {IS_PRIMITIVE}]
                hoge_add $p [get_cells -hier -regexp {WRAPPER/CL/HomGate_1/axi4sout_output_word_cnt_reg.*}]
            }
        }
    }
    # Locate the critical gate INSIDE generated HBM by connectivity.
    set ports [get_cells -hier -filter {REF_NAME == HBM_SNGLBLI_INTF_AXI}]
    if {[llength $ports]!=32} {error "Expected 32 concrete HBM AXI primitives"}
    set found 0
    set seen {}
    foreach c $ports {
        # Before placement Vivado can leave LOC empty on these hard ports.
        # The generated two-stack hierarchy identifies the physical interface.
        if {![regexp {HBM_ONE_STACK_INTF<([01])>_INST/HBM_SNGLBLI_INTF_AXI<([0-9]+)>_INST$} $c -> stack port] || $port>15} {
            error "Unexpected HBM primitive hierarchy: $c"
        }
        set bank [expr {16*$stack+$port}]
        if {$bank in $seen} {error "Duplicate HBM interface $bank"}
        lappend seen $bank
        set loc [get_property LOC $c]
        set expected BLI_HBM_AXI_INTF_X${bank}Y0
        if {$loc ne "" && $loc ne $expected} {error "HBM interface $bank has unexpected location $loc"}
        # Leave the HBM macro's relative placement to Vivado. Setting LOC on
        # individual members can move other members of the generated shape.
        set gate [get_cells -quiet -of_objects [get_pins -quiet -leaf -of_objects [get_nets -of_objects [get_pins $c/RREADY]] -filter {DIRECTION == OUT}] -filter {REF_NAME =~ LUT*}]
        if {$bank in [lrange $banks 1 end] && [llength $gate]==0} {error "Missing HBM RREADY feedback gate at port $bank"}
        set p [hoge_region pblock_hoge_hbm_port$bank 0 [expr {$bank/4}] [expr {$bank/4}] 0 0]
        if {[llength $gate]>0} {hoge_add $p $gate; incr found}
        hoge_add $p [get_cells [format {WRAPPER/CL/HBM/PORT_RESET[%d].LOCAL_RESET} $bank]]
    }
    if {$found<21} {error "HBM feedback placement incomplete"}
    # Register-tree levels follow the physical HBM columns, then converge.
    foreach level {0 1 2} count {8 4 2} span {1 2 4} {
        for {set node 0} {$node<$count} {incr node} {
            set left [expr {$node*$span}]
            set p [hoge_region pblock_hoge_ready_${level}_$node 0 $left [expr {$left+$span-1}] 0 0]
            hoge_add $p [get_cells [format {WRAPPER/CL/HBM/READY_TREE/LEVEL%d[%d].NODE} $level $node]]
        }
    }
    set p [hoge_region pblock_hoge_ready_root 0 3 4 0 0]
    hoge_add $p [get_cells WRAPPER/CL/HBM/READY_TREE/ROOT]
    hoge_add $p [get_cells WRAPPER/CL/HBM/RESET_READY_CDC/src_ff_reg]
    # Enumerate every intended branch from generated metadata; never cut a
    # payload path or silently skip a synchronizer when stage counts change.
    set reset_clear {}
    set reset_groups {}
    foreach pin [get_pins -hier -regexp {WRAPPER/CL/.*/sync_reg\[[0-3]\]/CLR}] {
        set instance [regsub {/sync_reg\[[0-3]\]/CLR$} $pin {}]
        dict lappend reset_groups $instance $pin
    }
    foreach instance $hoge_reset_instances {
        if {![dict exists $reset_groups $instance]} {error "Missing local reset branch $instance"}
        set pins [dict get $reset_groups $instance]
        if {[llength $pins]!=4} {error "Expected four local reset CLR pins at $instance, got [llength $pins]"}
        lappend reset_clear {*}$pins
    }
    if {[llength [lsort -unique $reset_clear]] != 4*[llength $hoge_reset_instances]} {error "Duplicate reset branch in placement metadata"}
    puts "INFO: F2 verified [llength $hoge_reset_instances] local reset branches ([llength $reset_clear] CLR pins)"
    set_false_path -to $reset_clear
    # This endpoint is an asynchronous reset, not sampled data. Either reset
    # request or loss of MMCM lock asserts it; XPM holds release for four HBM
    # clocks. A combinational assertion can only extend reset. Keep this
    # reviewed CDC-10 exception limited to the first reset-stage CLR pin.
    set reset_entry [get_pins -quiet {WRAPPER/CL/HBM_RESET/arststages_ff_reg[0]/CLR}]
    if {[llength $reset_entry]!=1} {error "HBM asynchronous reset entry changed"}
    create_waiver -type CDC -id CDC-10 -to $reset_entry -user HOGE \
        -description {Asynchronous active-low reset AND MMCM lock: either source asserts reset, and XPM synchronizes release through four HBM-clock stages. Not a data crossing.}
}
