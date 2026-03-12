`timescale 1ns/1ps

module HomGate_control_s_axi #(
    parameter integer C_S_AXI_ADDR_WIDTH = 12,
    parameter integer C_S_AXI_DATA_WIDTH = 32
) (
    input  wire                            ACLK,
    input  wire                            ARESET,
    input  wire                            ACLK_EN,
    // AXI4-Lite slave interface
    input  wire                            AWVALID,
    output wire                            AWREADY,
    input  wire [C_S_AXI_ADDR_WIDTH-1:0]   AWADDR,
    input  wire                            WVALID,
    output wire                            WREADY,
    input  wire [C_S_AXI_DATA_WIDTH-1:0]   WDATA,
    input  wire [C_S_AXI_DATA_WIDTH/8-1:0] WSTRB,
    input  wire                            ARVALID,
    output wire                            ARREADY,
    input  wire [C_S_AXI_ADDR_WIDTH-1:0]   ARADDR,
    output wire                            RVALID,
    input  wire                            RREADY,
    output wire [C_S_AXI_DATA_WIDTH-1:0]   RDATA,
    output wire [1:0]                      RRESP,
    output wire                            BVALID,
    input  wire                            BREADY,
    output wire [1:0]                      BRESP,
    output wire                            interrupt,
    // User ports
    output wire                            ap_start,
    input  wire                            ap_done,
    input  wire                            ap_ready,
    input  wire                            ap_idle,
    output wire [15:0]                     scaledaindex,
    output wire [15:0]                     scaledbindex,
    output wire [15:0]                     offsetindex,
    output wire [63:0]                     axi00_ptr0,
    output wire [63:0]                     axi01_ptr0,
    output wire [63:0]                     axi02_ptr0,
    output wire [63:0]                     axi03_ptr0,
    output wire [63:0]                     axi04_ptr0,
    output wire [63:0]                     axi05_ptr0,
    output wire [63:0]                     axi06_ptr0,
    output wire [63:0]                     axi07_ptr0,
    output wire [63:0]                     axi08_ptr0,
    output wire [63:0]                     axi09_ptr0,
    output wire [63:0]                     axi10_ptr0,
    output wire [63:0]                     axi11_ptr0,
    output wire [63:0]                     axi12_ptr0,
    output wire [63:0]                     axi13_ptr0,
    output wire [63:0]                     axi14_ptr0,
    output wire [63:0]                     axi15_ptr0,
    output wire [63:0]                     axi16_ptr0,
    output wire [63:0]                     axi17_ptr0,
    output wire [63:0]                     axi18_ptr0,
    output wire [63:0]                     axi19_ptr0,
    output wire [63:0]                     axi20_ptr0,
    // Debug read-only registers
    input  wire [31:0]                     dbg_reg0,
    input  wire [31:0]                     dbg_reg1,
    input  wire [31:0]                     dbg_reg2
);

    //------------------------Address Info-------------------
    // 0x000 : Control signals
    //         bit 0  - ap_start (Read/Write/COH)
    //         bit 1  - ap_done (Read/COR)
    //         bit 2  - ap_idle (Read)
    //         bit 3  - ap_ready (Read/COR)
    //         bit 7  - auto_restart (Read/Write)
    //         others - reserved
    // 0x004 : Global Interrupt Enable Register
    //         bit 0  - Global Interrupt Enable (Read/Write)
    //         others - reserved
    // 0x008 : IP Interrupt Enable Register (Read/Write)
    //         bit 0  - enable ap_done interrupt (Read/Write)
    //         bit 1  - enable ap_ready interrupt (Read/Write)
    //         others - reserved
    // 0x00c : IP Interrupt Status Register (Read/TOW)
    //         bit 0  - ap_done (Read/TOW)
    //         bit 1  - ap_ready (Read/TOW)
    //         others - reserved
    // 0x010 : Data signal of scaledaindex
    //         bit 15:0 - scaledaindex[15:0] (Read/Write)
    //         others   - reserved
    // 0x018 : Data signal of scaledbindex
    //         bit 15:0 - scaledbindex[15:0] (Read/Write)
    //         others   - reserved
    // 0x020 : Data signal of offsetindex
    //         bit 15:0 - offsetindex[15:0] (Read/Write)
    //         others   - reserved
    // 0x028 : Data signal of axi00_ptr0
    //         bit 31:0 - axi00_ptr0[31:0] (Read/Write)
    // 0x02c : Data signal of axi00_ptr0
    //         bit 31:0 - axi00_ptr0[63:32] (Read/Write)
    // ... (similar pattern for axi01 through axi20)
    //------------------------Parameter----------------------
    localparam
        ADDR_AP_CTRL             = 12'h000,
        ADDR_GIE                 = 12'h004,
        ADDR_IER                 = 12'h008,
        ADDR_ISR                 = 12'h00c,
        ADDR_SCALEDAINDEX_DATA_0 = 12'h010,
        ADDR_SCALEDBINDEX_DATA_0 = 12'h018,
        ADDR_OFFSETINDEX_DATA_0  = 12'h020,
        ADDR_AXI00_PTR0_DATA_0   = 12'h028,
        ADDR_AXI00_PTR0_DATA_1   = 12'h02c,
        ADDR_AXI01_PTR0_DATA_0   = 12'h034,
        ADDR_AXI01_PTR0_DATA_1   = 12'h038,
        ADDR_AXI02_PTR0_DATA_0   = 12'h040,
        ADDR_AXI02_PTR0_DATA_1   = 12'h044,
        ADDR_AXI03_PTR0_DATA_0   = 12'h04c,
        ADDR_AXI03_PTR0_DATA_1   = 12'h050,
        ADDR_AXI04_PTR0_DATA_0   = 12'h058,
        ADDR_AXI04_PTR0_DATA_1   = 12'h05c,
        ADDR_AXI05_PTR0_DATA_0   = 12'h064,
        ADDR_AXI05_PTR0_DATA_1   = 12'h068,
        ADDR_AXI06_PTR0_DATA_0   = 12'h070,
        ADDR_AXI06_PTR0_DATA_1   = 12'h074,
        ADDR_AXI07_PTR0_DATA_0   = 12'h07c,
        ADDR_AXI07_PTR0_DATA_1   = 12'h080,
        ADDR_AXI08_PTR0_DATA_0   = 12'h088,
        ADDR_AXI08_PTR0_DATA_1   = 12'h08c,
        ADDR_AXI09_PTR0_DATA_0   = 12'h094,
        ADDR_AXI09_PTR0_DATA_1   = 12'h098,
        ADDR_AXI10_PTR0_DATA_0   = 12'h0a0,
        ADDR_AXI10_PTR0_DATA_1   = 12'h0a4,
        ADDR_AXI11_PTR0_DATA_0   = 12'h0ac,
        ADDR_AXI11_PTR0_DATA_1   = 12'h0b0,
        ADDR_AXI12_PTR0_DATA_0   = 12'h0b8,
        ADDR_AXI12_PTR0_DATA_1   = 12'h0bc,
        ADDR_AXI13_PTR0_DATA_0   = 12'h0c4,
        ADDR_AXI13_PTR0_DATA_1   = 12'h0c8,
        ADDR_AXI14_PTR0_DATA_0   = 12'h0d0,
        ADDR_AXI14_PTR0_DATA_1   = 12'h0d4,
        ADDR_AXI15_PTR0_DATA_0   = 12'h0dc,
        ADDR_AXI15_PTR0_DATA_1   = 12'h0e0,
        ADDR_AXI16_PTR0_DATA_0   = 12'h0e8,
        ADDR_AXI16_PTR0_DATA_1   = 12'h0ec,
        ADDR_AXI17_PTR0_DATA_0   = 12'h0f4,
        ADDR_AXI17_PTR0_DATA_1   = 12'h0f8,
        ADDR_AXI18_PTR0_DATA_0   = 12'h100,
        ADDR_AXI18_PTR0_DATA_1   = 12'h104,
        ADDR_AXI19_PTR0_DATA_0   = 12'h10c,
        ADDR_AXI19_PTR0_DATA_1   = 12'h110,
        ADDR_AXI20_PTR0_DATA_0   = 12'h118,
        ADDR_AXI20_PTR0_DATA_1   = 12'h11c,
        ADDR_DBG_REG0            = 12'h200,  // AXISIKS output beat count
        ADDR_DBG_REG1            = 12'h204,  // axis01 input beat count (SEI→HomGate)
        ADDR_DBG_REG2            = 12'h208,  // misc status bits
        WRIDLE                   = 2'd0,
        WRDATA                   = 2'd1,
        WRRESP                   = 2'd2,
        WRRESET                  = 2'd3,
        RDIDLE                   = 2'd0,
        RDDATA                   = 2'd1,
        RDRESET                  = 2'd2,
        ADDR_BITS = C_S_AXI_ADDR_WIDTH;

    //------------------------Local signal-------------------
    reg  [1:0]                    wstate = WRRESET;
    reg  [1:0]                    wnext;
    reg  [ADDR_BITS-1:0]          waddr;
    wire [C_S_AXI_DATA_WIDTH-1:0] wmask;
    wire                          aw_hs;
    wire                          w_hs;
    reg  [1:0]                    rstate = RDRESET;
    reg  [1:0]                    rnext;
    reg  [C_S_AXI_DATA_WIDTH-1:0] rdata;
    wire                          ar_hs;
    wire [ADDR_BITS-1:0]          raddr;
    // internal registers
    reg                           int_ap_idle;
    reg                           int_ap_ready = 1'b0;
    reg                           int_ap_done  = 1'b0;
    reg                           int_ap_start = 1'b0;
    reg                           int_auto_restart = 1'b0;
    reg                           int_gie = 1'b0;
    reg  [1:0]                    int_ier = 2'b0;
    reg  [1:0]                    int_isr = 2'b0;
    reg  [15:0]                   int_scaledaindex = 16'b0;
    reg  [15:0]                   int_scaledbindex = 16'b0;
    reg  [15:0]                   int_offsetindex  = 16'b0;
    reg  [63:0]                   int_axi00_ptr0 = 64'b0;
    reg  [63:0]                   int_axi01_ptr0 = 64'b0;
    reg  [63:0]                   int_axi02_ptr0 = 64'b0;
    reg  [63:0]                   int_axi03_ptr0 = 64'b0;
    reg  [63:0]                   int_axi04_ptr0 = 64'b0;
    reg  [63:0]                   int_axi05_ptr0 = 64'b0;
    reg  [63:0]                   int_axi06_ptr0 = 64'b0;
    reg  [63:0]                   int_axi07_ptr0 = 64'b0;
    reg  [63:0]                   int_axi08_ptr0 = 64'b0;
    reg  [63:0]                   int_axi09_ptr0 = 64'b0;
    reg  [63:0]                   int_axi10_ptr0 = 64'b0;
    reg  [63:0]                   int_axi11_ptr0 = 64'b0;
    reg  [63:0]                   int_axi12_ptr0 = 64'b0;
    reg  [63:0]                   int_axi13_ptr0 = 64'b0;
    reg  [63:0]                   int_axi14_ptr0 = 64'b0;
    reg  [63:0]                   int_axi15_ptr0 = 64'b0;
    reg  [63:0]                   int_axi16_ptr0 = 64'b0;
    reg  [63:0]                   int_axi17_ptr0 = 64'b0;
    reg  [63:0]                   int_axi18_ptr0 = 64'b0;
    reg  [63:0]                   int_axi19_ptr0 = 64'b0;
    reg  [63:0]                   int_axi20_ptr0 = 64'b0;

    //------------------------Instantiation------------------

    //------------------------AXI write fsm------------------
    assign AWREADY = (wstate == WRIDLE);
    assign WREADY  = (wstate == WRDATA);
    assign BRESP   = 2'b00;  // OKAY
    assign BVALID  = (wstate == WRRESP);
    assign wmask   = { {8{WSTRB[3]}}, {8{WSTRB[2]}}, {8{WSTRB[1]}}, {8{WSTRB[0]}} };
    assign aw_hs   = AWVALID & AWREADY;
    assign w_hs    = WVALID & WREADY;

    // wstate
    always @(posedge ACLK) begin
        if (ARESET)
            wstate <= WRRESET;
        else if (ACLK_EN)
            wstate <= wnext;
    end

    // wnext
    always @(*) begin
        case (wstate)
            WRIDLE:
                if (AWVALID)
                    wnext = WRDATA;
                else
                    wnext = WRIDLE;
            WRDATA:
                if (WVALID)
                    wnext = WRRESP;
                else
                    wnext = WRDATA;
            WRRESP:
                if (BREADY)
                    wnext = WRIDLE;
                else
                    wnext = WRRESP;
            default:
                wnext = WRIDLE;
        endcase
    end

    // waddr
    always @(posedge ACLK) begin
        if (ACLK_EN) begin
            if (aw_hs)
                waddr <= AWADDR[ADDR_BITS-1:0];
        end
    end

    //------------------------AXI read fsm-------------------
    assign ARREADY = (rstate == RDIDLE);
    assign RDATA   = rdata;
    assign RRESP   = 2'b00;  // OKAY
    assign RVALID  = (rstate == RDDATA);
    assign ar_hs   = ARVALID & ARREADY;
    assign raddr   = ARADDR[ADDR_BITS-1:0];

    // rstate
    always @(posedge ACLK) begin
        if (ARESET)
            rstate <= RDRESET;
        else if (ACLK_EN)
            rstate <= rnext;
    end

    // rnext
    always @(*) begin
        case (rstate)
            RDIDLE:
                if (ARVALID)
                    rnext = RDDATA;
                else
                    rnext = RDIDLE;
            RDDATA:
                if (RREADY & RVALID)
                    rnext = RDIDLE;
                else
                    rnext = RDDATA;
            default:
                rnext = RDIDLE;
        endcase
    end

    // rdata
    always @(posedge ACLK) begin
        if (ACLK_EN) begin
            if (ar_hs) begin
                rdata <= 32'b0;
                case (raddr)
                    ADDR_AP_CTRL: begin
                        rdata[0] <= int_ap_start;
                        rdata[1] <= int_ap_done;
                        rdata[2] <= int_ap_idle;
                        rdata[3] <= int_ap_ready;
                        rdata[7] <= int_auto_restart;
                    end
                    ADDR_GIE: begin
                        rdata[0] <= int_gie;
                    end
                    ADDR_IER: begin
                        rdata[1:0] <= int_ier;
                    end
                    ADDR_ISR: begin
                        rdata[1:0] <= int_isr;
                    end
                    ADDR_SCALEDAINDEX_DATA_0: begin
                        rdata[15:0] <= int_scaledaindex[15:0];
                    end
                    ADDR_SCALEDBINDEX_DATA_0: begin
                        rdata[15:0] <= int_scaledbindex[15:0];
                    end
                    ADDR_OFFSETINDEX_DATA_0: begin
                        rdata[15:0] <= int_offsetindex[15:0];
                    end
                    ADDR_AXI00_PTR0_DATA_0: rdata <= int_axi00_ptr0[31:0];
                    ADDR_AXI00_PTR0_DATA_1: rdata <= int_axi00_ptr0[63:32];
                    ADDR_AXI01_PTR0_DATA_0: rdata <= int_axi01_ptr0[31:0];
                    ADDR_AXI01_PTR0_DATA_1: rdata <= int_axi01_ptr0[63:32];
                    ADDR_AXI02_PTR0_DATA_0: rdata <= int_axi02_ptr0[31:0];
                    ADDR_AXI02_PTR0_DATA_1: rdata <= int_axi02_ptr0[63:32];
                    ADDR_AXI03_PTR0_DATA_0: rdata <= int_axi03_ptr0[31:0];
                    ADDR_AXI03_PTR0_DATA_1: rdata <= int_axi03_ptr0[63:32];
                    ADDR_AXI04_PTR0_DATA_0: rdata <= int_axi04_ptr0[31:0];
                    ADDR_AXI04_PTR0_DATA_1: rdata <= int_axi04_ptr0[63:32];
                    ADDR_AXI05_PTR0_DATA_0: rdata <= int_axi05_ptr0[31:0];
                    ADDR_AXI05_PTR0_DATA_1: rdata <= int_axi05_ptr0[63:32];
                    ADDR_AXI06_PTR0_DATA_0: rdata <= int_axi06_ptr0[31:0];
                    ADDR_AXI06_PTR0_DATA_1: rdata <= int_axi06_ptr0[63:32];
                    ADDR_AXI07_PTR0_DATA_0: rdata <= int_axi07_ptr0[31:0];
                    ADDR_AXI07_PTR0_DATA_1: rdata <= int_axi07_ptr0[63:32];
                    ADDR_AXI08_PTR0_DATA_0: rdata <= int_axi08_ptr0[31:0];
                    ADDR_AXI08_PTR0_DATA_1: rdata <= int_axi08_ptr0[63:32];
                    ADDR_AXI09_PTR0_DATA_0: rdata <= int_axi09_ptr0[31:0];
                    ADDR_AXI09_PTR0_DATA_1: rdata <= int_axi09_ptr0[63:32];
                    ADDR_AXI10_PTR0_DATA_0: rdata <= int_axi10_ptr0[31:0];
                    ADDR_AXI10_PTR0_DATA_1: rdata <= int_axi10_ptr0[63:32];
                    ADDR_AXI11_PTR0_DATA_0: rdata <= int_axi11_ptr0[31:0];
                    ADDR_AXI11_PTR0_DATA_1: rdata <= int_axi11_ptr0[63:32];
                    ADDR_AXI12_PTR0_DATA_0: rdata <= int_axi12_ptr0[31:0];
                    ADDR_AXI12_PTR0_DATA_1: rdata <= int_axi12_ptr0[63:32];
                    ADDR_AXI13_PTR0_DATA_0: rdata <= int_axi13_ptr0[31:0];
                    ADDR_AXI13_PTR0_DATA_1: rdata <= int_axi13_ptr0[63:32];
                    ADDR_AXI14_PTR0_DATA_0: rdata <= int_axi14_ptr0[31:0];
                    ADDR_AXI14_PTR0_DATA_1: rdata <= int_axi14_ptr0[63:32];
                    ADDR_AXI15_PTR0_DATA_0: rdata <= int_axi15_ptr0[31:0];
                    ADDR_AXI15_PTR0_DATA_1: rdata <= int_axi15_ptr0[63:32];
                    ADDR_AXI16_PTR0_DATA_0: rdata <= int_axi16_ptr0[31:0];
                    ADDR_AXI16_PTR0_DATA_1: rdata <= int_axi16_ptr0[63:32];
                    ADDR_AXI17_PTR0_DATA_0: rdata <= int_axi17_ptr0[31:0];
                    ADDR_AXI17_PTR0_DATA_1: rdata <= int_axi17_ptr0[63:32];
                    ADDR_AXI18_PTR0_DATA_0: rdata <= int_axi18_ptr0[31:0];
                    ADDR_AXI18_PTR0_DATA_1: rdata <= int_axi18_ptr0[63:32];
                    ADDR_AXI19_PTR0_DATA_0: rdata <= int_axi19_ptr0[31:0];
                    ADDR_AXI19_PTR0_DATA_1: rdata <= int_axi19_ptr0[63:32];
                    ADDR_AXI20_PTR0_DATA_0: rdata <= int_axi20_ptr0[31:0];
                    ADDR_AXI20_PTR0_DATA_1: rdata <= int_axi20_ptr0[63:32];
                    ADDR_DBG_REG0: rdata <= dbg_reg0;
                    ADDR_DBG_REG1: rdata <= dbg_reg1;
                    ADDR_DBG_REG2: rdata <= dbg_reg2;
                    default: rdata <= 32'b0;
                endcase
            end
        end
    end

    //------------------------Register logic-----------------

    //--- interrupt ---
    assign interrupt = int_gie & (|int_isr);

    //--- ap_start ---
    assign ap_start = int_ap_start;

    always @(posedge ACLK) begin
        if (ARESET)
            int_ap_start <= 1'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0] && WDATA[0])
                int_ap_start <= 1'b1;
            else if (ap_ready)
                int_ap_start <= int_auto_restart; // clear or restart
        end
    end

    //--- ap_done ---
    always @(posedge ACLK) begin
        if (ARESET)
            int_ap_done <= 1'b0;
        else if (ACLK_EN) begin
            if (ap_done)
                int_ap_done <= 1'b1;
            else if (ar_hs && raddr == ADDR_AP_CTRL)
                int_ap_done <= 1'b0; // clear on read
        end
    end

    //--- ap_idle ---
    always @(posedge ACLK) begin
        if (ARESET)
            int_ap_idle <= 1'b0;
        else if (ACLK_EN)
            int_ap_idle <= ap_idle;
    end

    //--- ap_ready ---
    always @(posedge ACLK) begin
        if (ARESET)
            int_ap_ready <= 1'b0;
        else if (ACLK_EN) begin
            if (ap_ready)
                int_ap_ready <= 1'b1;
            else if (ar_hs && raddr == ADDR_AP_CTRL)
                int_ap_ready <= 1'b0; // clear on read
        end
    end

    //--- auto_restart ---
    always @(posedge ACLK) begin
        if (ARESET)
            int_auto_restart <= 1'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AP_CTRL && WSTRB[0])
                int_auto_restart <= WDATA[7];
        end
    end

    //--- gie ---
    always @(posedge ACLK) begin
        if (ARESET)
            int_gie <= 1'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_GIE && WSTRB[0])
                int_gie <= WDATA[0];
        end
    end

    //--- ier ---
    always @(posedge ACLK) begin
        if (ARESET)
            int_ier <= 2'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_IER && WSTRB[0])
                int_ier <= WDATA[1:0];
        end
    end

    //--- isr ---
    always @(posedge ACLK) begin
        if (ARESET)
            int_isr <= 2'b0;
        else if (ACLK_EN) begin
            if (int_ier[0] & ap_done)
                int_isr[0] <= 1'b1;
            if (int_ier[1] & ap_ready)
                int_isr[1] <= 1'b1;
            if (w_hs && waddr == ADDR_ISR && WSTRB[0])
                int_isr <= int_isr & ~WDATA[1:0]; // toggle on write (clear on write 1)
        end
    end

    //--- scaledaindex ---
    assign scaledaindex = int_scaledaindex;

    always @(posedge ACLK) begin
        if (ARESET)
            int_scaledaindex <= 16'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_SCALEDAINDEX_DATA_0)
                int_scaledaindex[15:0] <= (WDATA[15:0] & wmask[15:0]) | (int_scaledaindex[15:0] & ~wmask[15:0]);
        end
    end

    //--- scaledbindex ---
    assign scaledbindex = int_scaledbindex;

    always @(posedge ACLK) begin
        if (ARESET)
            int_scaledbindex <= 16'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_SCALEDBINDEX_DATA_0)
                int_scaledbindex[15:0] <= (WDATA[15:0] & wmask[15:0]) | (int_scaledbindex[15:0] & ~wmask[15:0]);
        end
    end

    //--- offsetindex ---
    assign offsetindex = int_offsetindex;

    always @(posedge ACLK) begin
        if (ARESET)
            int_offsetindex <= 16'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_OFFSETINDEX_DATA_0)
                int_offsetindex[15:0] <= (WDATA[15:0] & wmask[15:0]) | (int_offsetindex[15:0] & ~wmask[15:0]);
        end
    end

    //--- axi00_ptr0 ---
    assign axi00_ptr0 = int_axi00_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi00_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI00_PTR0_DATA_0)
                int_axi00_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi00_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI00_PTR0_DATA_1)
                int_axi00_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi00_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi01_ptr0 ---
    assign axi01_ptr0 = int_axi01_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi01_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI01_PTR0_DATA_0)
                int_axi01_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi01_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI01_PTR0_DATA_1)
                int_axi01_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi01_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi02_ptr0 ---
    assign axi02_ptr0 = int_axi02_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi02_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI02_PTR0_DATA_0)
                int_axi02_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi02_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI02_PTR0_DATA_1)
                int_axi02_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi02_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi03_ptr0 ---
    assign axi03_ptr0 = int_axi03_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi03_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI03_PTR0_DATA_0)
                int_axi03_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi03_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI03_PTR0_DATA_1)
                int_axi03_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi03_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi04_ptr0 ---
    assign axi04_ptr0 = int_axi04_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi04_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI04_PTR0_DATA_0)
                int_axi04_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi04_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI04_PTR0_DATA_1)
                int_axi04_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi04_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi05_ptr0 ---
    assign axi05_ptr0 = int_axi05_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi05_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI05_PTR0_DATA_0)
                int_axi05_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi05_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI05_PTR0_DATA_1)
                int_axi05_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi05_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi06_ptr0 ---
    assign axi06_ptr0 = int_axi06_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi06_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI06_PTR0_DATA_0)
                int_axi06_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi06_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI06_PTR0_DATA_1)
                int_axi06_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi06_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi07_ptr0 ---
    assign axi07_ptr0 = int_axi07_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi07_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI07_PTR0_DATA_0)
                int_axi07_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi07_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI07_PTR0_DATA_1)
                int_axi07_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi07_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi08_ptr0 ---
    assign axi08_ptr0 = int_axi08_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi08_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI08_PTR0_DATA_0)
                int_axi08_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi08_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI08_PTR0_DATA_1)
                int_axi08_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi08_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi09_ptr0 ---
    assign axi09_ptr0 = int_axi09_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi09_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI09_PTR0_DATA_0)
                int_axi09_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi09_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI09_PTR0_DATA_1)
                int_axi09_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi09_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi10_ptr0 ---
    assign axi10_ptr0 = int_axi10_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi10_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI10_PTR0_DATA_0)
                int_axi10_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi10_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI10_PTR0_DATA_1)
                int_axi10_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi10_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi11_ptr0 ---
    assign axi11_ptr0 = int_axi11_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi11_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI11_PTR0_DATA_0)
                int_axi11_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi11_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI11_PTR0_DATA_1)
                int_axi11_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi11_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi12_ptr0 ---
    assign axi12_ptr0 = int_axi12_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi12_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI12_PTR0_DATA_0)
                int_axi12_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi12_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI12_PTR0_DATA_1)
                int_axi12_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi12_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi13_ptr0 ---
    assign axi13_ptr0 = int_axi13_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi13_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI13_PTR0_DATA_0)
                int_axi13_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi13_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI13_PTR0_DATA_1)
                int_axi13_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi13_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi14_ptr0 ---
    assign axi14_ptr0 = int_axi14_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi14_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI14_PTR0_DATA_0)
                int_axi14_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi14_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI14_PTR0_DATA_1)
                int_axi14_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi14_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi15_ptr0 ---
    assign axi15_ptr0 = int_axi15_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi15_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI15_PTR0_DATA_0)
                int_axi15_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi15_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI15_PTR0_DATA_1)
                int_axi15_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi15_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi16_ptr0 ---
    assign axi16_ptr0 = int_axi16_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi16_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI16_PTR0_DATA_0)
                int_axi16_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi16_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI16_PTR0_DATA_1)
                int_axi16_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi16_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi17_ptr0 ---
    assign axi17_ptr0 = int_axi17_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi17_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI17_PTR0_DATA_0)
                int_axi17_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi17_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI17_PTR0_DATA_1)
                int_axi17_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi17_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi18_ptr0 ---
    assign axi18_ptr0 = int_axi18_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi18_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI18_PTR0_DATA_0)
                int_axi18_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi18_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI18_PTR0_DATA_1)
                int_axi18_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi18_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi19_ptr0 ---
    assign axi19_ptr0 = int_axi19_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi19_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI19_PTR0_DATA_0)
                int_axi19_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi19_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI19_PTR0_DATA_1)
                int_axi19_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi19_ptr0[63:32] & ~wmask[31:0]);
        end
    end

    //--- axi20_ptr0 ---
    assign axi20_ptr0 = int_axi20_ptr0;

    always @(posedge ACLK) begin
        if (ARESET)
            int_axi20_ptr0 <= 64'b0;
        else if (ACLK_EN) begin
            if (w_hs && waddr == ADDR_AXI20_PTR0_DATA_0)
                int_axi20_ptr0[31:0] <= (WDATA[31:0] & wmask[31:0]) | (int_axi20_ptr0[31:0] & ~wmask[31:0]);
            if (w_hs && waddr == ADDR_AXI20_PTR0_DATA_1)
                int_axi20_ptr0[63:32] <= (WDATA[31:0] & wmask[31:0]) | (int_axi20_ptr0[63:32] & ~wmask[31:0]);
        end
    end

endmodule
