`default_nettype none
`timescale 1 ns / 1 ps

module BRBack #(
  parameter integer C_AXIS00_TDATA_WIDTH = 512,
  parameter integer C_AXIS01_TDATA_WIDTH = 512,
  parameter integer C_AXIS02_TDATA_WIDTH = 512,
  parameter integer C_AXIS03_TDATA_WIDTH = 512,
  parameter integer C_AXIS04_TDATA_WIDTH = 512,
  parameter integer C_AXIS05_TDATA_WIDTH = 512,
  parameter integer C_AXIS06_TDATA_WIDTH = 512,
  parameter integer C_AXIS07_TDATA_WIDTH = 512,
  parameter integer C_AXIS08_TDATA_WIDTH = 512,
  parameter integer C_AXIS09_TDATA_WIDTH = 512,
  parameter integer C_AXIS10_TDATA_WIDTH = 512,
  parameter integer C_AXIS11_TDATA_WIDTH = 512,
  parameter integer C_AXIS12_TDATA_WIDTH = 512,
  parameter integer C_AXIS13_TDATA_WIDTH = 512,
  parameter integer C_AXIS14_TDATA_WIDTH = 512,
  parameter integer C_AXIS15_TDATA_WIDTH = 512,
  parameter integer C_AXIS16_TDATA_WIDTH = 32,
  parameter integer C_AXIS17_TDATA_WIDTH = 32,
  parameter integer C_AXIS18_TDATA_WIDTH = 32
)
(
  // System signals
  input  wire                                ap_clk,
  input  wire                                ap_rst_n,

  // axis00: SLAVE, 512-bit - from BRFront (NTT output ch0)
  input  wire                                axis00_tvalid,
  output wire                                axis00_tready,
  input  wire [C_AXIS00_TDATA_WIDTH-1:0]     axis00_tdata,
  input  wire [C_AXIS00_TDATA_WIDTH/8-1:0]   axis00_tkeep,
  input  wire                                axis00_tlast,

  // axis01: SLAVE, 512-bit - from BRFront (NTT output ch1)
  input  wire                                axis01_tvalid,
  output wire                                axis01_tready,
  input  wire [C_AXIS01_TDATA_WIDTH-1:0]     axis01_tdata,
  input  wire [C_AXIS01_TDATA_WIDTH/8-1:0]   axis01_tkeep,
  input  wire                                axis01_tlast,

  // axis02: SLAVE, 512-bit - from BRFront (NTT output ch2)
  input  wire                                axis02_tvalid,
  output wire                                axis02_tready,
  input  wire [C_AXIS02_TDATA_WIDTH-1:0]     axis02_tdata,
  input  wire [C_AXIS02_TDATA_WIDTH/8-1:0]   axis02_tkeep,
  input  wire                                axis02_tlast,

  // axis03: SLAVE, 512-bit - from BRFront (NTT output ch3)
  input  wire                                axis03_tvalid,
  output wire                                axis03_tready,
  input  wire [C_AXIS03_TDATA_WIDTH-1:0]     axis03_tdata,
  input  wire [C_AXIS03_TDATA_WIDTH/8-1:0]   axis03_tkeep,
  input  wire                                axis03_tlast,

  // axis04: MASTER, 512-bit - to BRFront (AXISBRLater output ch0)
  output wire                                axis04_tvalid,
  input  wire                                axis04_tready,
  output wire [C_AXIS04_TDATA_WIDTH-1:0]     axis04_tdata,
  output wire [C_AXIS04_TDATA_WIDTH/8-1:0]   axis04_tkeep,
  output wire                                axis04_tlast,

  // axis05: MASTER, 512-bit - to BRFront (AXISBRLater output ch1)
  output wire                                axis05_tvalid,
  input  wire                                axis05_tready,
  output wire [C_AXIS05_TDATA_WIDTH-1:0]     axis05_tdata,
  output wire [C_AXIS05_TDATA_WIDTH/8-1:0]   axis05_tkeep,
  output wire                                axis05_tlast,

  // axis06: SLAVE, 512-bit - from HomGate (BK ch0)
  input  wire                                axis06_tvalid,
  output wire                                axis06_tready,
  input  wire [C_AXIS06_TDATA_WIDTH-1:0]     axis06_tdata,
  input  wire [C_AXIS06_TDATA_WIDTH/8-1:0]   axis06_tkeep,
  input  wire                                axis06_tlast,

  // axis07: SLAVE, 512-bit - from HomGate (BK ch1)
  input  wire                                axis07_tvalid,
  output wire                                axis07_tready,
  input  wire [C_AXIS07_TDATA_WIDTH-1:0]     axis07_tdata,
  input  wire [C_AXIS07_TDATA_WIDTH/8-1:0]   axis07_tkeep,
  input  wire                                axis07_tlast,

  // axis08: SLAVE, 512-bit - from HomGate (BK ch2)
  input  wire                                axis08_tvalid,
  output wire                                axis08_tready,
  input  wire [C_AXIS08_TDATA_WIDTH-1:0]     axis08_tdata,
  input  wire [C_AXIS08_TDATA_WIDTH/8-1:0]   axis08_tkeep,
  input  wire                                axis08_tlast,

  // axis09: SLAVE, 512-bit - from HomGate (BK ch3)
  input  wire                                axis09_tvalid,
  output wire                                axis09_tready,
  input  wire [C_AXIS09_TDATA_WIDTH-1:0]     axis09_tdata,
  input  wire [C_AXIS09_TDATA_WIDTH/8-1:0]   axis09_tkeep,
  input  wire                                axis09_tlast,

  // axis10: SLAVE, 512-bit - from HomGate (BK ch4)
  input  wire                                axis10_tvalid,
  output wire                                axis10_tready,
  input  wire [C_AXIS10_TDATA_WIDTH-1:0]     axis10_tdata,
  input  wire [C_AXIS10_TDATA_WIDTH/8-1:0]   axis10_tkeep,
  input  wire                                axis10_tlast,

  // axis11: SLAVE, 512-bit - from HomGate (BK ch5)
  input  wire                                axis11_tvalid,
  output wire                                axis11_tready,
  input  wire [C_AXIS11_TDATA_WIDTH-1:0]     axis11_tdata,
  input  wire [C_AXIS11_TDATA_WIDTH/8-1:0]   axis11_tkeep,
  input  wire                                axis11_tlast,

  // axis12: SLAVE, 512-bit - from HomGate (BK ch6)
  input  wire                                axis12_tvalid,
  output wire                                axis12_tready,
  input  wire [C_AXIS12_TDATA_WIDTH-1:0]     axis12_tdata,
  input  wire [C_AXIS12_TDATA_WIDTH/8-1:0]   axis12_tkeep,
  input  wire                                axis12_tlast,

  // axis13: SLAVE, 512-bit - from HomGate (BK ch7)
  input  wire                                axis13_tvalid,
  output wire                                axis13_tready,
  input  wire [C_AXIS13_TDATA_WIDTH-1:0]     axis13_tdata,
  input  wire [C_AXIS13_TDATA_WIDTH/8-1:0]   axis13_tkeep,
  input  wire                                axis13_tlast,

  // axis14: MASTER, 512-bit - to BRFront (BK2Formerslice output)
  output wire                                axis14_tvalid,
  input  wire                                axis14_tready,
  output wire [C_AXIS14_TDATA_WIDTH-1:0]     axis14_tdata,
  output wire [C_AXIS14_TDATA_WIDTH/8-1:0]   axis14_tkeep,
  output wire                                axis14_tlast,

  // axis15: SLAVE, 512-bit - from HomGate (IKS output)
  input  wire                                axis15_tvalid,
  output wire                                axis15_tready,
  input  wire [C_AXIS15_TDATA_WIDTH-1:0]     axis15_tdata,
  input  wire [C_AXIS15_TDATA_WIDTH/8-1:0]   axis15_tkeep,
  input  wire                                axis15_tlast,

  // axis16: MASTER, 32-bit - to HomGate (GlobalOutslice output)
  output wire                                axis16_tvalid,
  input  wire                                axis16_tready,
  output wire [C_AXIS16_TDATA_WIDTH-1:0]     axis16_tdata,
  output wire [C_AXIS16_TDATA_WIDTH/8-1:0]   axis16_tkeep,
  output wire                                axis16_tlast,

  // axis17: SLAVE, 32-bit - from BRFront (globalout)
  input  wire                                axis17_tvalid,
  output wire                                axis17_tready,
  input  wire [C_AXIS17_TDATA_WIDTH-1:0]     axis17_tdata,
  input  wire [C_AXIS17_TDATA_WIDTH/8-1:0]   axis17_tkeep,
  input  wire                                axis17_tlast,

  // axis18: MASTER, 32-bit - debug stream to HomGate
  output wire                                axis18_tvalid,
  input  wire                                axis18_tready,
  output wire [C_AXIS18_TDATA_WIDTH-1:0]     axis18_tdata,
  output wire [C_AXIS18_TDATA_WIDTH/8-1:0]   axis18_tkeep,
  output wire                                axis18_tlast
);

  // --------------------------------------------------------------------------
  // Reset
  // --------------------------------------------------------------------------
  reg areset;
  always @(posedge ap_clk) begin
    areset <= ~ap_rst_n;
  end

  // --------------------------------------------------------------------------
  // NTTdataPipeline x4 (axis00-03 -> ntt_pipe_out)
  // NTTdataPipeline has NO reset and NO TREADY
  // --------------------------------------------------------------------------
  assign axis00_tready = 1'b1;
  assign axis01_tready = 1'b1;
  assign axis02_tready = 1'b1;
  assign axis03_tready = 1'b1;

  wire        ntt_pipe_out_0_TVALID;
  wire [511:0] ntt_pipe_out_0_TDATA;
  wire        ntt_pipe_out_1_TVALID;
  wire [511:0] ntt_pipe_out_1_TDATA;
  wire        ntt_pipe_out_2_TVALID;
  wire [511:0] ntt_pipe_out_2_TDATA;
  wire        ntt_pipe_out_3_TVALID;
  wire [511:0] ntt_pipe_out_3_TDATA;

  NTTdataPipeline nttPipe0 (
    .clock              (ap_clk),
    .io_subordinate_TVALID (axis00_tvalid),
    .io_subordinate_TDATA  (axis00_tdata),
    .io_manager_TVALID     (ntt_pipe_out_0_TVALID),
    .io_manager_TDATA      (ntt_pipe_out_0_TDATA)
  );

  NTTdataPipeline nttPipe1 (
    .clock              (ap_clk),
    .io_subordinate_TVALID (axis01_tvalid),
    .io_subordinate_TDATA  (axis01_tdata),
    .io_manager_TVALID     (ntt_pipe_out_1_TVALID),
    .io_manager_TDATA      (ntt_pipe_out_1_TDATA)
  );

  NTTdataPipeline nttPipe2 (
    .clock              (ap_clk),
    .io_subordinate_TVALID (axis02_tvalid),
    .io_subordinate_TDATA  (axis02_tdata),
    .io_manager_TVALID     (ntt_pipe_out_2_TVALID),
    .io_manager_TDATA      (ntt_pipe_out_2_TDATA)
  );

  NTTdataPipeline nttPipe3 (
    .clock              (ap_clk),
    .io_subordinate_TVALID (axis03_tvalid),
    .io_subordinate_TDATA  (axis03_tdata),
    .io_manager_TVALID     (ntt_pipe_out_3_TVALID),
    .io_manager_TDATA      (ntt_pipe_out_3_TDATA)
  );

  // --------------------------------------------------------------------------
  // AXISBRMiddle (BK from axis06-13, NTT from ntt_pipe_out)
  // --------------------------------------------------------------------------
  wire        middle_out_0_TVALID;
  wire [511:0] middle_out_0_TDATA;
  wire [511:0] middle_out_1_TDATA;
  wire [511:0] middle_out_2_TDATA;
  wire [511:0] middle_out_3_TDATA;

  AXISBRMiddle axisbrmiddle (
    .clock                  (ap_clk),
    .reset                  (areset),
    // 8 BK inputs from axis06-13
    .io_axi4bkin_0_TVALID   (axis06_tvalid),
    .io_axi4bkin_0_TREADY   (axis06_tready),
    .io_axi4bkin_0_TDATA    (axis06_tdata),
    .io_axi4bkin_1_TVALID   (axis07_tvalid),
    .io_axi4bkin_1_TREADY   (axis07_tready),
    .io_axi4bkin_1_TDATA    (axis07_tdata),
    .io_axi4bkin_2_TVALID   (axis08_tvalid),
    .io_axi4bkin_2_TREADY   (axis08_tready),
    .io_axi4bkin_2_TDATA    (axis08_tdata),
    .io_axi4bkin_3_TVALID   (axis09_tvalid),
    .io_axi4bkin_3_TREADY   (axis09_tready),
    .io_axi4bkin_3_TDATA    (axis09_tdata),
    .io_axi4bkin_4_TVALID   (axis10_tvalid),
    .io_axi4bkin_4_TREADY   (axis10_tready),
    .io_axi4bkin_4_TDATA    (axis10_tdata),
    .io_axi4bkin_5_TVALID   (axis11_tvalid),
    .io_axi4bkin_5_TREADY   (axis11_tready),
    .io_axi4bkin_5_TDATA    (axis11_tdata),
    .io_axi4bkin_6_TVALID   (axis12_tvalid),
    .io_axi4bkin_6_TREADY   (axis12_tready),
    .io_axi4bkin_6_TDATA    (axis12_tdata),
    .io_axi4bkin_7_TVALID   (axis13_tvalid),
    .io_axi4bkin_7_TREADY   (axis13_tready),
    .io_axi4bkin_7_TDATA    (axis13_tdata),
    // 4 NTT inputs (only channel 0 has TVALID)
    .io_axi4sin_0_TVALID    (ntt_pipe_out_0_TVALID),
    .io_axi4sin_0_TDATA     (ntt_pipe_out_0_TDATA),
    .io_axi4sin_1_TDATA     (ntt_pipe_out_1_TDATA),
    .io_axi4sin_2_TDATA     (ntt_pipe_out_2_TDATA),
    .io_axi4sin_3_TDATA     (ntt_pipe_out_3_TDATA),
    // 4 outputs (only channel 0 has TVALID)
    .io_axi4sout_0_TVALID   (middle_out_0_TVALID),
    .io_axi4sout_0_TDATA    (middle_out_0_TDATA),
    .io_axi4sout_1_TDATA    (middle_out_1_TDATA),
    .io_axi4sout_2_TDATA    (middle_out_2_TDATA),
    .io_axi4sout_3_TDATA    (middle_out_3_TDATA),
    // Debug probes
    .io_dbg_fifovalid       (dbg_fifovalid),
    .io_dbg_hazard          (dbg_hazard),
    .io_dbg_mulacc_state    (dbg_mulacc_state),
    .io_dbg_itercnt         (dbg_itercnt),
    .io_dbg_queuedrop       (dbg_queuedrop),
    .io_dbg_enq_valid       (dbg_enq_valid),
    .io_dbg_enq_ready       (dbg_enq_ready)
  );

  // Debug probe wires
  wire        dbg_fifovalid;
  wire        dbg_hazard;
  wire        dbg_mulacc_state;
  wire [15:0] dbg_itercnt;
  wire [31:0] dbg_queuedrop;
  wire        dbg_enq_valid;
  wire        dbg_enq_ready;

  // --------------------------------------------------------------------------
  // AXISBRLater (AXISBRMiddle output -> axis04-05)
  // --------------------------------------------------------------------------
  wire        later_out_0_TVALID;
  wire [511:0] later_out_0_TDATA;
  wire [511:0] later_out_1_TDATA;

  AXISBRLater axisbrlater (
    .clock              (ap_clk),
    .reset              (areset),
    .io_axi4sin_0_TVALID  (middle_out_0_TVALID),
    .io_axi4sin_0_TDATA   (middle_out_0_TDATA),
    .io_axi4sin_1_TDATA   (middle_out_1_TDATA),
    .io_axi4sin_2_TDATA   (middle_out_2_TDATA),
    .io_axi4sin_3_TDATA   (middle_out_3_TDATA),
    .io_axi4sout_0_TVALID (later_out_0_TVALID),
    .io_axi4sout_0_TDATA  (later_out_0_TDATA),
    .io_axi4sout_1_TDATA  (later_out_1_TDATA)
  );

  assign axis04_tvalid = later_out_0_TVALID;
  assign axis04_tdata  = later_out_0_TDATA;
  assign axis04_tkeep  = {64{1'b1}};
  assign axis04_tlast  = 1'b0;
  assign axis05_tvalid = later_out_0_TVALID;
  assign axis05_tdata  = later_out_1_TDATA;
  assign axis05_tkeep  = {64{1'b1}};
  assign axis05_tlast  = 1'b0;

  // --------------------------------------------------------------------------
  // BK2Formerslice (axis15 -> axis14)
  // --------------------------------------------------------------------------
  BK2Formerslice globalinslice (
    .clock              (ap_clk),
    .reset              (areset),
    .io_subordinate_TVALID (axis15_tvalid),
    .io_subordinate_TREADY (axis15_tready),
    .io_subordinate_TDATA  (axis15_tdata),
    .io_manager_TVALID     (axis14_tvalid),
    .io_manager_TREADY     (axis14_tready),
    .io_manager_TDATA      (axis14_tdata)
  );

  assign axis14_tkeep = {64{1'b1}};
  assign axis14_tlast = 1'b0;

  // --------------------------------------------------------------------------
  // GlobalOutslice (axis17 -> axis16)
  // --------------------------------------------------------------------------
  GlobalOutslice globaloutslice (
    .clock              (ap_clk),
    .reset              (areset),
    .io_subordinate_TVALID (axis17_tvalid),
    .io_subordinate_TREADY (axis17_tready),
    .io_subordinate_TDATA  (axis17_tdata),
    .io_manager_TVALID     (axis16_tvalid),
    .io_manager_TREADY     (axis16_tready),
    .io_manager_TDATA      (axis16_tdata)
  );

  assign axis16_tkeep = {4{1'b1}};
  assign axis16_tlast = 1'b0;

// Debug: globalout path monitoring (axis17 → GlobalOutslice → axis16)
reg [31:0] dbg_gout_ax17_beats;
reg [31:0] dbg_gout_ax16_beats;
reg        dbg_gout_seen;
always @(posedge ap_clk) begin
    if (!ap_rst_n) begin
        dbg_gout_ax17_beats <= 0;
        dbg_gout_ax16_beats <= 0;
        dbg_gout_seen <= 0;
    end else begin
        if (axis17_tvalid & axis17_tready)
            dbg_gout_ax17_beats <= dbg_gout_ax17_beats + 1;
        if (axis16_tvalid & axis16_tready)
            dbg_gout_ax16_beats <= dbg_gout_ax16_beats + 1;
        if (axis17_tvalid & !dbg_gout_seen) begin
            dbg_gout_seen <= 1;
        end
    end
end

// =========================================================================
// Debug axis18: Internal probes → 32-bit stream to HomGate
// =========================================================================
// Bit layout (Build 16):
//   [0]     dbg_fifovalid       — INTT queue has data (deq.valid)
//   [1]     dbg_hazard          — MULandACC address hazard active
//   [2]     dbg_mulacc_state    — MULandACC state: 0=RUN, 1=OUT
//   [3]     dbg_enq_valid       — INTT queue enq valid (data arriving)
//   [4]     dbg_enq_ready       — INTT queue enq ready (not full)
//   [5]     ntt_pipe_in_ever    — INTT data ever reached AXISBRMiddle
//   [6]     middle_out_ever     — MULandACC ever produced output
//   [7]     axis17_in_ever      — GlobalOut ever received from BRFront
//   [15:8]  dbg_queuedrop[7:0]  — INTT queue overflow count (sat 8-bit)
//   [31:16] dbg_itercnt         — MULandACC iteration count (16-bit)

reg        dbg_ntt_pipe_in_ever;
reg        dbg_middle_out_ever;
reg        dbg_axis17_in_ever;

always @(posedge ap_clk) begin
    if (!ap_rst_n) begin
        dbg_ntt_pipe_in_ever  <= 0;
        dbg_middle_out_ever   <= 0;
        dbg_axis17_in_ever    <= 0;
    end else begin
        if (ntt_pipe_out_0_TVALID)  dbg_ntt_pipe_in_ever  <= 1;
        if (middle_out_0_TVALID)    dbg_middle_out_ever   <= 1;
        if (axis17_tvalid)          dbg_axis17_in_ever    <= 1;
    end
end

// Saturate queuedrop to 8 bits for packing
wire [7:0] dbg_queuedrop_sat = (dbg_queuedrop[31:8] != 0) ? 8'hFF : dbg_queuedrop[7:0];

wire [31:0] brback_debug_word = {
    dbg_itercnt,
    dbg_queuedrop_sat,
    dbg_axis17_in_ever, dbg_middle_out_ever, dbg_ntt_pipe_in_ever,
    dbg_enq_ready, dbg_enq_valid,
    dbg_mulacc_state, dbg_hazard, dbg_fifovalid
};

// 8-stage register pipeline for timing (SLR1 internal)
reg [31:0] dbg_pipe [0:7];
integer dbg_i;
always @(posedge ap_clk) begin
    dbg_pipe[0] <= brback_debug_word;
    for (dbg_i = 1; dbg_i < 8; dbg_i = dbg_i + 1)
        dbg_pipe[dbg_i] <= dbg_pipe[dbg_i-1];
end

assign axis18_tvalid = 1'b1;
assign axis18_tdata  = dbg_pipe[7];
assign axis18_tkeep  = {4{1'b1}};
assign axis18_tlast  = 1'b0;

endmodule

`default_nettype wire
