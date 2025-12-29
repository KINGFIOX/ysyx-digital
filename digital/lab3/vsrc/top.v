module top(
    input  [3:0] A,
    input  [3:0] B,
    input  [2:0] OP,
    output reg OF,
    output reg ZF,
    output reg [6:0] h      // a b c d e f g，低电平点亮
);

    wire signed [3:0] Y;

    alu alu_inst(
        .A(A),
        .B(B),
        .OP(OP),
        .OF(OF),
        .ZF(ZF),
        .Y(Y)
    );

    bcd7seg bcd7seg_inst(
        .en(1'b1),
        .b(Y),
        .h(h)
    );

endmodule
