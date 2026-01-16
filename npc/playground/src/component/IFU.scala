package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import general.AXI4LiteMasterIO
import general.AXI4LiteParams
import general.AXI4LiteResp

class IFUOutputBundle extends Bundle with HasCoreParameter {
  val inst = UInt(InstLen.W) //
  val pc   = UInt(XLEN.W)    // the pc of the instruction
  val isValid = Bool() // this is a valid instruct, used for pipeline flush control
  val exception = IFUExceptionType()
}

class IFInputBundle extends Bundle with HasCoreParameter {
  val dnpc = UInt(XLEN.W) // 这个是计算出来的下地址
}

// mcause:
// 0. instruction address misaligned
// 1. instruction access fault
// 12. instruction page fault
object IFUExceptionType extends ChiselEnum {
  val ifu_X,
    ifu_INSTRUCTION_ADDRESS_MISALIGNED, ifu_INSTRUCTION_ACCESS_FAULT,
    ifu_INSTRUCTION_PAGE_FAULT
    = Value
}

class IFU(params: AXI4LiteParams) extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val out = DecoupledIO(new IFUOutputBundle)
    val in  = Flipped(DecoupledIO(new IFInputBundle))
    val step = Input(Bool())
    val icache = new AXI4LiteMasterIO(params)
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
  private val exception_reg = RegInit(IFUExceptionType.ifu_X)

  object State extends ChiselEnum {
    // avail_wait: next stage is allowin -> inst_wait
    val idle, ar_wait, r_wait, allowin_wait, done_wait = Value
  }
  private val state = RegInit(State.idle)

  // 检查指令地址是否对齐 (4 字节对齐)
  private val pcMisaligned = pc_reg(1, 0) =/= 0.U

  // bus
  io.icache.ar.valid := (state === State.ar_wait) && !pcMisaligned
  io.icache.ar.bits.prot := Cat(true.B/*instr*/, false.B/*secure*/, true.B/*priviledge*/)
  io.icache.r.ready := (state === State.r_wait)
  io.icache.ar.bits.addr := pc_reg

  // pipeline
  io.out.valid := (state === State.allowin_wait)
  io.out.bits.inst := inst_reg
  io.out.bits.pc := pc_reg
  io.out.bits.isValid := (state === State.allowin_wait) // 后续用来冲刷流水线的时候用
  io.out.bits.exception := exception_reg
  io.in.ready := (state === State.done_wait)  // 在 done_wait 状态接收 dnpc

  switch(state) {
    is(State.idle) {
      when(io.step) {
        state := State.ar_wait
        exception_reg := IFUExceptionType.ifu_X // 重置异常状态
      }
    }
    is(State.ar_wait) {
      when(pcMisaligned) {
        // 指令地址未对齐异常，不发起总线请求，直接进入 allowin_wait
        state := State.allowin_wait
        exception_reg := IFUExceptionType.ifu_INSTRUCTION_ADDRESS_MISALIGNED
        inst_reg := 0.U // 无效指令
      }.elsewhen(io.icache.ar.fire) {
        state := State.r_wait
      }
    }
    is(State.r_wait) {
      when(io.icache.r.fire) {
        state := State.allowin_wait
        inst_reg := io.icache.r.bits.data
        when(io.icache.r.bits.resp =/= AXI4LiteResp.OKAY) {
          exception_reg := IFUExceptionType.ifu_INSTRUCTION_ACCESS_FAULT
        }
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
        exception_reg := IFUExceptionType.ifu_X // 重置异常状态
      }
    }
  }

}
