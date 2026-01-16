module ExceptionDpiWrapper(
        input en_i,
        input [31:0] pc_i,
        input [31:0] mcause_i,
        input [31:0] a0_i
    );

    import "DPI-C" function void exception_dpi(int en, int pc, int mcause, int a0);
    always @(*) begin
        exception_dpi(en_i, pc_i, mcause_i, a0_i);
    end

endmodule
