package lab2

import chisel3._
import chisel3.util._

class Encode83 extends Module {
  val io = IO(new Bundle {
    val x = Input(UInt(8.W))
    val y = Output(UInt(3.W))
  })

  private def encode(x: UInt) = {
    MuxCase(
      DontCare,
      Seq(
        (x === "b0000_0001".U(8.W), 0.U),
        (x === "b0000_0010".U(8.W), 1.U),
        (x === "b0000_0100".U(8.W), 3.U),
        (x === "b0000_1000".U(8.W), 2.U),
        (x === "b0001_0000".U(8.W), 4.U),
        (x === "b0010_0000".U(8.W), 5.U),
        (x === "b0100_0000".U(8.W), 6.U),
        (x === "b1000_0000".U(8.W), 7.U)
      )
    )
  }

  io.y := encode(io.x)
}

import _root_.circt.stage.ChiselStage

object Encode83 extends App {
  ChiselStage.emitSystemVerilogFile(
    new Encode83,
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
