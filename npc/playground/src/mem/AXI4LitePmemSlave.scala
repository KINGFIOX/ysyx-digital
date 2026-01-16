package component

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR
import common.HasCoreParameter
import general.{AXI4LiteSlaveIO, AXI4LiteParams, AXI4LiteResp}
import blackbox.{PmemReadDpiWrapper, PmemWriteDpiWrapper}

class AXI4LitePmemSlave(params: AXI4LiteParams) extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val axi = new AXI4LiteSlaveIO(params)
  })

  // 共用延迟计数器（读写不会同时进行）
  // 使用 Chisel 自带的 LFSR 生成随机延迟
  val counter = RegInit(0.U(8.W))

  // ========== 读操作状态机 ==========
  object ReadState extends ChiselEnum {
    val idle, reading, done = Value
  }

  val read_state = RegInit(ReadState.idle)
  val read_addr_reg = RegInit(0.U(params.addrWidth.W))
  val read_data_reg = RegInit(0.U(params.dataWidth.W))

  // AR 通道握手
  io.axi.ar.ready := (read_state === ReadState.idle)

  // R 通道输出
  io.axi.r.valid := (read_state === ReadState.done)
  io.axi.r.bits.data := read_data_reg
  io.axi.r.bits.resp := AXI4LiteResp.OKAY

  // DPI 读取模块
  val pmemReadDpiWrapper = Module(new PmemReadDpiWrapper)
  pmemReadDpiWrapper.io.clock := clock
  pmemReadDpiWrapper.io.en_i := false.B
  pmemReadDpiWrapper.io.addr_i := read_addr_reg
  pmemReadDpiWrapper.io.len_i := 4.U

  switch(read_state) {
    is(ReadState.idle) {
      when(io.axi.ar.fire) {
        read_addr_reg := io.axi.ar.bits.addr
        counter := LFSR(4)
        read_state := ReadState.reading
      }
    }
    is(ReadState.reading) {
      pmemReadDpiWrapper.io.en_i := true.B
      pmemReadDpiWrapper.io.addr_i := read_addr_reg

      when(counter === 0.U) {
        read_data_reg := pmemReadDpiWrapper.io.data_o
        read_state := ReadState.done
      }.otherwise {
        counter := counter - 1.U
      }
    }
    is(ReadState.done) {
      when(io.axi.r.fire) {
        read_state := ReadState.idle
      }
    }
  }

  // ========== 写操作状态机 ==========
  object WriteState extends ChiselEnum {
    val idle, writing, done = Value
  }

  val write_state = RegInit(WriteState.idle)

  // AW 和 W 通道可能不同顺序到达，需要分别记录
  val aw_received = RegInit(false.B)
  val w_received  = RegInit(false.B)

  val write_addr_reg = RegInit(0.U(params.addrWidth.W))
  val write_data_reg = RegInit(0.U(params.dataWidth.W))
  val write_strb_reg = RegInit(0.U(params.strbWidth.W))

  // DPI 写入模块
  val pmemWriteDpiWrapper = Module(new PmemWriteDpiWrapper)
  pmemWriteDpiWrapper.io.clock  := clock
  pmemWriteDpiWrapper.io.en_i   := false.B
  pmemWriteDpiWrapper.io.addr_i := write_addr_reg
  pmemWriteDpiWrapper.io.strb_i := write_strb_reg
  pmemWriteDpiWrapper.io.data_i := write_data_reg

  // AW 通道握手：仅在 idle 状态且未收到 AW 时接受
  io.axi.aw.ready := (write_state === WriteState.idle) && !aw_received

  // W 通道握手：仅在 idle 状态且未收到 W 时接受
  io.axi.w.ready := (write_state === WriteState.idle) && !w_received

  // B 通道输出
  io.axi.b.valid     := (write_state === WriteState.done)
  io.axi.b.bits.resp := AXI4LiteResp.OKAY

  switch(write_state) {
    is(WriteState.idle) {
      when(io.axi.aw.fire) {
        write_addr_reg := io.axi.aw.bits.addr
        aw_received := true.B
      }

      when(io.axi.w.fire) {
        write_data_reg := io.axi.w.bits.data
        write_strb_reg := io.axi.w.bits.strb
        w_received := true.B
      }

      val aw_done = aw_received || io.axi.aw.fire
      val w_done  = w_received || io.axi.w.fire

      when(aw_done && w_done) {
        when(io.axi.aw.fire) {
          write_addr_reg := io.axi.aw.bits.addr
        }
        when(io.axi.w.fire) {
          write_data_reg := io.axi.w.bits.data
          write_strb_reg := io.axi.w.bits.strb
        }

        counter := LFSR(4)  // 随机延迟
        // counter := 1.U          // 固定延迟
        write_state := WriteState.writing
        aw_received := false.B
        w_received  := false.B
      }
    }
    is(WriteState.writing) {
      pmemWriteDpiWrapper.io.en_i := true.B

      when(counter === 0.U) {
        write_state := WriteState.done
      }.otherwise {
        counter := counter - 1.U
      }
    }
    is(WriteState.done) {
      when(io.axi.b.fire) {
        write_state := WriteState.idle
      }
    }
  }
}
