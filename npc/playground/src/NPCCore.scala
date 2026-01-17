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
// 2. 处理组件的输出信号 (时序) + 组合逻辑元件的输入
// 3. 状态机
// 4. 处理组件的输入信号 (时序)
// 5. commit
class NPCCore(params: AXI4LiteParams) extends Module with HasCoreParameter with HasRegFileParameter with HasCSRParameter {
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
  private val csru = Module(new CSRU)
  private val alu = Module(new ALU)
  private val bru = Module(new BRU)
  private val lsu = Module(new LSU(params))
  private val excu = Module(new EXCU)

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

  /* ========== CSR 读取 ========== */
  csru.io.in.raddr := imm
  private val csr_read = csru.io.out.rdata

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
  bru.io.in.op := cu.io.out.bruOp
  private val brTaken = bru.io.out.br_flag

  /* ========== 下地址的计算 ========== */
  private val dnpc = MuxCase(
    snpc, // 默认: PC + 4
    Seq(
      (cu.io.out.npcOp === NPCOpType.NPC_JAL) -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_JALR) -> (aluResult & (~1.U(XLEN.W))),
      (cu.io.out.npcOp === NPCOpType.NPC_BR && brTaken) -> aluResult,
      (cu.io.out.npcOp === NPCOpType.NPC_MRET) -> csru.io.out.rdata
    )
  )

  /* ========== 非 Mem, 写回值计算 ========== */
  private val rd_v = MuxCase(0.U, Seq(
              (cu.io.out.wbSel === WBSel.WB_ALU) -> aluResult,
              (cu.io.out.wbSel === WBSel.WB_PC4) -> snpc,
            ) )


  /* ========== 锁存器 ========== */
  private val mem_addr_reg = RegInit(0.U(XLEN.W))
  private val mem_wdata_reg = RegInit(0.U(XLEN.W))
  private val mem_op_reg = Reg(MemUOpType())
  private val mem_en_reg = RegInit(false.B)
  private val rd_i_reg = RegInit(0.U(NRRegbits.W))
  private val rd_v_reg = RegInit(0.U(XLEN.W))
  private val rf_wen_reg = RegInit(false.B)
  private val dnpc_reg = RegInit(0.U(XLEN.W))
  private val pc_reg = RegInit(0.U(XLEN.W))
  private val inst_reg = RegInit(0.U(InstLen.W))
  private val csr_wen_reg = RegInit(false.B)
  private val csr_wdata_reg = RegInit(0.U(XLEN.W))
  private val csr_wop_reg = Reg(CSROpType())
  private val csr_waddr_reg = RegInit(0.U(XLEN.W))

  /* ========== 辅助信号 ========== */
  private val isMem = cu.io.out.memEn

  /* ========== 状态机 ========== */
  object State extends ChiselEnum {
    val idle, ifu_valid_wait, writeback, ex1, ex2, mem_ready_wait, mem_valid_wait, ifu_ready_wait = Value
  }
  private val state = RegInit(State.idle)
  switch(state) {
    is(State.idle) {
      when(io.step) {
        state := State.ifu_valid_wait
        // reset some write enable status
        mem_en_reg := false.B
        rf_wen_reg := false.B
        csr_wen_reg := false.B
      }
    }
    is(State.ifu_valid_wait) {
      when(ifu.io.out.fire) { // 锁存组合逻辑的结果
        /* rf */ // load, alu
        rf_wen_reg := cu.io.out.rfWen
        rd_i_reg := rd
        /* dnpc */
        dnpc_reg := dnpc
        /* pc */
        pc_reg := pc
        /* IR */
        inst_reg := inst
        when(excu.io.out.valid) {
          state := State.ex1
          csr_wdata_reg := excu.io.out.bits.mcause
          csr_wen_reg := true.B
          csr_wop_reg := CSROpType.CSR_RW
          csr_waddr_reg := MCAUSE.U
        } .elsewhen(isMem) {
          state := State.mem_ready_wait
          mem_op_reg := cu.io.out.memOp
          mem_en_reg := cu.io.out.memEn
          mem_addr_reg := aluResult
          mem_wdata_reg := rs2Data
        } .otherwise {
          state := State.writeback
          /* rf */ // alu
          rd_v_reg := rd_v
          /* csr */ // csr
          csr_wen_reg := cu.io.out.csrWen
          csr_wdata_reg := rs1Data
          csr_wop_reg := cu.io.out.csrOp
          csr_waddr_reg := imm
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
        when(excu.io.out.valid) {
          state := State.ex1
          csr_wdata_reg := excu.io.out.bits.mcause
          csr_wen_reg := true.B
          csr_wop_reg := CSROpType.CSR_RW
          csr_waddr_reg := MCAUSE.U
        } .otherwise {
          state := State.writeback
          rd_v_reg := lsu.io.out.bits.rdata
        }
      }
    }
    is(State.writeback) {
      state := State.ifu_ready_wait
    }
    is(State.ex1) {
      state := State.ex2
      csr_wdata_reg := pc_reg
      csr_wen_reg := true.B
      csr_wop_reg := CSROpType.CSR_RW
      csr_waddr_reg := MEPC.U
    }
    is(State.ex2) {
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

  /* ========== CSRU ========== */
  csru.io.in.waddr := csr_waddr_reg
  csru.io.in.wdata := csr_wdata_reg
  csru.io.in.wen := csr_wen_reg && ((state === State.writeback) || (state === State.ex1) || (state === State.ex2))
  csru.io.in.wop := csr_wop_reg

  /* ========== LSU ========== */
  lsu.io.in.valid := (state === State.mem_ready_wait)
  lsu.io.in.bits.op := mem_op_reg
  lsu.io.in.bits.en := mem_en_reg
  lsu.io.in.bits.addr := mem_addr_reg
  lsu.io.in.bits.wdata := mem_wdata_reg
  lsu.io.out.ready := (state === State.mem_valid_wait)
  lsu.io.dcache <> io.dcache

  /* ========== EXCU ========== */
  excu.io.in.ifu := ifu.io.out.bits.exception
  excu.io.in.ifuEn := ifu.io.out.fire && ifu.io.out.bits.exceptionEn
  excu.io.in.cu := cu.io.out.exception
  excu.io.in.cuEn := ifu.io.out.fire && cu.io.out.exceptionEn
  excu.io.in.lsu := lsu.io.out.bits.exception
  excu.io.in.lsuEn := lsu.io.out.fire && lsu.io.out.bits.exceptionEn
  excu.io.in.pc := pc_reg
  excu.io.in.a0 := rfu.io.out.commit.gpr(10)

  /* ========== commit ========== */
  io.commit.valid := (state === State.writeback)
  io.commit.pc := pc_reg
  io.commit.dnpc := dnpc_reg
  io.commit.inst := inst_reg
  io.commit.gpr := rfu.io.out.commit.gpr
  io.commit.csr := DontCare // TODO: 添加 CSRU 后填写

}
