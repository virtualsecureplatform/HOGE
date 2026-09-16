`timescale 1ns/1ps
// Registered reduction across physically distributed HBM reset branches.
(* KEEP_HIERARCHY="yes" *)
module hoge_ready_node #(parameter WIDTH=2) (
    input wire clk, arst_n,
    input wire [WIDTH-1:0] ready_in,
    output reg ready_out=0
);
    wire local_rst_n;
    hoge_local_reset RESET (.clk(clk), .arst_n(arst_n), .rst_n(local_rst_n));
    always @(posedge clk or negedge local_rst_n)
        if (!local_rst_n) ready_out<=0;
        else ready_out<=&ready_in;
endmodule

(* KEEP_HIERARCHY="yes" *)
module hoge_ready_tree (
    input wire clk, arst_n,
    input wire [31:0] ready_in,
    output wire ready_out
);
    wire [7:0] q0;
    wire [3:0] q1;
    wire [1:0] q2;
    for (genvar i=0;i<8;i=i+1) begin: LEVEL0
        hoge_ready_node #(.WIDTH(4)) NODE(clk,arst_n,ready_in[4*i+:4],q0[i]);
    end
    for (genvar i=0;i<4;i=i+1) begin: LEVEL1
        hoge_ready_node NODE(clk,arst_n,q0[2*i+:2],q1[i]);
    end
    for (genvar i=0;i<2;i=i+1) begin: LEVEL2
        hoge_ready_node NODE(clk,arst_n,q1[2*i+:2],q2[i]);
    end
    hoge_ready_node ROOT(clk,arst_n,q2,ready_out);
endmodule
