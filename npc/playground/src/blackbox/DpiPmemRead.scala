package blackbox

import chisel3._

class DpiPmemRead extends ExtModule {
  val io = FlatIO(new Bundle {
    val en   = Input(Bool())
    val addr = Input(UInt(32.W))
    val len  = Input(UInt(32.W))
    val data = Output(UInt(32.W))
  })

  addResource("DpiPmemRead.sv")
}
