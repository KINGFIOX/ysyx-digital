package lab1

import chisel3._
import chisel3.util._

class Mux4InputBundle(val w: Int) extends Bundle {
  val x0 = UInt(w.W)
  val x1 = UInt(w.W)
  val x2 = UInt(w.W)
  val x3 = UInt(w.W)
  val y = UInt(2.W)
}

class Mux4OutputBundle(val w: Int) extends Bundle {
  val f = UInt(w.W)
}

class Mux4(w: Int = 2) extends Module {
  val input = IO(Flipped(new Mux4InputBundle(w)))
  val output = IO(new Mux4OutputBundle(w))

  output.f := MuxCase(
    DontCare,
    Seq(
      (input.y === 0.U, input.x0),
      (input.y === 1.U, input.x1),
      (input.y === 2.U, input.x2),
      (input.y === 3.U, input.x3)
    )
  )

}

import _root_.circt.stage.ChiselStage

object Mux4 extends App {
  ChiselStage.emitSystemVerilogFile(
    new Mux4,
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
