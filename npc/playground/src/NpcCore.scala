package npc

import chisel3._
import chisel3.util._

import blackbox.{DpiPmemRead, DpiPmemWrite}

/** 用来给 Verilator 暴露提交信息, 便于在 C++ 侧采样做差分测试
  */
class CommitBundle extends Bundle {
  val valid  = Output(Bool())     // commit valid
  val pc     = Output(UInt(32.W)) // s->pc
  val nextPc = Output(UInt(32.W)) // s->dnpc
  val inst   = Output(UInt(32.W))
  val gpr    = Output(Vec(32, UInt(32.W)))
}

class NpcCoreTop extends Module {
  val io = IO(new Bundle {
    val step   = Input(Bool())    // 单步触发 (宿主侧拉高一个周期)
    val commit = new CommitBundle // 提交信息, 供 difftest 使用
  })

  val pcReg = RegInit("h80000000".U(32.W)) /* RESET_ */
  val gpr   = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  gpr(0) := 0.U

  // ========== DPI-C 内存接口 ==========
  val pmemRead  = Module(new DpiPmemRead)
  val pmemWrite = Module(new DpiPmemWrite)

  // ========== 取指 (组合逻辑) ==========
  // 只在 step 有效时才进行取指, 避免 reset/idle 期间的无效内存访问
  pmemRead.io.en   := io.step
  pmemRead.io.addr := pcReg
  pmemRead.io.len  := 4.U // 32位指令
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
  val OP_LOAD  = "b0000011".U // I-type load
  val OP_STORE = "b0100011".U // S-type store
  val OP_AUIPC = "b0010111".U // U-type auipc

  // 寄存器读取
  val rs1Data = Mux(rs1 === 0.U, 0.U, gpr(rs1))
  val rs2Data = Mux(rs2 === 0.U, 0.U, gpr(rs2))

  // ========== 执行 ==========
  // AUIPC: rd = pc + (imm << 12) = pc + immU
  val auipcResult = pcReg + immU

  // Load/Store 地址计算
  val loadAddr  = rs1Data + immI
  val storeAddr = rs1Data + immS

  // 内存读取 (用于 Load) - 仅在 step 有效且是 Load 指令时使能
  val loadMemRead = Module(new DpiPmemRead)
  loadMemRead.io.en   := io.step && (opcode === OP_LOAD)
  loadMemRead.io.addr := loadAddr
  loadMemRead.io.len  := 1.U // LBU 读取1字节

  // LBU: 零扩展字节
  val lbuResult = Cat(0.U(24.W), loadMemRead.io.data(7, 0))

  // 内存写入控制 - 仅在 step 有效时执行写入
  val storeEn = io.step && (opcode === OP_STORE) && (funct3 === "b000".U)
  pmemWrite.io.en   := storeEn
  pmemWrite.io.addr := storeAddr
  pmemWrite.io.len  := 1.U           // SB 写入1字节
  pmemWrite.io.data := rs2Data(7, 0) // 只取低8位

  // 写回数据选择
  val wbData = MuxCase(
    0.U,
    Seq(
      (opcode === OP_AUIPC) -> auipcResult,
      (opcode === OP_LOAD)  -> lbuResult
    )
  )

  // 是否写回寄存器
  val wbEn = (opcode === OP_AUIPC) || (opcode === OP_LOAD && funct3 === "b100".U)

  // 下一条指令的 PC (顺序执行)
  val nextPc = pcReg + 4.U

  // ========== 单周期执行: 当 step 有效时完成所有操作 ==========
  when(io.step) {
    // 写回寄存器 (排除 x0)
    when(wbEn && rd =/= 0.U) {
      gpr(rd) := wbData
    }
    // 更新 PC
    pcReg := nextPc
  }

  // ========== 输出 ==========
  io.commit.valid  := io.step // 单周期CPU, 同一周期的 step, 同一周期 commit
  io.commit.pc     := pcReg   // commit 的这条指令, 对应的PC
  io.commit.nextPc := nextPc  //
  io.commit.inst   := inst    //
  io.commit.gpr    := gpr
}
