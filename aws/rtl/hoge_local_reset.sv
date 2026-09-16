`timescale 1ns/1ps
// Separate physical reset branches must not merge across HBM ports.
(* KEEP_HIERARCHY = "yes" *)
module hoge_local_reset(input wire clk, arst_n, output wire rst_n);
    (* ASYNC_REG = "TRUE", SHREG_EXTRACT = "NO", DONT_TOUCH = "TRUE" *)
    reg [3:0] sync=0;
    always @(posedge clk or negedge arst_n)
        if (!arst_n) sync<=0;
        else sync<={sync[2:0],1'b1};
    assign rst_n=sync[3];
endmodule
