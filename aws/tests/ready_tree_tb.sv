`timescale 1ns/1ps
module ready_tree_tb;
    reg clk=0, arst_n=0;
    reg [31:0] ready_in=0;
    wire ready_out;
    always #1 clk=~clk;
    hoge_ready_tree dut(.*);
    initial begin
        repeat(5) @(negedge clk);
        arst_n=1;
        // Every individual missing port must prevent readiness.
        for(integer i=0;i<32;i=i+1) begin
            arst_n=0; ready_in=~(32'b1<<i);
            repeat(5) @(negedge clk); arst_n=1;
            repeat(16) begin
                @(negedge clk);
                if(ready_out) $fatal(1,"Premature readiness with missing port %d",i);
            end
        end
        ready_in='1;
        repeat(3) begin
            @(negedge clk);
            if(ready_out) $fatal(1,"Reduction bypassed a pipeline stage");
        end
        @(negedge clk);
        if(!ready_out) $fatal(1,"All-ready reduction did not propagate in four cycles");
        #0.2; arst_n=0;
        #0.1;if(ready_out) $fatal(1,"Reset did not immediately clear readiness");
        repeat(5) @(negedge clk); arst_n=1;
        repeat(12) @(negedge clk);
        if(!ready_out) $fatal(1,"Readiness did not recover after reset");
        $display("HOGE_READY_TREE_PASS: all ports required, pipeline latency, reset, restart");
        $finish;
    end
    initial begin #10000;$fatal(1,"ready tree watchdog");end
endmodule
