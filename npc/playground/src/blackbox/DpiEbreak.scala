package blackbox

import chisel3._
import chisel3.util._

class DpiEbreak extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val en  = Input(Bool())
    val pc  = Input(UInt(32.W))
    val a0  = Input(UInt(32.W)) // $a0 寄存器作为返回值
  })

  setInline("DpiEbreak.sv",
    """module DpiEbreak(
      |  input        en,
      |  input  [31:0] pc,
      |  input  [31:0] a0
      |);
      |  import "DPI-C" function void ebreak_dpi(input int en, input int pc, input int a0);
      |  always @(*) begin
      |    ebreak_dpi(en, pc, a0);
      |  end
      |endmodule
      |""".stripMargin)
}

