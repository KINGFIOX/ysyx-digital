module DpiInvalidInst(
        input        en,
        input  [31:0] pc,
        input  [31:0] inst
    );

    import "DPI-C" function void invalid_inst_dpi(input int en, input int pc, input int inst);
    always @(*) begin
        invalid_inst_dpi(en, pc, inst);
    end

endmodule
