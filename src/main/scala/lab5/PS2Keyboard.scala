package lab5

import chisel3._
import chisel3.util._

class PS2Keyboard extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val clr_n = Input(Bool())
    val ps2_clk = Input(Bool())
    val ps2_data = Input(Bool())
    val data = Output(UInt(8.W))
    val ready = Output(Bool())
    val nextdata_n = Input(Bool())
    val overflow = Output(Bool())
  })

  addResource("/ps2_keyboard.v")
}
