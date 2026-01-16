module PmemWriteDpiWrapper(
        input         clock,
        input         en_i,
        input  [31:0] addr_i,
        input  [3:0]  strb_i,
        input  [31:0] data_i
    );
    import "DPI-C" function void pmem_write_dpi(input int en, input int addr, input int strb, input int data);
    always @(posedge clock) begin
        if (en_i) begin
            pmem_write_dpi(en_i, addr_i, strb_i, data_i);
        end
    end
endmodule
