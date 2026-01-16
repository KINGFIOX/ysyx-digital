package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import general.{AXI4LiteSlaveIO, AXI4LiteParams, AXI4LiteResp}
import blackbox.PmemReadDpiWrapper

class AXI4LitePmemSlave extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val axi = new AXI4LiteSlaveIO(new AXI4LiteParams)
  })

  // ========== 读操作状态机 ==========
  object ReadState extends ChiselEnum {
    val idle, data_ready = Value
  }

  // dummy read
  io.axi.r.bits.data := 0.U
  io.axi.r.bits.resp := AXI4LiteResp.OKAY
  io.axi.r.valid := true.B
  io.axi.ar.ready := true.B

  // ========== 写操作模块和状态机(dummy) ==========
  io.axi.b.bits.resp := AXI4LiteResp.OKAY
  io.axi.b.valid := true.B
  io.axi.aw.ready := true.B
  io.axi.w.ready := true.B

}
