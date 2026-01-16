module PmemReadDpiWrapper(
        input [31:0] addr_i,
        input [31:0] len_i,
        output wire [31:0] data_o
    );
    import "DPI-C" function int pmem_read_dpi(input int addr, input int len);
    assign data_o = pmem_read_dpi(addr_i, len_i);
endmodule
