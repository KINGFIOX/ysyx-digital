/** @brief
  *   CU - 控制单元 (Control Unit) 根据指令生成各个模块的控制信号
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
  val OP2_RS2, OP2_IMM = Value
}

/** 写回数据选择 */
object WBSel extends ChiselEnum {
  val WB_ALU, WB_MEM, WB_PC4, WB_CSR = Value
}

/** NPC 选择 (下一条 PC) */
object NPCOpType extends ChiselEnum {
  val NPC_4, NPC_BR, NPC_JAL, NPC_JALR, NPC_ECALL, NPC_MRET = Value
}

/** CSR 操作类型 */
object CSROpType extends ChiselEnum {
  val CSR_NOP, CSR_RW, CSR_RS = Value // NOP, Read-Write, Read-Set
}

/** CU 输出的控制信号 */
class CUOutputBundle extends Bundle with HasRegFileParameter {
  // ALU 控制
  val aluOp       = ALUOpType()
  val aluSel1     = ALUOp1Sel()
  val aluSel2     = ALUOp2Sel()
  // 立即数类型
  val immType     = ImmType()
  // 内存操作
  val memOp       = MemUOpType()
  val memEn       = Bool() // 内存使能
  // 写回控制
  val wbSel       = WBSel()
  val rfWen       = Bool() // 寄存器写使能
  // 分支控制
  val bruOp       = BRUOpType()
  val npcOp       = NPCOpType()
  // CSR 控制
  val csrOp       = CSROpType()
  val csrWen      = Bool() // CSR 写使能
  // 特殊指令
  val isEbreak    = Bool()
  val isEcall     = Bool()
  val isMret      = Bool()
  // 无效指令
  val invalidInst = Bool()
}

class CUInputBundle extends Bundle with HasCoreParameter {
  val inst = UInt(InstLen.W)
}

class CU extends Module with HasCoreParameter with HasRegFileParameter {
  val io = IO(new Bundle {
    val in  = Flipped(new CUInputBundle)
    val out = new CUOutputBundle
  })

  private val inst = io.in.inst

  /* ---------- 默认值 ---------- */
  io.out.aluOp   := DontCare
  io.out.aluSel1 := DontCare
  io.out.aluSel2 := DontCare
  io.out.immType := DontCare
  io.out.memOp   := DontCare
  io.out.wbSel   := DontCare
  io.out.bruOp   := DontCare
  io.out.csrOp   := CSROpType.CSR_NOP

  io.out.memEn       := false.B         // 禁止写入, 状态不变
  io.out.rfWen       := false.B
  io.out.csrWen      := false.B
  io.out.npcOp       := NPCOpType.NPC_4 // 默认 pc + 4
  io.out.isEbreak    := false.B
  io.out.isEcall     := false.B
  io.out.isMret      := false.B
  io.out.invalidInst := true.B

  /* ---------- R-type: add rd, rs1, rs2 ---------- */
  private def rInst(op: ALUOpType.Type): Unit /*无返回值*/ = {
    io.out.aluOp       := op
    io.out.aluSel1     := ALUOp1Sel.OP1_RS1
    io.out.aluSel2     := ALUOp2Sel.OP2_RS2
    io.out.wbSel       := WBSel.WB_ALU
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }
  when(inst === ADD) { rInst(ALUOpType.alu_ADD) }
  when(inst === SUB) { rInst(ALUOpType.alu_SUB) }
  when(inst === AND) { rInst(ALUOpType.alu_AND) }
  when(inst === OR) { rInst(ALUOpType.alu_OR) }
  when(inst === XOR) { rInst(ALUOpType.alu_XOR) }
  when(inst === SLL) { rInst(ALUOpType.alu_SLL) }
  when(inst === SRL) { rInst(ALUOpType.alu_SRL) }
  when(inst === SRA) { rInst(ALUOpType.alu_SRA) }
  when(inst === SLT) { rInst(ALUOpType.alu_SLT) }
  when(inst === SLTU) { rInst(ALUOpType.alu_SLTU) }

  /* ---------- I-type: addi rd, rs1, imm ---------- */
  private def iInst(op: ALUOpType.Type): Unit /*无返回值*/ = {
    io.out.aluOp       := op
    io.out.aluSel1     := ALUOp1Sel.OP1_RS1
    io.out.aluSel2     := ALUOp2Sel.OP2_IMM
    io.out.immType     := ImmType.IMM_I
    io.out.wbSel       := WBSel.WB_ALU
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }
  when(inst === ADDI) { iInst(ALUOpType.alu_ADD) }
  when(inst === ANDI) { iInst(ALUOpType.alu_AND) }
  when(inst === ORI) { iInst(ALUOpType.alu_OR) }
  when(inst === XORI) { iInst(ALUOpType.alu_XOR) }
  when(inst === SLLI) { iInst(ALUOpType.alu_SLL) }
  when(inst === SRLI) { iInst(ALUOpType.alu_SRL) }
  when(inst === SRAI) { iInst(ALUOpType.alu_SRA) }
  when(inst === SLTI) { iInst(ALUOpType.alu_SLT) }
  when(inst === SLTIU) { iInst(ALUOpType.alu_SLTU) }

  // /* ---------- Load: lw rd, offset(rs1) ---------- */
  // private def lInst(op: MemUOpType.Type): Unit /*无返回值*/ = {
  //   io.out.aluOp       := ALUOpType.alu_ADD
  //   io.out.aluSel1     := ALUOp1Sel.OP1_RS1
  //   io.out.aluSel2     := ALUOp2Sel.OP2_IMM
  //   io.out.immType     := ImmType.IMM_I
  //   io.out.memOp       := op
  //   io.out.memEn       := true.B
  //   io.out.wbSel       := WBSel.WB_MEM
  //   io.out.rfWen       := true.B
  //   io.out.invalidInst := false.B
  // }
  // when(inst === LB) { lInst(MemUOpType.mem_LB) }
  // when(inst === LH) { lInst(MemUOpType.mem_LH) }
  // when(inst === LW) { lInst(MemUOpType.mem_LW) }
  // when(inst === LBU) { lInst(MemUOpType.mem_LBU) }
  // when(inst === LHU) { lInst(MemUOpType.mem_LHU) }

  // /* ---------- Store: sw rs2, offset(rs1) ---------- */
  // private def sInst(op: MemUOpType.Type): Unit /*无返回值*/ = {
  //   io.out.aluOp       := ALUOpType.alu_ADD
  //   io.out.aluSel1     := ALUOp1Sel.OP1_RS1
  //   io.out.aluSel2     := ALUOp2Sel.OP2_IMM
  //   io.out.immType     := ImmType.IMM_S
  //   io.out.memOp       := op
  //   io.out.memEn       := true.B
  //   io.out.invalidInst := false.B
  // }
  // when(inst === SB) { sInst(MemUOpType.mem_SB) }
  // when(inst === SH) { sInst(MemUOpType.mem_SH) }
  // when(inst === SW) { sInst(MemUOpType.mem_SW) }

  /* ---------- Branch: beq rs1, rs2, offset ---------- */
  private def bInst(op: BRUOpType.Type): Unit /*无返回值*/ = {
    io.out.aluOp       := ALUOpType.alu_ADD
    io.out.aluSel1     := ALUOp1Sel.OP1_PC
    io.out.aluSel2     := ALUOp2Sel.OP2_IMM
    io.out.bruOp       := op
    io.out.immType     := ImmType.IMM_B
    io.out.npcOp       := NPCOpType.NPC_BR
    io.out.invalidInst := false.B
  }
  when(inst === BEQ) { bInst(BRUOpType.bru_BEQ) }
  when(inst === BNE) { bInst(BRUOpType.bru_BNE) }
  when(inst === BLT) { bInst(BRUOpType.bru_BLT) }
  when(inst === BGE) { bInst(BRUOpType.bru_BGE) }
  when(inst === BLTU) { bInst(BRUOpType.bru_BLTU) }
  when(inst === BGEU) { bInst(BRUOpType.bru_BGEU) }

  /* ---------- JAL: jal rd, offset ---------- */
  when(inst === JAL) {
    io.out.aluOp       := ALUOpType.alu_ADD
    io.out.aluSel1     := ALUOp1Sel.OP1_PC
    io.out.aluSel2     := ALUOp2Sel.OP2_IMM
    io.out.immType     := ImmType.IMM_J
    io.out.npcOp       := NPCOpType.NPC_JAL
    io.out.wbSel       := WBSel.WB_PC4
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }

  /* ---------- JALR: jalr rd, rs1, offset ---------- */
  when(inst === JALR) {
    io.out.aluOp       := ALUOpType.alu_ADD // ALU 计算 rs1 + imm
    io.out.aluSel1     := ALUOp1Sel.OP1_RS1
    io.out.aluSel2     := ALUOp2Sel.OP2_IMM
    io.out.immType     := ImmType.IMM_I
    io.out.npcOp       := NPCOpType.NPC_JALR
    io.out.wbSel       := WBSel.WB_PC4
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }

  /* ---------- LUI: lui rd, imm ---------- */
  when(inst === LUI) {
    io.out.aluOp       := ALUOpType.alu_ADD
    io.out.aluSel1     := ALUOp1Sel.OP1_ZERO
    io.out.aluSel2     := ALUOp2Sel.OP2_IMM
    io.out.immType     := ImmType.IMM_U
    io.out.wbSel       := WBSel.WB_ALU
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }

  /* ---------- AUIPC: auipc rd, imm ---------- */
  when(inst === AUIPC) {
    io.out.aluOp       := ALUOpType.alu_ADD
    io.out.aluSel1     := ALUOp1Sel.OP1_PC
    io.out.aluSel2     := ALUOp2Sel.OP2_IMM
    io.out.immType     := ImmType.IMM_U
    io.out.wbSel       := WBSel.WB_ALU
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }

  /* ---------- EBREAK: ebreak ---------- */
  when(inst === EBREAK) {
    io.out.isEbreak    := true.B
    io.out.invalidInst := false.B
  }

  /* ---------- CSR 指令 ---------- */
  // CSRRW: t = CSRs[csr]; CSRs[csr] = rs1; rd = t
  when(inst === CSRRW) {
    io.out.csrOp       := CSROpType.CSR_RW
    io.out.csrWen      := true.B
    io.out.wbSel       := WBSel.WB_CSR
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }

  // CSRRS: t = CSRs[csr]; CSRs[csr] = t | rs1; rd = t
  when(inst === CSRRS) {
    io.out.csrOp       := CSROpType.CSR_RS
    io.out.csrWen      := true.B
    io.out.wbSel       := WBSel.WB_CSR
    io.out.rfWen       := true.B
    io.out.invalidInst := false.B
  }

  /* ---------- ECALL: 触发环境调用异常 ---------- */
  when(inst === ECALL) {
    io.out.isEcall     := true.B
    io.out.npcOp       := NPCOpType.NPC_ECALL
    io.out.invalidInst := false.B
  }

  /* ---------- MRET: 从异常返回 ---------- */
  when(inst === MRET) {
    io.out.isMret      := true.B
    io.out.npcOp       := NPCOpType.NPC_MRET
    io.out.invalidInst := false.B
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
