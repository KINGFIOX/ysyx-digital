package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import blackbox.{DpiPmemRead, DpiPmemWrite}

object MemUOpType extends ChiselEnum {
  val mem_LB, mem_LH, mem_LW, mem_LBU, mem_LHU, mem_SB, mem_SH, mem_SW = Value
}

class MEMUInputBundle extends Bundle with HasCoreParameter {
  val op    = Output(MemUOpType()) // 操作类型
  val wdata = Output(UInt(XLEN.W)) // 写数据 (Store 用)
  val addr  = Output(UInt(XLEN.W)) // 地址
  val en    = Output(Bool())
}

class MEMUOutputBundle extends Bundle with HasCoreParameter {
  val rdata = Output(UInt(XLEN.W))
}

/** 符号扩展 */
object SignExt {
  def apply(data: UInt, width: Int = 32): UInt = {
    val signBit = data(data.getWidth - 1)
    Cat(Fill(width - data.getWidth, signBit), data)
  }
}

/** 零扩展 */
object ZeroExt {
  def apply(data: UInt, width: Int = 32): UInt = {
    Cat(0.U((width - data.getWidth).W), data)
  }
}

class MemU extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val in  = Flipped(new MEMUInputBundle) // 输入
    val out = new MEMUOutputBundle         // 输出
  })

  /* ---------- DPI-C 内存接口 ---------- */
  private val pmemRead  = Module(new DpiPmemRead)
  private val pmemWrite = Module(new DpiPmemWrite)

  /* ---------- 辅助信号 ---------- */
  private val isLoad  =
    io.in.op.isOneOf(MemUOpType.mem_LB, MemUOpType.mem_LH, MemUOpType.mem_LW, MemUOpType.mem_LBU, MemUOpType.mem_LHU)
  private val isStore = io.in.op.isOneOf(MemUOpType.mem_SB, MemUOpType.mem_SH, MemUOpType.mem_SW)
  private val subword = io.in.addr(dataBytesBits - 1, 0) // 字内偏移 (1:0)

  /* ---------- 读取长度计算 ---------- */
  private val readLen = MuxCase(
    0.U,
    Seq(
      (io.in.op === MemUOpType.mem_LB || io.in.op === MemUOpType.mem_LBU) -> 1.U,
      (io.in.op === MemUOpType.mem_LH || io.in.op === MemUOpType.mem_LHU) -> 2.U,
      (io.in.op === MemUOpType.mem_LW)                                    -> 4.U
    )
  )

  /* ---------- 写入长度计算 ---------- */
  private val writeLen = MuxCase(
    0.U,
    Seq(
      (io.in.op === MemUOpType.mem_SB) -> 1.U,
      (io.in.op === MemUOpType.mem_SH) -> 2.U,
      (io.in.op === MemUOpType.mem_SW) -> 4.U
    )
  )

  // 当不使能时，传一个安全的默认地址，避免 DPI 侧越界检查失败
  private val safeAddr = Mux(io.in.en, io.in.addr, "h80000000".U(XLEN.W))

  /* ---------- DPI 读取控制 ---------- */
  pmemRead.io.en   := io.in.en && isLoad
  pmemRead.io.addr := safeAddr
  pmemRead.io.len  := readLen
  pmemRead.io.clock := clock

  /* ---------- DPI 写入控制 ---------- */
  pmemWrite.io.clock := clock
  pmemWrite.io.en    := io.in.en && isStore
  pmemWrite.io.addr  := safeAddr
  pmemWrite.io.len   := writeLen
  pmemWrite.io.data  := io.in.wdata // DPI 侧会根据 len 和 addr 处理字节选择

  /* ---------- 读取数据处理 ---------- */
  private val rawData = pmemRead.io.data

  io.out.rdata := MuxCase(
    0.U,
    Seq(
      (io.in.op === MemUOpType.mem_LB)  -> SignExt(rawData(7, 0)),
      (io.in.op === MemUOpType.mem_LBU) -> ZeroExt(rawData(7, 0)),
      (io.in.op === MemUOpType.mem_LH)  -> SignExt(rawData(15, 0)),
      (io.in.op === MemUOpType.mem_LHU) -> ZeroExt(rawData(15, 0)),
      (io.in.op === MemUOpType.mem_LW)  -> rawData
    )
  )
}

object MemU extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new MemU, args, firtoolOptions)
}
