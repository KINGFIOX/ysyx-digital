package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import common.HasRegFileParameter

class RFUOutputBundle extends Bundle with HasCoreParameter with HasRegFileParameter {
  val rs1_v = Output(UInt(XLEN.W))
  val rs2_v = Output(UInt(XLEN.W))
  val gpr   = Output(Vec(NRReg, UInt(XLEN.W))) // 导出所有寄存器用于 difftest
}

class RFUInputBundle extends Bundle with HasRegFileParameter with HasCoreParameter {
  val rs1_i = Output(UInt(NRRegbits.W))
  val rs2_i = Output(UInt(NRRegbits.W))
  val rd_i  = Output(UInt(NRRegbits.W))
  // write
  val wdata = Output(UInt(XLEN.W))
  val wen   = Output(Bool())
}

/** @brief
  *   寄存器堆
  */
class RFU extends Module with HasCoreParameter with HasRegFileParameter {
  val io = IO(new Bundle {
    val in  = Flipped(new RFUInputBundle)
    val out = new RFUOutputBundle
  })

  // 使用 RegInit 初始化为 0
  private val rf = RegInit(VecInit(Seq.fill(NRReg)(0.U(XLEN.W))))

  // 读取: x0 始终为 0
  io.out.rs1_v := Mux(io.in.rs1_i === 0.U, 0.U, rf(io.in.rs1_i))
  io.out.rs2_v := Mux(io.in.rs2_i === 0.U, 0.U, rf(io.in.rs2_i))

  // 写入: x0 不可写
  when(io.in.wen && (io.in.rd_i =/= 0.U)) { rf(io.in.rd_i) := io.in.wdata }

  // 导出所有寄存器用于 difftest
  io.out.gpr := rf
}

object RFU extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new RFU, args, firtoolOptions)
}
