package blackbox

import chisel3._

class PmemReadDpiWrapper extends ExtModule {
  val io = FlatIO(new Bundle {
    val clock = Input(Clock())
    val en_i = Input(Bool())
    val addr_i = Input(UInt(32.W))
    val len_i  = Input(UInt(32.W))
    val data_o = Output(UInt(32.W))
  })

  addResource("PmemReadDpiWrapper.sv")
}
