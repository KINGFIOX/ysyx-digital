module DpiPmemRead(
        input        en,
        input  [31:0] addr,
        input  [31:0] len,
        output reg [31:0] data
    );

    import "DPI-C" function int pmem_read_dpi(input int en, input int addr, input int len);
    always @(*) begin
        if (en) begin
            data <= pmem_read_dpi(1, addr, len);
        end
    end

endmodule
