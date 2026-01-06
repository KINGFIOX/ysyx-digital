package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import blackbox.DpiPmemRead

class IFOutputBundle extends Bundle with HasCoreParameter {
  val inst = Output(UInt(XLEN.W)) //
  val snpc = Output(UInt(XLEN.W)) // which is pc + 4
  val pc   = Output(UInt(XLEN.W)) // the pc of the instruction
}

class IFInputBundle extends Bundle with HasCoreParameter {
  val dnpc = Output(UInt(XLEN.W))
}

class IF extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val out = Decoupled(new IFOutputBundle)         // 取指令输出, pc, snpc 输出
    val in  = Flipped(Decoupled(new IFInputBundle)) // 接收 dnpc (用于跳转)
  })

  private val pc = RegInit(UInt(XLEN.W), "h8000_0000".U)

  // read from irom
  private val pmemRead = Module(new DpiPmemRead)
  pmemRead.io.en   := true.B // TODO: 后面 SoC 的时候, 这里要改(暂时不动)
  pmemRead.io.addr := pc
  pmemRead.io.len  := 4.U

  io.out.valid     := true.B // TODO:
  io.out.bits.inst := pmemRead.io.data
  io.out.bits.pc   := pc
  io.out.bits.snpc := pc + 4.U

  io.in.ready := true.B // TODO:

  // TODO: pc的下一时刻始终是dnpc, dnpc 由下游计算传入
  when(io.in.fire) {
    pc := io.in.bits.dnpc
  }
}

object IF extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new IF, args, firtoolOptions)
}
