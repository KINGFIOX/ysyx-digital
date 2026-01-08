package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import common.HasRegFileParameter
import common.HasCSRParameter

object CSRUOpType extends ChiselEnum {
  val OP1_RS1, OP1_PC, OP1_ZERO = Value
}

class CSRUOutputBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  // EX阶段读取CSR
  val rdata = Output(UInt(XLEN.W))
}

class CSRUInputBundle extends Bundle with HasCoreParameter with HasCSRParameter {
  // EX阶段读取CSR -> MEM阶段计算(置位、清零)
  val csr_addr = Output(UInt(NRCSRbits.W))
  val csr_op   = Output(CSRUOpType())
  val rs1_data = Output(UInt(XLEN.W))
  // WB阶段写回CSR
  val wen      = Output(Bool())
  val waddr    = Output(UInt(NRCSRbits.W))
  val wdata    = Output(UInt(XLEN.W))
  //
}


/// IF  ->  ID  ->  EX  ->  MEM  ->  WB
///                读取      计算     写回
class CSRU extends Module with HasCoreParameter with HasCSRParameter {
  val io = IO(new Bundle {
    val in  = Flipped(new CSRUInputBundle)
    val out = new CSRUOutputBundle
  })

}
