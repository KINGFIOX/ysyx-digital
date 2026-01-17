package npc

import chisel3._
import chisel3.util._

import common.{HasCSRParameter, HasCoreParameter, HasRegFileParameter}
import component._
import general.AXI4LiteMasterIO
import general.AXI4LiteParams
import firrtl.options.Stage
import blackbox.ExceptionDpiWrapper

// 1. 组件初始化
// 2. 处理组件的输出信号 + 组合逻辑元件的输入
// 3. 状态机
// 4. 处理组件的输入信号
// 5. commit
class NPCCore(params: AXI4LiteParams) extends Module with HasCoreParameter with HasRegFileParameter {
  val io = IO(new Bundle {
    val step = Input(Bool())
    val commit = Output(new CommitBundle)
    val icache = new AXI4LiteMasterIO(params)
    val dcache = new AXI4LiteMasterIO(params)
  })

  /* ========== exception 与 mcause 的映射 ========== */
  private val ifuExceptionMcauseMap = Seq(
    IFUExceptionType.ifu_INSTRUCTION_ADDRESS_MISALIGNED -> 0.U,
    IFUExceptionType.ifu_INSTRUCTION_ACCESS_FAULT       -> 1.U,
    IFUExceptionType.ifu_INSTRUCTION_PAGE_FAULT         -> 12.U
  )
  private val cuExceptionMcauseMap = Seq(
    CUExceptionType.cu_ILLEGAL_INSTRUCTION  -> 2.U,
    CUExceptionType.cu_BREAKPOINT           -> 3.U,
    CUExceptionType.cu_ECALL_FROM_U_MODE    -> 8.U,
    CUExceptionType.cu_ECALL_FROM_S_MODE    -> 9.U,
    CUExceptionType.cu_ECALL_FROM_M_MODE    -> 11.U
  )
  private val lsuExceptionMcauseMap = Seq(
    MemUExceptionType.mem_LOAD_ADDRESS_MISALIGNED  -> 4.U,
    MemUExceptionType.mem_LOAD_ACCESS_FAULT        -> 5.U,
    MemUExceptionType.mem_STORE_ADDRESS_MISALIGNED -> 6.U,
    MemUExceptionType.mem_STORE_ACCESS_FAULT       -> 7.U,
    MemUExceptionType.mem_LOAD_PAGE_FAULT          -> 13.U,
    MemUExceptionType.mem_STORE_PAGE_FAULT         -> 15.U
  )

  /* ========== 实例化各模块 ========== */
  private val ifu = Module(new IFU(params))
  private val cu = Module(new CU)
  private val igu = Module(new IGU)
  private val rfu = Module(new RFU)
  private val alu = Module(new ALU)
  private val bru = Module(new BRU)
  private val lsu = Module(new LSU(params))

  /* ========== 指令字段提取 ========== */
  private val inst = ifu.io.out.bits.inst
  private val pc = ifu.io.out.bits.pc
  private val snpc = ifu.io.out.bits.pc + 4.U
  private val rd = inst(11, 7)
  private val rs1 = inst(19, 15)
  private val rs2 = inst(24, 20)

  /* ========== 控制单元 ========== */
  cu.io.in.inst := inst

  /* ========== 立即数扩展 ========== */
  igu.io.in.inst_31_7 := inst(InstLen - 1, OpcodeLen) // 只传递 inst[31:7], 不需要 opcode
  igu.io.in.immType := cu.io.out.immType
  private val imm = igu.io.out.imm

  /* ========== 寄存器堆读取 ========== */
  rfu.io.in.rs1_i := rs1
  rfu.io.in.rs2_i := rs2
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

  /* ========== 下地址的计算 ========== */
  private val dnpc = MuxCase(
    snpc, // 默认: PC + 4
    Seq(
      (cu.io.out.npcOp === NPCOpType.NPC_JAL) -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_JALR) -> (aluResult & (~1.U(XLEN.W))),
      (cu.io.out.npcOp === NPCOpType.NPC_BR && brTaken) -> aluResult
    )
  )

  /* ========== 锁存器 ========== */
  private val mem_addr_reg = RegInit(0.U(XLEN.W))
  private val mem_wdata_reg = RegInit(0.U(XLEN.W))
  private val mem_op_reg = RegInit(MemUOpType.mem_X)
  private val rd_i_reg = RegInit(0.U(NRRegbits.W))
  private val rd_v_reg = RegInit(0.U(XLEN.W))
  private val rf_wen_reg = RegInit(false.B)
  private val dnpc_reg = RegInit(0.U(XLEN.W))
  private val pc_reg = RegInit(0.U(XLEN.W))
  private val inst_reg = RegInit(0.U(InstLen.W))

  /* ========== 辅助信号 ========== */
  private val isMem = cu.io.out.memEn

  /* ========== 状态机 ========== */
  object State extends ChiselEnum {
    val idle, ifu_valid_wait, writeback, mem_ready_wait, mem_valid_wait, ifu_ready_wait = Value
  }
  private val state = RegInit(State.idle)
  switch(state) {
    is(State.idle) {
      when(io.step) {
        state := State.ifu_valid_wait
      }
    }
    is(State.ifu_valid_wait) {
      when(ifu.io.out.fire) { // 锁存组合逻辑的结果
        rf_wen_reg := cu.io.out.rfWen
        rd_i_reg := rd
        dnpc_reg := dnpc
        pc_reg := pc
        inst_reg := inst
        when(isMem) {
          state := State.mem_ready_wait
          mem_addr_reg := aluResult
          mem_wdata_reg := rs2Data // store
          mem_op_reg := cu.io.out.memOp
        } .otherwise {
          state := State.writeback
          rd_v_reg := aluResult
        }
      }
    }
    is(State.mem_ready_wait) {
      when(lsu.io.in.fire) {
        state := State.mem_valid_wait
      }
    }
    is(State.mem_valid_wait) {
      when(lsu.io.out.fire) {
        state := State.writeback
        rd_v_reg := lsu.io.out.bits.rdata
        // TODO: exception
      }
    }
    is(State.writeback) {
      state := State.ifu_ready_wait
    }
    is(State.ifu_ready_wait) {
      when(ifu.io.in.fire) {
        state := State.idle
      }
    }
  }

  /* ========== IFU ========== */
  ifu.io.out.ready := (state === State.ifu_valid_wait)
  ifu.io.in.valid := (state === State.ifu_ready_wait)
  ifu.io.in.bits.dnpc := dnpc_reg
  ifu.io.icache <> io.icache
  ifu.io.step := io.step

  /* ========== RFU ========== */
  rfu.io.in.wen := rf_wen_reg && (state === State.writeback)
  rfu.io.in.wdata := rd_v_reg
  rfu.io.in.rd_i := rd_i_reg

  /* ========== LSU ========== */
  lsu.io.in.valid := (state === State.mem_ready_wait)
  lsu.io.in.bits.op := mem_op_reg
  lsu.io.in.bits.addr := mem_addr_reg
  lsu.io.in.bits.wdata := mem_wdata_reg
  lsu.io.out.ready := (state === State.mem_valid_wait)
  lsu.io.dcache <> io.dcache

  /* ========== commit ========== */
  io.commit.valid := (state === State.writeback)
  io.commit.pc := pc_reg
  io.commit.dnpc := dnpc_reg
  io.commit.inst := inst_reg
  io.commit.gpr := rfu.io.out.commit.gpr
  io.commit.csr := DontCare // TODO: 添加 CSRU 后填写

}
