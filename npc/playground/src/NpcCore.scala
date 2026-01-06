package npc

import chisel3._
import chisel3.util._

// 用来给 Verilator 暴露提交信息, 便于在 C++ 侧采样做差分
class CommitBundle extends Bundle {
  val valid  = Output(Bool())
  val pc     = Output(UInt(32.W))
  val nextPc = Output(UInt(32.W))
  val inst   = Output(UInt(32.W))
  val gpr    = Output(Vec(32, UInt(32.W)))
}

// 占位核: 仅自增 PC, 便于先打通 Verilator 接口
class NpcCoreTop extends Module {
  val io = IO(new Bundle {
    val step   = Input(Bool())        // 单步触发 (宿主侧拉高一个周期)
    val commit = new CommitBundle
  })

  val pcReg   = RegInit("h80000000".U(32.W))
  val instReg = RegInit(0.U(32.W))

  when(io.step) {
    pcReg   := pcReg + 4.U
    instReg := instReg + 1.U
  }

  io.commit.valid  := io.step
  io.commit.pc     := pcReg
  io.commit.nextPc := pcReg + 4.U
  io.commit.inst   := instReg
  io.commit.gpr.foreach(_ := 0.U)
}

