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
  private val exceptionDpi = Module(new ExceptionDpiWrapper)

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

  /* ========== 异常收集与 mcause 计算 ========== */
  // IFU 异常 -> mcause 映射
  private val ifuException = ifu.io.out.bits.exception
  private val ifuHasException = ifuException =/= IFUExceptionType.ifu_X
  private val ifuMcause = MuxLookup(ifuException.asUInt, 0.U)(Seq(
    IFUExceptionType.ifu_INSTRUCTION_ADDRESS_MISALIGNED.asUInt -> 0.U,
    IFUExceptionType.ifu_INSTRUCTION_ACCESS_FAULT.asUInt       -> 1.U,
    IFUExceptionType.ifu_INSTRUCTION_PAGE_FAULT.asUInt         -> 12.U
  ))

  // CU 异常 -> mcause 映射
  private val cuException = cu.io.out.exception
  private val cuHasException = cuException =/= CUExceptionType.cu_X && cuException =/= CUExceptionType.cu_MRET  // mret 不是异常
  private val cuMcause = MuxLookup(cuException.asUInt, 0.U)(Seq(
    CUExceptionType.cu_ILLEGAL_INSTRUCTION.asUInt  -> 2.U,
    CUExceptionType.cu_BREAKPOINT.asUInt           -> 3.U,
    CUExceptionType.cu_ECALL_FROM_U_MODE.asUInt    -> 8.U,
    CUExceptionType.cu_ECALL_FROM_S_MODE.asUInt    -> 9.U,
    CUExceptionType.cu_ECALL_FROM_M_MODE.asUInt    -> 11.U
  ))

  // LSU 异常 -> mcause 映射（在 mem_wait 状态使用）
  private val lsuException = lsu.io.out.bits.exception
  private val lsuHasException = lsuException =/= MemUExceptionType.mem_X
  private val lsuMcause = MuxLookup(lsuException.asUInt, 0.U)(Seq(
    MemUExceptionType.mem_LOAD_ADDRESS_MISALIGNED.asUInt  -> 4.U,
    MemUExceptionType.mem_LOAD_ACCESS_FAULT.asUInt        -> 5.U,
    MemUExceptionType.mem_STORE_ADDRESS_MISALIGNED.asUInt -> 6.U,
    MemUExceptionType.mem_STORE_ACCESS_FAULT.asUInt       -> 7.U,
    MemUExceptionType.mem_LOAD_PAGE_FAULT.asUInt          -> 13.U,
    MemUExceptionType.mem_STORE_PAGE_FAULT.asUInt         -> 15.U
  ))

  // 异常优先级: IFU > CU > LSU
  // 非内存指令: 只检查 IFU 和 CU 异常
  // 内存指令: 还需要检查 LSU 异常
  private val hasException_noMem = ifuHasException || cuHasException
  private val mcause_noMem = Mux(ifuHasException, ifuMcause, cuMcause)

  // 是否可处理的异常 (ecall 跳转到 mtvec)
  private val isEcall = cuException === CUExceptionType.cu_ECALL_FROM_M_MODE ||
                        cuException === CUExceptionType.cu_ECALL_FROM_S_MODE ||
                        cuException === CUExceptionType.cu_ECALL_FROM_U_MODE
  private val isMret = cuException === CUExceptionType.cu_MRET

  // 不可处理的异常 (需要调用 DPI)
  private val isUnhandledException_noMem = hasException_noMem && !isEcall

  /* ========== CSR 单元 ========== */
  csru.io.in.addr := csrAddr
  csru.io.in.op := cu.io.out.csrOp
  // wen, hasException, isMret 的门控在状态机之后设置
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
  // 异常优先级最高，发生异常时跳转到 mtvec
  private val dnpc = MuxCase(
    snpc, // 默认: PC + 4
    Seq(
      // 不可处理的异常: 由 DPI 处理，dnpc 不重要（会停机）
      isUnhandledException_noMem -> snpc,
      // 可处理的异常: ecall 跳转到 mtvec
      isEcall -> csru.io.out.mtvec,
      // mret: 返回到 mepc
      isMret -> csru.io.out.mepc,
      // 正常指令
      (cu.io.out.npcOp === NPCOpType.NPC_JAL) -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_JALR) -> (aluResult & (~1.U(XLEN.W))),
      (cu.io.out.npcOp === NPCOpType.NPC_BR && brTaken) -> aluResult
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
  // 异常相关锁存
  private val hasException_reg = RegInit(false.B)
  private val mcause_reg = RegInit(0.U(XLEN.W))
  private val isEcall_reg = RegInit(false.B)
  private val isMret_reg = RegInit(false.B)
  private val a0_reg = RegInit(0.U(XLEN.W))

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
        // 锁存异常信息（用于内存指令在 mem_wait 状态使用）
        hasException_reg := hasException_noMem
        mcause_reg := mcause_noMem
        isEcall_reg := isEcall
        isMret_reg := isMret
        a0_reg := rfu.io.out.commit.gpr(10) // 锁存 a0 寄存器的值，用于 ebreak 停机
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

  /* ========== 异常处理（内存指令需要合并 LSU 异常）========== */
  // 内存指令的最终异常状态（合并 IFU/CU 和 LSU 的异常）
  // 优先级: IFU > CU > LSU
  private val hasException_mem = hasException_reg || lsuHasException
  private val mcause_mem = Mux(hasException_reg, mcause_reg, lsuMcause)

  // 最终的异常状态（根据指令类型选择）
  private val finalHasException = Mux(inst_complete_no_mem, hasException_noMem, hasException_mem)
  private val finalMcause = Mux(inst_complete_no_mem, mcause_noMem, mcause_mem)
  private val finalIsEcall = Mux(inst_complete_no_mem, isEcall, isEcall_reg)
  private val finalIsMret = Mux(inst_complete_no_mem, isMret, isMret_reg)

  // 不可处理的异常（需要调用 DPI）
  private val isUnhandledException = finalHasException && !finalIsEcall

  /* ========== 寄存器堆/CSR 写入门控 ========== */
  // 只有在指令执行完成且无不可处理异常时才写入寄存器堆
  rfu.io.in.wen := rfWenFromCU && inst_complete && !isUnhandledException
  // CSR 写入也需要门控
  csru.io.in.wen := cu.io.out.csrWen && inst_complete && !isUnhandledException
  // 异常处理：ecall 时写入 mepc 和 mcause
  csru.io.in.hasException := finalIsEcall && inst_complete
  csru.io.in.mcause := finalMcause
  csru.io.in.isMret := finalIsMret && inst_complete

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

  /* ========== 异常 DPI 调用 ========== */
  // 不可处理的异常调用 DPI（包括 breakpoint、非法指令、访存异常等）
  // AM 停机约定: li a0, xxx; ebreak - a0=0 表示成功，a0!=0 表示失败
  exceptionDpi.io.en_i := inst_complete && isUnhandledException
  exceptionDpi.io.pc_i := Mux(inst_complete_no_mem, pc, pc_reg)
  exceptionDpi.io.mcause_i := finalMcause
  exceptionDpi.io.a0_i := Mux(inst_complete_no_mem, rfu.io.out.commit.gpr(10), a0_reg)

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
