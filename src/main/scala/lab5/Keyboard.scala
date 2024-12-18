package lab5

import chisel3._
import chisel3.util._

class Keyboard extends Module {
  val io = IO(new Bundle {
    val data = Input(UInt(8.W))
    val valid = Input(Bool())
    val keycode = Output(UInt(8.W))
    val ascii = Output(UInt(7.W))
    val count = Output(UInt(8.W))
    val ready = Output(Bool())
    val nextdata = Output(Bool())
  })

  val counter = Counter(0xff)

  io.keycode := io.data
  io.count := counter.value

  val s_IDLE :: s_HOLD :: s_BREAK :: Nil = Enum(3)
  val state = RegInit(s_IDLE)
  val keycode = Reg(UInt(8.W))
  val ready = RegInit(false.B)
  io.ready := ready
  io.keycode := keycode
  val kc_ascii = Reg(UInt(8.W)) // keycode to ascii
  io.ascii := KeycodeToAscii(kc_ascii)
  io.nextdata := true.B

  when(io.valid) {
    switch(state) {
      is(s_IDLE) {
        when(io.data =/= 0xf0.U) {
          state := s_HOLD
          keycode := io.data
          ready := true.B
          kc_ascii := io.data
        }
        // otherwise, do nothing. receive a break prefix at IDLE state is invalid
      }
      is(s_HOLD) {
        when(io.data === 0xf0.U) {
          state := s_BREAK
          keycode := 0xf0.U
          ready := true.B
          // kc_ascii := (unchanged)
          counter.inc()
        }.otherwise {
          // state := (unchanged)
          // keycode := (unchanged)
          // ready := (unchanged)
          // kc_ascii := (unchanged)
        }
      }
      is(s_BREAK) {
        state := s_IDLE
        ready := false.B
        // keycode := (don't care)
        // kc_ascii := (don't care)
      }
    }

  }

}

import _root_.circt.stage.ChiselStage

object Keyboard extends App {
  ChiselStage.emitSystemVerilogFile(
    new Keyboard,
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
