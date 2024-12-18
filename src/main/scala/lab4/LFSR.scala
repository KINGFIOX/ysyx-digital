package lab4

import chisel3._
import chisel3.util._

class LFSR(seed: Int = 0xde, width: Int = 8) extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(width.W))
    val btn = Input(Bool())
  })

  private def lfsr_next(lfsr: UInt) = {
    val x0 = lfsr(0)
    val x2 = lfsr(2)
    val x3 = lfsr(3)
    val x4 = lfsr(4)
    val x8 = x0 ^ x2 ^ x3 ^ x4
    val next_lfsr = Cat(x8, lfsr(width - 1, 1))
    next_lfsr
  }

  val trigger = RegNext(io.btn) && !io.btn // posedge

  val lfsr = RegInit(seed.U(width.W))

  when(trigger) {
    lfsr := lfsr_next(lfsr)
  }

  io.out := lfsr

}

import _root_.circt.stage.ChiselStage

object LFSR extends App {
  ChiselStage.emitSystemVerilogFile(
    new LFSR,
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
