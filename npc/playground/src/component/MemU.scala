package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import blackbox.PmemReadDpiWrapper

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

  io.out.rdata := 0.U

}
