package blackbox

import chisel3._

class DpiInvalidInst extends ExtModule {
  val io = FlatIO(new Bundle {
    val en   = Input(Bool())
    val pc   = Input(UInt(32.W))
    val inst = Input(UInt(32.W))
  })

  addResource("DpiInvalidInst.sv")
}

