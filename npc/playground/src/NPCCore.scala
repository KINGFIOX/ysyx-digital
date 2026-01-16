package npc

import chisel3._
import chisel3.util._

import common.{HasCSRParameter, HasCoreParameter, HasRegFileParameter}
import component._
import blackbox.{DpiEbreak, DpiInvalidInst}
import general.AXI4LiteMasterIO
import general.AXI4LiteParams
import firrtl.options.Stage

// 1. 组件初始化
// 2. 组合逻辑电路
// 3. 状态机
// 4. 连线
class NPCCore(params: AXI4LiteParams) extends Module with HasCoreParameter with HasRegFileParameter {
  val io = IO(new Bundle {
    val step = Input(Bool())
    val commit = Output(new CommitBundle)
    val icache = new AXI4LiteMasterIO(params)
    val dcache = new AXI4LiteMasterIO(params)
  })

  /* ========== 实例化各模块 ========== */
  private val ifu = Module(new IFU(params))
  private val cu = Module(new CU)
  private val igu = Module(new IGU)
  private val rfu = Module(new RFU)
  private val alu = Module(new ALU)
  private val bru = Module(new BRU)
  private val lsu = Module(new LSU(params))
  private val csru = Module(new CSRU)
  private val ebreakDpi = Module(new DpiEbreak)
  private val invalidInstDpi = Module(new DpiInvalidInst)

  /* ========== 指令字段提取 ========== */
  private val inst = ifu.io.out.bits.inst
  private val pc = ifu.io.out.bits.pc
  private val snpc = ifu.io.out.bits.pc + 4.U
  private val rd = inst(11, 7)
  private val rs1 = inst(19, 15)
  private val rs2 = inst(24, 20)
  private val csrAddr = inst(31, 20) // CSR 地址

  /* ========== 控制单元 ========== */
  cu.io.in.inst := inst

  /* ========== 立即数扩展 ========== */
  igu.io.in.inst_31_7 := inst(InstLen - 1, OpcodeLen) // 只传递 inst[31:7], 不需要 opcode
  igu.io.in.immType := cu.io.out.immType
  private val imm = igu.io.out.imm

  /* ========== 寄存器堆读取 ========== */
  rfu.io.in.rs1_i := rs1
  rfu.io.in.rs2_i := rs2
  rfu.io.in.rd_i := rd
  private val rs1Data = rfu.io.out.rs1_v
  private val rs2Data = rfu.io.out.rs2_v

  /* ========== ALU 操作数选择 ========== */
  alu.io.in.op1 := MuxCase(
    0.U,
    Seq(
      (cu.io.out.aluSel1 === ALUOp1Sel.OP1_RS1) -> rs1Data,
      (cu.io.out.aluSel1 === ALUOp1Sel.OP1_PC) -> pc,
      (cu.io.out.aluSel1 === ALUOp1Sel.OP1_ZERO) -> 0.U
    )
  )
  alu.io.in.op2 := MuxCase(
    0.U,
    Seq(
      (cu.io.out.aluSel2 === ALUOp2Sel.OP2_RS2) -> rs2Data,
      (cu.io.out.aluSel2 === ALUOp2Sel.OP2_IMM) -> imm
    )
  )
  alu.io.in.aluOp := cu.io.out.aluOp
  private val aluResult = alu.io.out.result

  /* ========== 分支单元 ========== */
  bru.io.in.rs1_v := rs1Data
  bru.io.in.rs2_v := rs2Data
  bru.io.in.bru_op := cu.io.out.bruOp
  private val brTaken = bru.io.out.br_flag

  /* ========== CSR 单元 ========== */
  csru.io.in.addr := csrAddr
  csru.io.in.op := cu.io.out.csrOp
  // wen, isEcall, isMret 的门控在状态机之后设置
  csru.io.in.rs1_data := rs1Data
  csru.io.in.pc := pc
  private val csrData = csru.io.out.rdata

  /* ========== 写回数据选择 ========== */
  private val memData = lsu.io.out.bits.rdata
  private val wbData = MuxCase(
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
  // 注意: wen 的门控需要在状态机之后设置，使用 inst_complete 信号
  // 这里先设置控制单元的 rfWen，实际写入门控在状态机部分处理
  private val rfWenFromCU = cu.io.out.rfWen

  /* ========== 下一条 PC 计算 ========== */
  // ALU 已计算: JAL/Branch 为 PC+imm, JALR 为 rs1+imm
  private val dnpc = MuxCase(
    snpc, // 默认: PC + 4
    Seq(
      (cu.io.out.npcOp === NPCOpType.NPC_JAL) -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_JALR) -> (aluResult & (~1.U(XLEN.W))),
      (cu.io.out.npcOp === NPCOpType.NPC_BR && brTaken) -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_ECALL) -> csru.io.out.mtvec,
      (cu.io.out.npcOp === NPCOpType.NPC_MRET) -> csru.io.out.mepc
    )
  )

  /* ========== 状态机 ========== */
  object State extends ChiselEnum {
    // wait for ifu.io.in.ready
    val idle, inst_wait, mem_wait, ifu_wait = Value
  }
  private val state = RegInit(State.idle)
  dontTouch(state)

  // 锁存信号寄存器（因为 inst_wait 后 IFU 输出可能变化）
  private val dnpc_reg = RegInit(0.U(XLEN.W))
  private val pc_reg = RegInit(0.U(XLEN.W))
  private val inst_reg = RegInit(0.U(InstLen.W))

  // 指令执行完成信号
  // - 非内存指令：在 inst_wait 状态且 ifu.io.out.fire 时完成
  // - 内存指令：在 mem_wait 状态且 lsu.io.out.fire 时完成
  private val inst_complete_no_mem = (state === State.inst_wait) && ifu.io.out.fire && !cu.io.out.memEn
  private val inst_complete_mem = (state === State.mem_wait) && lsu.io.out.fire
  private val inst_complete = inst_complete_no_mem || inst_complete_mem

  switch(state) {
    is(State.idle) {
      when(io.step) {
        state := State.inst_wait
      }
    }
    is(State.inst_wait) {
      when(ifu.io.out.fire) {
        // 锁存所有需要在后续状态使用的信号
        dnpc_reg := dnpc
        pc_reg := pc
        inst_reg := inst
        when(cu.io.out.memEn) {
          state := State.mem_wait
        }.otherwise {
          state := State.ifu_wait
        }
      }
    }
    is(State.mem_wait) {
      when(lsu.io.out.fire) {
        state := State.ifu_wait
      }
    }
    is(State.ifu_wait) {
      when(ifu.io.in.fire) {
        state := State.idle
      }
    }
  }

  /* ========== 寄存器堆/CSR 写入门控 ========== */
  // 只有在指令执行完成时才真正写入寄存器堆
  rfu.io.in.wen := rfWenFromCU && inst_complete
  // CSR 写入也需要门控
  csru.io.in.wen := cu.io.out.csrWen && inst_complete
  csru.io.in.isEcall := cu.io.out.isEcall && inst_complete
  csru.io.in.isMret := cu.io.out.isMret && inst_complete

  /* ========== IFU 连接 ========== */
  ifu.io.step := io.step
  ifu.io.out.ready := (state === State.inst_wait) // 在 inst_wait 状态接收指令
  ifu.io.in.valid := (state === State.ifu_wait) // 在 ifu_wait 状态发送 dnpc
  ifu.io.in.bits.dnpc := dnpc_reg // 使用锁存的 dnpc
  ifu.io.icache <> io.icache

  /* ========== LSU 连接 ========== */
  lsu.io.in.valid := (state === State.inst_wait) && ifu.io.out.fire && cu.io.out.memEn
  lsu.io.in.bits.op := cu.io.out.memOp
  lsu.io.in.bits.addr := aluResult              // 地址由 ALU 计算 (rs1 + imm)
  lsu.io.in.bits.wdata := rs2Data               // Store 数据来自 rs2
  lsu.io.in.bits.en := cu.io.out.memEn
  lsu.io.out.ready := (state === State.mem_wait)
  lsu.io.dcache <> io.dcache

  /* ========== EBREAK DPI 调用 ========== */
  // 只在指令执行完成且是 ebreak 指令时触发
  // ebreak 不是内存指令，所以只会在 inst_complete_no_mem 时触发
  ebreakDpi.io.en := inst_complete_no_mem && cu.io.out.isEbreak
  ebreakDpi.io.pc := pc
  ebreakDpi.io.a0 := rfu.io.out.commit.gpr(10) // $a0 = x10, 作为返回值

  /* ========== Invalid Instruction DPI 调用 ========== */
  // 只在指令执行完成且是无效指令时触发
  // 无效指令也只会在 inst_complete_no_mem 时触发
  invalidInstDpi.io.en := inst_complete_no_mem && cu.io.out.invalidInst
  invalidInstDpi.io.pc := pc
  invalidInstDpi.io.inst := inst

  /* ========== Commit 输出 (供 difftest) ========== */
  // 非内存指令在 inst_wait 状态完成，使用组合逻辑值
  // 内存指令在 mem_wait 状态完成，使用锁存值
  io.commit.valid := inst_complete
  io.commit.pc := Mux(inst_complete_no_mem, pc, pc_reg)
  io.commit.dnpc := Mux(inst_complete_no_mem, dnpc, dnpc_reg)
  io.commit.inst := Mux(inst_complete_no_mem, inst, inst_reg)
  io.commit.gpr := rfu.io.out.commit.gpr
  io.commit.csr := csru.io.out.commit
}
