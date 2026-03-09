module S2MMTlastCounter(
  input         clock,
  input         reset,
  input         io_subordinate_TVALID, // @[src/main/scala/Util.scala 131:20]
  output        io_subordinate_TREADY, // @[src/main/scala/Util.scala 131:20]
  input  [31:0] io_subordinate_TDATA, // @[src/main/scala/Util.scala 131:20]
  output        io_manager_TVALID, // @[src/main/scala/Util.scala 131:20]
  input         io_manager_TREADY, // @[src/main/scala/Util.scala 131:20]
  output [31:0] io_manager_TDATA, // @[src/main/scala/Util.scala 131:20]
  output        io_tlast // @[src/main/scala/Util.scala 131:20]
);
`ifdef RANDOMIZE_REG_INIT
  reg [31:0] _RAND_0;
`endif // RANDOMIZE_REG_INIT
  reg [11:0] counter; // @[src/main/scala/Util.scala 136:30]
  wire  lastBeat = counter == 12'h801; // @[src/main/scala/Util.scala 137:32]
  wire [11:0] _counter_T_1 = counter + 12'h1; // @[src/main/scala/Util.scala 151:44]
  assign io_subordinate_TREADY = io_manager_TREADY; // @[src/main/scala/Util.scala 141:31]
  assign io_manager_TVALID = io_subordinate_TVALID; // @[src/main/scala/Util.scala 140:27]
  assign io_manager_TDATA = io_subordinate_TDATA; // @[src/main/scala/Util.scala 142:26]
  assign io_tlast = counter == 12'h801; // @[src/main/scala/Util.scala 137:32]
  always @(posedge clock) begin
    if (reset) begin // @[src/main/scala/Util.scala 136:30]
      counter <= 12'h0; // @[src/main/scala/Util.scala 136:30]
    end else if (io_subordinate_TVALID & io_manager_TREADY) begin // @[src/main/scala/Util.scala 147:57]
      if (lastBeat) begin // @[src/main/scala/Util.scala 148:31]
        counter <= 12'h0; // @[src/main/scala/Util.scala 149:33]
      end else begin
        counter <= _counter_T_1; // @[src/main/scala/Util.scala 151:33]
      end
    end
  end
// Register and memory initialization
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE_MEM_INIT
  integer initvar;
`endif
`ifndef SYNTHESIS
`ifdef FIRRTL_BEFORE_INITIAL
`FIRRTL_BEFORE_INITIAL
`endif
initial begin
  `ifdef RANDOMIZE
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      `ifdef RANDOMIZE_DELAY
        #`RANDOMIZE_DELAY begin end
      `else
        #0.002 begin end
      `endif
    `endif
`ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  counter = _RAND_0[11:0];
`endif // RANDOMIZE_REG_INIT
  `endif // RANDOMIZE
end // initial
`ifdef FIRRTL_AFTER_INITIAL
`FIRRTL_AFTER_INITIAL
`endif
`endif // SYNTHESIS
endmodule
