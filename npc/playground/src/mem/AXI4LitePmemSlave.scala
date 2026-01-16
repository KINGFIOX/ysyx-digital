package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import general.{AXI4LiteSlaveIO, AXI4LiteParams, AXI4LiteResp}
import blackbox.PmemReadDpiWrapper
import general.LFSRRand

class AXI4LitePmemSlave extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val axi = new AXI4LiteSlaveIO(new AXI4LiteParams)
  })

  val lfsr = Module(new LFSRRand(1))

  // ========== 读操作状态机 ==========
  object ReadState extends ChiselEnum {
    val idle, reading, done = Value
  }

  val counter = RegInit(0.U(8.W)) // same width as lfsr

  val read_prot_reg = RegInit(0.U(3.W))
  val read_data_reg = RegInit(0.U(32.W))

  val state = RegInit(ReadState.idle)

  io.axi.ar.ready := (state === ReadState.idle)
  io.axi.r.valid := (state === ReadState.done)
  io.axi.r.bits.data := read_data_reg
  io.axi.r.bits.resp := AXI4LiteResp.OKAY

  val pmemReadDpiWrapper = Module(new PmemReadDpiWrapper)
  pmemReadDpiWrapper.io.addr_i := 0.U
  pmemReadDpiWrapper.io.len_i := 4.U
  pmemReadDpiWrapper.io.clock := clock
  pmemReadDpiWrapper.io.en_i := false.B

  // counter := Mux( state === ReadState.idle, lfsr.io.out, counter - 1.U )
  counter := Mux( state === ReadState.idle, 1.U, counter - 1.U )


  switch(state) {
    is(ReadState.idle) {
      when(io.axi.ar.fire) {
        read_prot_reg := io.axi.ar.bits.prot
        pmemReadDpiWrapper.io.en_i := true.B
        pmemReadDpiWrapper.io.addr_i := io.axi.ar.bits.addr
        state := ReadState.reading
      }
    }
    is(ReadState.reading) {
      when(counter === 0.U) {
        read_data_reg := pmemReadDpiWrapper.io.data_o
        state := ReadState.done
      } .otherwise {
        counter := counter - 1.U
      }
    }
    is(ReadState.done) {
      when(io.axi.r.fire) {
        state := ReadState.idle
      }
    }
  }

  // ========== 写操作模块和状态机(dummy) ==========
  io.axi.b.bits.resp := AXI4LiteResp.OKAY
  io.axi.b.valid := true.B
  io.axi.aw.ready := true.B
  io.axi.w.ready := true.B

}
