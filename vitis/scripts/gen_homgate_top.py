#!/usr/bin/env python3
"""Generate HomGate_top.v - the main kernel wrapper with DataMovers.

Key difference from old template (homgate.template):
- AXISBRMiddle has moved to BRBack kernel
- axis02-09 are ALL MASTER (output) ports for BK data pass-through
- No AXISBRMiddle instantiation in HomGate
"""

import sys
import os

def gen_axi_master_params(n):
    """Generate AXI master parameter declarations."""
    lines = []
    for i in range(n):
        lines.append(f"  parameter integer C_M{i:02d}_AXI_ADDR_WIDTH       = 64 ,")
        comma = "," if i < n-1 else ","
        lines.append(f"  parameter integer C_M{i:02d}_AXI_DATA_WIDTH       = 512{comma}")
    return "\n".join(lines)

def gen_axis_params():
    """Generate AXI-Stream parameter declarations."""
    lines = []
    lines.append("  parameter integer C_AXIS00_TDATA_WIDTH       = 512,")
    lines.append("  parameter integer C_AXIS01_TDATA_WIDTH       = 32 ,")
    for i in range(2, 10):
        comma = "" if i == 9 else ","
        lines.append(f"  parameter integer C_AXIS{i:02d}_TDATA_WIDTH       = 512{comma}")
    return "\n".join(lines)

def gen_axi_master_ports(idx):
    """Generate AXI4 master port declarations for one master."""
    p = f"m{idx:02d}_axi"
    W = f"C_M{idx:02d}_AXI_DATA_WIDTH"
    A = f"C_M{idx:02d}_AXI_ADDR_WIDTH"
    return f"""  // AXI4 master interface {p}
  output wire                                    {p}_awvalid      ,
  input  wire                                    {p}_awready      ,
  output wire [{A}-1:0]         {p}_awaddr       ,
  output wire [8-1:0]                            {p}_awlen        ,
  output wire                                    {p}_wvalid       ,
  input  wire                                    {p}_wready       ,
  output wire [{W}-1:0]         {p}_wdata        ,
  output wire [{W}/8-1:0]       {p}_wstrb        ,
  output wire                                    {p}_wlast        ,
  input  wire                                    {p}_bvalid       ,
  output wire                                    {p}_bready       ,
  output wire                                    {p}_arvalid      ,
  input  wire                                    {p}_arready      ,
  output wire [{A}-1:0]         {p}_araddr       ,
  output wire [8-1:0]                            {p}_arlen        ,
  input  wire                                    {p}_rvalid       ,
  output wire                                    {p}_rready       ,
  input  wire [{W}-1:0]         {p}_rdata        ,
  input  wire                                    {p}_rlast        ,"""

def gen_axis_master_port(idx, width_param, last=False):
    """Generate AXI4-Stream master port."""
    comma = "" if last else ","
    return f"""  // AXI4-Stream (master) interface axis{idx:02d}
  output wire                                    axis{idx:02d}_tvalid        ,
  input  wire                                    axis{idx:02d}_tready        ,
  output wire [{width_param}-1:0]         axis{idx:02d}_tdata         ,
  output wire [{width_param}/8-1:0]       axis{idx:02d}_tkeep         ,
  output wire                                    axis{idx:02d}_tlast         {comma}"""

def gen_axis_slave_port(idx, width_param):
    """Generate AXI4-Stream slave port."""
    return f"""  // AXI4-Stream (slave) interface axis{idx:02d}
  input  wire                                    axis{idx:02d}_tvalid        ,
  output wire                                    axis{idx:02d}_tready        ,
  input  wire [{width_param}-1:0]         axis{idx:02d}_tdata         ,
  input  wire [{width_param}/8-1:0]       axis{idx:02d}_tkeep         ,
  input  wire                                    axis{idx:02d}_tlast         ,"""

def gen_s2mm_datamover(dm_idx, axi_idx, cmd_prefix, data_prefix):
    """Generate S2MM DataMover instantiation."""
    p = f"m{axi_idx:02d}_axi"
    return f"""axi_datamover_0 datamover{dm_idx:02d} (
  .m_axi_s2mm_aclk(ap_clk),
  .m_axi_s2mm_aresetn(ap_rst_n&user_rst_n),
  .s2mm_err(),
  .m_axis_s2mm_cmdsts_awclk(ap_clk),
  .m_axis_s2mm_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_s2mm_cmd_tvalid({cmd_prefix}_TVALID),
  .s_axis_s2mm_cmd_tready({cmd_prefix}_TREADY),
  .s_axis_s2mm_cmd_tdata({cmd_prefix}_TDATA),
  .m_axis_s2mm_sts_tvalid(),
  .m_axis_s2mm_sts_tready(1'b1),
  .m_axis_s2mm_sts_tdata(),
  .m_axis_s2mm_sts_tkeep(),
  .m_axis_s2mm_sts_tlast(),
  .m_axi_s2mm_awid(),
  .m_axi_s2mm_awaddr({p}_awaddr),
  .m_axi_s2mm_awlen({p}_awlen),
  .m_axi_s2mm_awsize(),
  .m_axi_s2mm_awburst(),
  .m_axi_s2mm_awprot(),
  .m_axi_s2mm_awcache(),
  .m_axi_s2mm_awuser(),
  .m_axi_s2mm_awvalid({p}_awvalid),
  .m_axi_s2mm_awready({p}_awready),
  .m_axi_s2mm_wdata({p}_wdata),
  .m_axi_s2mm_wstrb({p}_wstrb),
  .m_axi_s2mm_wlast({p}_wlast),
  .m_axi_s2mm_wvalid({p}_wvalid),
  .m_axi_s2mm_wready({p}_wready),
  .m_axi_s2mm_bresp(2'b0),
  .m_axi_s2mm_bvalid({p}_bvalid),
  .m_axi_s2mm_bready({p}_bready),
  .s_axis_s2mm_tdata({data_prefix}_TDATA),
  .s_axis_s2mm_tkeep(4'hF),
  .s_axis_s2mm_tlast(1'b0),
  .s_axis_s2mm_tvalid({data_prefix}_TVALID),
  .s_axis_s2mm_tready({data_prefix}_TREADY)
);"""

def gen_mm2s_datamover(dm_idx, axi_idx, cmd_prefix, data_prefix):
    """Generate MM2S DataMover instantiation."""
    p = f"m{axi_idx:02d}_axi"
    return f"""axi_datamover_1 datamover{dm_idx:02d} (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid({cmd_prefix}_TVALID),
  .s_axis_mm2s_cmd_tready({cmd_prefix}_TREADY),
  .s_axis_mm2s_cmd_tdata({cmd_prefix}_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr({p}_araddr),
  .m_axi_mm2s_arlen({p}_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid({p}_arvalid),
  .m_axi_mm2s_arready({p}_arready),
  .m_axi_mm2s_rdata({p}_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast({p}_rlast),
  .m_axi_mm2s_rvalid({p}_rvalid),
  .m_axi_mm2s_rready({p}_rready),
  .m_axis_mm2s_tdata({data_prefix}_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid({data_prefix}_TVALID),
  .m_axis_mm2s_tready({data_prefix}_TREADY)
);"""

def gen_homgate_top():
    lines = []

    # Header
    lines.append("""\
// This is a generated file. Use and modify at your own risk.
////////////////////////////////////////////////////////////////////////////////
// default_nettype of none prevents implicit wire declaration.
`default_nettype none
`timescale 1 ns / 1 ps
// Top level of the kernel. Do not modify module name, parameters or ports.
module HomGate #(
  parameter integer C_S_AXI_CONTROL_ADDR_WIDTH = 12 ,
  parameter integer C_S_AXI_CONTROL_DATA_WIDTH = 32 ,""")

    # AXI master parameters
    lines.append(gen_axi_master_params(21))
    lines.append(gen_axis_params())

    # Module ports
    lines.append(""")
(
  // System Signals
  input  wire                                    ap_clk               ,
  input  wire                                    ap_rst_n             ,""")

    # 21 AXI master ports
    for i in range(21):
        lines.append(gen_axi_master_ports(i))

    # AXI-Stream ports
    # axis00: MASTER 512-bit (IKS output to BRFront)
    lines.append(gen_axis_master_port(0, "C_AXIS00_TDATA_WIDTH"))
    # axis01: SLAVE 32-bit (GlobalOut from BRFront)
    lines.append(gen_axis_slave_port(1, "C_AXIS01_TDATA_WIDTH"))
    # axis02-09: ALL MASTER 512-bit (BK data pass-through to BRBack)
    for i in range(2, 10):
        lines.append(gen_axis_master_port(i, f"C_AXIS{i:02d}_TDATA_WIDTH"))

    # AXI-Lite slave interface
    lines.append("""\
  // AXI4-Lite slave interface
  input  wire                                    s_axi_control_awvalid,
  output wire                                    s_axi_control_awready,
  input  wire [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]   s_axi_control_awaddr ,
  input  wire                                    s_axi_control_wvalid ,
  output wire                                    s_axi_control_wready ,
  input  wire [C_S_AXI_CONTROL_DATA_WIDTH-1:0]   s_axi_control_wdata  ,
  input  wire [C_S_AXI_CONTROL_DATA_WIDTH/8-1:0] s_axi_control_wstrb  ,
  input  wire                                    s_axi_control_arvalid,
  output wire                                    s_axi_control_arready,
  input  wire [C_S_AXI_CONTROL_ADDR_WIDTH-1:0]   s_axi_control_araddr ,
  output wire                                    s_axi_control_rvalid ,
  input  wire                                    s_axi_control_rready ,
  output wire [C_S_AXI_CONTROL_DATA_WIDTH-1:0]   s_axi_control_rdata  ,
  output wire [2-1:0]                            s_axi_control_rresp  ,
  output wire                                    s_axi_control_bvalid ,
  input  wire                                    s_axi_control_bready ,
  output wire [2-1:0]                            s_axi_control_bresp  ,
  output wire                                    interrupt
);""")

    # Wires and variables
    lines.append("""
///////////////////////////////////////////////////////////////////////////////
// Local Parameters
///////////////////////////////////////////////////////////////////////////////

///////////////////////////////////////////////////////////////////////////////
// Wires and Variables
///////////////////////////////////////////////////////////////////////////////
(* DONT_TOUCH = "yes" *)
reg                                 areset                         = 1'b0;
wire                                ap_start                      ;
wire                                ap_idle                       ;
wire                                ap_done                       ;
wire                                ap_ready                      ;
wire [16-1:0]                       scaledaindex                  ;
wire [16-1:0]                       scaledbindex                  ;
wire [16-1:0]                       offsetindex                   ;""")

    for i in range(21):
        lines.append(f"wire [64-1:0]                       axi{i:02d}_ptr0                    ;")

    lines.append("""
// Register and invert reset signal.
always @(posedge ap_clk) begin
  areset <= ~ap_rst_n;
end

///////////////////////////////////////////////////////////////////////////////
// Begin control interface RTL.  Modifying not recommended.
///////////////////////////////////////////////////////////////////////////////

// AXI4-Lite slave interface
HomGate_control_s_axi #(
  .C_S_AXI_ADDR_WIDTH ( C_S_AXI_CONTROL_ADDR_WIDTH ),
  .C_S_AXI_DATA_WIDTH ( C_S_AXI_CONTROL_DATA_WIDTH )
)
inst_control_s_axi (
  .ACLK         ( ap_clk                ),
  .ARESET       ( areset                ),
  .ACLK_EN      ( 1'b1                  ),
  .AWVALID      ( s_axi_control_awvalid ),
  .AWREADY      ( s_axi_control_awready ),
  .AWADDR       ( s_axi_control_awaddr  ),
  .WVALID       ( s_axi_control_wvalid  ),
  .WREADY       ( s_axi_control_wready  ),
  .WDATA        ( s_axi_control_wdata   ),
  .WSTRB        ( s_axi_control_wstrb   ),
  .ARVALID      ( s_axi_control_arvalid ),
  .ARREADY      ( s_axi_control_arready ),
  .ARADDR       ( s_axi_control_araddr  ),
  .RVALID       ( s_axi_control_rvalid  ),
  .RREADY       ( s_axi_control_rready  ),
  .RDATA        ( s_axi_control_rdata   ),
  .RRESP        ( s_axi_control_rresp   ),
  .BVALID       ( s_axi_control_bvalid  ),
  .BREADY       ( s_axi_control_bready  ),
  .BRESP        ( s_axi_control_bresp   ),
  .interrupt    ( interrupt             ),
  .ap_start     ( ap_start              ),
  .ap_done      ( ap_done               ),
  .ap_ready     ( ap_ready              ),
  .ap_idle      ( ap_idle               ),
  .scaledaindex ( scaledaindex          ),
  .scaledbindex ( scaledbindex          ),
  .offsetindex  ( offsetindex           ),""")

    for i in range(21):
        comma = "," if i < 20 else ""
        lines.append(f"  .axi{i:02d}_ptr0   ( axi{i:02d}_ptr0            ){comma}")

    lines.append(""");

///////////////////////////////////////////////////////////////////////////////
// Add kernel logic here.  Modify/remove example code as necessary.
///////////////////////////////////////////////////////////////////////////////

wire user_rst;
reg user_rst_n;
always @(posedge ap_clk) begin
  user_rst_n <= ~user_rst;
end

// Output S2MM command and data wires
wire axi4outcmd_TVALID;
wire axi4outcmd_TREADY;
wire [103:0] axi4outcmd_TDATA;

// IKS output command wires (unused in new design, but HomGateTop still has the port)
wire axi4iksoutcmd_TVALID;
wire axi4iksoutcmd_TREADY;
wire [103:0] axi4iksoutcmd_TDATA;
wire axi4iksout_TVALID;
wire axi4iksout_TREADY;

// Input A (CT) wires
wire axi4sina_TVALID;
wire axi4sina_TREADY;
wire [511:0] axi4sina_TDATA;
wire axi4inacmd_TVALID;
wire axi4inacmd_TREADY;
wire [103:0] axi4inacmd_TDATA;

// Input B (CT) wires
wire axi4sinb_TVALID;
wire axi4sinb_TREADY;
wire [511:0] axi4sinb_TDATA;
wire axi4inbcmd_TVALID;
wire axi4inbcmd_TREADY;
wire [103:0] axi4inbcmd_TDATA;

// GlobalOut (S2MM data) wires
wire axi4sout_TVALID;
wire axi4sout_TREADY;
wire [31:0] axi4sout_TDATA;""")

    # S2MM DataMover (datamover00, m00_axi)
    lines.append("")
    lines.append(gen_s2mm_datamover(0, 0, "axi4outcmd", "axi4sout"))

    # MM2S DataMover for input A (datamover01, m01_axi)
    lines.append("")
    lines.append(gen_mm2s_datamover(1, 1, "axi4inacmd", "axi4sina"))

    # MM2S DataMover for input B (datamover02, m02_axi)
    lines.append("")
    lines.append(gen_mm2s_datamover(2, 2, "axi4inbcmd", "axi4sinb"))

    # IKSK DataMovers (datamover03-12, m03-m12_axi)
    for bus_idx in range(10):
        dm_idx = bus_idx + 3
        axi_idx = bus_idx + 3
        lines.append(f"""
wire axi4ikskin_{bus_idx}_TVALID;
wire axi4ikskin_{bus_idx}_TREADY;
wire [511:0] axi4ikskin_{bus_idx}_TDATA;

wire axi4ikskincmd_{bus_idx}_TVALID;
wire axi4ikskincmd_{bus_idx}_TREADY;
wire [103:0] axi4ikskincmd_{bus_idx}_TDATA;
""")
        lines.append(gen_mm2s_datamover(dm_idx, axi_idx,
                                         f"axi4ikskincmd_{bus_idx}",
                                         f"axi4ikskin_{bus_idx}"))

    # BK DataMovers (datamover13-20, m13-m20_axi)
    for bus_idx in range(8):
        dm_idx = bus_idx + 13
        axi_idx = bus_idx + 13
        lines.append(f"""
wire axi4bkincmd_{bus_idx}_TVALID;
wire axi4bkincmd_{bus_idx}_TREADY;
wire [103:0] axi4bkincmd_{bus_idx}_TDATA;
wire axi4bkin_{bus_idx}_TVALID;
wire axi4bkin_{bus_idx}_TREADY;
wire [511:0] axi4bkin_{bus_idx}_TDATA;
""")
        lines.append(gen_mm2s_datamover(dm_idx, axi_idx,
                                         f"axi4bkincmd_{bus_idx}",
                                         f"axi4bkin_{bus_idx}"))

    # TLWEADD
    lines.append("""
// TLWEADD: adds two TLWE ciphertexts with scaling
wire axi4sadded_TVALID;
wire axi4sadded_TREADY;
wire [511:0] axi4sadded_TDATA;

TLWEADD tlweadd(
  .clock(ap_clk),
  .reset(areset),
  .io_scaleaindex(scaledaindex),
  .io_scalebindex(scaledbindex),
  .io_offsetindex(offsetindex),
  .io_axi4sout_TVALID(axi4sadded_TVALID),
  .io_axi4sout_TREADY(axi4sadded_TREADY),
  .io_axi4sout_TDATA(axi4sadded_TDATA),
  .io_axi4sina_TVALID(axi4sina_TVALID),
  .io_axi4sina_TREADY(axi4sina_TREADY),
  .io_axi4sina_TDATA(axi4sina_TDATA),
  .io_axi4sinb_TVALID(axi4sinb_TVALID),
  .io_axi4sinb_TREADY(axi4sinb_TREADY),
  .io_axi4sinb_TDATA(axi4sinb_TDATA)
);""")

    # HomGateTop
    lines.append("""
// HomGateTop: main controller - generates DataMover commands
HomGateTop homgate(
  .clock(ap_clk),
  .reset(areset),
  .io_brvalid(axi4sout_TVALID),
  .io_axi4outcmd_TVALID(axi4outcmd_TVALID),
  .io_axi4outcmd_TREADY(axi4outcmd_TREADY),
  .io_axi4outcmd_TDATA(axi4outcmd_TDATA),
  .io_axi4inacmd_TVALID(axi4inacmd_TVALID),
  .io_axi4inacmd_TREADY(axi4inacmd_TREADY),
  .io_axi4inacmd_TDATA(axi4inacmd_TDATA),
  .io_axi4inbcmd_TVALID(axi4inbcmd_TVALID),
  .io_axi4inbcmd_TREADY(axi4inbcmd_TREADY),
  .io_axi4inbcmd_TDATA(axi4inbcmd_TDATA),""")

    for i in range(10):
        lines.append(f"  .io_axi4ikskincmd_{i}_TVALID(axi4ikskincmd_{i}_TVALID),")
        lines.append(f"  .io_axi4ikskincmd_{i}_TREADY(axi4ikskincmd_{i}_TREADY),")
        lines.append(f"  .io_axi4ikskincmd_{i}_TDATA(axi4ikskincmd_{i}_TDATA),")

    for i in range(8):
        lines.append(f"  .io_axi4bkincmd_{i}_TVALID(axi4bkincmd_{i}_TVALID),")
        lines.append(f"  .io_axi4bkincmd_{i}_TREADY(axi4bkincmd_{i}_TREADY),")
        lines.append(f"  .io_axi4bkincmd_{i}_TDATA(axi4bkincmd_{i}_TDATA),")

    lines.append("  .io_outaddr(axi00_ptr0),")
    lines.append("  .io_inaaddr(axi01_ptr0),")
    lines.append("  .io_inbaddr(axi02_ptr0),")

    for i in range(10):
        lines.append(f"  .io_ikskaddr_{i}(axi{i+3:02d}_ptr0),")

    for i in range(8):
        lines.append(f"  .io_bkaddr_{i}(axi{i+13:02d}_ptr0),")

    lines.append("""\
  .io_user_rst(user_rst),
  .io_ap_start(ap_start),
  .io_ap_done(ap_done),
  .io_ap_idle(ap_idle),
  .io_ap_ready(ap_ready)
);""")

    # AXISIKS
    lines.append("""
// AXISIKS: Identity Key Switching
wire axi4siksout_TVALID;
wire axi4siksout_TREADY;
wire [511:0] axi4siksout_TDATA;

AXISIKS axisiks(
  .io_axi4out_TVALID(axi4siksout_TVALID),
  .io_axi4out_TREADY(axi4siksout_TREADY),
  .io_axi4out_TDATA(axi4siksout_TDATA),
  .io_axi4in_TVALID(axi4sadded_TVALID),
  .io_axi4in_TREADY(axi4sadded_TREADY),
  .io_axi4in_TDATA(axi4sadded_TDATA),""")

    for i in range(10):
        lines.append(f"  .io_axi4ikskin_{i}_TVALID(axi4ikskin_{i}_TVALID),")
        lines.append(f"  .io_axi4ikskin_{i}_TREADY(axi4ikskin_{i}_TREADY),")
        lines.append(f"  .io_axi4ikskin_{i}_TDATA(axi4ikskin_{i}_TDATA),")

    lines.append("""\
  .clock(ap_clk),
  .reset(areset)
);""")

    # BK data pass-through: axis02-09 ← BK DataMover outputs
    # In the new design, AXISBRMiddle is in BRBack, so BK data goes out via axis02-09
    lines.append("""
///////////////////////////////////////////////////////////////////////////////
// BK data pass-through to BRBack kernel via axis02-09
// (AXISBRMiddle has moved to BRBack kernel)
///////////////////////////////////////////////////////////////////////////////""")

    for i in range(8):
        axis_idx = i + 2
        lines.append(f"assign axis{axis_idx:02d}_tvalid = axi4bkin_{i}_TVALID;")
        lines.append(f"assign axis{axis_idx:02d}_tdata  = axi4bkin_{i}_TDATA;")
        lines.append(f"assign axis{axis_idx:02d}_tkeep  = {{64{{1'b1}}}};")
        lines.append(f"assign axis{axis_idx:02d}_tlast  = 1'b0;")
        lines.append(f"assign axi4bkin_{i}_TREADY = axis{axis_idx:02d}_tready;")
        lines.append("")

    # BK2Formerslice: IKS output → axis00 (to BRFront)
    lines.append("""\
// BK2Formerslice: IKS output -> axis00 (to BRFront via BRBack)
BK2Formerslice globalinsliceSLR0toSLR1(
  .clock(ap_clk),
  .reset(areset),
  .io_subordinate_TVALID(axi4siksout_TVALID),
  .io_subordinate_TREADY(axi4siksout_TREADY),
  .io_subordinate_TDATA(axi4siksout_TDATA),
  .io_manager_TVALID(axis00_tvalid),
  .io_manager_TREADY(axis00_tready),
  .io_manager_TDATA(axis00_tdata)
);

assign axis00_tkeep = {64{1'b1}};
assign axis00_tlast = 1'b0;""")

    # GlobalOutslice: axis01 → S2MM DataMover
    lines.append("""
// GlobalOutslice: axis01 (from BRFront) -> S2MM DataMover input
GlobalOutslice globaloutsliceSLR1toSLR0(
  .clock(ap_clk),
  .reset(areset),
  .io_subordinate_TVALID(axis01_tvalid),
  .io_subordinate_TREADY(axis01_tready),
  .io_subordinate_TDATA(axis01_tdata),
  .io_manager_TVALID(axi4sout_TVALID),
  .io_manager_TREADY(axi4sout_TREADY),
  .io_manager_TDATA(axi4sout_TDATA)
);

endmodule
`default_nettype wire""")

    return "\n".join(lines)


if __name__ == "__main__":
    content = gen_homgate_top()
    outpath = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                           "rtl", "HomGate_top.v")
    with open(outpath, "w") as f:
        f.write(content)
    print(f"Generated {outpath}")
    print(f"Size: {len(content)} bytes, {content.count(chr(10))+1} lines")
