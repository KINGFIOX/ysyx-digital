package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import general.{AXI4LiteSlaveIO, AXI4LiteParams, AXI4LiteResp}
import blackbox.{DpiPmemRead, DpiPmemWrite}

class AXI4LitePmemSlave extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val axi = new AXI4LiteSlaveIO(new AXI4LiteParams)
  })

  // ========== 读操作模块和状态机 ==========
  object ReadState extends ChiselEnum {
    val idle, reading, data_ready = Value
  }

  private val read_state = RegInit(ReadState.idle)
  private val read_addr_reg = RegInit(0.U(32.W))
  private val read_data_reg = RegInit(0.U(32.W))
  private val read_resp_reg = RegInit(AXI4LiteResp.OKAY)
  private val read_en = RegInit(false.B)

  // AR 通道握手：只在空闲状态时接受新请求
  io.axi.ar.ready := (read_state === ReadState.idle)

  // 启动 DpiPmemRead
  private val pmemRead = Module(new DpiPmemRead)
  pmemRead.io.clock := clock
  pmemRead.io.en := read_en
  pmemRead.io.addr := read_addr_reg
  pmemRead.io.len := 4.U // AXI4-Lite 固定 32 位数据宽度

  // 读数据通道 (R)
  io.axi.r.valid := (read_state === ReadState.data_ready)
  io.axi.r.bits.data := read_data_reg
  io.axi.r.bits.resp := read_resp_reg

  // 读操作状态机
  switch(read_state) {
    is(ReadState.idle) {
      when(io.axi.ar.fire) {
        read_state := ReadState.reading
        read_addr_reg := io.axi.ar.bits.addr
        read_en := true.B
      }
    }
    is(ReadState.reading) {
      // DpiPmemRead 在时钟上升沿采样，数据在下一个周期有效
      read_data_reg := pmemRead.io.data
      read_state := ReadState.data_ready
      read_resp_reg := AXI4LiteResp.OKAY
      read_en := false.B // 只读取一个周期
    }
    is(ReadState.data_ready) {
      when(io.axi.r.fire) {
        read_state := ReadState.idle
      }
    }
  }

  // ========== 写操作模块和状态机(dummy) ==========
  io.axi.b.bits.resp := AXI4LiteResp.OKAY
  io.axi.b.valid := true.B
  io.axi.aw.ready := true.B
  io.axi.w.ready := true.B

}
