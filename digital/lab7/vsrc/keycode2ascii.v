module keycode2ascii(
        input [7:0] keycode,
        output [7:0] ascii
    );
    reg [7:0] ascii_r;
    assign ascii = ascii_r;

    always @(*) begin
        case (keycode)
            // 字母（PS/2 set1 make code）
            8'h1C:
                ascii_r = "a";
            8'h32:
                ascii_r = "b";
            8'h21:
                ascii_r = "c";
            8'h23:
                ascii_r = "d";
            8'h24:
                ascii_r = "e";
            8'h2B:
                ascii_r = "f";
            8'h34:
                ascii_r = "g";
            8'h33:
                ascii_r = "h";
            8'h43:
                ascii_r = "i";
            8'h3B:
                ascii_r = "j";
            8'h42:
                ascii_r = "k";
            8'h4B:
                ascii_r = "l";
            8'h3A:
                ascii_r = "m";
            8'h31:
                ascii_r = "n";
            8'h44:
                ascii_r = "o";
            8'h4D:
                ascii_r = "p";
            8'h15:
                ascii_r = "q";
            8'h2D:
                ascii_r = "r";
            8'h1B:
                ascii_r = "s";
            8'h2C:
                ascii_r = "t";
            8'h3C:
                ascii_r = "u";
            8'h2A:
                ascii_r = "v";
            8'h1D:
                ascii_r = "w";
            8'h22:
                ascii_r = "x";
            8'h35:
                ascii_r = "y";
            8'h1A:
                ascii_r = "z";

            // 数字行
            8'h45:
                ascii_r = "0";
            8'h16:
                ascii_r = "1";
            8'h1E:
                ascii_r = "2";
            8'h26:
                ascii_r = "3";
            8'h25:
                ascii_r = "4";
            8'h2E:
                ascii_r = "5";
            8'h36:
                ascii_r = "6";
            8'h3D:
                ascii_r = "7";
            8'h3E:
                ascii_r = "8";
            8'h46:
                ascii_r = "9";

            default:
                ascii_r = 8'h00;
        endcase
    end
endmodule
