package lab3

import chisel3._
import chisel3.util._

class ALU extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(4.W))
    val b = Input(UInt(4.W))
    val x = Input(UInt(4.W))
    val op = Input(UInt(3.W))
  })

  io.x := MuxCase(
    DontCare,
    Seq(
      (io.op === "b000".U, io.a + io.b),
      (io.op === "b001".U, io.a - io.b),
      (io.op === "b010".U, ~io.a),
      (io.op === "b011".U, io.a & io.b),
      (io.op === "b100".U, io.a | io.b),
      (io.op === "b101".U, io.a < io.b),
      (io.op === "b110".U, io.a === io.b)
    )
  )
}
