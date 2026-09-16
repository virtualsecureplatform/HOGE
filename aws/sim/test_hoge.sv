`timescale 1ns/1ps
module test_hoge;
  import tb_type_defines_pkg::*;
  string vectors;
  integer manifest, input_file, expected_file, rc, index, bytes;
  integer scalea, scaleb, offset_index, expected_count;
  integer sizes[21];
  logic [63:0] addresses[21], address;
  logic [511:0] value, observed;
  logic [31:0] control, expected[2048];
  logic [31:0] write_words[$];
  logic [511:0] first_word;
  integer mismatches=0;
  bit dma_smoke=0, dma_inject_valid=0;

  initial begin
    dma_smoke=$test$plusargs("dma_smoke");
    #100ms;
    $fatal(1,"HOGE_HDK_FAIL: simulation timeout");
  end

  initial begin
    if(!$value$plusargs("vectors=%s",vectors)) $fatal(1,"Missing +vectors=directory");
    manifest=$fopen({vectors,"/manifest.txt"},"r");
    if(!manifest) $fatal(1,"Cannot read vector manifest");
    rc=$fscanf(manifest,"%d %d %d %d",scalea,scaleb,offset_index,expected_count);
    if(rc!=4 || expected_count<1 || expected_count>2048) $fatal(1,"Invalid vector header");
    for(int i=0;i<21;i++) begin
      rc=$fscanf(manifest,"%d %h %d",index,address,bytes);
      if(rc!=3 || index!=i || bytes<=0) $fatal(1,"Invalid vector buffer %0d",i);
      addresses[i]=address; sizes[i]=bytes;
    end
    $fclose(manifest);
    if(sizes[0]!=8*expected_count) $fatal(1,"Expected exactly two output ciphertexts");
    expected_file=$fopen({vectors,"/expected.hex"},"r");
    if(!expected_file) $fatal(1,"Missing CPU reference");
    for(int i=0;i<expected_count;i++) begin
      rc=$fscanf(expected_file,"%h",expected[i]);
      if(rc!=1) $fatal(1,"Truncated CPU reference");
    end
    $fclose(expected_file);

    tb.power_up(.clk_recipe_a(ClockRecipe::A1));
    wait(tb.card.fpga.CL.hbm_ready===1'b1);
    #1us;
    if($test$plusargs("hbm_smoke")) begin
      // Write every bank before reading any back, so address aliases fail.
      for(int i=0;i<21;i++) begin
        for(int edge_index=0;edge_index<2;edge_index++) begin
          address=addresses[i]+(edge_index ? ((sizes[i]-1)/4096)*4096 : 0);
          for(int j=0;j<16;j++) value[32*j+:32]=32'hcafe0000 ^ (i<<12) ^ (edge_index<<8) ^ j;
          tb.poke(.addr(address),.data(value),.size(DataSize::UINT512));
        end
      end
      for(int i=0;i<21;i++) begin
        for(int edge_index=0;edge_index<2;edge_index++) begin
          address=addresses[i]+(edge_index ? ((sizes[i]-1)/4096)*4096 : 0);
          for(int j=0;j<16;j++) value[32*j+:32]=32'hcafe0000 ^ (i<<12) ^ (edge_index<<8) ^ j;
          tb.peek(.addr(address),.data(observed),.size(DataSize::UINT512));
          if(observed!==value) $fatal(1,"HOGE_HDK_FAIL: HBM smoke bank index=%0d edge=%0d",i,edge_index);
        end
        tb.poke_ocl(.addr('h28+12*i),.data(addresses[i][31:0]));
        tb.poke_ocl(.addr('h2c+12*i),.data(addresses[i][63:32]));
        tb.peek_ocl(.addr('h28+12*i),.data(control));
        if(control!==addresses[i][31:0]) $fatal(1,"HOGE_HDK_FAIL: pointer low word");
        tb.peek_ocl(.addr('h2c+12*i),.data(control));
        if(control!==addresses[i][63:32]) $fatal(1,"HOGE_HDK_FAIL: pointer high word");
        $display("HOGE_HDK: HBM smoke checked bank index %0d",i);
      end
      // A narrow write must preserve the other bytes of the 512-bit line.
      // This address is above 8 GiB, and the BFM delays W relative to AW.
      address=addresses[20]+128;
      value={16{32'h12345678}};
      tb.poke(.addr(address),.data(value),.size(DataSize::UINT512),.id(6'h2d));
      tb.poke(.addr(address+12),.data(32'hfeedcafe),.size(DataSize::UINT32),.id(6'h17));
      value[96+:32]=32'hfeedcafe;
      tb.peek(.addr(address),.data(observed),.size(DataSize::UINT512),.id(6'h35));
      if(observed!==value) $fatal(1,"HOGE_HDK_FAIL: partial write corrupted adjacent bytes");
      // FLR must last long enough to flush the clocked queues and controller.
      tb.issue_flr();
      wait(tb.card.fpga.CL.hbm_ready===1'b0);
      wait(tb.card.fpga.CL.hbm_ready===1'b1);
      #1us;
      tb.peek_ocl(.addr('h28),.data(control));
      if(control!==0) $fatal(1,"HOGE_HDK_FAIL: FLR did not reset OCL pointer");
      tb.poke(.addr(address),.data(value),.size(DataSize::UINT512));
      tb.peek(.addr(address),.data(observed),.size(DataSize::UINT512));
      if(observed!==value) $fatal(1,"HOGE_HDK_FAIL: HBM transfer after FLR");
      if(tb.chk_prot_err_stat()!==1'b0) $fatal(1,"HOGE_HDK_FAIL: HBM smoke protocol error");
      tb.power_down();
      $display("HOGE_HDK_HBM_SMOKE_PASS: 21 banks, OCL, partial write, FLR; NAND not executed");
      $finish;
    end
    $display("HOGE_HDK: HBM ready; loading vectors through PCIS");
    for(int i=0;i<21;i++) begin
`ifdef HOGE_AXI_MEMORY_MODEL
      if(i>=3) tb.card.fpga.CL.HBM.load_buffer($sformatf("%s/arg%0d.hex",vectors,i),addresses[i]);
`endif
      input_file=$fopen($sformatf("%s/arg%0d.hex",vectors,i),"r");
      if(!input_file) $fatal(1,"Missing argument %0d",i);
      for(int pos=0;pos<sizes[i];pos+=4096) begin
        write_words.delete();
        for(int beat=0;beat<64 && pos+64*beat<sizes[i];beat++) begin
          rc=$fscanf(input_file,"%h",value);
          if(rc!=1) $fatal(1,"Truncated argument %0d at %0d",i,pos+64*beat);
          if(beat==0) first_word=value;
          for(int word_index=0;word_index<16;word_index++) write_words.push_back(value[32*word_index+:32]);
        end
`ifdef HOGE_AXI_MEMORY_MODEL
        if(i<3) tb.poke_pcis_wc(.addr(addresses[i]+pos),.data(write_words));
`else
        tb.poke_pcis_wc(.addr(addresses[i]+pos),.data(write_words));
`endif
        if(pos==0 || pos+4096>=sizes[i]) begin
          tb.peek(.addr(addresses[i]+pos),.data(observed),.size(DataSize::UINT512));
          if(observed!==first_word) $fatal(1,"HBM transfer mismatch argument %0d offset %0d",i,pos);
        end
      end
      $fclose(input_file);
      $display("HOGE_HDK: loaded argument %0d (%0d bytes)",i,sizes[i]);
    end
    // Repeated launches exercise AP_DONE clearing and retained key/stream state.
    // The short DMA test drives a known stream through the real F2 slices,
    // DataMover and HBM converter. It does not test NAND arithmetic.
    if(dma_smoke) begin
      force tb.card.fpga.CL.HomGate_1.axi4sout_TVALID=dma_inject_valid;
      force tb.card.fpga.CL.HomGate_1.axi4sout_TDATA=32'b0;
    end
    for(int run_index=0;run_index<(dma_smoke ? 1 : 2);run_index++) begin
      value='1;
      value[31:0]=32'hbad00000 ^ run_index;
      for(int pos=0;pos<sizes[0];pos+=64)
        tb.poke(.addr(addresses[0]+pos),.data(value),.size(DataSize::UINT512));
      tb.peek_ocl(.addr(0),.data(control));
      tb.poke_ocl(.addr('h10),.data(scalea));
      tb.poke_ocl(.addr('h18),.data(scaleb));
      tb.poke_ocl(.addr('h20),.data(offset_index));
      for(int i=0;i<21;i++) begin
        tb.poke_ocl(.addr('h28+12*i),.data(addresses[i][31:0]));
        tb.poke_ocl(.addr('h2c+12*i),.data(addresses[i][63:32]));
      end
      tb.poke_ocl(.addr(0),.data(1));
      $display("HOGE_HDK: launched NAND run %0d",run_index);
      if(dma_smoke) begin
        @(negedge tb.card.fpga.CL.clk);
        dma_inject_valid=1;
        for(int beat=0;beat<sizes[0]/4;beat++) begin
          @(posedge tb.card.fpga.CL.clk);
          while(!tb.card.fpga.CL.HomGate_1.axi4sout_TREADY)
            @(posedge tb.card.fpga.CL.clk);
        end
        @(negedge tb.card.fpga.CL.clk);
        dma_inject_valid=0;
      end
      control=0;
      while(control[1]!==1'b1) begin
        #1us;
        tb.peek_ocl(.addr(0),.data(control));
        if($isunknown(control)) $fatal(1,"Unknown control register");
      end
      for(int pos=0;pos<sizes[0];pos+=64) begin
        tb.peek(.addr(addresses[0]+pos),.data(observed),.size(DataSize::UINT512));
        for(int j=0;j<16 && pos+4*j<sizes[0];j++) begin
          if(observed[32*j+:32]!==(dma_smoke ? 32'b0 : expected[((pos/4)+j)%expected_count])) begin
            if(mismatches<16) $display("Mismatch run=%0d coefficient=%0d got=%h expected=%h",run_index,pos/4+j,observed[32*j+:32],expected[((pos/4)+j)%expected_count]);
            mismatches++;
          end
        end
      end
      $display("HOGE_HDK: checked run %0d, cumulative mismatches=%0d",run_index,mismatches);
    end
    if(mismatches || tb.chk_prot_err_stat()!==1'b0) $fatal(1,"HOGE_HDK_FAIL: data or AXI protocol errors");
    tb.power_down();
    if(dma_smoke) $display("HOGE_HDK_DMA_SMOKE_PASS: output stream, write status, host completion and memory readback; NAND not executed");
    else $display("HOGE_HDK_PASS: two NAND launches match every TFHEpp coefficient");
    $finish;
  end
endmodule
