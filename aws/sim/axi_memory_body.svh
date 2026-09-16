// Functional shared AXI3 memory, not an HBM timing/training model.
// Included inside a simulation-only copy of the AWS HBM wrapper's port header.
typedef bit [28:0] word_address_t;
logic [255:0] memory[word_address_t];
logic reading[NUM_OF_AXI_PORTS], writing[NUM_OF_AXI_PORTS];
logic [33:0] read_address[NUM_OF_AXI_PORTS], write_address[NUM_OF_AXI_PORTS];
logic [7:0] reads_left[NUM_OF_AXI_PORTS], writes_left[NUM_OF_AXI_PORTS];
logic [2:0] read_size[NUM_OF_AXI_PORTS], write_size[NUM_OF_AXI_PORTS];
logic [5:0] read_id[NUM_OF_AXI_PORTS], write_id[NUM_OF_AXI_PORTS];
integer cycle_count=0, ready_count=0;

task automatic load_buffer(input string filename,input logic [63:0] address);
  integer fd, count;
  logic [511:0] data;
  word_address_t at_word;
  fd=$fopen(filename,"r");
  if(!fd || address[4:0]!=0 || address[63:34]!=0) $fatal(1,"Invalid memory preload");
  at_word=address>>5;
  while(!$feof(fd)) begin
    count=$fscanf(fd,"%h",data);
    if(count==1) begin
      memory[at_word]=data[255:0];
      memory[at_word+1]=data[511:256];
      at_word+=2;
    end else if(!$feof(fd)) $fatal(1,"Malformed memory preload %s",filename);
  end
  $fclose(fd);
endtask

always @(posedge i_clk_250m) begin
  if(!i_rst_250m_n || !i_rst_450m_n) begin ready_count<=0; o_hbm_ready<=0; end
  else if(ready_count<32) ready_count<=ready_count+1;
  else o_hbm_ready<=1;
end
assign hbm_stat_bus.ack=hbm_stat_bus.rd|hbm_stat_bus.wr;
assign hbm_stat_bus.rdata={29'b0,o_hbm_ready,o_hbm_ready,1'b0};
assign o_hbm_apb_paddr_0=0, o_hbm_apb_pprot_0=0, o_hbm_apb_psel_0=0,
       o_hbm_apb_penable_0=0, o_hbm_apb_pwrite_0=0, o_hbm_apb_pwdata_0=0,
       o_hbm_apb_pstrb_0=0, o_hbm_apb_pready_0=0, o_hbm_apb_prdata_0=0,
       o_hbm_apb_pslverr_0=0;
assign o_hbm_apb_paddr_1=0, o_hbm_apb_pprot_1=0, o_hbm_apb_psel_1=0,
       o_hbm_apb_penable_1=0, o_hbm_apb_pwrite_1=0, o_hbm_apb_pwdata_1=0,
       o_hbm_apb_pstrb_1=0, o_hbm_apb_pready_1=0, o_hbm_apb_prdata_1=0,
       o_hbm_apb_pslverr_1=0;

always_comb begin
  for(int p=0;p<NUM_OF_AXI_PORTS;p++) begin
    // Bounded stalls exercise independent channels and response backpressure.
    o_axi_arready[p]=i_rst_450m_n && !reading[p] && !o_axi_rvalid[p] && (cycle_count+p)%11!=0;
    o_axi_awready[p]=i_rst_450m_n && !writing[p] && !o_axi_bvalid[p] && (cycle_count+p)%13!=0;
    o_axi_wready[p]=i_rst_450m_n && writing[p] && (cycle_count+p)%17!=0;
  end
end

always @(posedge i_clk_450m) begin
  if(!i_rst_450m_n) begin
    cycle_count<=0;
    for(int p=0;p<NUM_OF_AXI_PORTS;p++) begin
      reading[p]<=0; writing[p]<=0;
      o_axi_rvalid[p]<=0; o_axi_bvalid[p]<=0;
      o_axi_rdata[p]<=0; o_axi_rid[p]<=0; o_axi_bid[p]<=0;
      o_axi_rlast[p]<=0; o_axi_rresp[p]<=0; o_axi_bresp[p]<=0;
    end
  end else begin
    cycle_count<=cycle_count+1;
    for(int p=0;p<NUM_OF_AXI_PORTS;p++) begin
      if(o_axi_bvalid[p] && i_axi_bready[p]) o_axi_bvalid[p]<=0;
      if(o_axi_rvalid[p] && i_axi_rready[p]) o_axi_rvalid[p]<=0;
      if(i_axi_awvalid[p] && o_axi_awready[p]) begin
        if(i_axi_awburst[p]!=1 || i_axi_awsize[p]>5) $fatal(1,"Unsupported AXI write");
        writing[p]<=1; write_address[p]<=i_axi_awaddr[p];
        writes_left[p]<=i_axi_awlen[p]; write_size[p]<=i_axi_awsize[p]; write_id[p]<=i_axi_awid[p];
      end
      if(i_axi_wvalid[p] && o_axi_wready[p]) begin
        if(i_axi_wlast[p] !== (writes_left[p]==0)) $fatal(1,"AXI WLAST mismatch port %0d",p);
        for(int byte_index=0;byte_index<32;byte_index++)
          if(i_axi_wstrb[p][byte_index]) memory[word_address_t'(write_address[p]>>5)][byte_index*8+:8]=i_axi_wdata[p][byte_index*8+:8];
        if(writes_left[p]==0) begin
          writing[p]<=0; o_axi_bvalid[p]<=1; o_axi_bid[p]<=write_id[p];
        end else begin
          writes_left[p]<=writes_left[p]-1;
          write_address[p]<=write_address[p]+(1<<write_size[p]);
        end
      end
      if(i_axi_arvalid[p] && o_axi_arready[p]) begin
        if(i_axi_arburst[p]!=1 || i_axi_arsize[p]>5) $fatal(1,"Unsupported AXI read");
        reading[p]<=1; read_address[p]<=i_axi_araddr[p];
        reads_left[p]<=i_axi_arlen[p]; read_size[p]<=i_axi_arsize[p]; read_id[p]<=i_axi_arid[p];
      end
      if(reading[p] && (!o_axi_rvalid[p] || i_axi_rready[p]) && (cycle_count+p)%7!=0) begin
        if(!memory.exists(word_address_t'(read_address[p]>>5))) $fatal(1,"Uninitialized AXI read port=%0d address=%h",p,read_address[p]);
        o_axi_rdata[p]<=memory[word_address_t'(read_address[p]>>5)];
        o_axi_rvalid[p]<=1; o_axi_rid[p]<=read_id[p]; o_axi_rlast[p]<=reads_left[p]==0;
        if(reads_left[p]==0) reading[p]<=0;
        else begin reads_left[p]<=reads_left[p]-1; read_address[p]<=read_address[p]+(1<<read_size[p]); end
      end
    end
  end
end
