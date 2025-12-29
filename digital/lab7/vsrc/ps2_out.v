module ps2_out(
        input clk,
        input rst,
        input ps2_clk,
        input ps2_data,
        output reg [7:0] keycode,
        output wire [7:0] ascii,
        output reg [7:0] count,
        output reg display_en
    );

// 两个动作:
//   1. 按下
//   2. 松开

    wire ready;
    wire overflow;

    wire [7:0] scan_code;

    keycode2ascii keycode2ascii_inst(
                      .keycode(keycode),
                      .ascii(ascii)
                  );

    ps2_keyboard keyboard_inst(
                     .clk(clk),
                     .rst(rst),
                     .ps2_clk(ps2_clk),
                     .ps2_data(ps2_data),
                     .nextdata(1'b1),
                     .data(scan_code),
                     .ready(ready),
                     .overflow(overflow)
                 );

    localparam sIDLE = 0, sHold = 1, sRel = 2;
    reg [1:0] cur_state, next_state;

    // 激励
    always @(posedge clk) begin
        if (rst) begin
            cur_state <= sIDLE;
        end
        else begin
            cur_state <= next_state;
        end
    end

    // 状态迁移表
    always @(*) begin
        next_state = cur_state;
        case (cur_state)
            sIDLE: begin
                if (ready) begin
                    if (scan_code != 8'hF0) // 按下
                        next_state = sHold;
                end
            end
            sHold: begin
                if (ready) begin
                    if (scan_code == 8'hF0) // 松开
                        next_state = sRel;
                end
            end
            sRel: begin
                if (ready) begin
                    next_state = sIDLE;
                end
            end
        endcase
    end

    always @(posedge clk) begin
        if (rst) begin
            keycode <= 8'h00;
            display_en <= 1'b0;
            count <= 8'h00;
        end
        else begin
            if (ready) begin
                case (cur_state)
                    sIDLE: begin
                        if (scan_code != 8'hF0) begin // 按下
                            keycode <= scan_code;
                            display_en <= 1'b1; // 按下, 显示
                        end
                    end
                    sHold: begin
                        if (scan_code != 8'hF0) begin
                            keycode <= scan_code;
                        end
                    end
                    sRel: begin
                        if (!overflow) begin
                            count <= count + 1;
                        end
                        display_en <= 1'b0;
                        keycode <= 8'h00;
                    end
                endcase
            end
        end
    end

endmodule
