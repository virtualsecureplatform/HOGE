`timescale 1ns/1ps
// F2-only elastic pipeline. Each stage has two entries, no combinational
// READY path through the stage, and one-transfer-per-clock steady throughput.
(* KEEP_HIERARCHY = "yes" *)
module hoge_stream_pipeline #(parameter WIDTH=512, STAGES=3) (
    input wire clk, rst_n,
    input wire [WIDTH-1:0] s_data,
    input wire s_valid,
    output wire s_ready,
    output wire [WIDTH-1:0] m_data,
    output wire m_valid,
    input wire m_ready
);
    wire [WIDTH-1:0] data [0:STAGES];
    wire [STAGES:0] valid, ready;
    assign data[0]=s_data;
    assign valid[0]=s_valid;
    assign s_ready=ready[0];
    assign m_data=data[STAGES];
    assign m_valid=valid[STAGES];
    assign ready[STAGES]=m_ready;
    for (genvar i=0; i<STAGES; i=i+1) begin: STAGE
        // Reset assertion stays asynchronous; release is local to each physical
        // stage. Upstream stages hold data while a later stage remains reset.
        // The global reset therefore does not drive payload enables directly.
        wire stage_rst_n;
        hoge_local_reset RESET (.clk(clk), .arst_n(rst_n), .rst_n(stage_rst_n));
        reg [WIDTH-1:0] head, tail;
        reg [1:0] count=0;
        wire push=valid[i] && ready[i];
        wire pop=valid[i+1] && ready[i+1];
        assign ready[i]=stage_rst_n && count!=2;
        assign valid[i+1]=stage_rst_n && count!=0;
        assign data[i+1]=head;
        always @(posedge clk or negedge stage_rst_n) begin
            if (!stage_rst_n) count<=0;
            else begin
                case ({push,pop})
                    2'b10: count<=count+1'b1;
                    2'b01: count<=count-1'b1;
                    default: count<=count;
                endcase
            end
        end
        // Payload does not need reset; count is the validity authority.
        // Speculatively capture into free storage. Incoming VALID and reset
        // affect occupancy only, not the enables of hundreds of payload FFs.
        wire data_pop=(count!=0) && ready[i+1];
        always @(posedge clk) begin
            if (count==0 || (count==1 && data_pop)) head<=data[i];
            else if (data_pop && count==2) head<=tail;
            if (count==1 && !data_pop) tail<=data[i];
        end
    end
endmodule
