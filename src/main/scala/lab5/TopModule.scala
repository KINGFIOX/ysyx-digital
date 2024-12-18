package lab5

import chisel3._
import lab2.Decode37

class TopModule extends Module {
  val io = IO(new Bundle {
    val keycode0 = Output(UInt(7.W))
    val keycode1 = Output(UInt(7.W))
    val ascii0 = Output(UInt(7.W))
    val ascii1 = Output(UInt(7.W))
    val count0 = Output(UInt(7.W))
    val count1 = Output(UInt(7.W))
    val overflow = Output(Bool())
    val clr_n = Input(Bool())
    val ps2_clk = Input(Bool())
    val ps2_data = Input(Bool())
  })
  val ps2 = Module(new PS2Keyboard)
  val keyboard = Module(new Keyboard)
  ps2.io.clk := this.clock
  ps2.io.clr_n := io.clr_n
  ps2.io.ps2_clk := io.ps2_clk
  ps2.io.ps2_data := io.ps2_data
  keyboard.io.data := ps2.io.data
  keyboard.io.valid := ps2.io.ready
  ps2.io.nextdata_n := ~keyboard.io.nextdata
  io.overflow := ps2.io.overflow

  val decode_ascii0 = Module(new Decode37(4))
  decode_ascii0.io.x := keyboard.io.keycode(3, 0)
  decode_ascii0.io.en := keyboard.io.ready
  io.ascii0 := decode_ascii0.io.y

  val decode_ascii1 = Module(new Decode37(4))
  decode_ascii1.io.x := keyboard.io.keycode(7, 4)
  decode_ascii1.io.en := keyboard.io.ready
  io.ascii1 := decode_ascii1.io.y

  val decode_keycode0 = Module(new Decode37(4))
  decode_keycode0.io.x := keyboard.io.keycode(3, 0)
  decode_keycode0.io.en := keyboard.io.ready
  io.keycode0 := decode_keycode0.io.y

  val decode_keycode1 = Module(new Decode37(4))
  decode_keycode1.io.x := keyboard.io.keycode(7, 4)
  decode_keycode1.io.en := keyboard.io.ready
  io.keycode1 := decode_keycode1.io.y

  val decode_count0 = Module(new Decode37(8))
  decode_count0.io.x := keyboard.io.count(3, 0)
  decode_count0.io.en := true.B
  io.count0 := decode_count0.io.y

  val decode_count1 = Module(new Decode37(8))
  decode_count1.io.x := keyboard.io.count(7, 4)
  decode_count1.io.en := true.B
  io.count1 := decode_count1.io.y
}

import _root_.circt.stage.ChiselStage

object TopModule extends App {
  ChiselStage.emitSystemVerilogFile(
    new TopModule,
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
