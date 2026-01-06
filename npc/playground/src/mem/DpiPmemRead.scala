package mem

import chisel3._
import chisel3.util._

class DpiPmemRead extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val en   = Input(Bool())
    val addr = Input(UInt(32.W))
    val len  = Input(UInt(32.W))
    val data = Output(UInt(32.W))
  })

  setInline("DpiPmemRead.sv",
    """module DpiPmemRead(
      |  input        en,
      |  input  [31:0] addr,
      |  input  [31:0] len,
      |  output [31:0] data
      |);
      |  import "DPI-C" function int pmem_read_dpi(input int en, input int addr, input int len);
      |  assign data = pmem_read_dpi(en, addr, len);
      |endmodule
      |""".stripMargin)
}
