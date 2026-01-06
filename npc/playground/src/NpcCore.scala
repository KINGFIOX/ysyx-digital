package npc

import chisel3._
import chisel3.util._

import mem.{DpiPmemRead, DpiPmemWrite}

/**
 * 用来给 Verilator 暴露提交信息, 便于在 C++ 侧采样做差分测试
 */
class CommitBundle extends Bundle {
  val valid  = Output(Bool()) // commit valid
  val pc     = Output(UInt(32.W)) // s->pc
  val nextPc = Output(UInt(32.W)) // s->dnpc
  val inst   = Output(UInt(32.W))
  val gpr    = Output(Vec(32, UInt(32.W)))
}


class NpcCoreTop extends Module {
  val io = IO(new Bundle {
    val step   = Input(Bool())        // 单步触发 (宿主侧拉高一个周期)
    val commit = new CommitBundle     // 提交信息, 供 difftest 使用
  })

  val pcReg = RegInit("h80000000".U(32.W)) /* RESET_ */
  val gpr   = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  gpr(0) := 0.U

  // ========== DPI-C 内存接口 ==========
  val pmemRead  = Module(new DpiPmemRead)
  val pmemWrite = Module(new DpiPmemWrite)

  // ========== 状态机 ==========
  val sIdle :: sExec :: sCommit :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // 提交信息寄存器
  val commitValid = RegInit(false.B)
  val commitPc    = RegInit(0.U(32.W))
  val commitInst  = RegInit(0.U(32.W))
  val nextPc      = RegInit(0.U(32.W))

  // 写回信息寄存器 (在 sExec 阶段锁存)
  val wbDataReg = RegInit(0.U(32.W))
  val wbRdReg   = RegInit(0.U(5.W))
  val wbEnReg   = RegInit(false.B)

  // 执行阶段使能信号
  val execEn = state === sExec

  // ========== 取指 ==========
  pmemRead.io.en   := execEn
  pmemRead.io.addr := pcReg
  pmemRead.io.len  := 4.U  // 32位指令
  val inst = pmemRead.io.data

  // ========== 译码 ==========
  // 指令字段提取
  val opcode = inst(6, 0)
  val rd     = inst(11, 7)
  val funct3 = inst(14, 12)
  val rs1    = inst(19, 15)
  val rs2    = inst(24, 20)

  // 立即数提取
  // I-type: imm[11:0] = inst[31:20]
  val immI = Cat(Fill(20, inst(31)), inst(31, 20))
  // S-type: imm[11:0] = {inst[31:25], inst[11:7]}
  val immS = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7))
  // U-type: imm[31:12] = inst[31:12], imm[11:0] = 0
  val immU = Cat(inst(31, 12), 0.U(12.W))

  // 操作码定义
  val OP_LOAD   = "b0000011".U  // I-type load
  val OP_STORE  = "b0100011".U  // S-type store
  val OP_AUIPC  = "b0010111".U  // U-type auipc

  // 寄存器读取
  val rs1Data = Mux(rs1 === 0.U, 0.U, gpr(rs1))
  val rs2Data = Mux(rs2 === 0.U, 0.U, gpr(rs2))

  // ========== 执行 ==========
  // AUIPC: rd = pc + (imm << 12) = pc + immU
  val auipcResult = pcReg + immU

  // Load/Store 地址计算
  val loadAddr  = rs1Data + immI
  val storeAddr = rs1Data + immS

  // 内存读取 (用于 Load) - 仅在执行阶段且是 LOAD 指令时使能
  val loadMemRead = Module(new DpiPmemRead)
  val loadEn = execEn && (opcode === OP_LOAD)
  loadMemRead.io.en   := loadEn
  loadMemRead.io.addr := loadAddr
  loadMemRead.io.len  := 1.U  // LBU 读取1字节

  // LBU: 零扩展字节
  val lbuResult = Cat(0.U(24.W), loadMemRead.io.data(7, 0))

  // 内存写入控制 - 仅在执行阶段且是 STORE 指令时使能
  val storeEn = execEn && (opcode === OP_STORE) && (funct3 === "b000".U)
  pmemWrite.io.en   := storeEn
  pmemWrite.io.addr := storeAddr
  pmemWrite.io.len  := 1.U  // SB 写入1字节
  pmemWrite.io.data := rs2Data(7, 0)  // 只取低8位

  // 写回数据选择 (组合逻辑, 在 sExec 阶段有效)
  val wbData = MuxCase(0.U, Seq(
    (opcode === OP_AUIPC) -> auipcResult,
    (opcode === OP_LOAD)  -> lbuResult
  ))

  // 是否写回寄存器
  val wbEn = (opcode === OP_AUIPC) || (opcode === OP_LOAD && funct3 === "b100".U)

  switch(state) {
    is(sIdle) {
      commitValid := false.B
      when(io.step) {
        state := sExec
      }
    }
    is(sExec) {
      // 记录提交信息
      commitPc   := pcReg
      commitInst := inst
      nextPc     := pcReg + 4.U  // 顺序执行

      // 锁存写回信息
      wbDataReg := wbData
      wbRdReg   := rd
      wbEnReg   := wbEn

      state := sCommit
    }
    is(sCommit) {
      // 写回寄存器 (排除 x0)
      when(wbEnReg && wbRdReg =/= 0.U) {
        gpr(wbRdReg) := wbDataReg
      }

      // 更新 PC
      pcReg       := nextPc
      commitValid := true.B
      state       := sIdle
    }
  }

  // ========== 输出 ==========
  io.commit.valid  := commitValid
  io.commit.pc     := commitPc
  io.commit.nextPc := nextPc
  io.commit.inst   := commitInst
  io.commit.gpr    := gpr
}
