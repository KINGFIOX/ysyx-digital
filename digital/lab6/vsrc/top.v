module top(
    input clk,
    input rst,
    output reg [6:0] seg0,      // a b c d e f g，低电平点亮
    output reg [6:0] seg1      // a b c d e f g，低电平点亮
);

    wire [7:0] q;

    lfsr lfsr_inst(
        .clk(clk),
        .rst(rst),
        .q(q)
    );

    bcd7seg bcd7seg_inst0(
        .en(1'b1),
        .b(q[3:0]),
        .h(seg0)
    );

    bcd7seg bcd7seg_inst1(
        .en(1'b1),
        .b(q[7:4]),
        .h(seg1)
    );

endmodule
