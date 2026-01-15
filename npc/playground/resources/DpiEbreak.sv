module DpiEbreak(
        input        en,
        input  [31:0] pc,
        input  [31:0] a0
    );

    import "DPI-C" function void ebreak_dpi(input int en, input int pc, input int a0);
    always @(*) begin
        ebreak_dpi(en, pc, a0);
    end

endmodule
