`timescale 1ns/1ps
module reset_skew_tb;
  reg clk=0, rst_n=0, s_valid=0, m_ready=1;
  reg [31:0] s_data=0;
  wire [31:0] m_data;
  wire s_ready,m_valid;
  integer sent=0,received=0,blocked=0;
  always #2 clk=~clk;
  hoge_stream_pipeline #(.WIDTH(32),.STAGES(3)) dut(.*);
  initial begin
    force dut.STAGE[2].stage_rst_n=0;
    repeat(5) @(negedge clk);
    rst_n=1;
    repeat(35) @(negedge clk);
    if(received!=0 || sent==0 || sent>4 || blocked==0) $fatal(1,"Downstream reset did not backpressure upstream safely");
    release dut.STAGE[2].stage_rst_n;
  end
  initial begin
    wait(rst_n);
    while(received<100) begin
      @(negedge clk);s_valid=sent<100;s_data=sent;
      @(posedge clk);
      if(s_valid && s_ready) sent++;
      if(s_valid && !s_ready) blocked++;
      if(m_valid && m_ready) begin
        if(m_data!==received) $fatal(1,"Lost data across staggered reset release");
        received++;
      end
    end
    $display("HOGE_RESET_SKEW_PASS: downstream reset holds upstream traffic until release");
    $finish;
  end
  initial begin #10000;$fatal(1,"reset skew timeout");end
endmodule
