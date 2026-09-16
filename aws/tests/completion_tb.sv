`timescale 1ns/1ps
module completion_tb;
    reg clk=0,rst_n=0,start=0,controller_done=0,status_valid=0;
    reg [7:0] status_data=0;
    wire kernel_start,done,idle,failed;
    hoge_dma_completion dut(.*);
    always #2 clk=~clk;
    integer launches=0;
    always @(posedge clk) if(rst_n && kernel_start) launches=launches+1;
    task automatic launch;
        @(negedge clk);start=1;
        repeat(4) @(negedge clk);
        if(idle || kernel_start || done) $fatal(1,"launch not held busy");
    endtask
    task automatic compute;
        controller_done=1;@(negedge clk);controller_done=0;
    endtask
    task automatic status(input [7:0] data);
        status_data=data;status_valid=1;@(negedge clk);status_valid=0;
    endtask
    task automatic finish_launch;
        wait(done);@(negedge clk);start=0;
        repeat(4) @(negedge clk);
        if(!idle || done || failed) $fatal(1,"completion did not return idle");
    endtask
    initial begin
        repeat(4) @(negedge clk);rst_n=1;
        launch();compute();
        repeat(40) begin @(negedge clk);if(done || idle || kernel_start) $fatal(1,"completed before write status");end
        status(8'h80);finish_launch();
        launch();status(8'h80);
        repeat(12) begin @(negedge clk);if(done) $fatal(1,"completed before compute");end
        compute();finish_launch();
        launch();compute();status(8'h10);
        repeat(12) begin @(negedge clk);if(done || idle) $fatal(1,"error reported as success");end
        if(!failed || launches!=3) $fatal(1,"bad error/launch accounting");
        rst_n=0;start=0;repeat(4) @(negedge clk);rst_n=1;
        launch();compute();status(8'h80);finish_launch();
        if(launches!=4) $fatal(1,"duplicate launch");
        $display("HOGE_COMPLETION_PASS: delayed writes, reverse arrival, errors, repeated launches, reset");$finish;
    end
    initial begin #100000;$fatal(1,"watchdog");end
endmodule
