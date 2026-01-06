package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import common.HasRegFileParameter

/** @brief
  *   write back 的时候, 会有一个多路复用器
  */
object WB_sel extends ChiselEnum {
  val wbsel_X = Value
}

class RFUOutputBundle extends Bundle with HasCoreParameter {
  val rs1_v = Output(UInt(XLEN.W))
  val rs2_v = Output(UInt(XLEN.W))
}

class RFUInputBundle extends Bundle with HasRegFileParameter with HasCoreParameter {
  val rs1_i = Output(UInt(NRRegbits.W))
  val rs2_i = Output(UInt(NRRegbits.W))
  val rd_i  = Output(UInt(NRRegbits.W))
  // write
  val wdata = Input(UInt(XLEN.W))
  val wen   = Output(Bool())
}

/** @brief
  *   寄存器堆, 但是其实不是一个 chisel 的 Module
  */
class RFU extends Module with HasCoreParameter with HasRegFileParameter {
  val io          = IO(new Bundle {
    val in  = Flipped(new RFUInputBundle)
    val out = Flipped(new RFUOutputBundle)
  })
  private val rf_ = Mem(NRReg, UInt(XLEN.W))

  io.out.rs1_v := Mux(io.in.rs1_i === 0.U, 0.U, rf_(io.in.rs1_i))
  io.out.rs2_v := Mux(io.in.rs2_i === 0.U, 0.U, rf_(io.in.rs2_i))

  when(io.in.wen && (io.in.rd_i =/= 0.U)) {
    rf_(io.in.rd_i) := io.in.wdata
  }
}
