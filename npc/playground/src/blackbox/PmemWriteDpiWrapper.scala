package blackbox

import chisel3._

class PmemWriteDpiWrapper extends ExtModule {
  val io = FlatIO(new Bundle {
    val clock   = Input(Clock())
    val en_i    = Input(Bool())
    val addr_i  = Input(UInt(32.W))
    val strb_i  = Input(UInt(4.W))
    val data_i  = Input(UInt(32.W))
  })

  addResource("PmemWriteDpiWrapper.sv")
}
