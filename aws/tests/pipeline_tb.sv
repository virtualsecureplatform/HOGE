`timescale 1ns/1ps
module pipeline_case #(parameter WIDTH=577, STAGES=3) (output reg passed=0);
    reg clk=0, rst_n=0, s_valid=0, m_ready=0;
    always #2 clk=~clk;
    reg [WIDTH-1:0] s_data=0;
    wire [WIDTH-1:0] m_data;
    wire s_ready,m_valid;
    hoge_stream_pipeline #(.WIDTH(WIDTH),.STAGES(STAGES)) dut(.*);
    integer sent,received,cycle,last_cycle,blocked;
    reg held_valid, source_held;
    reg [WIDTH-1:0] held;
    function automatic [WIDTH-1:0] payload(input integer n);
        integer b;
        begin
            for(b=0;b<WIDTH;b=b+1) payload[b]=((n*37+b*13)>>(b%13))&1;
            payload[31:0]=n;
        end
    endfunction
    task automatic run(input integer base,input bit stalls);
        begin
            sent=0;received=0;cycle=0;last_cycle=-1;blocked=0;held_valid=0;source_held=0;
            while(received<400) begin
                @(negedge clk);
                s_valid=sent<400 && (!stalls || source_held || cycle%5!=0);
                s_data=s_valid ? payload(base+sent) : ~payload(base+cycle);
                m_ready=!stalls || (cycle>35 && cycle%7!=0 && cycle%11!=0);
                @(posedge clk);
                if(held_valid && (!m_valid || m_data!==held)) $fatal(1,"unstable stalled payload");
                held_valid=m_valid && !m_ready;held=m_data;
                source_held=s_valid && !s_ready;
                if(s_valid && s_ready) sent=sent+1;
                if(s_valid && !s_ready) blocked=blocked+1;
                if(m_valid && m_ready) begin
                    if(m_data!==payload(base+received)) $fatal(1,"payload/order mismatch WIDTH=%d beat=%d",WIDTH,received);
                    if(!stalls && received>0 && cycle!=last_cycle+1) $fatal(1,"throughput bubble");
                    last_cycle=cycle;received=received+1;
                end
                cycle=cycle+1;
                if(cycle>2000) $fatal(1,"pipeline timeout");
            end
            if(stalls && blocked==0) $fatal(1,"backpressure not exercised");
            @(negedge clk);s_valid=0;
        end
    endtask
    initial begin
        repeat(5) @(negedge clk);rst_n=1;
        run(0,0);run(1000,1);
        // Reset while every stage contains old traffic, then start a new epoch.
        @(negedge clk);m_ready=0;s_valid=1;s_data='1;
        repeat(20) @(negedge clk);
        rst_n=0;s_valid=0;
        #1;if(m_valid || s_ready) $fatal(1,"reset did not suppress handshakes");
        repeat(5) @(negedge clk);rst_n=1;
        run(5000,1);
        passed=1;
    end
endmodule

module pipeline_tb;
    wire [4:0] passed;
    pipeline_case #(.WIDTH(577),.STAGES(3)) data_test(passed[0]);
    pipeline_case #(.WIDTH(104),.STAGES(3)) cmd_test(passed[1]);
    pipeline_case #(.WIDTH(37),.STAGES(1)) single_test(passed[2]);
    pipeline_case #(.WIDTH(32),.STAGES(3)) control_test(passed[3]);
    pipeline_case #(.WIDTH(577),.STAGES(5)) pcis_test(passed[4]);
    initial begin
        wait(&passed);$display("HOGE_PIPELINE_PASS: payload, sidebands, stalls, throughput, reset");$finish;
    end
    initial begin #100000;$fatal(1,"watchdog");end
endmodule
