module lfsr(
        input clk,
        input rst,
        output reg [7:0] q
    );

    always @(posedge clk) begin
        if (rst) begin q <= 8'h1; end
        else begin
            automatic logic x8 = q[4] ^ q[3] ^ q[2] ^ q[0];
            q <= { x8, q[7:1] };
        end
    end

endmodule
