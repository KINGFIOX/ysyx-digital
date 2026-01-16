package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import general.AXI4LiteMasterIO
import general.AXI4LiteParams

class IFOutputBundle extends Bundle with HasCoreParameter {
  val inst = Output(UInt(InstLen.W)) //
  val pc   = Output(UInt(XLEN.W))    // the pc of the instruction
}

class IFInputBundle extends Bundle with HasCoreParameter {
  val dnpc = Output(UInt(XLEN.W))
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
  //

  object State extends ChiselEnum {
    val s_idle, s_ar_wait, s_r_wait = Value
  }
  private val state = RegInit(State.s_idle)

  private val pc_reg = RegInit("h8000_0000".U(XLEN.W))

  // 保存接收到的指令数据
  private val inst_reg = Reg(UInt(InstLen.W))
  // 什么时候数据无效
  // 1. 复位后
  // 2. 数据被读取了以后
  // 这是为了防止溢出(深度为1的缓冲区溢出)
  private val data_valid = RegInit(false.B)


  // AXI4-Lite AR channel (Read Address)
  io.icache.ar.valid := (state === State.s_idle) && io.step && !data_valid /*数据无效时*/
  io.icache.ar.bits.addr := pc_reg
  io.icache.ar.bits.prot := Cat(true.B/*instruction*/, true.B/*secure*/, true.B/*priviledge*/)
  // AXI4-Lite R channel (Read Data) - ready when waiting for read data and not holding data
  io.icache.r.ready := (state === State.s_r_wait) && !data_valid

  // Input ready: 当处于空闲状态时可以接受新的 dnpc
  io.in.ready := (state === State.s_idle) && !data_valid

  // Output valid: 当有有效数据时输出有效
  io.out.valid := data_valid
  io.out.bits.inst := inst_reg
  io.out.bits.pc   := pc_reg

  // State transitions
  switch(state) {
    is(State.s_idle) {
      when(io.in.fire) {
        pc_reg := io.in.bits.dnpc
      }
      // 开始新的取指请求
      when(io.step && io.icache.ar.ready && !data_valid) {
        state := State.s_ar_wait
      }
    }
    is(State.s_ar_wait) {
      when(io.icache.ar.fire) {
        state := State.s_r_wait
      }
    }
    is(State.s_r_wait) {
      when(io.icache.r.fire && !data_valid) {
        // 保存接收到的数据
        inst_reg := io.icache.r.bits.data
        data_valid := true.B

        // 更新 PC: 顺序执行，PC + 4
        // 注意：分支跳转的 PC 更新在 s_idle 状态通过 io.in 处理
        pc_reg := pc_reg + 4.U

        // 回到空闲状态，等待输出被接受
        state := State.s_idle
      }
    }
  }

  // 当输出被接受时，清除数据有效标志
  when(io.out.fire) {
    data_valid := false.B
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
