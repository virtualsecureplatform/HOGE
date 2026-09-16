`timescale 1ns/1ps
// Keep the host busy until compute AND the output DMA have completed.
module hoge_dma_completion (
    input wire clk, rst_n, start, controller_done,
    input wire status_valid,
    input wire [7:0] status_data,
    output wire kernel_start,
    output reg done=0,
    output wire idle,
    output reg failed=0
);
    reg busy=0, compute_seen=0, write_seen=0;
    assign kernel_start=start && !busy && !done;
    assign idle=!busy && !start;
    always @(posedge clk or negedge rst_n) begin
        if (!rst_n) begin
            busy<=0; compute_seen<=0; write_seen<=0; done<=0; failed<=0;
        end else begin
            done<=0;
            if (kernel_start) begin
                busy<=1; compute_seen<=0; write_seen<=0; failed<=0;
            end else if (busy) begin
                if (controller_done) compute_seen<=1;
                if (status_valid) begin
                    if (status_data[7:4]==4'b1000) write_seen<=1;
                    else failed<=1;
                end
                if (compute_seen && write_seen && !failed) begin
                    done<=1;
                    busy<=0;
                end
            end
        end
    end
endmodule
