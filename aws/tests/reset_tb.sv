`timescale 1ns/1ps
module reset_tb;
    reg clk=0,arst_n=0;
    wire rst_n;
    hoge_local_reset dut(.*);
    always #2 clk=~clk;
    initial begin
        repeat(4) @(negedge clk);
        for(integer epoch=0;epoch<3;epoch=epoch+1) begin
            arst_n=1;
            repeat(3) begin @(posedge clk);#0.1;if(rst_n) $fatal(1,"reset released early");end
            @(posedge clk);#0.1;if(!rst_n) $fatal(1,"reset did not release after four clocks");
            #0.7;arst_n=0;#0.1;
            if(rst_n) $fatal(1,"reset assertion waited for clock");
            repeat(5) @(negedge clk);
        end
        $display("HOGE_RESET_PASS: asynchronous assertion and four-clock release");$finish;
    end
endmodule
