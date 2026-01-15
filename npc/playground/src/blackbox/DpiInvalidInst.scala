package blackbox

import chisel3._

class DpiInvalidInst extends ExtModule {
  val io = FlatIO(new Bundle {
    val en   = Input(Bool())
    val pc   = Input(UInt(32.W))
    val inst = Input(UInt(32.W))
  })

  setInline(
    "DpiInvalidInst.sv",
    """module DpiInvalidInst(
      |  input        en,
      |  input  [31:0] pc,
      |  input  [31:0] inst
      |);
      |  import "DPI-C" function void invalid_inst_dpi(input int en, input int pc, input int inst);
      |  always @(*) begin
      |    invalid_inst_dpi(en, pc, inst);
      |  end
      |endmodule
      |""".stripMargin
  )
}

