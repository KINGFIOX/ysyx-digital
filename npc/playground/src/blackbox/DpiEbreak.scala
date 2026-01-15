package blackbox

import chisel3._

class DpiEbreak extends ExtModule {
  val io = FlatIO(new Bundle {
    val en = Input(Bool())
    val pc = Input(UInt(32.W))
    val a0 = Input(UInt(32.W)) // $a0 寄存器作为返回值
  })

  addResource("DpiEbreak.sv")
}
