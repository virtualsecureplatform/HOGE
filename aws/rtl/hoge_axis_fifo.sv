// F2 replacement for the FIFOs inserted by the Vitis linker.
module hoge_axis_fifo #(parameter WIDTH=512, DEPTH=32) (
    input wire clk, rst_n,
    input wire [WIDTH-1:0] s_tdata,
    input wire [WIDTH/8-1:0] s_tkeep,
    input wire s_tlast, s_tvalid,
    output wire s_tready,
    output wire [WIDTH-1:0] m_tdata,
    output wire [WIDTH/8-1:0] m_tkeep,
    output wire m_tlast, m_tvalid,
    input wire m_tready
);
    wire local_rst_n;
    hoge_local_reset RESET (.clk(clk), .arst_n(rst_n), .rst_n(local_rst_n));
    wire full, empty, wr_busy, rd_busy;
    assign s_tready = !full && !wr_busy && local_rst_n;
    assign m_tvalid = !empty && !rd_busy && local_rst_n;
    xpm_fifo_sync #(
        .FIFO_MEMORY_TYPE("auto"), .FIFO_WRITE_DEPTH(DEPTH),
        .WRITE_DATA_WIDTH(WIDTH+WIDTH/8+1), .READ_DATA_WIDTH(WIDTH+WIDTH/8+1),
        .READ_MODE("fwft"), .FIFO_READ_LATENCY(0),
        .WR_DATA_COUNT_WIDTH($clog2(DEPTH)+1),
        .RD_DATA_COUNT_WIDTH($clog2(DEPTH)+1),
        .USE_ADV_FEATURES("0000"), .SIM_ASSERT_CHK(1)
    ) storage (
        .wr_clk(clk), .rst(!local_rst_n),
        .din({s_tlast,s_tkeep,s_tdata}), .wr_en(s_tvalid && s_tready), .full(full),
        .dout({m_tlast,m_tkeep,m_tdata}), .rd_en(m_tvalid && m_tready), .empty(empty),
        .wr_rst_busy(wr_busy), .rd_rst_busy(rd_busy),
        .sleep(1'b0), .injectsbiterr(1'b0), .injectdbiterr(1'b0)
    );
endmodule
