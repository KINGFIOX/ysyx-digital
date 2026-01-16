package blackbox

import chisel3._

class ExceptionDpiWrapper extends ExtModule {
  val io = FlatIO(new Bundle {
    val en_i = Input(Bool())
    val pc_i = Input(UInt(32.W))
    val mcause_i = Input(UInt(32.W))
    val a0_i = Input(UInt(32.W))
  })

  addResource("ExceptionDpiWrapper.sv")
}
