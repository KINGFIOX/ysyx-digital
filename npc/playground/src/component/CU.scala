/** @brief
  *   CU - 控制单元 (Control Unit)
  *   根据指令生成各个模块的控制信号
  */

package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import common.HasRegFileParameter
import common.Instructions._

/** ALU 操作数1 选择 */
object ALUOp1Sel extends ChiselEnum {
  val OP1_RS1, OP1_PC, OP1_ZERO = Value
}

/** ALU 操作数2 选择 */
object ALUOp2Sel extends ChiselEnum {
  val OP2_RS2, OP2_IMM, OP2_4 = Value
}

/** 写回数据选择 */
object WBSel extends ChiselEnum {
  val WB_X, WB_ALU, WB_MEM, WB_PC4 = Value
}

/** NPC 选择 (下一条 PC) */
object NPCOpType extends ChiselEnum {
  val NPC_4, NPC_BR, NPC_JAL, NPC_JALR = Value
}

/** CU 输出的控制信号 */
class CUOutputBundle extends Bundle with HasRegFileParameter {
  // ALU 控制
  val aluOp   = Output(ALUOpType())
  val aluOp1  = Output(ALUOp1Sel())
  val aluOp2  = Output(ALUOp2Sel())
  // 立即数类型
  val immType = Output(ImmType())
  // 内存操作
  val memOp   = Output(MemUOpType())
  val memEn   = Output(Bool()) // 内存使能
  // 写回控制
  val wbSel   = Output(WBSel())
  val rfWen   = Output(Bool()) // 寄存器写使能
  // 分支控制
  val bruOp   = Output(BRUOpType())
  val npcOp   = Output(NPCOpType())
}

class CU extends Module with HasCoreParameter with HasRegFileParameter {
  val io = IO(new Bundle {
    val inst = Input(UInt(XLEN.W))
    val ctrl = new CUOutputBundle
  })

  private val inst = io.inst

  /* ---------- 默认值 ---------- */
  io.ctrl.aluOp   := ALUOpType.alu_ADD
  io.ctrl.aluOp1  := ALUOp1Sel.OP1_ZERO
  io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
  io.ctrl.immType := ImmType.IMM_I
  io.ctrl.memOp   := MemUOpType.mem_LB // 默认值，memEn=false 时不生效
  io.ctrl.memEn   := false.B
  io.ctrl.wbSel   := WBSel.WB_X
  io.ctrl.rfWen   := false.B
  io.ctrl.bruOp   := BRUOpType.bru_X
  io.ctrl.npcOp   := NPCOpType.NPC_4

  /* ---------- R-type: add rd, rs1, rs2 ---------- */
  when(inst === ADD) {
    io.ctrl.aluOp  := ALUOpType.alu_ADD
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === SUB) {
    io.ctrl.aluOp  := ALUOpType.alu_SUB
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === AND) {
    io.ctrl.aluOp  := ALUOpType.alu_AND
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === OR) {
    io.ctrl.aluOp  := ALUOpType.alu_OR
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === XOR) {
    io.ctrl.aluOp  := ALUOpType.alu_XOR
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === SLL) {
    io.ctrl.aluOp  := ALUOpType.alu_SLL
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === SRL) {
    io.ctrl.aluOp  := ALUOpType.alu_SRL
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === SRA) {
    io.ctrl.aluOp  := ALUOpType.alu_SRA
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === SLT) {
    io.ctrl.aluOp  := ALUOpType.alu_SLT
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }
  when(inst === SLTU) {
    io.ctrl.aluOp  := ALUOpType.alu_SLTU
    io.ctrl.aluOp1 := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2 := ALUOp2Sel.OP2_RS2
    io.ctrl.wbSel  := WBSel.WB_ALU
    io.ctrl.rfWen  := true.B
  }

  /* ---------- I-type 算术: addi rd, rs1, imm ---------- */
  when(inst === ADDI) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === ANDI) {
    io.ctrl.aluOp   := ALUOpType.alu_AND
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === ORI) {
    io.ctrl.aluOp   := ALUOpType.alu_OR
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === XORI) {
    io.ctrl.aluOp   := ALUOpType.alu_XOR
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === SLLI) {
    io.ctrl.aluOp   := ALUOpType.alu_SLL
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === SRLI) {
    io.ctrl.aluOp   := ALUOpType.alu_SRL
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === SRAI) {
    io.ctrl.aluOp   := ALUOpType.alu_SRA
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === SLTI) {
    io.ctrl.aluOp   := ALUOpType.alu_SLT
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
  when(inst === SLTIU) {
    io.ctrl.aluOp   := ALUOpType.alu_SLTU
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }

  /* ---------- Load: lw rd, offset(rs1) ---------- */
  when(inst === LB) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.memOp   := MemUOpType.mem_LB
    io.ctrl.memEn   := true.B
    io.ctrl.wbSel   := WBSel.WB_MEM
    io.ctrl.rfWen   := true.B
  }
  when(inst === LH) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.memOp   := MemUOpType.mem_LH
    io.ctrl.memEn   := true.B
    io.ctrl.wbSel   := WBSel.WB_MEM
    io.ctrl.rfWen   := true.B
  }
  when(inst === LW) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.memOp   := MemUOpType.mem_LW
    io.ctrl.memEn   := true.B
    io.ctrl.wbSel   := WBSel.WB_MEM
    io.ctrl.rfWen   := true.B
  }
  when(inst === LBU) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.memOp   := MemUOpType.mem_LBU
    io.ctrl.memEn   := true.B
    io.ctrl.wbSel   := WBSel.WB_MEM
    io.ctrl.rfWen   := true.B
  }
  when(inst === LHU) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.memOp   := MemUOpType.mem_LHU
    io.ctrl.memEn   := true.B
    io.ctrl.wbSel   := WBSel.WB_MEM
    io.ctrl.rfWen   := true.B
  }

  /* ---------- Store: sw rs2, offset(rs1) ---------- */
  when(inst === SB) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_S
    io.ctrl.memOp   := MemUOpType.mem_SB
    io.ctrl.memEn   := true.B
  }
  when(inst === SH) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_S
    io.ctrl.memOp   := MemUOpType.mem_SH
    io.ctrl.memEn   := true.B
  }
  when(inst === SW) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_RS1
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_S
    io.ctrl.memOp   := MemUOpType.mem_SW
    io.ctrl.memEn   := true.B
  }

  /* ---------- Branch: beq rs1, rs2, offset ---------- */
  when(inst === BEQ) {
    io.ctrl.bruOp   := BRUOpType.bru_BEQ
    io.ctrl.immType := ImmType.IMM_B
    io.ctrl.npcOp   := NPCOpType.NPC_BR
  }
  when(inst === BNE) {
    io.ctrl.bruOp   := BRUOpType.bru_BNE
    io.ctrl.immType := ImmType.IMM_B
    io.ctrl.npcOp   := NPCOpType.NPC_BR
  }
  when(inst === BLT) {
    io.ctrl.bruOp   := BRUOpType.bru_BLT
    io.ctrl.immType := ImmType.IMM_B
    io.ctrl.npcOp   := NPCOpType.NPC_BR
  }
  when(inst === BGE) {
    io.ctrl.bruOp   := BRUOpType.bru_BGE
    io.ctrl.immType := ImmType.IMM_B
    io.ctrl.npcOp   := NPCOpType.NPC_BR
  }
  when(inst === BLTU) {
    io.ctrl.bruOp   := BRUOpType.bru_BLTU
    io.ctrl.immType := ImmType.IMM_B
    io.ctrl.npcOp   := NPCOpType.NPC_BR
  }
  when(inst === BGEU) {
    io.ctrl.bruOp   := BRUOpType.bru_BGEU
    io.ctrl.immType := ImmType.IMM_B
    io.ctrl.npcOp   := NPCOpType.NPC_BR
  }

  /* ---------- JAL: jal rd, offset ---------- */
  when(inst === JAL) {
    io.ctrl.immType := ImmType.IMM_J
    io.ctrl.npcOp   := NPCOpType.NPC_JAL
    io.ctrl.wbSel   := WBSel.WB_PC4
    io.ctrl.rfWen   := true.B
  }

  /* ---------- JALR: jalr rd, rs1, offset ---------- */
  when(inst === JALR) {
    io.ctrl.immType := ImmType.IMM_I
    io.ctrl.npcOp   := NPCOpType.NPC_JALR
    io.ctrl.wbSel   := WBSel.WB_PC4
    io.ctrl.rfWen   := true.B
  }

  /* ---------- LUI: lui rd, imm ---------- */
  when(inst === LUI) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_ZERO
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_U
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }

  /* ---------- AUIPC: auipc rd, imm ---------- */
  when(inst === AUIPC) {
    io.ctrl.aluOp   := ALUOpType.alu_ADD
    io.ctrl.aluOp1  := ALUOp1Sel.OP1_PC
    io.ctrl.aluOp2  := ALUOp2Sel.OP2_IMM
    io.ctrl.immType := ImmType.IMM_U
    io.ctrl.wbSel   := WBSel.WB_ALU
    io.ctrl.rfWen   := true.B
  }
}

object CU extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new CU, args, firtoolOptions)
}
