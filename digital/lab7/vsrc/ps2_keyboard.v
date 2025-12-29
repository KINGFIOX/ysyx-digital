module ps2_keyboard(
        input clk,
        input rst,
        input ps2_clk,
        input ps2_data,
        input nextdata,
        output wire [7:0] data,
        output reg ready,
        output reg overflow
    );

    // internal signal, for test
    reg [9:0] buffer; // ps2_data bits
    reg [7:0] fifo[7:0]; // data fifo
    reg [2:0] w_ptr, r_ptr; // fifo write and read pointers
    reg [3:0] count; // count ps2_data bits

    // detect falling edge of ps2_clk
    reg [2:0] ps2_clk_sync;
    always @(posedge clk) begin
        ps2_clk_sync <= {ps2_clk_sync[1:0], ps2_clk};
    end
    wire sampling = ps2_clk_sync[2] & ~ps2_clk_sync[1];

    always @( posedge clk ) begin
        if ( rst ) begin // reset
            count <= 0;
            w_ptr <= 0;
            r_ptr <= 0;
            overflow <= 0;
            ready<= 0;
        end
        else begin
            if ( ready ) begin // read to output next data
                if( nextdata == 1'b1 ) // read next data
                begin
                    r_ptr <= r_ptr + 3'b1;
                    if( w_ptr == (r_ptr + 1'b1) ) begin // empty
                        ready <= 1'b0;
                    end
                end
            end
            if ( sampling ) begin
                if ( count == 4'd10 ) begin // 表示已经有10bit了, ps_data 是第11bit(正在传)
                    if ((buffer[0] == 0) && // start bit
                            (ps2_data) && // stop bit
                            (^buffer[9:1])) // odd parity
                    begin
                        fifo[w_ptr] <= buffer[8:1]; // kbd scan code
                        w_ptr <= w_ptr + 3'b1;
                        ready <= 1'b1;
                        overflow <= overflow | (r_ptr == (w_ptr + 3'b1));
                    end
                    count <= 0; // for next
                end
                else begin
                    buffer[count] <= ps2_data; // store ps2_data
                    count <= count + 3'b1;
                end
            end
        end
    end
    assign data = fifo[r_ptr]; //always set output data

endmodule
