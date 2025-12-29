module top(
        input clk,
        input rst,
        input ps2_clk,
        input ps2_data,
        output reg [6:0] seg0,
        output reg [6:0] seg1,
        output reg [6:0] seg2,
        output reg [6:0] seg3,
        output reg [6:0] seg4,
        output reg [6:0] seg5
    );

    wire [7:0] keycode;
    wire [7:0] ascii;
    wire [7:0] count;
    wire display_en;

    ps2_out ps2_out_inst(
                .clk(clk),
                .rst(rst),
                .ps2_clk(ps2_clk),
                .ps2_data(ps2_data),
                .keycode(keycode),
                .ascii(ascii),
                .count(count),
                .display_en(display_en)
            );

    bcd7seg bcd7seg_inst0(
                .en(display_en),
                .b(keycode[3:0]),
                .h(seg0)
            );

    bcd7seg bcd7seg_inst1(
                .en(display_en),
                .b(keycode[7:4]),
                .h(seg1)
            );

    bcd7seg bcd7seg_inst2(
                .en(display_en),
                .b(ascii[3:0]),
                .h(seg2)
            );

    bcd7seg bcd7seg_inst3(
                .en(display_en),
                .b(ascii[7:4]),
                .h(seg3)
            );

    bcd7seg bcd7seg_inst4(
                .en(1'b1),
                .b(count[3:0]),
                .h(seg4)
            );

    bcd7seg bcd7seg_inst5(
                .en(1'b1),
                .b(count[7:4]),
                .h(seg5)
            );

endmodule
