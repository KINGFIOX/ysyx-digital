module alu(
    input  signed [3:0] A, /* signed */
    input  signed [3:0] B, /* signed */
    input         [2:0] OP,
    output reg           OF,
    output reg           ZF,
    output reg signed [3:0] Y /* signed */
);

    always @(*) begin
        // 默认值，避免锁存
        Y  = 0;
        OF = 0;
        ZF = 1;

        case (OP)
            3'b000: begin // 加
                Y   = A + B;
                OF  = (A[3] == B[3]) && (Y[3] != A[3]);
                ZF  = (Y == 0);
            end
            3'b001: begin // 减
                Y   = A - B;
                OF  = (A[3] != B[3]) && (Y[3] != A[3]);
                ZF  = (Y == 0);
            end
            3'b010: begin // 取反
                Y  = ~A;
                OF = 0;
                ZF = (Y == 0);
            end
            3'b011: begin // 与
                Y  = A & B;
                OF = 0;
                ZF = (Y == 0);
            end
            3'b100: begin // 或
                Y  = A | B;
                OF = 0;
                ZF = (Y == 0);
            end
            3'b101: begin // 异或
                Y  = A ^ B;
                OF = 0;
                ZF = (Y == 0);
            end
            3'b110: begin // 小于 (有符号)
                Y  = (A < B) ? 1 : 0;
                OF = 0;
                ZF = (Y == 0);
            end
            3'b111: begin // 相等
                Y  = (A == B) ? 1 : 0;
                OF = 0;
                ZF = (Y == 0);
            end
            default: begin
                Y  = 0;
                OF = 0;
                ZF = 1;
            end
        endcase
    end

endmodule
