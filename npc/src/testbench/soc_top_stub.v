// Auto-generated stub to satisfy Verilator build when real RTL is absent.
// Provides minimal connections for simu_top.v dotted references.

module soc_top #(
    parameter BUS_WIDTH  = 32,
    parameter DATA_WIDTH = 32,
    parameter CPU_WIDTH  = 32
)(
    input                      aclk,
    input                      aresetn,
    input                      enable_delay,
    input  [22:0]              random_seed,
    // ram
    output                     sram_ren,
    output [BUS_WIDTH-1:0]     sram_raddr,
    input  [DATA_WIDTH-1:0]    sram_rdata,
    output [DATA_WIDTH/8-1:0]  sram_wen,
    output [BUS_WIDTH-1:0]     sram_waddr,
    output [DATA_WIDTH-1:0]    sram_wdata,
    // debug
    output [CPU_WIDTH-1:0]     debug0_wb_pc,
    output                     debug0_wb_rf_wen,
    output [4:0]               debug0_wb_rf_wnum,
    output [CPU_WIDTH-1:0]     debug0_wb_rf_wdata,
`ifdef CPU_2CMT
    output [CPU_WIDTH-1:0]     debug1_wb_pc,
    output                     debug1_wb_rf_wen,
    output [4:0]               debug1_wb_rf_wnum,
    output [CPU_WIDTH-1:0]     debug1_wb_rf_wdata,
`endif
    inout                      UART_RX,
    inout                      UART_TX,
    // confreg
    output [15:0]              led,
    output [1:0]               led_rg0,
    output [1:0]               led_rg1,
    output [7:0]               num_csn,
    output [6:0]               num_a_g,
    input  [7:0]               switch,
    output [3:0]               btn_key_col,
    input  [3:0]               btn_key_row,
    input  [1:0]               btn_step
);

  // simple defaults
  assign sram_ren   = 1'b0;
  assign sram_raddr = {BUS_WIDTH{1'b0}};
  assign sram_wen   = {DATA_WIDTH/8{1'b0}};
  assign sram_waddr = {BUS_WIDTH{1'b0}};
  assign sram_wdata = {DATA_WIDTH{1'b0}};

  assign debug0_wb_pc       = {CPU_WIDTH{1'b0}};
  assign debug0_wb_rf_wen   = 1'b0;
  assign debug0_wb_rf_wnum  = 5'd0;
  assign debug0_wb_rf_wdata = {CPU_WIDTH{1'b0}};
`ifdef CPU_2CMT
  assign debug1_wb_pc       = {CPU_WIDTH{1'b0}};
  assign debug1_wb_rf_wen   = 1'b0;
  assign debug1_wb_rf_wnum  = 5'd0;
  assign debug1_wb_rf_wdata = {CPU_WIDTH{1'b0}};
`endif

  assign UART_TX = 1'b1;

  assign led        = 16'h0;
  assign led_rg0    = 2'b0;
  assign led_rg1    = 2'b0;
  assign num_csn    = 8'hff;
  assign num_a_g    = 7'h7f;
  assign btn_key_col= 4'hf;

  // confreg structure expected by simu_top dotted reference
  confreg_stub confreg();

  // APB device hierarchy expected by simu_top dotted reference
  apb_dev_stub APB_DEV();

endmodule

// Minimal confreg stub with required fields
module confreg_stub;
  wire [31:0] num_data          = 32'h0;
  wire        open_trace        = 1'b0;
  wire        num_monitor       = 1'b0;
  wire [7:0]  confreg_uart_data = 8'h0;
  wire        write_uart_valid  = 1'b0;
endmodule

// Minimal APB device/uart stub to satisfy dotted access
module apb_dev_stub;
  uart0_stub uart0();
endmodule

module uart0_stub;
  wire [31:0] PWDATA  = 32'h0;
  wire [31:0] PADDR   = 32'h0;
  wire        PWRITE  = 1'b0;
  wire        PENABLE = 1'b0;
endmodule

