module DpiPmemWrite(
        input        clock,
        input        en,
        input  [31:0] addr,
        input  [31:0] len,
        input  [31:0] data
    );

    import "DPI-C" function void pmem_write_dpi(input int en, input int addr, input int len, input int data);
    always @(posedge clock) begin
        if (en) begin
            pmem_write_dpi(1, addr, len, data);
        end
    end

endmodule
