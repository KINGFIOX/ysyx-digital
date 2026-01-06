package npc

import chisel3._
import chisel3.util._

/**
 * 用来给 Verilator 暴露提交信息, 便于在 C++ 侧采样做差分测试
 */
class CommitBundle extends Bundle {
  val valid  = Output(Bool())        // 指令提交有效
  val pc     = Output(UInt(32.W))    // 当前指令 PC
  val nextPc = Output(UInt(32.W))    // 下一条指令 PC (分支/跳转后的实际目标)
  val inst   = Output(UInt(32.W))    // 当前指令编码
  val gpr    = Output(Vec(32, UInt(32.W)))  // 通用寄存器组快照
}

/**
 * NPC Core Top - 单周期 RISC-V 处理器核心 (占位版本)
 *
 * 当前为占位实现, 仅用于验证 Verilator 集成流程。
 * 后续可逐步添加:
 *   - 取指单元 (IFU)
 *   - 译码单元 (IDU)
 *   - 执行单元 (EXU)
 *   - 写回单元 (WBU)
 *   - 寄存器堆
 *   - 控制单元
 *
 * 时序:
 *   1. 宿主侧拉高 io.step 一个周期
 *   2. 核心执行一条指令
 *   3. 拉高 io.commit.valid, 暴露提交信息
 *   4. 宿主侧采样后拉低 io.step
 */
class NpcCoreTop extends Module {
  val io = IO(new Bundle {
    val step   = Input(Bool())        // 单步触发 (宿主侧拉高一个周期)
    val commit = new CommitBundle     // 提交信息, 供 difftest 使用
  })

  // ========== 架构状态 ==========
  val pcReg = RegInit("h80000000".U(32.W))  // 程序计数器, 复位到 RESET_VECTOR
  val gpr   = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))  // 通用寄存器组

  // x0 硬连线为 0
  gpr(0) := 0.U

  // ========== 占位: 指令计数器 (后续替换为真实取指) ==========
  val instCnt = RegInit(0.U(32.W))

  // ========== 状态机 ==========
  // 简单三状态机: Idle -> Exec -> Commit -> Idle
  val sIdle :: sExec :: sCommit :: Nil = Enum(3)
  val state = RegInit(sIdle)

  // 提交信息寄存器
  val commitValid = RegInit(false.B)
  val commitPc    = RegInit(0.U(32.W))
  val commitInst  = RegInit(0.U(32.W))
  val nextPc      = RegInit(0.U(32.W))

  switch(state) {
    is(sIdle) {
      commitValid := false.B
      when(io.step) {
        state := sExec
      }
    }
    is(sExec) {
      // ========== 占位执行逻辑 ==========
      // TODO: 这里后续替换为真实的取指-译码-执行流程
      //   - 通过 DPI-C 调用 pmem_read 取指令
      //   - 译码指令
      //   - 执行指令
      //   - 计算下一条 PC
      commitPc   := pcReg
      commitInst := instCnt      // 占位: 用计数器代替真实指令
      nextPc     := pcReg + 4.U  // 占位: 顺序执行
      state      := sCommit
    }
    is(sCommit) {
      // 提交: 更新架构状态
      pcReg       := nextPc
      instCnt     := instCnt + 1.U
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
