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

  addResource("DpiPmemWrite.sv")
}
