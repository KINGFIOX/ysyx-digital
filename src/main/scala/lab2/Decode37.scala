package lab2

import chisel3._
import chisel3.util._

class Decode37(width: Int) extends Module {
  val io = IO(new Bundle {
    val x = Input(UInt(width.W))
    val y = Output(UInt(7.W))
    val en = Input(Bool())
  })

  private def decode(x: UInt) = {
    ~MuxCase(
      0.U(7.W),
      Seq(
        ((x === 0x0.U), "b111_1110".U(7.W)),
        ((x === 0x1.U), "b011_0000".U(7.W)),
        ((x === 0x2.U), "b110_1101".U(7.W)),
        ((x === 0x3.U), "b111_1001".U(7.W)),
        ((x === 0x4.U), "b011_0011".U(7.W)),
        ((x === 0x5.U), "b101_1011".U(7.W)),
        ((x === 0x6.U), "b101_1111".U(7.W)),
        ((x === 0x7.U), "b111_0000".U(7.W)),
        ((x === 0x8.U), "b111_1111".U(7.W)),
        ((x === 0x9.U), "b111_1011".U(7.W)),
        ((x === 0xa.U), "b111_0111".U(7.W)),
        ((x === 0xb.U), "b001_1111".U(7.W)),
        ((x === 0xc.U), "b100_1110".U(7.W)),
        ((x === 0xd.U), "b011_1101".U(7.W)),
        ((x === 0xe.U), "b100_1111".U(7.W)),
        ((x === 0xf.U), "b100_0111".U(7.W))
      )
    )
  }

  io.y := Mux(io.en, decode(io.x), ~0.U(7.W))
}

import _root_.circt.stage.ChiselStage

object Decode37 extends App {
  ChiselStage.emitSystemVerilogFile(
    new Decode37(4),
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
