module bcd7seg(
        input        en,        // 使能，高电平显示
        input  [3:0] b,         // BCD 输入
        output reg [6:0] h      // a b c d e f g，低电平点亮
    );

    always @(*) begin
        if (en) begin
            case (b)
                4'h0: h = 7'b0000001;
                4'h1: h = 7'b1001111;
                4'h2: h = 7'b0010010;
                4'h3: h = 7'b0000110;
                4'h4: h = 7'b1001100;
                4'h5: h = 7'b0100100;
                4'h6: h = 7'b0100000;
                4'h7: h = 7'b0001111;
                4'h8: h = 7'b0000000;
                4'h9: h = 7'b0000100;
                4'ha: h = 7'b0001000;
                4'hb: h = 7'b1100000;
                4'hc: h = 7'b0110001;
                4'hd: h = 7'b1000010;
                4'he: h = 7'b0110000;
                4'hf: h = 7'b0111000;
                default: h = 7'b1111111;
            endcase
        end
        else begin
            h = 7'b1111111;
        end
    end

endmodule
