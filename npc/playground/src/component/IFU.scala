package component

import chisel3._
import chisel3.util._
import common.HasCoreParameter
import blackbox.DpiPmemRead

class IFOutputBundle extends Bundle with HasCoreParameter {
  val inst = Output(UInt(InstLen.W)) //
  val snpc = Output(UInt(XLEN.W)) // which is pc + 4
  val pc   = Output(UInt(XLEN.W)) // the pc of the instruction
}

class IFInputBundle extends Bundle with HasCoreParameter {
  val dnpc = Output(UInt(XLEN.W))
}

class IFU extends Module with HasCoreParameter {
  val io = IO(new Bundle {
    val out = new IFOutputBundle
    val in  = Flipped(new IFInputBundle)
  })

  private val pc_ = RegInit(UInt(XLEN.W), "h8000_0000".U)

  // read from irom
  private val pmemRead = Module(new DpiPmemRead)
  pmemRead.io.en   := true.B // TODO: 后面 SoC 的时候, 这里要改(暂时不动)
  pmemRead.io.addr := pc_
  pmemRead.io.len  := 4.U

  io.out.inst := pmemRead.io.data
  io.out.pc   := pc_
  io.out.snpc := pc_ + 4.U

  pc_ := io.in.dnpc
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
