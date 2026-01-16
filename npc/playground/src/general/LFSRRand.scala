package general

import chisel3._
import chisel3.util._

class LFSRRand(seed: Int = 0xff) extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
  })
  val state = RegInit(seed.U(8.W))
  val x8 = state(4) ^ state(3) ^ state(2) ^ state(0)
  state := Cat(x8, state(7, 1))
  io.out := state
}
