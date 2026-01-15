package npc

import chisel3._
import chisel3.util._

import common.{HasCSRParameter, HasCoreParameter, HasRegFileParameter}
import component.CSRUCommitBundle

/** 用来给 Verilator 暴露提交信息, 便于在 C++ 侧采样做差分测试 */
class CommitBundle extends Bundle with HasCoreParameter with HasRegFileParameter with HasCSRParameter {
  val valid = Output(Bool())
  val pc    = Output(UInt(XLEN.W))
  val dnpc  = Output(UInt(XLEN.W))
  val inst  = Output(UInt(InstLen.W))
  val gpr   = Output(Vec(NRReg, UInt(XLEN.W)))
  // CSR 提交信息
  val csr   = Output(new CSRUCommitBundle)
}

class NPCSoC extends Module with HasCoreParameter with HasRegFileParameter {
  val io = IO(new Bundle {
    val step = Input(Bool())
    val commit = Output(new CommitBundle)
  })

  val core = Module(new NPCCore)
  io.commit := core.io.commit
  core.io.step := io.step
}

object NPCSoC extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new NPCSoC, args, firtoolOptions)
}
