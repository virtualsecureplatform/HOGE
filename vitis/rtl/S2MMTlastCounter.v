// S2MMTlastCounter: generates TLAST for S2MM DRE flush
// Counts 1025 beats (N+1 = 1024+1) per TLWE ciphertext
module S2MMTlastCounter(
  input         clock,
  input         reset,
  input         io_subordinate_TVALID,
  output        io_subordinate_TREADY,
  input  [31:0] io_subordinate_TDATA,
  output        io_manager_TVALID,
  input         io_manager_TREADY,
  output [31:0] io_manager_TDATA,
  output        io_tlast
);

  reg [10:0] counter;
  wire lastBeat = (counter == 11'd1024);

  // Pass through stream
  assign io_manager_TVALID = io_subordinate_TVALID;
  assign io_subordinate_TREADY = io_manager_TREADY;
  assign io_manager_TDATA = io_subordinate_TDATA;

  // TLAST on last beat
  assign io_tlast = lastBeat;

  always @(posedge clock) begin
    if (reset) begin
      counter <= 11'd0;
    end else if (io_subordinate_TVALID && io_manager_TREADY) begin
      if (lastBeat)
        counter <= 11'd0;
      else
        counter <= counter + 11'd1;
    end
  end

endmodule
