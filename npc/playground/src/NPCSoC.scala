package npc

import chisel3._
import chisel3.util._

import common.{HasCSRParameter, HasCoreParameter, HasRegFileParameter}
import component.CSRUCommitBundle
import component.AXI4LitePmemSlave
import general.{AXI4LiteXBar, AXI4LiteXBarParams, AXI4LiteParams}

/** 用来给 Verilator 暴露提交信息, 便于在 C++ 侧采样做差分测试 */
class CommitBundle extends Bundle with HasCoreParameter with HasRegFileParameter with HasCSRParameter {
  val valid = Bool()
  val pc    = UInt(XLEN.W)
  val dnpc  = UInt(XLEN.W)
  val inst  = UInt(InstLen.W)
  val gpr   = Vec(NRReg, UInt(XLEN.W))
  // CSR 提交信息
  val csr   = new CSRUCommitBundle
}

class NPCSoC extends Module {
  val io = IO(new Bundle {
    val step = Input(Bool())
    val commit = new CommitBundle
  })

  val core = Module(new NPCCore)
  io.commit := core.io.commit
  core.io.step := io.step

  // AXI4-Lite Crossbar 配置
  // 地址映射：内存从 0x80000000 开始，大小为 256MB (0x10000000)
  val xbarParams = AXI4LiteXBarParams(
    axi = AXI4LiteParams(),
    numMasters = 1, // 只有 IFU 作为 master
    numSlaves = 1,  // 只有一个内存 slave
    addrMap = Seq(
      (BigInt(0x80000000L), BigInt(0x10000000L)) // 内存地址空间
    )
  )

  val xbar = Module(new AXI4LiteXBar(xbarParams))

  // 连接 core 的 icache 到 xbar 的 master 端口
  xbar.io.masters(0) <> core.io.icache

  // 创建内存 slave
  val memSlave = Module(new AXI4LitePmemSlave)

  // 连接 xbar 的 slave 端口到内存 slave
  xbar.io.slaves(0) <> memSlave.io.axi
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
