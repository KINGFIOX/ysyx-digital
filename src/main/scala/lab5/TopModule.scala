package lab5

import chisel3._

class TopModule extends Module {
  val io = IO(new Bundle {
    val keycode = Input(UInt(8.W))
    val ascii = Output(UInt(7.W))
  })

  io.ascii := KeycodeToAscii(io.keycode)
}
