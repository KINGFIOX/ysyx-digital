package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import common.HasRegFileParameter
import common.HasCSRParameter

class CSRUInputBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  // CSR 地址 (inst[31:20])
  val addr     = Output(UInt(NRCSRbits.W))
  // CSR 操作
  val op       = Output(CSROpType())
  val wen      = Output(Bool())
  val rs1_data = Output(UInt(XLEN.W))
  // ECALL/MRET 控制
  val isEcall  = Output(Bool())
  val isMret   = Output(Bool())
  val pc       = Output(UInt(XLEN.W)) // 用于保存到 mepc
}

class CSRUCommitBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  val mstatus   = Output(UInt(XLEN.W))
  val mtvec     = Output(UInt(XLEN.W))
  val mepc      = Output(UInt(XLEN.W))
  val mcause    = Output(UInt(XLEN.W))
  val mcycle    = Output(UInt(XLEN.W))
  val mcycleh   = Output(UInt(XLEN.W))
  val mvendorid = Output(UInt(XLEN.W))
  val marchid   = Output(UInt(XLEN.W))
}

class CSRUOutputBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  // CSR 读取数据 (用于写回 rd)
  val rdata  = Output(UInt(XLEN.W))
  // 异常处理
  val mtvec  = Output(UInt(XLEN.W)) // ECALL 跳转地址
  val mepc   = Output(UInt(XLEN.W)) // MRET 返回地址
  // 用于 commit/difftest
  val commit = Output(new CSRUCommitBundle)
}

/// IF  ->  ID  ->  EX  ->  MEM  ->  WB
///                读取      计算     写回
class CSRU extends Module with HasCoreParameter with HasCSRParameter {
  val io = IO(new Bundle {
    val in  = Flipped(new CSRUInputBundle)
    val out = new CSRUOutputBundle
  })

  // ==================== CSR 寄存器定义 ====================
  // 可读写寄存器
  private val mstatus = RegInit(0x1800.U(XLEN.W)) // TODO: 写入时某些位无效果
  private val mtvec   = RegInit(0.U(XLEN.W))
  private val mepc    = RegInit(0.U(XLEN.W))
  private val mcause  = RegInit(0.U(XLEN.W))

  // 只读寄存器
  private val mvendorid = 0x79737978.U(XLEN.W) // "ysyx" in ASCII
  private val marchid   = 26010003.U(XLEN.W)

  // 64位周期计数器
  private val mcycle = RegInit(0.U(64.W))
  mcycle := mcycle + 1.U

  // ==================== 读取映射表 ====================
  private val csrReadMap = Seq(
    (MSTATUS.U,   mstatus),
    (MTVEC.U,     mtvec),
    (MEPC.U,      mepc),
    (MCAUSE.U,    mcause),
    (MCYCLE.U,    mcycle(31, 0)),
    (MCYCLEH.U,   mcycle(63, 32)),
    (MVENDORID.U,    mvendorid), // mvendorid 地址
    (MARCHID.U,    marchid)    // marchid 地址
  )

  // ==================== 读取 CSR ====================
  private val csrRdata = MuxLookup(io.in.addr, 0.U)(csrReadMap)
  io.out.rdata := csrRdata

  // ==================== 计算写入数据 ====================
  // CSRRW: wdata = rs1
  // CSRRS: wdata = csr | rs1
  private val csrWdata = MuxCase(io.in.rs1_data, Seq(
    (io.in.op === CSROpType.CSR_RW) -> io.in.rs1_data,
    (io.in.op === CSROpType.CSR_RS) -> (csrRdata | io.in.rs1_data)
  ))

  // ==================== 写入 CSR ====================
  when(io.in.wen) {
    when(io.in.addr === MSTATUS.U) { mstatus := csrWdata }
    when(io.in.addr === MTVEC.U)   { mtvec   := csrWdata }
    when(io.in.addr === MEPC.U)    { mepc    := csrWdata }
    when(io.in.addr === MCAUSE.U)  { mcause  := csrWdata }
  }

  // ==================== ECALL 异常处理 ====================
  // ECALL: mepc = pc, mcause = 11 (Environment call from M-mode)
  when(io.in.isEcall) {
    mepc   := io.in.pc
    mcause := 11.U // Environment call from M-mode
  }

  // ==================== 异常跳转地址输出 ====================
  io.out.mtvec := mtvec // ECALL 跳转到 mtvec
  io.out.mepc  := mepc  // MRET 返回到 mepc

  // ==================== Commit 输出 ====================
  io.out.commit.mstatus   := mstatus
  io.out.commit.mtvec     := mtvec
  io.out.commit.mepc      := mepc
  io.out.commit.mcause    := mcause
  io.out.commit.mcycle    := mcycle(31, 0)
  io.out.commit.mcycleh   := mcycle(63, 32)
  io.out.commit.mvendorid := mvendorid
  io.out.commit.marchid   := marchid
}
