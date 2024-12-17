package lab2

import chisel3._
import chisel3.util._

class Decode37 extends Module {
  val io = IO(new Bundle {
    val x = Input(UInt(3.W))
    val y = Output(UInt(7.W))
    val en = Input(Bool())
  })

  private def decode(x: UInt) = {
    ~MuxCase(
      0.U(7.W),
      Seq(
        ((x === 0.U), "b111_1110".U(7.W)),
        ((x === 1.U), "b011_0000".U(7.W)),
        ((x === 2.U), "b110_1101".U(7.W)),
        ((x === 3.U), "b111_1001".U(7.W)),
        ((x === 4.U), "b011_0011".U(7.W)),
        ((x === 5.U), "b101_1011".U(7.W)),
        ((x === 6.U), "b101_1111".U(7.W)),
        ((x === 7.U), "b111_0000".U(7.W))
      )
    )
  }

  io.y := Mux(io.en, decode(io.x), ~0.U(7.W))
}

import _root_.circt.stage.ChiselStage

object Decode37 extends App {
  ChiselStage.emitSystemVerilogFile(
    new Decode37,
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
