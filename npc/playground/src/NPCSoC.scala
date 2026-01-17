package npc

import chisel3._
import chisel3.util._

import common.{HasCSRParameter, HasCoreParameter, HasRegFileParameter}
import component.CSRUDebugBundle
import component.AXI4LitePmemSlave
import general.{AXI4LiteXBar, AXI4LiteXBarParams, AXI4LiteParams}

/** 用来给 Verilator 暴露提交信息, 便于在 C++ 侧采样做差分测试 */
class DebugBundle extends Bundle with HasCoreParameter with HasRegFileParameter with HasCSRParameter {
  val valid = Bool()
  val pc    = UInt(XLEN.W)
  val dnpc  = UInt(XLEN.W)
  val inst  = UInt(InstLen.W)
  val gpr   = Vec(NRReg, UInt(XLEN.W))
  // CSR 提交信息
  val csr   = new CSRUDebugBundle
}

class NPCSoC(params: AXI4LiteParams) extends Module {
  val io = IO(new Bundle {
    val step = Input(Bool())
    val debug = new DebugBundle
  })

  private val core = Module(new NPCCore(params))
  io.debug := core.io.debug
  core.io.step := io.step

  // AXI4-Lite Crossbar 配置
  // 地址映射：内存从 0x80000000 开始，大小为 256MB (0x10000000)
  private val xbarParams = AXI4LiteXBarParams(
    axi = params,
    numMasters = 2, // IFU (icache) 和 LSU (dcache) 作为 master
    numSlaves = 1,  // 只有一个内存 slave
    addrMap = Seq(
      (BigInt(0x80000000L), BigInt(0x10000000L)) // 内存地址空间
    )
  )

  private val xbar = Module(new AXI4LiteXBar(xbarParams))

  // 连接 core 的 icache 和 dcache 到 xbar 的 master 端口
  xbar.io.masters(0) <> core.io.icache
  xbar.io.masters(1) <> core.io.dcache

  // 创建内存 slave
  private val memSlave = Module(new AXI4LitePmemSlave(params))

  // 连接 xbar 的 slave 端口到内存 slave
  xbar.io.slaves(0) <> memSlave.io.axi
}

object NPCSoC extends App with HasCoreParameter {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new NPCSoC(AXI4LiteParams(addrWidth = XLEN, dataWidth = XLEN)), args, firtoolOptions)
}
