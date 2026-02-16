`default_nettype none
`timescale 1 ns / 1 ps

module BRFront #(
  parameter integer C_AXIS00_TDATA_WIDTH = 512,
  parameter integer C_AXIS01_TDATA_WIDTH = 32,
  parameter integer C_AXIS02_TDATA_WIDTH = 512,
  parameter integer C_AXIS03_TDATA_WIDTH = 512,
  parameter integer C_AXIS04_TDATA_WIDTH = 512,
  parameter integer C_AXIS05_TDATA_WIDTH = 512,
  parameter integer C_AXIS06_TDATA_WIDTH = 512,
  parameter integer C_AXIS07_TDATA_WIDTH = 512
)
(
  // System signals
  input  wire                                ap_clk,
  input  wire                                ap_rst_n,

  // axis00: SLAVE, 512-bit - from BRBack (BK2Formerslice output)
  input  wire                                axis00_tvalid,
  output wire                                axis00_tready,
  input  wire [C_AXIS00_TDATA_WIDTH-1:0]     axis00_tdata,
  input  wire [C_AXIS00_TDATA_WIDTH/8-1:0]   axis00_tkeep,
  input  wire                                axis00_tlast,

  // axis01: MASTER, 32-bit - to BRBack (globalout)
  output wire                                axis01_tvalid,
  input  wire                                axis01_tready,
  output wire [C_AXIS01_TDATA_WIDTH-1:0]     axis01_tdata,
  output wire [C_AXIS01_TDATA_WIDTH/8-1:0]   axis01_tkeep,
  output wire                                axis01_tlast,

  // axis02: MASTER, 512-bit - to BRBack (NTT output ch0)
  output wire                                axis02_tvalid,
  input  wire                                axis02_tready,
  output wire [C_AXIS02_TDATA_WIDTH-1:0]     axis02_tdata,
  output wire [C_AXIS02_TDATA_WIDTH/8-1:0]   axis02_tkeep,
  output wire                                axis02_tlast,

  // axis03: MASTER, 512-bit - to BRBack (NTT output ch1)
  output wire                                axis03_tvalid,
  input  wire                                axis03_tready,
  output wire [C_AXIS03_TDATA_WIDTH-1:0]     axis03_tdata,
  output wire [C_AXIS03_TDATA_WIDTH/8-1:0]   axis03_tkeep,
  output wire                                axis03_tlast,

  // axis04: MASTER, 512-bit - to BRBack (NTT output ch2)
  output wire                                axis04_tvalid,
  input  wire                                axis04_tready,
  output wire [C_AXIS04_TDATA_WIDTH-1:0]     axis04_tdata,
  output wire [C_AXIS04_TDATA_WIDTH/8-1:0]   axis04_tkeep,
  output wire                                axis04_tlast,

  // axis05: MASTER, 512-bit - to BRBack (NTT output ch3)
  output wire                                axis05_tvalid,
  input  wire                                axis05_tready,
  output wire [C_AXIS05_TDATA_WIDTH-1:0]     axis05_tdata,
  output wire [C_AXIS05_TDATA_WIDTH/8-1:0]   axis05_tkeep,
  output wire                                axis05_tlast,

  // axis06: SLAVE, 512-bit - from BRBack (AXISBRLater output ch0)
  input  wire                                axis06_tvalid,
  output wire                                axis06_tready,
  input  wire [C_AXIS06_TDATA_WIDTH-1:0]     axis06_tdata,
  input  wire [C_AXIS06_TDATA_WIDTH/8-1:0]   axis06_tkeep,
  input  wire                                axis06_tlast,

  // axis07: SLAVE, 512-bit - from BRBack (AXISBRLater output ch1)
  input  wire                                axis07_tvalid,
  output wire                                axis07_tready,
  input  wire [C_AXIS07_TDATA_WIDTH-1:0]     axis07_tdata,
  input  wire [C_AXIS07_TDATA_WIDTH/8-1:0]   axis07_tkeep,
  input  wire                                axis07_tlast
);

  // --------------------------------------------------------------------------
  // Reset
  // --------------------------------------------------------------------------
  reg areset;
  always @(posedge ap_clk) begin
    areset <= ~ap_rst_n;
  end

  // --------------------------------------------------------------------------
  // TVALID sharing for NTT output channels (axis03-05 mirror axis02)
  // --------------------------------------------------------------------------
  assign axis03_tvalid = axis02_tvalid;
  assign axis04_tvalid = axis02_tvalid;
  assign axis05_tvalid = axis02_tvalid;

  // --------------------------------------------------------------------------
  // AXISBRLater input channels do not backpressure
  // --------------------------------------------------------------------------
  assign axis06_tready = 1'b1;
  assign axis07_tready = 1'b1;

  // --------------------------------------------------------------------------
  // AXISBRFormer
  // --------------------------------------------------------------------------
  AXISBRFormer axisbrformer (
    .clock                     (ap_clk),
    .reset                     (areset),
    // Global input (from BRBack via BK2Formerslice)
    .io_axi4sglobalin_TVALID   (axis00_tvalid),
    .io_axi4sglobalin_TREADY   (axis00_tready),
    .io_axi4sglobalin_TDATA    (axis00_tdata),
    // Global output (to BRBack)
    .io_axi4sglobalout_TVALID  (axis01_tvalid),
    .io_axi4sglobalout_TREADY  (axis01_tready),
    .io_axi4sglobalout_TDATA   (axis01_tdata),
    // 2 inputs from AXISBRLater (only channel 0 has TVALID)
    .io_axi4sin_0_TVALID       (axis06_tvalid),
    .io_axi4sin_0_TDATA        (axis06_tdata),
    .io_axi4sin_1_TDATA        (axis07_tdata),
    // 4 NTT outputs (only channel 0 has TVALID)
    .io_axi4sout_0_TVALID      (axis02_tvalid),
    .io_axi4sout_0_TDATA       (axis02_tdata),
    .io_axi4sout_1_TDATA       (axis03_tdata),
    .io_axi4sout_2_TDATA       (axis04_tdata),
    .io_axi4sout_3_TDATA       (axis05_tdata)
  );

  // --------------------------------------------------------------------------
  // TKEEP / TLAST for MASTER ports
  // --------------------------------------------------------------------------
  // axis01: 32-bit master
  assign axis01_tkeep = 4'hF;
  assign axis01_tlast = 1'b0;

  // axis02-05: 512-bit masters
  assign axis02_tkeep = {64{1'b1}};
  assign axis02_tlast = 1'b0;
  assign axis03_tkeep = {64{1'b1}};
  assign axis03_tlast = 1'b0;
  assign axis04_tkeep = {64{1'b1}};
  assign axis04_tlast = 1'b0;
  assign axis05_tkeep = {64{1'b1}};
  assign axis05_tlast = 1'b0;

// Debug: globalout monitoring (SEI → axis01)
reg [31:0] dbg_gout_beats;
reg        dbg_gout_first;
reg [31:0] dbg_gout_stall;
always @(posedge ap_clk) begin
    if (!ap_rst_n) begin
        dbg_gout_beats <= 0;
        dbg_gout_first <= 0;
        dbg_gout_stall <= 0;
    end else begin
        if (axis01_tvalid & axis01_tready) begin
            dbg_gout_beats <= dbg_gout_beats + 1;
            if (!dbg_gout_first) begin
                dbg_gout_first <= 1;
                $fwrite(32'h80000002, "BRFRONT_GOUT_FIRST: data=0x%08x ready=%0d\n",
                        axis01_tdata, axis01_tready);
            end
        end
        if (axis01_tvalid & !axis01_tready) begin
            dbg_gout_stall <= dbg_gout_stall + 1;
            if (dbg_gout_stall == 5000) begin
                dbg_gout_stall <= 0;
                $fwrite(32'h80000002, "BRFRONT_GOUT_STALL: beats=%0d v=%0d r=%0d\n",
                        dbg_gout_beats, axis01_tvalid, axis01_tready);
            end
        end else begin
            dbg_gout_stall <= 0;
        end
    end
end

endmodule

`default_nettype wire
