# Observe the design without recording large protocol waveforms.
set slow [expr {[info exists ::env(HOGE_HBM_MODEL)] && $::env(HOGE_HBM_MODEL) eq "amd"}]
set limit [expr {$slow ? 11000 : 1100}]
for {set step 0} {$step < $limit} {incr step} {
    if {$step < 10} {set interval 1us} elseif {$slow} {set interval 10us} else {set interval 100us}
    if {[catch {run $interval} reason]} {
        puts "HOGE_HDK: simulator stopped: $reason"
        break
    }
    # XSIM permits a later run to resume after $finish. Stop at the test's
    # completion marker before issuing another run, including for old snapshots.
    set log_file [open simulate.log r]
    set log_text [read $log_file]
    close $log_file
    if {[regexp -line {^HOGE_HDK_((HBM|DMA)_SMOKE_)?PASS:|^Fatal:|^Error:|^ERROR:} $log_text]} {break}
    puts "HOGE_HDK_PROGRESS: time=[current_time] ready=[get_value /tb/card/fpga/CL/hbm_ready] control=[get_value /test_hoge/control]"
    if {!$slow} {
        set h /tb/card/fpga/CL/HomGate_1/DMA_COMPLETION
        puts "HOGE_DMA_PROGRESS: busy=[get_value $h/busy] compute=[get_value $h/compute_seen] write=[get_value $h/write_seen] failed=[get_value $h/failed]"
    }
}
quit
