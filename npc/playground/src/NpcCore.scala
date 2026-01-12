package npc

import chisel3._
import chisel3.util._

import common.{HasCSRParameter, HasCoreParameter, HasRegFileParameter}
import component._
import blackbox.{DpiEbreak, DpiInvalidInst}

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

class NpcCoreTop extends Module with HasCoreParameter with HasRegFileParameter {
  val io = IO(new Bundle {
    val step   = Input(Bool())    // 单步触发 (宿主侧拉高一个周期)
    val commit = new CommitBundle // 提交信息, 供 difftest 使用
  })

  /* ========== 实例化各模块 ========== */
  private val ifu            = Module(new IFU)
  private val cu             = Module(new CU)
  private val igu            = Module(new IGU)
  private val rfu            = Module(new RFU)
  private val alu            = Module(new ALU)
  private val bru            = Module(new BRU)
  private val memU           = Module(new MemU)
  private val csru           = Module(new CSRU)
  private val ebreakDpi      = Module(new DpiEbreak)
  private val invalidInstDpi = Module(new DpiInvalidInst)

  /* ========== 指令字段提取 ========== */
  private val inst    = ifu.io.out.inst
  private val pc      = ifu.io.out.pc
  private val snpc    = ifu.io.out.snpc
  private val rd      = inst(11, 7)
  private val rs1     = inst(19, 15)
  private val rs2     = inst(24, 20)
  private val csrAddr = inst(31, 20) // CSR 地址

  /* ========== 控制单元 ========== */
  cu.io.in.inst := inst

  /* ========== 立即数扩展 ========== */
  igu.io.in.inst_31_7 := inst(InstLen - 1, OpcodeLen) // 只传递 inst[31:7], 不需要 opcode
  igu.io.in.immType   := cu.io.out.immType
  val imm = igu.io.out.imm

  /* ========== 寄存器堆读取 ========== */
  rfu.io.in.rs1_i := rs1
  rfu.io.in.rs2_i := rs2
  rfu.io.in.rd_i  := rd
  val rs1Data = rfu.io.out.rs1_v
  val rs2Data = rfu.io.out.rs2_v

  /* ========== ALU 操作数选择 ========== */
  alu.io.in.op1   := MuxCase(
    0.U,
    Seq(
      (cu.io.out.aluSel1 === ALUOp1Sel.OP1_RS1)  -> rs1Data,
      (cu.io.out.aluSel1 === ALUOp1Sel.OP1_PC)   -> pc,
      (cu.io.out.aluSel1 === ALUOp1Sel.OP1_ZERO) -> 0.U
    )
  )
  alu.io.in.op2   := MuxCase(
    0.U,
    Seq(
      (cu.io.out.aluSel2 === ALUOp2Sel.OP2_RS2) -> rs2Data,
      (cu.io.out.aluSel2 === ALUOp2Sel.OP2_IMM) -> imm
    )
  )
  alu.io.in.aluOp := cu.io.out.aluOp
  val aluResult = alu.io.out.result

  /* ========== 分支单元 ========== */
  bru.io.in.rs1_v  := rs1Data
  bru.io.in.rs2_v  := rs2Data
  bru.io.in.bru_op := cu.io.out.bruOp
  val brTaken = bru.io.out.br_flag

  /* ========== 内存单元 ========== */
  memU.io.in.en    := io.step && cu.io.out.memEn
  memU.io.in.op    := cu.io.out.memOp
  memU.io.in.addr  := aluResult // ALU 计算出的地址
  memU.io.in.wdata := rs2Data   // Store 的数据
  val memData = memU.io.out.rdata

  /* ========== CSR 单元 ========== */
  csru.io.in.addr     := csrAddr
  csru.io.in.op       := cu.io.out.csrOp
  csru.io.in.wen      := io.step && cu.io.out.csrWen
  csru.io.in.rs1_data := rs1Data
  csru.io.in.isEcall  := io.step && cu.io.out.isEcall
  csru.io.in.isMret   := io.step && cu.io.out.isMret
  csru.io.in.pc       := pc
  val csrData = csru.io.out.rdata

  /* ========== 写回数据选择 ========== */
  val wbData = MuxCase(
    0.U,
    Seq(
      (cu.io.out.wbSel === WBSel.WB_ALU) -> aluResult,
      (cu.io.out.wbSel === WBSel.WB_MEM) -> memData,
      (cu.io.out.wbSel === WBSel.WB_PC4) -> snpc,
      (cu.io.out.wbSel === WBSel.WB_CSR) -> csrData
    )
  )

  /* ========== 寄存器堆写入 ========== */
  rfu.io.in.wdata := wbData
  rfu.io.in.wen   := io.step && cu.io.out.rfWen // 只在 step 有效时写入

  /* ========== 下一条 PC 计算 ========== */
  // ALU 已计算: JAL/Branch 为 PC+imm, JALR 为 rs1+imm
  val dnpc = MuxCase(
    snpc, // 默认: PC + 4
    Seq(
      (cu.io.out.npcOp === NPCOpType.NPC_JAL)           -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_JALR)          -> (aluResult & (~1.U(XLEN.W))),
      (cu.io.out.npcOp === NPCOpType.NPC_BR && brTaken) -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_ECALL)         -> csru.io.out.mtvec,
      (cu.io.out.npcOp === NPCOpType.NPC_MRET)          -> csru.io.out.mepc
    )
  )

  /* ========== IFU 连接 ========== */
  // dnpc 只在 step 有效时更新
  ifu.io.in.dnpc := Mux(io.step, dnpc, pc)
  ifu.io.in.step := io.step

  /* ========== EBREAK DPI 调用 ========== */
  // 只在 step 有效且是 ebreak 指令时触发
  ebreakDpi.io.en := io.step && cu.io.out.isEbreak
  ebreakDpi.io.pc := pc
  ebreakDpi.io.a0 := rfu.io.out.commit.gpr(10) // $a0 = x10, 作为返回值

  /* ========== Invalid Instruction DPI 调用 ========== */
  // 只在 step 有效且是无效指令时触发
  invalidInstDpi.io.en   := io.step && cu.io.out.invalidInst
  invalidInstDpi.io.pc   := pc
  invalidInstDpi.io.inst := inst

  /* ========== Commit 输出 (供 difftest) ========== */
  io.commit.valid := io.step
  io.commit.pc    := pc
  io.commit.dnpc  := dnpc
  io.commit.inst  := inst
  io.commit.gpr   := rfu.io.out.commit.gpr
  io.commit.csr   := csru.io.out.commit
}

object NpcCoreTop extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new NpcCoreTop, args, firtoolOptions)
}
