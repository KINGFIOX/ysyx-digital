package blackbox

import chisel3._

class DpiPmemWrite extends ExtModule {
  val io = FlatIO(new Bundle {
    val clock = Input(Clock())
    val en    = Input(Bool())
    val addr  = Input(UInt(32.W))
    val len   = Input(UInt(32.W))
    val data  = Input(UInt(32.W))
  })

  setInline(
    "DpiPmemWrite.sv",
    """module DpiPmemWrite(
      |  input        clock,
      |  input        en,
      |  input  [31:0] addr,
      |  input  [31:0] len,
      |  input  [31:0] data
      |);
      |  import "DPI-C" function void pmem_write_dpi(input int en, input int addr, input int len, input int data);
      |  always @(posedge clock) begin
      |    if (en) begin
      |      pmem_write_dpi(1, addr, len, data);
      |    end
      |  end
      |endmodule
      |""".stripMargin
  )
}
