`timescale 1ns/1ps
module axis_fifo_tb;
    reg clk=0, rst_n=0;
    always #2 clk=~clk;
    reg [31:0] s_tdata=0;
    reg [3:0] s_tkeep=0;
    reg s_tlast=0,s_tvalid=0,m_tready=0;
    wire s_tready,m_tvalid,m_tlast;
    wire [31:0] m_tdata;
    wire [3:0] m_tkeep;
    hoge_axis_fifo #(.WIDTH(32),.DEPTH(16)) dut(.*);
    integer sent=0,received=0,cycle=0,blocked=0;
    reg stalled=0;
    reg [36:0] held;
    initial begin
        repeat(20) @(negedge clk);
        rst_n=1;
        while(received<200) begin
            @(negedge clk);
            s_tvalid=sent<200;
            s_tdata=sent;
            s_tkeep=sent%16;
            s_tlast=sent%7==0;
            m_tready=cycle>50 && cycle%5!=0 && cycle%7!=0;
            @(posedge clk);
            if(stalled && (!m_tvalid || {m_tlast,m_tkeep,m_tdata}!==held))
                $fatal(1,"Output changed under backpressure");
            stalled=m_tvalid && !m_tready;
            held={m_tlast,m_tkeep,m_tdata};
            if(s_tvalid && !s_tready) blocked=blocked+1;
            if(s_tvalid && s_tready) sent=sent+1;
            if(m_tvalid && m_tready) begin
                if(m_tdata!==received || m_tkeep!==(received%16) || m_tlast!==(received%7==0))
                    $fatal(1,"FIFO payload/sideband/order mismatch at %d",received);
                received=received+1;
            end
            cycle=cycle+1;
            if(cycle>2000) $fatal(1,"FIFO timeout");
        end
        if(blocked==0) $fatal(1,"Full FIFO not exercised");
        @(negedge clk); rst_n=0; s_tvalid=0;
        repeat(20) @(negedge clk);
        rst_n=1;
        repeat(20) @(negedge clk);
        if(m_tvalid) $fatal(1,"Reset left stale data");
        $display("F2 AXIS FIFO test passed: %d words, %d blocked cycles",received,blocked);
        $finish;
    end
endmodule
