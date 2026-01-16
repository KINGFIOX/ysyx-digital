module PmemReadDpiWrapper(
        input clock,
        input en_i,
        input [31:0] addr_i,
        input [31:0] len_i,
        output wire [31:0] data_o
    );

    reg [31:0] data;
    assign data_o = data;

    import "DPI-C" function int pmem_read_dpi(input int en, input int addr, input int len);
    always @(posedge clock) begin
        if (en_i) begin
            data <= pmem_read_dpi(en_i, addr_i, len_i);
        end
    end
endmodule
