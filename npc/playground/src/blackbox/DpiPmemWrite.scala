package blackbox

import chisel3._
import chisel3.util._

class DpiPmemWrite extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val en   = Input(Bool())
    val addr = Input(UInt(32.W))
    val len  = Input(UInt(32.W))
    val data = Input(UInt(32.W))
  })

  setInline("DpiPmemWrite.sv",
    """module DpiPmemWrite(
      |  input        en,
      |  input  [31:0] addr,
      |  input  [31:0] len,
      |  input  [31:0] data
      |);
      |  import "DPI-C" function void pmem_write_dpi(input int en, input int addr, input int len, input int data);
      |  always @(*) begin
      |    pmem_write_dpi(en, addr, len, data);
      |  end
      |endmodule
      |""".stripMargin)
}
