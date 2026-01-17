package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import blackbox.PmemReadDpiWrapper
import general.AXI4LiteMasterIO
import general.AXI4LiteParams
import general.AXI4LiteResp

object MemUOpType extends ChiselEnum {
  val mem_LB, mem_LH, mem_LW, mem_LBU, mem_LHU, mem_SB, mem_SH, mem_SW = Value
}

// mcause:
// 4. load address misaligned
// 5. load access fault
// 6. store address misaligned
// 7. store access fault
// 12. instruction page fault
// 13. load page fault
// 15. store page fault
object MemUExceptionType extends ChiselEnum {
  val mem_LOAD_ADDRESS_MISALIGNED, mem_LOAD_ACCESS_FAULT, // load fault
    mem_STORE_ADDRESS_MISALIGNED, mem_STORE_ACCESS_FAULT, // store fault
    mem_LOAD_PAGE_FAULT, mem_STORE_PAGE_FAULT // page fault
    = Value
}

class MEMUInputBundle extends Bundle with HasCoreParameter {
  val op    = MemUOpType()
  val wdata = UInt(XLEN.W)
  val addr  = UInt(XLEN.W)
  val en = Bool()
}

class MEMUOutputBundle extends Bundle with HasCoreParameter {
  val rdata = UInt(XLEN.W)
  val exception = MemUExceptionType(); val exceptionEn = Bool()
}

/** 符号扩展 */
object SignExt {
  def apply(data: UInt, width: Int = 32): UInt = {
    val signBit = data(data.getWidth - 1)
    Cat(Fill(width - data.getWidth, signBit), data)
  }
}

/** 零扩展 */
object ZeroExt {
  def apply(data: UInt, width: Int = 32): UInt = {
    Cat(0.U((width - data.getWidth).W), data)
  }
}

class LSU(params: AXI4LiteParams) extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val in     = Flipped(DecoupledIO(new MEMUInputBundle))
    val out    = DecoupledIO(new MEMUOutputBundle)
    val dcache = new AXI4LiteMasterIO(params)
  })

  // ========== 操作类型判断 ==========
  private val isLoad  = io.in.bits.op.isOneOf(MemUOpType.mem_LB, MemUOpType.mem_LH, MemUOpType.mem_LW, MemUOpType.mem_LBU, MemUOpType.mem_LHU)
  private val isStore = io.in.bits.op.isOneOf(MemUOpType.mem_SB, MemUOpType.mem_SH, MemUOpType.mem_SW)

  // ========== 地址对齐检查 ==========
  private val addr = io.in.bits.addr
  private val addrAlign2 = addr(0) === 0.U
  private val addrAlign4 = addr(1, 0) === 0.U

  private val loadMisaligned = MuxLookup(io.in.bits.op.asUInt, false.B)(Seq(
    MemUOpType.mem_LH.asUInt  -> !addrAlign2,
    MemUOpType.mem_LHU.asUInt -> !addrAlign2,
    MemUOpType.mem_LW.asUInt  -> !addrAlign4
  ))

  private val storeMisaligned = MuxLookup(io.in.bits.op.asUInt, false.B)(Seq(
    MemUOpType.mem_SH.asUInt -> !addrAlign2,
    MemUOpType.mem_SW.asUInt -> !addrAlign4
  ))

  // ========== 寄存器保存请求信息 ==========
  private val op_reg    = RegInit(MemUOpType.mem_LB)
  private val addr_reg  = RegInit(0.U(XLEN.W))
  private val wdata_reg = RegInit(0.U(XLEN.W))
  private val exception_reg = Reg(MemUExceptionType())
  private val exceptionEn_reg = RegInit(false.B)

  // ========== 读状态机 ==========
  object ReadState extends ChiselEnum {
    val idle, ar_wait, r_wait, done = Value
  }
  private val read_state = RegInit(ReadState.idle)

  // ========== 写状态机 ==========
  object WriteState extends ChiselEnum {
    val idle, aw_w_wait, b_wait, done = Value
  }
  private val write_state = RegInit(WriteState.idle)

  // AW 和 W 通道可以并行发送，分别跟踪握手状态
  private val aw_sent = RegInit(false.B)
  private val w_sent  = RegInit(false.B)

  // ========== 读状态机逻辑 ==========
  switch(read_state) {
    is(ReadState.idle) {
      when(io.in.fire && isLoad && io.in.bits.en) {
        op_reg   := io.in.bits.op
        addr_reg := io.in.bits.addr
        when(loadMisaligned) {
          exception_reg := MemUExceptionType.mem_LOAD_ADDRESS_MISALIGNED
          exceptionEn_reg := true.B
          read_state := ReadState.done
        }.otherwise {
          read_state := ReadState.ar_wait
        }
      }
    }
    is(ReadState.ar_wait) {
      when(io.dcache.ar.fire) {
        read_state := ReadState.r_wait
      }
    }
    is(ReadState.r_wait) {
      when(io.dcache.r.fire) {
        when(io.dcache.r.bits.resp =/= AXI4LiteResp.OKAY) {
          exception_reg := MemUExceptionType.mem_LOAD_ACCESS_FAULT
          exceptionEn_reg := true.B
        }
        read_state := ReadState.done
      }
    }
    is(ReadState.done) {
      when(io.out.fire) {
        read_state := ReadState.idle
      }
    }
  }

  // ========== 写状态机逻辑 ==========
  switch(write_state) {
    is(WriteState.idle) {
      when(io.in.fire && isStore && io.in.bits.en) {
        op_reg    := io.in.bits.op
        addr_reg  := io.in.bits.addr
        wdata_reg := io.in.bits.wdata
        when(storeMisaligned) {
          exception_reg := MemUExceptionType.mem_STORE_ADDRESS_MISALIGNED
          exceptionEn_reg := true.B
          write_state := WriteState.done
        }.otherwise {
          write_state := WriteState.aw_w_wait
          aw_sent := false.B
          w_sent  := false.B
          exceptionEn_reg := false.B // reset
        }
      }
    }
    is(WriteState.aw_w_wait) {
      // AW 和 W 通道并行发送，分别跟踪握手状态
      when(io.dcache.aw.fire) {
        aw_sent := true.B
      }
      when(io.dcache.w.fire) {
        w_sent := true.B
      }

      // 两个通道都完成后进入 b_wait
      val aw_done = aw_sent || io.dcache.aw.fire
      val w_done  = w_sent || io.dcache.w.fire
      when(aw_done && w_done) {
        write_state := WriteState.b_wait
      }
    }
    is(WriteState.b_wait) {
      when(io.dcache.b.fire) {
        when(io.dcache.b.bits.resp =/= AXI4LiteResp.OKAY) {
          exception_reg := MemUExceptionType.mem_STORE_ACCESS_FAULT
          exceptionEn_reg := true.B
        }
        write_state := WriteState.done
      }
    }
    is(WriteState.done) {
      when(io.out.fire) {
        write_state := WriteState.idle
      }
    }
  }

  // ========== AXI4-Lite 读通道信号 ==========
  io.dcache.ar.valid     := (read_state === ReadState.ar_wait)
  io.dcache.ar.bits.addr := Cat(addr_reg(XLEN - 1, 2), 0.U(2.W))  // 字对齐地址
  io.dcache.ar.bits.prot := Cat(false.B/*instr*/, false.B/*secure*/, true.B/*priviledge*/)

  io.dcache.r.ready := (read_state === ReadState.r_wait)

  // ========== 读数据处理（符号扩展/零扩展）==========
  private val rdata_raw = io.dcache.r.bits.data
  private val rdata_reg = RegInit(0.U(XLEN.W))

  // 根据地址低位选择字节/半字
  private val byte_offset = addr_reg(1, 0)
  private val rdata_byte = (rdata_raw >> (byte_offset << 3.U))(7, 0)
  private val rdata_half = Mux(addr_reg(1), rdata_raw(31, 16), rdata_raw(15, 0))

  // 读数据存入寄存器
  when(io.dcache.r.fire) {
    rdata_reg := MuxLookup(op_reg.asUInt, rdata_raw)(Seq(
      MemUOpType.mem_LB.asUInt  -> SignExt(rdata_byte, XLEN),
      MemUOpType.mem_LBU.asUInt -> ZeroExt(rdata_byte, XLEN),
      MemUOpType.mem_LH.asUInt  -> SignExt(rdata_half, XLEN),
      MemUOpType.mem_LHU.asUInt -> ZeroExt(rdata_half, XLEN),
      MemUOpType.mem_LW.asUInt  -> rdata_raw
    ))
  }

  // ========== AXI4-Lite 写通道信号 ==========
  // AW 和 W 通道并行发送，仅在未发送时置 valid
  io.dcache.aw.valid     := (write_state === WriteState.aw_w_wait) && !aw_sent
  io.dcache.aw.bits.addr := Cat(addr_reg(XLEN - 1, 2), 0.U(2.W))  // 字对齐地址
  io.dcache.aw.bits.prot := 0.U

  io.dcache.w.valid := (write_state === WriteState.aw_w_wait) && !w_sent

  // 根据操作类型生成写数据和写掩码
  private val wstrb = MuxLookup(op_reg.asUInt, "b1111".U(4.W))(Seq(
    MemUOpType.mem_SB.asUInt -> ("b0001".U << byte_offset),
    MemUOpType.mem_SH.asUInt -> Mux(addr_reg(1), "b1100".U(4.W), "b0011".U(4.W)),
    MemUOpType.mem_SW.asUInt -> "b1111".U(4.W)
  ))

  private val wdata_shifted = MuxLookup(op_reg.asUInt, wdata_reg)(Seq(
    MemUOpType.mem_SB.asUInt -> (wdata_reg(7, 0) << (byte_offset << 3.U)),
    MemUOpType.mem_SH.asUInt -> Mux(addr_reg(1), wdata_reg(15, 0) << 16.U, wdata_reg(15, 0)),
    MemUOpType.mem_SW.asUInt -> wdata_reg
  ))

  io.dcache.w.bits.data := wdata_shifted
  io.dcache.w.bits.strb := wstrb

  io.dcache.b.ready := (write_state === WriteState.b_wait)

  // ========== 输出信号 ==========

  io.in.ready := (read_state === ReadState.idle) && (write_state === WriteState.idle)

  private val isReadDone  = (read_state === ReadState.done)
  private val isWriteDone = (write_state === WriteState.done)

  io.out.valid          := isReadDone || isWriteDone
  io.out.bits.rdata     := Mux(isReadDone, rdata_reg, 0.U)
  io.out.bits.exception := exception_reg
  io.out.bits.exceptionEn := exceptionEn_reg
}
