// This is a generated file. Use and modify at your own risk.
////////////////////////////////////////////////////////////////////////////////
// default_nettype of none prevents implicit wire declaration.
`default_nettype none
`timescale 1 ns / 1 ps
// Top level of the kernel. Do not modify module name, parameters or ports.
module HomGate #(
  parameter integer C_S_AXI_CONTROL_ADDR_WIDTH = 12 ,
  parameter integer C_S_AXI_CONTROL_DATA_WIDTH = 32 ,
  parameter integer C_M00_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M00_AXI_DATA_WIDTH       = 512,
  parameter integer C_M01_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M01_AXI_DATA_WIDTH       = 512,
  parameter integer C_M02_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M02_AXI_DATA_WIDTH       = 512,
  parameter integer C_M03_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M03_AXI_DATA_WIDTH       = 512,
  parameter integer C_M04_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M04_AXI_DATA_WIDTH       = 512,
  parameter integer C_M05_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M05_AXI_DATA_WIDTH       = 512,
  parameter integer C_M06_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M06_AXI_DATA_WIDTH       = 512,
  parameter integer C_M07_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M07_AXI_DATA_WIDTH       = 512,
  parameter integer C_M08_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M08_AXI_DATA_WIDTH       = 512,
  parameter integer C_M09_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M09_AXI_DATA_WIDTH       = 512,
  parameter integer C_M10_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M10_AXI_DATA_WIDTH       = 512,
  parameter integer C_M11_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M11_AXI_DATA_WIDTH       = 512,
  parameter integer C_M12_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M12_AXI_DATA_WIDTH       = 512,
  parameter integer C_M13_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M13_AXI_DATA_WIDTH       = 512,
  parameter integer C_M14_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M14_AXI_DATA_WIDTH       = 512,
  parameter integer C_M15_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M15_AXI_DATA_WIDTH       = 512,
  parameter integer C_M16_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M16_AXI_DATA_WIDTH       = 512,
  parameter integer C_M17_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M17_AXI_DATA_WIDTH       = 512,
  parameter integer C_M18_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M18_AXI_DATA_WIDTH       = 512,
  parameter integer C_M19_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M19_AXI_DATA_WIDTH       = 512,
  parameter integer C_M20_AXI_ADDR_WIDTH       = 64 ,
  parameter integer C_M20_AXI_DATA_WIDTH       = 512,
  parameter integer C_AXIS00_TDATA_WIDTH       = 512,
  parameter integer C_AXIS01_TDATA_WIDTH       = 32 ,
  parameter integer C_AXIS02_TDATA_WIDTH       = 512,
  parameter integer C_AXIS03_TDATA_WIDTH       = 512,
  parameter integer C_AXIS04_TDATA_WIDTH       = 512,
  parameter integer C_AXIS05_TDATA_WIDTH       = 512,
  parameter integer C_AXIS06_TDATA_WIDTH       = 512,
  parameter integer C_AXIS07_TDATA_WIDTH       = 512,
  parameter integer C_AXIS08_TDATA_WIDTH       = 512,
  parameter integer C_AXIS09_TDATA_WIDTH       = 512,
  parameter integer C_AXIS10_TDATA_WIDTH       = 32
)
(
  // System Signals
  input  wire                                    ap_clk               ,
  input  wire                                    ap_rst_n             ,
  // AXI4 master interface m00_axi
  output wire                                    m00_axi_awvalid      ,
  input  wire                                    m00_axi_awready      ,
  output wire [C_M00_AXI_ADDR_WIDTH-1:0]         m00_axi_awaddr       ,
  output wire [8-1:0]                            m00_axi_awlen        ,
  output wire                                    m00_axi_wvalid       ,
  input  wire                                    m00_axi_wready       ,
  output wire [C_M00_AXI_DATA_WIDTH-1:0]         m00_axi_wdata        ,
  output wire [C_M00_AXI_DATA_WIDTH/8-1:0]       m00_axi_wstrb        ,
  output wire                                    m00_axi_wlast        ,
  input  wire                                    m00_axi_bvalid       ,
  output wire                                    m00_axi_bready       ,
  output wire                                    m00_axi_arvalid      ,
  input  wire                                    m00_axi_arready      ,
  output wire [C_M00_AXI_ADDR_WIDTH-1:0]         m00_axi_araddr       ,
  output wire [8-1:0]                            m00_axi_arlen        ,
  input  wire                                    m00_axi_rvalid       ,
  output wire                                    m00_axi_rready       ,
  input  wire [C_M00_AXI_DATA_WIDTH-1:0]         m00_axi_rdata        ,
  input  wire                                    m00_axi_rlast        ,
  // AXI4 master interface m01_axi
  output wire                                    m01_axi_awvalid      ,
  input  wire                                    m01_axi_awready      ,
  output wire [C_M01_AXI_ADDR_WIDTH-1:0]         m01_axi_awaddr       ,
  output wire [8-1:0]                            m01_axi_awlen        ,
  output wire                                    m01_axi_wvalid       ,
  input  wire                                    m01_axi_wready       ,
  output wire [C_M01_AXI_DATA_WIDTH-1:0]         m01_axi_wdata        ,
  output wire [C_M01_AXI_DATA_WIDTH/8-1:0]       m01_axi_wstrb        ,
  output wire                                    m01_axi_wlast        ,
  input  wire                                    m01_axi_bvalid       ,
  output wire                                    m01_axi_bready       ,
  output wire                                    m01_axi_arvalid      ,
  input  wire                                    m01_axi_arready      ,
  output wire [C_M01_AXI_ADDR_WIDTH-1:0]         m01_axi_araddr       ,
  output wire [8-1:0]                            m01_axi_arlen        ,
  input  wire                                    m01_axi_rvalid       ,
  output wire                                    m01_axi_rready       ,
  input  wire [C_M01_AXI_DATA_WIDTH-1:0]         m01_axi_rdata        ,
  input  wire                                    m01_axi_rlast        ,
  // AXI4 master interface m02_axi
  output wire                                    m02_axi_awvalid      ,
  input  wire                                    m02_axi_awready      ,
  output wire [C_M02_AXI_ADDR_WIDTH-1:0]         m02_axi_awaddr       ,
  output wire [8-1:0]                            m02_axi_awlen        ,
  output wire                                    m02_axi_wvalid       ,
  input  wire                                    m02_axi_wready       ,
  output wire [C_M02_AXI_DATA_WIDTH-1:0]         m02_axi_wdata        ,
  output wire [C_M02_AXI_DATA_WIDTH/8-1:0]       m02_axi_wstrb        ,
  output wire                                    m02_axi_wlast        ,
  input  wire                                    m02_axi_bvalid       ,
  output wire                                    m02_axi_bready       ,
  output wire                                    m02_axi_arvalid      ,
  input  wire                                    m02_axi_arready      ,
  output wire [C_M02_AXI_ADDR_WIDTH-1:0]         m02_axi_araddr       ,
  output wire [8-1:0]                            m02_axi_arlen        ,
  input  wire                                    m02_axi_rvalid       ,
  output wire                                    m02_axi_rready       ,
  input  wire [C_M02_AXI_DATA_WIDTH-1:0]         m02_axi_rdata        ,
  input  wire                                    m02_axi_rlast        ,
  // AXI4 master interface m03_axi
  output wire                                    m03_axi_awvalid      ,
  input  wire                                    m03_axi_awready      ,
  output wire [C_M03_AXI_ADDR_WIDTH-1:0]         m03_axi_awaddr       ,
  output wire [8-1:0]                            m03_axi_awlen        ,
  output wire                                    m03_axi_wvalid       ,
  input  wire                                    m03_axi_wready       ,
  output wire [C_M03_AXI_DATA_WIDTH-1:0]         m03_axi_wdata        ,
  output wire [C_M03_AXI_DATA_WIDTH/8-1:0]       m03_axi_wstrb        ,
  output wire                                    m03_axi_wlast        ,
  input  wire                                    m03_axi_bvalid       ,
  output wire                                    m03_axi_bready       ,
  output wire                                    m03_axi_arvalid      ,
  input  wire                                    m03_axi_arready      ,
  output wire [C_M03_AXI_ADDR_WIDTH-1:0]         m03_axi_araddr       ,
  output wire [8-1:0]                            m03_axi_arlen        ,
  input  wire                                    m03_axi_rvalid       ,
  output wire                                    m03_axi_rready       ,
  input  wire [C_M03_AXI_DATA_WIDTH-1:0]         m03_axi_rdata        ,
  input  wire                                    m03_axi_rlast        ,
  // AXI4 master interface m04_axi
  output wire                                    m04_axi_awvalid      ,
  input  wire                                    m04_axi_awready      ,
  output wire [C_M04_AXI_ADDR_WIDTH-1:0]         m04_axi_awaddr       ,
  output wire [8-1:0]                            m04_axi_awlen        ,
  output wire                                    m04_axi_wvalid       ,
  input  wire                                    m04_axi_wready       ,
  output wire [C_M04_AXI_DATA_WIDTH-1:0]         m04_axi_wdata        ,
  output wire [C_M04_AXI_DATA_WIDTH/8-1:0]       m04_axi_wstrb        ,
  output wire                                    m04_axi_wlast        ,
  input  wire                                    m04_axi_bvalid       ,
  output wire                                    m04_axi_bready       ,
  output wire                                    m04_axi_arvalid      ,
  input  wire                                    m04_axi_arready      ,
  output wire [C_M04_AXI_ADDR_WIDTH-1:0]         m04_axi_araddr       ,
  output wire [8-1:0]                            m04_axi_arlen        ,
  input  wire                                    m04_axi_rvalid       ,
  output wire                                    m04_axi_rready       ,
  input  wire [C_M04_AXI_DATA_WIDTH-1:0]         m04_axi_rdata        ,
  input  wire                                    m04_axi_rlast        ,
  // AXI4 master interface m05_axi
  output wire                                    m05_axi_awvalid      ,
  input  wire                                    m05_axi_awready      ,
  output wire [C_M05_AXI_ADDR_WIDTH-1:0]         m05_axi_awaddr       ,
  output wire [8-1:0]                            m05_axi_awlen        ,
  output wire                                    m05_axi_wvalid       ,
  input  wire                                    m05_axi_wready       ,
  output wire [C_M05_AXI_DATA_WIDTH-1:0]         m05_axi_wdata        ,
  output wire [C_M05_AXI_DATA_WIDTH/8-1:0]       m05_axi_wstrb        ,
  output wire                                    m05_axi_wlast        ,
  input  wire                                    m05_axi_bvalid       ,
  output wire                                    m05_axi_bready       ,
  output wire                                    m05_axi_arvalid      ,
  input  wire                                    m05_axi_arready      ,
  output wire [C_M05_AXI_ADDR_WIDTH-1:0]         m05_axi_araddr       ,
  output wire [8-1:0]                            m05_axi_arlen        ,
  input  wire                                    m05_axi_rvalid       ,
  output wire                                    m05_axi_rready       ,
  input  wire [C_M05_AXI_DATA_WIDTH-1:0]         m05_axi_rdata        ,
  input  wire                                    m05_axi_rlast        ,
  // AXI4 master interface m06_axi
  output wire                                    m06_axi_awvalid      ,
  input  wire                                    m06_axi_awready      ,
  output wire [C_M06_AXI_ADDR_WIDTH-1:0]         m06_axi_awaddr       ,
  output wire [8-1:0]                            m06_axi_awlen        ,
  output wire                                    m06_axi_wvalid       ,
  input  wire                                    m06_axi_wready       ,
  output wire [C_M06_AXI_DATA_WIDTH-1:0]         m06_axi_wdata        ,
  output wire [C_M06_AXI_DATA_WIDTH/8-1:0]       m06_axi_wstrb        ,
  output wire                                    m06_axi_wlast        ,
  input  wire                                    m06_axi_bvalid       ,
  output wire                                    m06_axi_bready       ,
  output wire                                    m06_axi_arvalid      ,
  input  wire                                    m06_axi_arready      ,
  output wire [C_M06_AXI_ADDR_WIDTH-1:0]         m06_axi_araddr       ,
  output wire [8-1:0]                            m06_axi_arlen        ,
  input  wire                                    m06_axi_rvalid       ,
  output wire                                    m06_axi_rready       ,
  input  wire [C_M06_AXI_DATA_WIDTH-1:0]         m06_axi_rdata        ,
  input  wire                                    m06_axi_rlast        ,
  // AXI4 master interface m07_axi
  output wire                                    m07_axi_awvalid      ,
  input  wire                                    m07_axi_awready      ,
  output wire [C_M07_AXI_ADDR_WIDTH-1:0]         m07_axi_awaddr       ,
  output wire [8-1:0]                            m07_axi_awlen        ,
  output wire                                    m07_axi_wvalid       ,
  input  wire                                    m07_axi_wready       ,
  output wire [C_M07_AXI_DATA_WIDTH-1:0]         m07_axi_wdata        ,
  output wire [C_M07_AXI_DATA_WIDTH/8-1:0]       m07_axi_wstrb        ,
  output wire                                    m07_axi_wlast        ,
  input  wire                                    m07_axi_bvalid       ,
  output wire                                    m07_axi_bready       ,
  output wire                                    m07_axi_arvalid      ,
  input  wire                                    m07_axi_arready      ,
  output wire [C_M07_AXI_ADDR_WIDTH-1:0]         m07_axi_araddr       ,
  output wire [8-1:0]                            m07_axi_arlen        ,
  input  wire                                    m07_axi_rvalid       ,
  output wire                                    m07_axi_rready       ,
  input  wire [C_M07_AXI_DATA_WIDTH-1:0]         m07_axi_rdata        ,
  input  wire                                    m07_axi_rlast        ,
  // AXI4 master interface m08_axi
  output wire                                    m08_axi_awvalid      ,
  input  wire                                    m08_axi_awready      ,
  output wire [C_M08_AXI_ADDR_WIDTH-1:0]         m08_axi_awaddr       ,
  output wire [8-1:0]                            m08_axi_awlen        ,
  output wire                                    m08_axi_wvalid       ,
  input  wire                                    m08_axi_wready       ,
  output wire [C_M08_AXI_DATA_WIDTH-1:0]         m08_axi_wdata        ,
  output wire [C_M08_AXI_DATA_WIDTH/8-1:0]       m08_axi_wstrb        ,
  output wire                                    m08_axi_wlast        ,
  input  wire                                    m08_axi_bvalid       ,
  output wire                                    m08_axi_bready       ,
  output wire                                    m08_axi_arvalid      ,
  input  wire                                    m08_axi_arready      ,
  output wire [C_M08_AXI_ADDR_WIDTH-1:0]         m08_axi_araddr       ,
  output wire [8-1:0]                            m08_axi_arlen        ,
  input  wire                                    m08_axi_rvalid       ,
  output wire                                    m08_axi_rready       ,
  input  wire [C_M08_AXI_DATA_WIDTH-1:0]         m08_axi_rdata        ,
  input  wire                                    m08_axi_rlast        ,
  // AXI4 master interface m09_axi
  output wire                                    m09_axi_awvalid      ,
  input  wire                                    m09_axi_awready      ,
  output wire [C_M09_AXI_ADDR_WIDTH-1:0]         m09_axi_awaddr       ,
  output wire [8-1:0]                            m09_axi_awlen        ,
  output wire                                    m09_axi_wvalid       ,
  input  wire                                    m09_axi_wready       ,
  output wire [C_M09_AXI_DATA_WIDTH-1:0]         m09_axi_wdata        ,
  output wire [C_M09_AXI_DATA_WIDTH/8-1:0]       m09_axi_wstrb        ,
  output wire                                    m09_axi_wlast        ,
  input  wire                                    m09_axi_bvalid       ,
  output wire                                    m09_axi_bready       ,
  output wire                                    m09_axi_arvalid      ,
  input  wire                                    m09_axi_arready      ,
  output wire [C_M09_AXI_ADDR_WIDTH-1:0]         m09_axi_araddr       ,
  output wire [8-1:0]                            m09_axi_arlen        ,
  input  wire                                    m09_axi_rvalid       ,
  output wire                                    m09_axi_rready       ,
  input  wire [C_M09_AXI_DATA_WIDTH-1:0]         m09_axi_rdata        ,
  input  wire                                    m09_axi_rlast        ,
  // AXI4 master interface m10_axi
  output wire                                    m10_axi_awvalid      ,
  input  wire                                    m10_axi_awready      ,
  output wire [C_M10_AXI_ADDR_WIDTH-1:0]         m10_axi_awaddr       ,
  output wire [8-1:0]                            m10_axi_awlen        ,
  output wire                                    m10_axi_wvalid       ,
  input  wire                                    m10_axi_wready       ,
  output wire [C_M10_AXI_DATA_WIDTH-1:0]         m10_axi_wdata        ,
  output wire [C_M10_AXI_DATA_WIDTH/8-1:0]       m10_axi_wstrb        ,
  output wire                                    m10_axi_wlast        ,
  input  wire                                    m10_axi_bvalid       ,
  output wire                                    m10_axi_bready       ,
  output wire                                    m10_axi_arvalid      ,
  input  wire                                    m10_axi_arready      ,
  output wire [C_M10_AXI_ADDR_WIDTH-1:0]         m10_axi_araddr       ,
  output wire [8-1:0]                            m10_axi_arlen        ,
  input  wire                                    m10_axi_rvalid       ,
  output wire                                    m10_axi_rready       ,
  input  wire [C_M10_AXI_DATA_WIDTH-1:0]         m10_axi_rdata        ,
  input  wire                                    m10_axi_rlast        ,
  // AXI4 master interface m11_axi
  output wire                                    m11_axi_awvalid      ,
  input  wire                                    m11_axi_awready      ,
  output wire [C_M11_AXI_ADDR_WIDTH-1:0]         m11_axi_awaddr       ,
  output wire [8-1:0]                            m11_axi_awlen        ,
  output wire                                    m11_axi_wvalid       ,
  input  wire                                    m11_axi_wready       ,
  output wire [C_M11_AXI_DATA_WIDTH-1:0]         m11_axi_wdata        ,
  output wire [C_M11_AXI_DATA_WIDTH/8-1:0]       m11_axi_wstrb        ,
  output wire                                    m11_axi_wlast        ,
  input  wire                                    m11_axi_bvalid       ,
  output wire                                    m11_axi_bready       ,
  output wire                                    m11_axi_arvalid      ,
  input  wire                                    m11_axi_arready      ,
  output wire [C_M11_AXI_ADDR_WIDTH-1:0]         m11_axi_araddr       ,
  output wire [8-1:0]                            m11_axi_arlen        ,
  input  wire                                    m11_axi_rvalid       ,
  output wire                                    m11_axi_rready       ,
  input  wire [C_M11_AXI_DATA_WIDTH-1:0]         m11_axi_rdata        ,
  input  wire                                    m11_axi_rlast        ,
  // AXI4 master interface m12_axi
  output wire                                    m12_axi_awvalid      ,
  input  wire                                    m12_axi_awready      ,
  output wire [C_M12_AXI_ADDR_WIDTH-1:0]         m12_axi_awaddr       ,
  output wire [8-1:0]                            m12_axi_awlen        ,
  output wire                                    m12_axi_wvalid       ,
  input  wire                                    m12_axi_wready       ,
  output wire [C_M12_AXI_DATA_WIDTH-1:0]         m12_axi_wdata        ,
  output wire [C_M12_AXI_DATA_WIDTH/8-1:0]       m12_axi_wstrb        ,
  output wire                                    m12_axi_wlast        ,
  input  wire                                    m12_axi_bvalid       ,
  output wire                                    m12_axi_bready       ,
  output wire                                    m12_axi_arvalid      ,
  input  wire                                    m12_axi_arready      ,
  output wire [C_M12_AXI_ADDR_WIDTH-1:0]         m12_axi_araddr       ,
  output wire [8-1:0]                            m12_axi_arlen        ,
  input  wire                                    m12_axi_rvalid       ,
  output wire                                    m12_axi_rready       ,
  input  wire [C_M12_AXI_DATA_WIDTH-1:0]         m12_axi_rdata        ,
  input  wire                                    m12_axi_rlast        ,
  // AXI4 master interface m13_axi
  output wire                                    m13_axi_awvalid      ,
  input  wire                                    m13_axi_awready      ,
  output wire [C_M13_AXI_ADDR_WIDTH-1:0]         m13_axi_awaddr       ,
  output wire [8-1:0]                            m13_axi_awlen        ,
  output wire                                    m13_axi_wvalid       ,
  input  wire                                    m13_axi_wready       ,
  output wire [C_M13_AXI_DATA_WIDTH-1:0]         m13_axi_wdata        ,
  output wire [C_M13_AXI_DATA_WIDTH/8-1:0]       m13_axi_wstrb        ,
  output wire                                    m13_axi_wlast        ,
  input  wire                                    m13_axi_bvalid       ,
  output wire                                    m13_axi_bready       ,
  output wire                                    m13_axi_arvalid      ,
  input  wire                                    m13_axi_arready      ,
  output wire [C_M13_AXI_ADDR_WIDTH-1:0]         m13_axi_araddr       ,
  output wire [8-1:0]                            m13_axi_arlen        ,
  input  wire                                    m13_axi_rvalid       ,
  output wire                                    m13_axi_rready       ,
  input  wire [C_M13_AXI_DATA_WIDTH-1:0]         m13_axi_rdata        ,
  input  wire                                    m13_axi_rlast        ,
  // AXI4 master interface m14_axi
  output wire                                    m14_axi_awvalid      ,
  input  wire                                    m14_axi_awready      ,
  output wire [C_M14_AXI_ADDR_WIDTH-1:0]         m14_axi_awaddr       ,
  output wire [8-1:0]                            m14_axi_awlen        ,
  output wire                                    m14_axi_wvalid       ,
  input  wire                                    m14_axi_wready       ,
  output wire [C_M14_AXI_DATA_WIDTH-1:0]         m14_axi_wdata        ,
  output wire [C_M14_AXI_DATA_WIDTH/8-1:0]       m14_axi_wstrb        ,
  output wire                                    m14_axi_wlast        ,
  input  wire                                    m14_axi_bvalid       ,
  output wire                                    m14_axi_bready       ,
  output wire                                    m14_axi_arvalid      ,
  input  wire                                    m14_axi_arready      ,
  output wire [C_M14_AXI_ADDR_WIDTH-1:0]         m14_axi_araddr       ,
  output wire [8-1:0]                            m14_axi_arlen        ,
  input  wire                                    m14_axi_rvalid       ,
  output wire                                    m14_axi_rready       ,
  input  wire [C_M14_AXI_DATA_WIDTH-1:0]         m14_axi_rdata        ,
  input  wire                                    m14_axi_rlast        ,
  // AXI4 master interface m15_axi
  output wire                                    m15_axi_awvalid      ,
  input  wire                                    m15_axi_awready      ,
  output wire [C_M15_AXI_ADDR_WIDTH-1:0]         m15_axi_awaddr       ,
  output wire [8-1:0]                            m15_axi_awlen        ,
  output wire                                    m15_axi_wvalid       ,
  input  wire                                    m15_axi_wready       ,
  output wire [C_M15_AXI_DATA_WIDTH-1:0]         m15_axi_wdata        ,
  output wire [C_M15_AXI_DATA_WIDTH/8-1:0]       m15_axi_wstrb        ,
  output wire                                    m15_axi_wlast        ,
  input  wire                                    m15_axi_bvalid       ,
  output wire                                    m15_axi_bready       ,
  output wire                                    m15_axi_arvalid      ,
  input  wire                                    m15_axi_arready      ,
  output wire [C_M15_AXI_ADDR_WIDTH-1:0]         m15_axi_araddr       ,
  output wire [8-1:0]                            m15_axi_arlen        ,
  input  wire                                    m15_axi_rvalid       ,
  output wire                                    m15_axi_rready       ,
  input  wire [C_M15_AXI_DATA_WIDTH-1:0]         m15_axi_rdata        ,
  input  wire                                    m15_axi_rlast        ,
  // AXI4 master interface m16_axi
  output wire                                    m16_axi_awvalid      ,
  input  wire                                    m16_axi_awready      ,
  output wire [C_M16_AXI_ADDR_WIDTH-1:0]         m16_axi_awaddr       ,
  output wire [8-1:0]                            m16_axi_awlen        ,
  output wire                                    m16_axi_wvalid       ,
  input  wire                                    m16_axi_wready       ,
  output wire [C_M16_AXI_DATA_WIDTH-1:0]         m16_axi_wdata        ,
  output wire [C_M16_AXI_DATA_WIDTH/8-1:0]       m16_axi_wstrb        ,
  output wire                                    m16_axi_wlast        ,
  input  wire                                    m16_axi_bvalid       ,
  output wire                                    m16_axi_bready       ,
  output wire                                    m16_axi_arvalid      ,
  input  wire                                    m16_axi_arready      ,
  output wire [C_M16_AXI_ADDR_WIDTH-1:0]         m16_axi_araddr       ,
  output wire [8-1:0]                            m16_axi_arlen        ,
  input  wire                                    m16_axi_rvalid       ,
  output wire                                    m16_axi_rready       ,
  input  wire [C_M16_AXI_DATA_WIDTH-1:0]         m16_axi_rdata        ,
  input  wire                                    m16_axi_rlast        ,
  // AXI4 master interface m17_axi
  output wire                                    m17_axi_awvalid      ,
  input  wire                                    m17_axi_awready      ,
  output wire [C_M17_AXI_ADDR_WIDTH-1:0]         m17_axi_awaddr       ,
  output wire [8-1:0]                            m17_axi_awlen        ,
  output wire                                    m17_axi_wvalid       ,
  input  wire                                    m17_axi_wready       ,
  output wire [C_M17_AXI_DATA_WIDTH-1:0]         m17_axi_wdata        ,
  output wire [C_M17_AXI_DATA_WIDTH/8-1:0]       m17_axi_wstrb        ,
  output wire                                    m17_axi_wlast        ,
  input  wire                                    m17_axi_bvalid       ,
  output wire                                    m17_axi_bready       ,
  output wire                                    m17_axi_arvalid      ,
  input  wire                                    m17_axi_arready      ,
  output wire [C_M17_AXI_ADDR_WIDTH-1:0]         m17_axi_araddr       ,
  output wire [8-1:0]                            m17_axi_arlen        ,
  input  wire                                    m17_axi_rvalid       ,
  output wire                                    m17_axi_rready       ,
  input  wire [C_M17_AXI_DATA_WIDTH-1:0]         m17_axi_rdata        ,
  input  wire                                    m17_axi_rlast        ,
  // AXI4 master interface m18_axi
  output wire                                    m18_axi_awvalid      ,
  input  wire                                    m18_axi_awready      ,
  output wire [C_M18_AXI_ADDR_WIDTH-1:0]         m18_axi_awaddr       ,
  output wire [8-1:0]                            m18_axi_awlen        ,
  output wire                                    m18_axi_wvalid       ,
  input  wire                                    m18_axi_wready       ,
  output wire [C_M18_AXI_DATA_WIDTH-1:0]         m18_axi_wdata        ,
  output wire [C_M18_AXI_DATA_WIDTH/8-1:0]       m18_axi_wstrb        ,
  output wire                                    m18_axi_wlast        ,
  input  wire                                    m18_axi_bvalid       ,
  output wire                                    m18_axi_bready       ,
  output wire                                    m18_axi_arvalid      ,
  input  wire                                    m18_axi_arready      ,
  output wire [C_M18_AXI_ADDR_WIDTH-1:0]         m18_axi_araddr       ,
  output wire [8-1:0]                            m18_axi_arlen        ,
  input  wire                                    m18_axi_rvalid       ,
  output wire                                    m18_axi_rready       ,
  input  wire [C_M18_AXI_DATA_WIDTH-1:0]         m18_axi_rdata        ,
  input  wire                                    m18_axi_rlast        ,
  // AXI4 master interface m19_axi
  output wire                                    m19_axi_awvalid      ,
  input  wire                                    m19_axi_awready      ,
  output wire [C_M19_AXI_ADDR_WIDTH-1:0]         m19_axi_awaddr       ,
  output wire [8-1:0]                            m19_axi_awlen        ,
  output wire                                    m19_axi_wvalid       ,
  input  wire                                    m19_axi_wready       ,
  output wire [C_M19_AXI_DATA_WIDTH-1:0]         m19_axi_wdata        ,
  output wire [C_M19_AXI_DATA_WIDTH/8-1:0]       m19_axi_wstrb        ,
  output wire                                    m19_axi_wlast        ,
  input  wire                                    m19_axi_bvalid       ,
  output wire                                    m19_axi_bready       ,
  output wire                                    m19_axi_arvalid      ,
  input  wire                                    m19_axi_arready      ,
  output wire [C_M19_AXI_ADDR_WIDTH-1:0]         m19_axi_araddr       ,
  output wire [8-1:0]                            m19_axi_arlen        ,
  input  wire                                    m19_axi_rvalid       ,
  output wire                                    m19_axi_rready       ,
  input  wire [C_M19_AXI_DATA_WIDTH-1:0]         m19_axi_rdata        ,
  input  wire                                    m19_axi_rlast        ,
  // AXI4 master interface m20_axi
  output wire                                    m20_axi_awvalid      ,
  input  wire                                    m20_axi_awready      ,
  output wire [C_M20_AXI_ADDR_WIDTH-1:0]         m20_axi_awaddr       ,
  output wire [8-1:0]                            m20_axi_awlen        ,
  output wire                                    m20_axi_wvalid       ,
  input  wire                                    m20_axi_wready       ,
  output wire [C_M20_AXI_DATA_WIDTH-1:0]         m20_axi_wdata        ,
  output wire [C_M20_AXI_DATA_WIDTH/8-1:0]       m20_axi_wstrb        ,
  output wire                                    m20_axi_wlast        ,
  input  wire                                    m20_axi_bvalid       ,
  output wire                                    m20_axi_bready       ,
  output wire                                    m20_axi_arvalid      ,
  input  wire                                    m20_axi_arready      ,
  output wire [C_M20_AXI_ADDR_WIDTH-1:0]         m20_axi_araddr       ,
  output wire [8-1:0]                            m20_axi_arlen        ,
  input  wire                                    m20_axi_rvalid       ,
  output wire                                    m20_axi_rready       ,
  input  wire [C_M20_AXI_DATA_WIDTH-1:0]         m20_axi_rdata        ,
  input  wire                                    m20_axi_rlast        ,
  // AXI4-Stream (master) interface axis00
  output wire                                    axis00_tvalid        ,
  input  wire                                    axis00_tready        ,
  output wire [C_AXIS00_TDATA_WIDTH-1:0]         axis00_tdata         ,
  output wire [C_AXIS00_TDATA_WIDTH/8-1:0]       axis00_tkeep         ,
  output wire                                    axis00_tlast         ,
  // AXI4-Stream (slave) interface axis01
  input  wire                                    axis01_tvalid        ,
  output wire                                    axis01_tready        ,
  input  wire [C_AXIS01_TDATA_WIDTH-1:0]         axis01_tdata         ,
  input  wire [C_AXIS01_TDATA_WIDTH/8-1:0]       axis01_tkeep         ,
  input  wire                                    axis01_tlast         ,
  // AXI4-Stream (master) interface axis02
  output wire                                    axis02_tvalid        ,
  input  wire                                    axis02_tready        ,
  output wire [C_AXIS02_TDATA_WIDTH-1:0]         axis02_tdata         ,
  output wire [C_AXIS02_TDATA_WIDTH/8-1:0]       axis02_tkeep         ,
  output wire                                    axis02_tlast         ,
  // AXI4-Stream (master) interface axis03
  output wire                                    axis03_tvalid        ,
  input  wire                                    axis03_tready        ,
  output wire [C_AXIS03_TDATA_WIDTH-1:0]         axis03_tdata         ,
  output wire [C_AXIS03_TDATA_WIDTH/8-1:0]       axis03_tkeep         ,
  output wire                                    axis03_tlast         ,
  // AXI4-Stream (master) interface axis04
  output wire                                    axis04_tvalid        ,
  input  wire                                    axis04_tready        ,
  output wire [C_AXIS04_TDATA_WIDTH-1:0]         axis04_tdata         ,
  output wire [C_AXIS04_TDATA_WIDTH/8-1:0]       axis04_tkeep         ,
  output wire                                    axis04_tlast         ,
  // AXI4-Stream (master) interface axis05
  output wire                                    axis05_tvalid        ,
  input  wire                                    axis05_tready        ,
  output wire [C_AXIS05_TDATA_WIDTH-1:0]         axis05_tdata         ,
  output wire [C_AXIS05_TDATA_WIDTH/8-1:0]       axis05_tkeep         ,
  output wire                                    axis05_tlast         ,
  // AXI4-Stream (master) interface axis06
  output wire                                    axis06_tvalid        ,
  input  wire                                    axis06_tready        ,
  output wire [C_AXIS06_TDATA_WIDTH-1:0]         axis06_tdata         ,
  output wire [C_AXIS06_TDATA_WIDTH/8-1:0]       axis06_tkeep         ,
  output wire                                    axis06_tlast         ,
  // AXI4-Stream (master) interface axis07
  output wire                                    axis07_tvalid        ,
  input  wire                                    axis07_tready        ,
  output wire [C_AXIS07_TDATA_WIDTH-1:0]         axis07_tdata         ,
  output wire [C_AXIS07_TDATA_WIDTH/8-1:0]       axis07_tkeep         ,
  output wire                                    axis07_tlast         ,
  // AXI4-Stream (master) interface axis08
  output wire                                    axis08_tvalid        ,
  input  wire                                    axis08_tready        ,
  output wire [C_AXIS08_TDATA_WIDTH-1:0]         axis08_tdata         ,
  output wire [C_AXIS08_TDATA_WIDTH/8-1:0]       axis08_tkeep         ,
  output wire                                    axis08_tlast         ,
  // AXI4-Stream (master) interface axis09
  output wire                                    axis09_tvalid        ,
  input  wire                                    axis09_tready        ,
  output wire [C_AXIS09_TDATA_WIDTH-1:0]         axis09_tdata         ,
  output wire [C_AXIS09_TDATA_WIDTH/8-1:0]       axis09_tkeep         ,
  output wire                                    axis09_tlast         ,
  // AXI4-Stream (slave) interface axis10 — BRBack debug stream
  input  wire                                    axis10_tvalid        ,
  output wire                                    axis10_tready        ,
  input  wire [C_AXIS10_TDATA_WIDTH-1:0]         axis10_tdata         ,
  input  wire [C_AXIS10_TDATA_WIDTH/8-1:0]       axis10_tkeep         ,
  input  wire                                    axis10_tlast         ,
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
);

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
wire [16-1:0]                       offsetindex                   ;
wire [64-1:0]                       axi00_ptr0                    ;
wire [64-1:0]                       axi01_ptr0                    ;
wire [64-1:0]                       axi02_ptr0                    ;
wire [64-1:0]                       axi03_ptr0                    ;
wire [64-1:0]                       axi04_ptr0                    ;
wire [64-1:0]                       axi05_ptr0                    ;
wire [64-1:0]                       axi06_ptr0                    ;
wire [64-1:0]                       axi07_ptr0                    ;
wire [64-1:0]                       axi08_ptr0                    ;
wire [64-1:0]                       axi09_ptr0                    ;
wire [64-1:0]                       axi10_ptr0                    ;
wire [64-1:0]                       axi11_ptr0                    ;
wire [64-1:0]                       axi12_ptr0                    ;
wire [64-1:0]                       axi13_ptr0                    ;
wire [64-1:0]                       axi14_ptr0                    ;
wire [64-1:0]                       axi15_ptr0                    ;
wire [64-1:0]                       axi16_ptr0                    ;
wire [64-1:0]                       axi17_ptr0                    ;
wire [64-1:0]                       axi18_ptr0                    ;
wire [64-1:0]                       axi19_ptr0                    ;
wire [64-1:0]                       axi20_ptr0                    ;

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
  .offsetindex  ( offsetindex           ),
  .axi00_ptr0   ( axi00_ptr0            ),
  .axi01_ptr0   ( axi01_ptr0            ),
  .axi02_ptr0   ( axi02_ptr0            ),
  .axi03_ptr0   ( axi03_ptr0            ),
  .axi04_ptr0   ( axi04_ptr0            ),
  .axi05_ptr0   ( axi05_ptr0            ),
  .axi06_ptr0   ( axi06_ptr0            ),
  .axi07_ptr0   ( axi07_ptr0            ),
  .axi08_ptr0   ( axi08_ptr0            ),
  .axi09_ptr0   ( axi09_ptr0            ),
  .axi10_ptr0   ( axi10_ptr0            ),
  .axi11_ptr0   ( axi11_ptr0            ),
  .axi12_ptr0   ( axi12_ptr0            ),
  .axi13_ptr0   ( axi13_ptr0            ),
  .axi14_ptr0   ( axi14_ptr0            ),
  .axi15_ptr0   ( axi15_ptr0            ),
  .axi16_ptr0   ( axi16_ptr0            ),
  .axi17_ptr0   ( axi17_ptr0            ),
  .axi18_ptr0   ( axi18_ptr0            ),
  .axi19_ptr0   ( axi19_ptr0            ),
  .axi20_ptr0   ( axi20_ptr0            )
);

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

// GlobalOut (S2MM data) wires - after S2MMTlastCounter
wire axi4sout_TVALID;
wire axi4sout_TREADY;
wire [31:0] axi4sout_TDATA;
wire axi4sout_TLAST;

// GlobalOut pre-TLAST wires (between GlobalOutslice and S2MMTlastCounter)
wire gout_pre_TVALID;
wire gout_pre_TREADY;
wire [31:0] gout_pre_TDATA;


// S2MM DataMover status wires (for ILA)
wire dm00_s2mm_err;
wire dm00_s2mm_sts_tvalid;

axi_datamover_0 datamover00 (
  .m_axi_s2mm_aclk(ap_clk),
  .m_axi_s2mm_aresetn(ap_rst_n&user_rst_n),
  .s2mm_err(dm00_s2mm_err),
  .m_axis_s2mm_cmdsts_awclk(ap_clk),
  .m_axis_s2mm_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_s2mm_cmd_tvalid(axi4outcmd_TVALID),
  .s_axis_s2mm_cmd_tready(axi4outcmd_TREADY),
  .s_axis_s2mm_cmd_tdata(axi4outcmd_TDATA),
  .m_axis_s2mm_sts_tvalid(dm00_s2mm_sts_tvalid),
  .m_axis_s2mm_sts_tready(1'b1),
  .m_axis_s2mm_sts_tdata(),
  .m_axis_s2mm_sts_tkeep(),
  .m_axis_s2mm_sts_tlast(),
  .m_axi_s2mm_awid(),
  .m_axi_s2mm_awaddr(m00_axi_awaddr),
  .m_axi_s2mm_awlen(m00_axi_awlen),
  .m_axi_s2mm_awsize(),
  .m_axi_s2mm_awburst(),
  .m_axi_s2mm_awprot(),
  .m_axi_s2mm_awcache(),
  .m_axi_s2mm_awuser(),
  .m_axi_s2mm_awvalid(m00_axi_awvalid),
  .m_axi_s2mm_awready(m00_axi_awready),
  .m_axi_s2mm_wdata(m00_axi_wdata),
  .m_axi_s2mm_wstrb(m00_axi_wstrb),
  .m_axi_s2mm_wlast(m00_axi_wlast),
  .m_axi_s2mm_wvalid(m00_axi_wvalid),
  .m_axi_s2mm_wready(m00_axi_wready),
  .m_axi_s2mm_bresp(2'b0),
  .m_axi_s2mm_bvalid(m00_axi_bvalid),
  .m_axi_s2mm_bready(m00_axi_bready),
  .s_axis_s2mm_tdata(axi4sout_TDATA),
  .s_axis_s2mm_tkeep(4'hF),
  .s_axis_s2mm_tlast(axi4sout_TLAST),
  .s_axis_s2mm_tvalid(axi4sout_TVALID),
  .s_axis_s2mm_tready(axi4sout_TREADY)
);

// MM2S CT_A DataMover status wires (for ILA)
wire dm01_mm2s_err;
wire dm01_mm2s_sts_tvalid;

axi_datamover_1 datamover01 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(dm01_mm2s_err),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4inacmd_TVALID),
  .s_axis_mm2s_cmd_tready(axi4inacmd_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4inacmd_TDATA),
  .m_axis_mm2s_sts_tvalid(dm01_mm2s_sts_tvalid),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m01_axi_araddr),
  .m_axi_mm2s_arlen(m01_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m01_axi_arvalid),
  .m_axi_mm2s_arready(m01_axi_arready),
  .m_axi_mm2s_rdata(m01_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m01_axi_rlast),
  .m_axi_mm2s_rvalid(m01_axi_rvalid),
  .m_axi_mm2s_rready(m01_axi_rready),
  .m_axis_mm2s_tdata(axi4sina_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4sina_TVALID),
  .m_axis_mm2s_tready(axi4sina_TREADY)
);

axi_datamover_1 datamover02 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4inbcmd_TVALID),
  .s_axis_mm2s_cmd_tready(axi4inbcmd_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4inbcmd_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m02_axi_araddr),
  .m_axi_mm2s_arlen(m02_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m02_axi_arvalid),
  .m_axi_mm2s_arready(m02_axi_arready),
  .m_axi_mm2s_rdata(m02_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m02_axi_rlast),
  .m_axi_mm2s_rvalid(m02_axi_rvalid),
  .m_axi_mm2s_rready(m02_axi_rready),
  .m_axis_mm2s_tdata(axi4sinb_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4sinb_TVALID),
  .m_axis_mm2s_tready(axi4sinb_TREADY)
);

wire axi4ikskin_0_TVALID;
wire axi4ikskin_0_TREADY;
wire [511:0] axi4ikskin_0_TDATA;

wire axi4ikskincmd_0_TVALID;
wire axi4ikskincmd_0_TREADY;
wire [103:0] axi4ikskincmd_0_TDATA;

// MM2S IKSK[0] DataMover status wires (for ILA)
wire dm03_mm2s_err;
wire dm03_mm2s_sts_tvalid;

axi_datamover_1 datamover03 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(dm03_mm2s_err),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_0_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_0_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_0_TDATA),
  .m_axis_mm2s_sts_tvalid(dm03_mm2s_sts_tvalid),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m03_axi_araddr),
  .m_axi_mm2s_arlen(m03_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m03_axi_arvalid),
  .m_axi_mm2s_arready(m03_axi_arready),
  .m_axi_mm2s_rdata(m03_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m03_axi_rlast),
  .m_axi_mm2s_rvalid(m03_axi_rvalid),
  .m_axi_mm2s_rready(m03_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_0_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_0_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_0_TREADY)
);

wire axi4ikskin_1_TVALID;
wire axi4ikskin_1_TREADY;
wire [511:0] axi4ikskin_1_TDATA;

wire axi4ikskincmd_1_TVALID;
wire axi4ikskincmd_1_TREADY;
wire [103:0] axi4ikskincmd_1_TDATA;

axi_datamover_1 datamover04 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_1_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_1_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_1_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m04_axi_araddr),
  .m_axi_mm2s_arlen(m04_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m04_axi_arvalid),
  .m_axi_mm2s_arready(m04_axi_arready),
  .m_axi_mm2s_rdata(m04_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m04_axi_rlast),
  .m_axi_mm2s_rvalid(m04_axi_rvalid),
  .m_axi_mm2s_rready(m04_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_1_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_1_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_1_TREADY)
);

wire axi4ikskin_2_TVALID;
wire axi4ikskin_2_TREADY;
wire [511:0] axi4ikskin_2_TDATA;

wire axi4ikskincmd_2_TVALID;
wire axi4ikskincmd_2_TREADY;
wire [103:0] axi4ikskincmd_2_TDATA;

axi_datamover_1 datamover05 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_2_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_2_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_2_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m05_axi_araddr),
  .m_axi_mm2s_arlen(m05_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m05_axi_arvalid),
  .m_axi_mm2s_arready(m05_axi_arready),
  .m_axi_mm2s_rdata(m05_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m05_axi_rlast),
  .m_axi_mm2s_rvalid(m05_axi_rvalid),
  .m_axi_mm2s_rready(m05_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_2_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_2_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_2_TREADY)
);

wire axi4ikskin_3_TVALID;
wire axi4ikskin_3_TREADY;
wire [511:0] axi4ikskin_3_TDATA;

wire axi4ikskincmd_3_TVALID;
wire axi4ikskincmd_3_TREADY;
wire [103:0] axi4ikskincmd_3_TDATA;

axi_datamover_1 datamover06 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_3_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_3_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_3_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m06_axi_araddr),
  .m_axi_mm2s_arlen(m06_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m06_axi_arvalid),
  .m_axi_mm2s_arready(m06_axi_arready),
  .m_axi_mm2s_rdata(m06_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m06_axi_rlast),
  .m_axi_mm2s_rvalid(m06_axi_rvalid),
  .m_axi_mm2s_rready(m06_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_3_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_3_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_3_TREADY)
);

wire axi4ikskin_4_TVALID;
wire axi4ikskin_4_TREADY;
wire [511:0] axi4ikskin_4_TDATA;

wire axi4ikskincmd_4_TVALID;
wire axi4ikskincmd_4_TREADY;
wire [103:0] axi4ikskincmd_4_TDATA;

axi_datamover_1 datamover07 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_4_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_4_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_4_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m07_axi_araddr),
  .m_axi_mm2s_arlen(m07_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m07_axi_arvalid),
  .m_axi_mm2s_arready(m07_axi_arready),
  .m_axi_mm2s_rdata(m07_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m07_axi_rlast),
  .m_axi_mm2s_rvalid(m07_axi_rvalid),
  .m_axi_mm2s_rready(m07_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_4_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_4_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_4_TREADY)
);

wire axi4ikskin_5_TVALID;
wire axi4ikskin_5_TREADY;
wire [511:0] axi4ikskin_5_TDATA;

wire axi4ikskincmd_5_TVALID;
wire axi4ikskincmd_5_TREADY;
wire [103:0] axi4ikskincmd_5_TDATA;

axi_datamover_1 datamover08 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_5_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_5_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_5_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m08_axi_araddr),
  .m_axi_mm2s_arlen(m08_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m08_axi_arvalid),
  .m_axi_mm2s_arready(m08_axi_arready),
  .m_axi_mm2s_rdata(m08_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m08_axi_rlast),
  .m_axi_mm2s_rvalid(m08_axi_rvalid),
  .m_axi_mm2s_rready(m08_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_5_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_5_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_5_TREADY)
);

wire axi4ikskin_6_TVALID;
wire axi4ikskin_6_TREADY;
wire [511:0] axi4ikskin_6_TDATA;

wire axi4ikskincmd_6_TVALID;
wire axi4ikskincmd_6_TREADY;
wire [103:0] axi4ikskincmd_6_TDATA;

axi_datamover_1 datamover09 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_6_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_6_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_6_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m09_axi_araddr),
  .m_axi_mm2s_arlen(m09_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m09_axi_arvalid),
  .m_axi_mm2s_arready(m09_axi_arready),
  .m_axi_mm2s_rdata(m09_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m09_axi_rlast),
  .m_axi_mm2s_rvalid(m09_axi_rvalid),
  .m_axi_mm2s_rready(m09_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_6_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_6_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_6_TREADY)
);

wire axi4ikskin_7_TVALID;
wire axi4ikskin_7_TREADY;
wire [511:0] axi4ikskin_7_TDATA;

wire axi4ikskincmd_7_TVALID;
wire axi4ikskincmd_7_TREADY;
wire [103:0] axi4ikskincmd_7_TDATA;

axi_datamover_1 datamover10 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_7_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_7_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_7_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m10_axi_araddr),
  .m_axi_mm2s_arlen(m10_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m10_axi_arvalid),
  .m_axi_mm2s_arready(m10_axi_arready),
  .m_axi_mm2s_rdata(m10_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m10_axi_rlast),
  .m_axi_mm2s_rvalid(m10_axi_rvalid),
  .m_axi_mm2s_rready(m10_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_7_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_7_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_7_TREADY)
);

wire axi4ikskin_8_TVALID;
wire axi4ikskin_8_TREADY;
wire [511:0] axi4ikskin_8_TDATA;

wire axi4ikskincmd_8_TVALID;
wire axi4ikskincmd_8_TREADY;
wire [103:0] axi4ikskincmd_8_TDATA;

axi_datamover_1 datamover11 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_8_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_8_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_8_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m11_axi_araddr),
  .m_axi_mm2s_arlen(m11_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m11_axi_arvalid),
  .m_axi_mm2s_arready(m11_axi_arready),
  .m_axi_mm2s_rdata(m11_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m11_axi_rlast),
  .m_axi_mm2s_rvalid(m11_axi_rvalid),
  .m_axi_mm2s_rready(m11_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_8_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_8_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_8_TREADY)
);

wire axi4ikskin_9_TVALID;
wire axi4ikskin_9_TREADY;
wire [511:0] axi4ikskin_9_TDATA;

wire axi4ikskincmd_9_TVALID;
wire axi4ikskincmd_9_TREADY;
wire [103:0] axi4ikskincmd_9_TDATA;

axi_datamover_1 datamover12 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4ikskincmd_9_TVALID),
  .s_axis_mm2s_cmd_tready(axi4ikskincmd_9_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4ikskincmd_9_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m12_axi_araddr),
  .m_axi_mm2s_arlen(m12_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m12_axi_arvalid),
  .m_axi_mm2s_arready(m12_axi_arready),
  .m_axi_mm2s_rdata(m12_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m12_axi_rlast),
  .m_axi_mm2s_rvalid(m12_axi_rvalid),
  .m_axi_mm2s_rready(m12_axi_rready),
  .m_axis_mm2s_tdata(axi4ikskin_9_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(),
  .m_axis_mm2s_tvalid(axi4ikskin_9_TVALID),
  .m_axis_mm2s_tready(axi4ikskin_9_TREADY)
);

wire axi4bkincmd_0_TVALID;
wire axi4bkincmd_0_TREADY;
wire [103:0] axi4bkincmd_0_TDATA;
wire axi4bkin_0_TVALID;
wire axi4bkin_0_TREADY;
wire [511:0] axi4bkin_0_TDATA;
wire axi4bkin_0_TLAST;

wire datamover13_mm2s_err;
wire datamover13_mm2s_sts_tvalid;
wire [7:0] datamover13_mm2s_sts_tdata;
axi_datamover_1 datamover13 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(datamover13_mm2s_err),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_0_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_0_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_0_TDATA),
  .m_axis_mm2s_sts_tvalid(datamover13_mm2s_sts_tvalid),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(datamover13_mm2s_sts_tdata),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m13_axi_araddr),
  .m_axi_mm2s_arlen(m13_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m13_axi_arvalid),
  .m_axi_mm2s_arready(m13_axi_arready),
  .m_axi_mm2s_rdata(m13_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m13_axi_rlast),
  .m_axi_mm2s_rvalid(m13_axi_rvalid),
  .m_axi_mm2s_rready(m13_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_0_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_0_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_0_TVALID),
  .m_axis_mm2s_tready(axi4bkin_0_TREADY)
);

wire axi4bkincmd_1_TVALID;
wire axi4bkincmd_1_TREADY;
wire [103:0] axi4bkincmd_1_TDATA;
wire axi4bkin_1_TVALID;
wire axi4bkin_1_TREADY;
wire [511:0] axi4bkin_1_TDATA;
wire axi4bkin_1_TLAST;

axi_datamover_1 datamover14 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_1_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_1_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_1_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m14_axi_araddr),
  .m_axi_mm2s_arlen(m14_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m14_axi_arvalid),
  .m_axi_mm2s_arready(m14_axi_arready),
  .m_axi_mm2s_rdata(m14_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m14_axi_rlast),
  .m_axi_mm2s_rvalid(m14_axi_rvalid),
  .m_axi_mm2s_rready(m14_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_1_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_1_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_1_TVALID),
  .m_axis_mm2s_tready(axi4bkin_1_TREADY)
);

wire axi4bkincmd_2_TVALID;
wire axi4bkincmd_2_TREADY;
wire [103:0] axi4bkincmd_2_TDATA;
wire axi4bkin_2_TVALID;
wire axi4bkin_2_TREADY;
wire [511:0] axi4bkin_2_TDATA;
wire axi4bkin_2_TLAST;

axi_datamover_1 datamover15 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_2_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_2_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_2_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m15_axi_araddr),
  .m_axi_mm2s_arlen(m15_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m15_axi_arvalid),
  .m_axi_mm2s_arready(m15_axi_arready),
  .m_axi_mm2s_rdata(m15_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m15_axi_rlast),
  .m_axi_mm2s_rvalid(m15_axi_rvalid),
  .m_axi_mm2s_rready(m15_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_2_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_2_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_2_TVALID),
  .m_axis_mm2s_tready(axi4bkin_2_TREADY)
);

wire axi4bkincmd_3_TVALID;
wire axi4bkincmd_3_TREADY;
wire [103:0] axi4bkincmd_3_TDATA;
wire axi4bkin_3_TVALID;
wire axi4bkin_3_TREADY;
wire [511:0] axi4bkin_3_TDATA;
wire axi4bkin_3_TLAST;

axi_datamover_1 datamover16 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_3_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_3_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_3_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m16_axi_araddr),
  .m_axi_mm2s_arlen(m16_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m16_axi_arvalid),
  .m_axi_mm2s_arready(m16_axi_arready),
  .m_axi_mm2s_rdata(m16_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m16_axi_rlast),
  .m_axi_mm2s_rvalid(m16_axi_rvalid),
  .m_axi_mm2s_rready(m16_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_3_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_3_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_3_TVALID),
  .m_axis_mm2s_tready(axi4bkin_3_TREADY)
);

wire axi4bkincmd_4_TVALID;
wire axi4bkincmd_4_TREADY;
wire [103:0] axi4bkincmd_4_TDATA;
wire axi4bkin_4_TVALID;
wire axi4bkin_4_TREADY;
wire [511:0] axi4bkin_4_TDATA;
wire axi4bkin_4_TLAST;

axi_datamover_1 datamover17 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_4_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_4_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_4_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m17_axi_araddr),
  .m_axi_mm2s_arlen(m17_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m17_axi_arvalid),
  .m_axi_mm2s_arready(m17_axi_arready),
  .m_axi_mm2s_rdata(m17_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m17_axi_rlast),
  .m_axi_mm2s_rvalid(m17_axi_rvalid),
  .m_axi_mm2s_rready(m17_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_4_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_4_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_4_TVALID),
  .m_axis_mm2s_tready(axi4bkin_4_TREADY)
);

wire axi4bkincmd_5_TVALID;
wire axi4bkincmd_5_TREADY;
wire [103:0] axi4bkincmd_5_TDATA;
wire axi4bkin_5_TVALID;
wire axi4bkin_5_TREADY;
wire [511:0] axi4bkin_5_TDATA;
wire axi4bkin_5_TLAST;

axi_datamover_1 datamover18 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_5_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_5_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_5_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m18_axi_araddr),
  .m_axi_mm2s_arlen(m18_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m18_axi_arvalid),
  .m_axi_mm2s_arready(m18_axi_arready),
  .m_axi_mm2s_rdata(m18_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m18_axi_rlast),
  .m_axi_mm2s_rvalid(m18_axi_rvalid),
  .m_axi_mm2s_rready(m18_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_5_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_5_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_5_TVALID),
  .m_axis_mm2s_tready(axi4bkin_5_TREADY)
);

wire axi4bkincmd_6_TVALID;
wire axi4bkincmd_6_TREADY;
wire [103:0] axi4bkincmd_6_TDATA;
wire axi4bkin_6_TVALID;
wire axi4bkin_6_TREADY;
wire [511:0] axi4bkin_6_TDATA;
wire axi4bkin_6_TLAST;

axi_datamover_1 datamover19 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_6_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_6_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_6_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m19_axi_araddr),
  .m_axi_mm2s_arlen(m19_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m19_axi_arvalid),
  .m_axi_mm2s_arready(m19_axi_arready),
  .m_axi_mm2s_rdata(m19_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m19_axi_rlast),
  .m_axi_mm2s_rvalid(m19_axi_rvalid),
  .m_axi_mm2s_rready(m19_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_6_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_6_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_6_TVALID),
  .m_axis_mm2s_tready(axi4bkin_6_TREADY)
);

wire axi4bkincmd_7_TVALID;
wire axi4bkincmd_7_TREADY;
wire [103:0] axi4bkincmd_7_TDATA;
wire axi4bkin_7_TVALID;
wire axi4bkin_7_TREADY;
wire [511:0] axi4bkin_7_TDATA;
wire axi4bkin_7_TLAST;

axi_datamover_1 datamover20 (
  .m_axi_mm2s_aclk(ap_clk),
  .m_axi_mm2s_aresetn(ap_rst_n&user_rst_n),
  .mm2s_err(),
  .m_axis_mm2s_cmdsts_aclk(ap_clk),
  .m_axis_mm2s_cmdsts_aresetn(ap_rst_n&user_rst_n),
  .s_axis_mm2s_cmd_tvalid(axi4bkincmd_7_TVALID),
  .s_axis_mm2s_cmd_tready(axi4bkincmd_7_TREADY),
  .s_axis_mm2s_cmd_tdata(axi4bkincmd_7_TDATA),
  .m_axis_mm2s_sts_tvalid(),
  .m_axis_mm2s_sts_tready(1'b1),
  .m_axis_mm2s_sts_tdata(),
  .m_axis_mm2s_sts_tkeep(),
  .m_axis_mm2s_sts_tlast(),
  .m_axi_mm2s_arid(),
  .m_axi_mm2s_araddr(m20_axi_araddr),
  .m_axi_mm2s_arlen(m20_axi_arlen),
  .m_axi_mm2s_arsize(),
  .m_axi_mm2s_arburst(),
  .m_axi_mm2s_arprot(),
  .m_axi_mm2s_arcache(),
  .m_axi_mm2s_aruser(),
  .m_axi_mm2s_arvalid(m20_axi_arvalid),
  .m_axi_mm2s_arready(m20_axi_arready),
  .m_axi_mm2s_rdata(m20_axi_rdata),
  .m_axi_mm2s_rresp(2'b0),
  .m_axi_mm2s_rlast(m20_axi_rlast),
  .m_axi_mm2s_rvalid(m20_axi_rvalid),
  .m_axi_mm2s_rready(m20_axi_rready),
  .m_axis_mm2s_tdata(axi4bkin_7_TDATA),
  .m_axis_mm2s_tkeep(),
  .m_axis_mm2s_tlast(axi4bkin_7_TLAST),
  .m_axis_mm2s_tvalid(axi4bkin_7_TVALID),
  .m_axis_mm2s_tready(axi4bkin_7_TREADY)
);

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
);

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
  .io_axi4inbcmd_TDATA(axi4inbcmd_TDATA),
  .io_axi4ikskincmd_0_TVALID(axi4ikskincmd_0_TVALID),
  .io_axi4ikskincmd_0_TREADY(axi4ikskincmd_0_TREADY),
  .io_axi4ikskincmd_0_TDATA(axi4ikskincmd_0_TDATA),
  .io_axi4ikskincmd_1_TVALID(axi4ikskincmd_1_TVALID),
  .io_axi4ikskincmd_1_TREADY(axi4ikskincmd_1_TREADY),
  .io_axi4ikskincmd_1_TDATA(axi4ikskincmd_1_TDATA),
  .io_axi4ikskincmd_2_TVALID(axi4ikskincmd_2_TVALID),
  .io_axi4ikskincmd_2_TREADY(axi4ikskincmd_2_TREADY),
  .io_axi4ikskincmd_2_TDATA(axi4ikskincmd_2_TDATA),
  .io_axi4ikskincmd_3_TVALID(axi4ikskincmd_3_TVALID),
  .io_axi4ikskincmd_3_TREADY(axi4ikskincmd_3_TREADY),
  .io_axi4ikskincmd_3_TDATA(axi4ikskincmd_3_TDATA),
  .io_axi4ikskincmd_4_TVALID(axi4ikskincmd_4_TVALID),
  .io_axi4ikskincmd_4_TREADY(axi4ikskincmd_4_TREADY),
  .io_axi4ikskincmd_4_TDATA(axi4ikskincmd_4_TDATA),
  .io_axi4ikskincmd_5_TVALID(axi4ikskincmd_5_TVALID),
  .io_axi4ikskincmd_5_TREADY(axi4ikskincmd_5_TREADY),
  .io_axi4ikskincmd_5_TDATA(axi4ikskincmd_5_TDATA),
  .io_axi4ikskincmd_6_TVALID(axi4ikskincmd_6_TVALID),
  .io_axi4ikskincmd_6_TREADY(axi4ikskincmd_6_TREADY),
  .io_axi4ikskincmd_6_TDATA(axi4ikskincmd_6_TDATA),
  .io_axi4ikskincmd_7_TVALID(axi4ikskincmd_7_TVALID),
  .io_axi4ikskincmd_7_TREADY(axi4ikskincmd_7_TREADY),
  .io_axi4ikskincmd_7_TDATA(axi4ikskincmd_7_TDATA),
  .io_axi4ikskincmd_8_TVALID(axi4ikskincmd_8_TVALID),
  .io_axi4ikskincmd_8_TREADY(axi4ikskincmd_8_TREADY),
  .io_axi4ikskincmd_8_TDATA(axi4ikskincmd_8_TDATA),
  .io_axi4ikskincmd_9_TVALID(axi4ikskincmd_9_TVALID),
  .io_axi4ikskincmd_9_TREADY(axi4ikskincmd_9_TREADY),
  .io_axi4ikskincmd_9_TDATA(axi4ikskincmd_9_TDATA),
  .io_axi4bkincmd_0_TVALID(axi4bkincmd_0_TVALID),
  .io_axi4bkincmd_0_TREADY(axi4bkincmd_0_TREADY),
  .io_axi4bkincmd_0_TDATA(axi4bkincmd_0_TDATA),
  .io_axi4bkincmd_1_TVALID(axi4bkincmd_1_TVALID),
  .io_axi4bkincmd_1_TREADY(axi4bkincmd_1_TREADY),
  .io_axi4bkincmd_1_TDATA(axi4bkincmd_1_TDATA),
  .io_axi4bkincmd_2_TVALID(axi4bkincmd_2_TVALID),
  .io_axi4bkincmd_2_TREADY(axi4bkincmd_2_TREADY),
  .io_axi4bkincmd_2_TDATA(axi4bkincmd_2_TDATA),
  .io_axi4bkincmd_3_TVALID(axi4bkincmd_3_TVALID),
  .io_axi4bkincmd_3_TREADY(axi4bkincmd_3_TREADY),
  .io_axi4bkincmd_3_TDATA(axi4bkincmd_3_TDATA),
  .io_axi4bkincmd_4_TVALID(axi4bkincmd_4_TVALID),
  .io_axi4bkincmd_4_TREADY(axi4bkincmd_4_TREADY),
  .io_axi4bkincmd_4_TDATA(axi4bkincmd_4_TDATA),
  .io_axi4bkincmd_5_TVALID(axi4bkincmd_5_TVALID),
  .io_axi4bkincmd_5_TREADY(axi4bkincmd_5_TREADY),
  .io_axi4bkincmd_5_TDATA(axi4bkincmd_5_TDATA),
  .io_axi4bkincmd_6_TVALID(axi4bkincmd_6_TVALID),
  .io_axi4bkincmd_6_TREADY(axi4bkincmd_6_TREADY),
  .io_axi4bkincmd_6_TDATA(axi4bkincmd_6_TDATA),
  .io_axi4bkincmd_7_TVALID(axi4bkincmd_7_TVALID),
  .io_axi4bkincmd_7_TREADY(axi4bkincmd_7_TREADY),
  .io_axi4bkincmd_7_TDATA(axi4bkincmd_7_TDATA),
  .io_outaddr(axi00_ptr0),
  .io_inaaddr(axi01_ptr0),
  .io_inbaddr(axi02_ptr0),
  .io_ikskaddr_0(axi03_ptr0),
  .io_ikskaddr_1(axi04_ptr0),
  .io_ikskaddr_2(axi05_ptr0),
  .io_ikskaddr_3(axi06_ptr0),
  .io_ikskaddr_4(axi07_ptr0),
  .io_ikskaddr_5(axi08_ptr0),
  .io_ikskaddr_6(axi09_ptr0),
  .io_ikskaddr_7(axi10_ptr0),
  .io_ikskaddr_8(axi11_ptr0),
  .io_ikskaddr_9(axi12_ptr0),
  .io_bkaddr_0(axi13_ptr0),
  .io_bkaddr_1(axi14_ptr0),
  .io_bkaddr_2(axi15_ptr0),
  .io_bkaddr_3(axi16_ptr0),
  .io_bkaddr_4(axi17_ptr0),
  .io_bkaddr_5(axi18_ptr0),
  .io_bkaddr_6(axi19_ptr0),
  .io_bkaddr_7(axi20_ptr0),
  .io_user_rst(user_rst),
  .io_ap_start(ap_start),
  .io_ap_done(ap_done),
  .io_ap_idle(ap_idle),
  .io_ap_ready(ap_ready)
);

// axis10: unused debug stream — always consume
assign axis10_tready = 1'b1;

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
  .io_axi4in_TDATA(axi4sadded_TDATA),
  .io_axi4ikskin_0_TVALID(axi4ikskin_0_TVALID),
  .io_axi4ikskin_0_TREADY(axi4ikskin_0_TREADY),
  .io_axi4ikskin_0_TDATA(axi4ikskin_0_TDATA),
  .io_axi4ikskin_1_TVALID(axi4ikskin_1_TVALID),
  .io_axi4ikskin_1_TREADY(axi4ikskin_1_TREADY),
  .io_axi4ikskin_1_TDATA(axi4ikskin_1_TDATA),
  .io_axi4ikskin_2_TVALID(axi4ikskin_2_TVALID),
  .io_axi4ikskin_2_TREADY(axi4ikskin_2_TREADY),
  .io_axi4ikskin_2_TDATA(axi4ikskin_2_TDATA),
  .io_axi4ikskin_3_TVALID(axi4ikskin_3_TVALID),
  .io_axi4ikskin_3_TREADY(axi4ikskin_3_TREADY),
  .io_axi4ikskin_3_TDATA(axi4ikskin_3_TDATA),
  .io_axi4ikskin_4_TVALID(axi4ikskin_4_TVALID),
  .io_axi4ikskin_4_TREADY(axi4ikskin_4_TREADY),
  .io_axi4ikskin_4_TDATA(axi4ikskin_4_TDATA),
  .io_axi4ikskin_5_TVALID(axi4ikskin_5_TVALID),
  .io_axi4ikskin_5_TREADY(axi4ikskin_5_TREADY),
  .io_axi4ikskin_5_TDATA(axi4ikskin_5_TDATA),
  .io_axi4ikskin_6_TVALID(axi4ikskin_6_TVALID),
  .io_axi4ikskin_6_TREADY(axi4ikskin_6_TREADY),
  .io_axi4ikskin_6_TDATA(axi4ikskin_6_TDATA),
  .io_axi4ikskin_7_TVALID(axi4ikskin_7_TVALID),
  .io_axi4ikskin_7_TREADY(axi4ikskin_7_TREADY),
  .io_axi4ikskin_7_TDATA(axi4ikskin_7_TDATA),
  .io_axi4ikskin_8_TVALID(axi4ikskin_8_TVALID),
  .io_axi4ikskin_8_TREADY(axi4ikskin_8_TREADY),
  .io_axi4ikskin_8_TDATA(axi4ikskin_8_TDATA),
  .io_axi4ikskin_9_TVALID(axi4ikskin_9_TVALID),
  .io_axi4ikskin_9_TREADY(axi4ikskin_9_TREADY),
  .io_axi4ikskin_9_TDATA(axi4ikskin_9_TDATA),
  .clock(ap_clk),
  .reset(areset)
);

///////////////////////////////////////////////////////////////////////////////
// BK data pass-through to BRBack kernel via axis02-09
// (AXISBRMiddle has moved to BRBack kernel)
///////////////////////////////////////////////////////////////////////////////
assign axis02_tvalid = axi4bkin_0_TVALID;
assign axis02_tdata  = axi4bkin_0_TDATA;
assign axis02_tkeep  = {64{1'b1}};
assign axis02_tlast  = axi4bkin_0_TLAST;
assign axi4bkin_0_TREADY = axis02_tready;

assign axis03_tvalid = axi4bkin_1_TVALID;
assign axis03_tdata  = axi4bkin_1_TDATA;
assign axis03_tkeep  = {64{1'b1}};
assign axis03_tlast  = axi4bkin_1_TLAST;
assign axi4bkin_1_TREADY = axis03_tready;

assign axis04_tvalid = axi4bkin_2_TVALID;
assign axis04_tdata  = axi4bkin_2_TDATA;
assign axis04_tkeep  = {64{1'b1}};
assign axis04_tlast  = axi4bkin_2_TLAST;
assign axi4bkin_2_TREADY = axis04_tready;

assign axis05_tvalid = axi4bkin_3_TVALID;
assign axis05_tdata  = axi4bkin_3_TDATA;
assign axis05_tkeep  = {64{1'b1}};
assign axis05_tlast  = axi4bkin_3_TLAST;
assign axi4bkin_3_TREADY = axis05_tready;

assign axis06_tvalid = axi4bkin_4_TVALID;
assign axis06_tdata  = axi4bkin_4_TDATA;
assign axis06_tkeep  = {64{1'b1}};
assign axis06_tlast  = axi4bkin_4_TLAST;
assign axi4bkin_4_TREADY = axis06_tready;

assign axis07_tvalid = axi4bkin_5_TVALID;
assign axis07_tdata  = axi4bkin_5_TDATA;
assign axis07_tkeep  = {64{1'b1}};
assign axis07_tlast  = axi4bkin_5_TLAST;
assign axi4bkin_5_TREADY = axis07_tready;

assign axis08_tvalid = axi4bkin_6_TVALID;
assign axis08_tdata  = axi4bkin_6_TDATA;
assign axis08_tkeep  = {64{1'b1}};
assign axis08_tlast  = axi4bkin_6_TLAST;
assign axi4bkin_6_TREADY = axis08_tready;

assign axis09_tvalid = axi4bkin_7_TVALID;
assign axis09_tdata  = axi4bkin_7_TDATA;
assign axis09_tkeep  = {64{1'b1}};
assign axis09_tlast  = axi4bkin_7_TLAST;
assign axi4bkin_7_TREADY = axis09_tready;

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
assign axis00_tlast = 1'b0;

// GlobalOutslice: axis01 (from BRFront) -> pre-TLAST wires
GlobalOutslice globaloutsliceSLR1toSLR0(
  .clock(ap_clk),
  .reset(areset),
  .io_subordinate_TVALID(axis01_tvalid),
  .io_subordinate_TREADY(axis01_tready),
  .io_subordinate_TDATA(axis01_tdata),
  .io_manager_TVALID(gout_pre_TVALID),
  .io_manager_TREADY(gout_pre_TREADY),
  .io_manager_TDATA(gout_pre_TDATA)
);

// S2MMTlastCounter: generates TLAST for S2MM DRE flush (1025 beats per TLWE)
S2MMTlastCounter s2mmtlastcnt(
  .clock(ap_clk),
  .reset(areset),
  .io_subordinate_TVALID(gout_pre_TVALID),
  .io_subordinate_TREADY(gout_pre_TREADY),
  .io_subordinate_TDATA(gout_pre_TDATA),
  .io_manager_TVALID(axi4sout_TVALID),
  .io_manager_TREADY(axi4sout_TREADY),
  .io_manager_TDATA(axi4sout_TDATA),
  .io_tlast(axi4sout_TLAST)
);

endmodule
`default_nettype wire