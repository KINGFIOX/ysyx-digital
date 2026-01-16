module PmemWriteDpiWrapper(
        input  [31:0] addr_i,
        input  [31:0] len_i,
        input  [31:0] data_i
    );
    import "DPI-C" function void pmem_write_dpi(input int en, input int addr, input int len, input int data);
    always @(*) begin
        pmem_write_dpi(1, addr_i, len_i, data_i);
    end
endmodule
