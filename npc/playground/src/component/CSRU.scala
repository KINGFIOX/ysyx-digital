package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import common.HasRegFileParameter
import common.HasCSRParameter

class CSRUInputBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  val raddr    = UInt(NRCSRbits.W) // csr 读取
  val wop       = CSROpType() // csr 写入
  val wen      = Bool()
  val waddr    = UInt(NRCSRbits.W)
  val wdata = UInt(XLEN.W) // rs1_data
}

class CSRUDebugBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  val mstatus   = UInt(XLEN.W)
  val mtvec     = UInt(XLEN.W)
  val mepc      = UInt(XLEN.W)
  val mcause    = UInt(XLEN.W)
  val mcycle    = UInt(XLEN.W)
  val mcycleh   = UInt(XLEN.W)
  val mvendorid = UInt(XLEN.W)
  val marchid   = UInt(XLEN.W)
}

class CSRUOutputBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  val rdata  = UInt(XLEN.W)
  val debug = new CSRUDebugBundle // difftest
}

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
    (MSTATUS.U, mstatus),
    (MTVEC.U, mtvec),
    (MEPC.U, mepc),
    (MCAUSE.U, mcause),
    (MCYCLE.U, mcycle(31, 0)),
    (MCYCLEH.U, mcycle(63, 32)),
    (MVENDORID.U, mvendorid), // mvendorid 地址
    (MARCHID.U, marchid)      // marchid 地址
  )

  // ==================== 读取 CSR ====================
  private val csrRdata = MuxLookup(io.in.raddr, 0.U)(csrReadMap)
  io.out.rdata := csrRdata

  // ==================== 计算写入数据 ====================
  // CSRRW: wdata = rs1
  // CSRRS: wdata = csr | rs1
  private val csrWdata = MuxCase(
    io.in.wdata,
    Seq(
      (io.in.wop === CSROpType.CSR_RW) -> io.in.wdata,
      (io.in.wop === CSROpType.CSR_RS) -> (csrRdata | io.in.wdata)
    )
  )

  // ==================== 写入 CSR ====================
  when(io.in.wen) {
    when(io.in.waddr === MSTATUS.U) { mstatus := csrWdata }
    when(io.in.waddr === MTVEC.U) { mtvec := csrWdata }
    when(io.in.waddr === MEPC.U) { mepc := csrWdata }
    when(io.in.waddr === MCAUSE.U) { mcause := csrWdata }
  }

  // ==================== debug 输出 ====================
  io.out.debug.mstatus := mstatus
  io.out.debug.mtvec := mtvec
  io.out.debug.mepc := mepc
  io.out.debug.mcause := mcause
  io.out.debug.mcycle    := mcycle(31, 0)
  io.out.debug.mcycleh   := mcycle(63, 32)
  io.out.debug.mvendorid := mvendorid
  io.out.debug.marchid   := marchid
}
