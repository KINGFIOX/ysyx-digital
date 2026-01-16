package blackbox

import chisel3._

class PmemWriteDpiWrapper extends ExtModule {
  val io = FlatIO(new Bundle {
    val addr_i  = Input(UInt(32.W))
    val len_i   = Input(UInt(32.W))
    val data_i  = Input(UInt(32.W))
  })

  addResource("PmemWriteDpiWrapper.sv")
}
