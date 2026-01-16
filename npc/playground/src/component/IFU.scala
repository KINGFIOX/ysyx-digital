package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import general.AXI4LiteMasterIO
import general.AXI4LiteParams
import general.AXI4LiteResp

class IFOutputBundle extends Bundle with HasCoreParameter {
  val inst = Output(UInt(InstLen.W)) //
  val pc   = Output(UInt(XLEN.W))    // the pc of the instruction
  val isValid = Output(Bool()) // this is a valid instruct
}

class IFInputBundle extends Bundle with HasCoreParameter {
  val dnpc = Output(UInt(XLEN.W)) // 这个是计算出来的下地址
}

class IFU extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val out = DecoupledIO(new IFOutputBundle)
    val in  = Flipped(DecoupledIO(new IFInputBundle))
    val step = Input(Bool())
    val icache = new AXI4LiteMasterIO(new AXI4LiteParams)
  })

  // dummy write channel
  io.icache.b.ready := true.B   // AXI4-Lite B channel
  io.icache.aw.bits.addr := 0.U   // AXI4-Lite AW channel
  io.icache.aw.bits.prot := 0.U
  io.icache.aw.valid := true.B
  io.icache.w.bits.data := 0.U   // AXI4-Lite W channel
  io.icache.w.valid := true.B
  io.icache.w.bits.strb := 0.U

  private val pc_reg = RegInit("h8000_0000".U(XLEN.W)) // pc_reg
  private val inst_reg = RegInit(0.U(InstLen.W)) // IR
  private val resp_reg = RegInit(AXI4LiteResp.OKAY)

  object State extends ChiselEnum {
    // avail_wait: next stage is allowin
    val idle, ar_wait, r_wait, allowin_wait, done_wait = Value
  }
  private val state = RegInit(State.idle)

  // bus
  io.icache.ar.valid := (state === State.ar_wait)
  io.icache.ar.bits.prot := Cat(true.B/*instr*/, false.B/*secure*/, true.B/*priviledge*/)
  io.icache.r.ready := (state === State.r_wait)
  io.icache.ar.bits.addr := pc_reg

  // pipeline
  io.out.valid := (state === State.allowin_wait)
  io.out.bits.inst := inst_reg
  io.out.bits.pc := pc_reg
  io.out.bits.isValid := (state === State.allowin_wait) && (resp_reg === AXI4LiteResp.OKAY)
  io.in.ready := (state === State.done_wait)

  switch(state) {
    is(State.idle) {
      when(io.step) {
        state := State.ar_wait
      }
    }
    is(State.ar_wait) {
      when(io.icache.ar.fire) {
        state := State.r_wait
      }
    }
    is(State.r_wait) {
      when(io.icache.r.fire) {
        state := State.allowin_wait
        inst_reg := io.icache.r.bits.data
        resp_reg := io.icache.r.bits.resp
      }
    }
    is(State.allowin_wait) {
      when(io.out.fire) {
        state := State.done_wait
      }
    }
    is(State.done_wait) {
      when(io.in.fire) {
        state := State.idle
        pc_reg := io.in.bits.dnpc
      }
    }
  }

}

object IFU extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  _root_.circt.stage.ChiselStage.emitSystemVerilogFile(new IFU, args, firtoolOptions)
}
