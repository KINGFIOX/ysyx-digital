/** @brief
  *   ExtU - 立即数扩展单元
  */

package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter

/** 立即数类型 */
object ImmType extends ChiselEnum {
  val IMM_I, IMM_S, IMM_B, IMM_U, IMM_J = Value
}

class ExtUInputBundle extends Bundle with HasCoreParameter {
  val inst    = Output(UInt(XLEN.W))
  val immType = Output(ImmType())
}

class ExtUOutputBundle extends Bundle with HasCoreParameter {
  val imm = Output(UInt(XLEN.W))
}

class ExtU extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val in  = Flipped(new ExtUInputBundle)
    val out = new ExtUOutputBundle
  })

  private val inst = io.in.inst

  // I-type: imm[11:0] = inst[31:20]
  private val immI = Cat(Fill(20, inst(31)), inst(31, 20))

  // S-type: imm[11:0] = {inst[31:25], inst[11:7]}
  private val immS = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7))

  // B-type: imm[12:1] = {inst[31], inst[7], inst[30:25], inst[11:8]}
  private val immB = Cat(Fill(19, inst(31)), inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))

  // U-type: imm[31:12] = inst[31:12], imm[11:0] = 0
  private val immU = Cat(inst(31, 12), 0.U(12.W))

  // J-type: imm[20:1] = {inst[31], inst[19:12], inst[20], inst[30:21]}
  private val immJ = Cat(Fill(11, inst(31)), inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))

  io.out.imm := MuxCase(
    0.U,
    Seq(
      (io.in.immType === ImmType.IMM_I) -> immI,
      (io.in.immType === ImmType.IMM_S) -> immS,
      (io.in.immType === ImmType.IMM_B) -> immB,
      (io.in.immType === ImmType.IMM_U) -> immU,
      (io.in.immType === ImmType.IMM_J) -> immJ
    )
  )
}

object ExtU extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new ExtU, args, firtoolOptions)
}
