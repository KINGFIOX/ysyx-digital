package general

import chisel3._
import chisel3.util._

// ============================================================================
// AXI4-Lite Bundle Definitions
// ============================================================================

case class AXI4LiteParams(
  addrWidth: Int = 32,
  dataWidth: Int = 32
) {
  val strbWidth = dataWidth / 8
}

// AW Channel (Write Address)
class AXI4LiteAW(p: AXI4LiteParams) extends Bundle {
  val addr = UInt(p.addrWidth.W)
  val prot = UInt(3.W)
}

// W Channel (Write Data)
class AXI4LiteW(p: AXI4LiteParams) extends Bundle {
  val data = UInt(p.dataWidth.W)
  val strb = UInt(p.strbWidth.W) // 掩码
}

// B Channel (Write Response)
class AXI4LiteB(p: AXI4LiteParams) extends Bundle {
  val resp = UInt(2.W)
}

// AR Channel (Read Address)
class AXI4LiteAR(p: AXI4LiteParams) extends Bundle {
  val addr = UInt(p.addrWidth.W)
  val prot = UInt(3.W)
}

// R Channel (Read Data)
class AXI4LiteR(p: AXI4LiteParams) extends Bundle {
  val data = UInt(p.dataWidth.W)
  val resp = UInt(2.W)
}

// AXI4-Lite Master Interface (from master's perspective)
class AXI4LiteMasterIO(p: AXI4LiteParams) extends Bundle {
  val aw = Decoupled(new AXI4LiteAW(p))
  val w  = Decoupled(new AXI4LiteW(p))
  val b  = Flipped(Decoupled(new AXI4LiteB(p)))
  val ar = Decoupled(new AXI4LiteAR(p))
  val r  = Flipped(Decoupled(new AXI4LiteR(p)))
}

// AXI4-Lite Slave Interface (from slave's perspective)
class AXI4LiteSlaveIO(p: AXI4LiteParams) extends Bundle {
  val aw = Flipped(Decoupled(new AXI4LiteAW(p)))
  val w  = Flipped(Decoupled(new AXI4LiteW(p)))
  val b  = Decoupled(new AXI4LiteB(p))
  val ar = Flipped(Decoupled(new AXI4LiteAR(p)))
  val r  = Decoupled(new AXI4LiteR(p))
}

// ============================================================================
// AXI4-Lite XBar Parameters
// ============================================================================

case class AXI4LiteXBarParams(
  axi: AXI4LiteParams = AXI4LiteParams(),
  numMasters: Int = 1, // default
  numSlaves: Int = 1,
  // Address mapping: Seq of (baseAddr, size) for each slave
  addrMap: Seq[(BigInt, BigInt)] = Seq((BigInt(0), BigInt(0x10000)))
) {
  require(addrMap.length == numSlaves, "Address map must have entry for each slave")
  val slaveIdW  = log2Ceil(numSlaves max 2)
  val masterIdW = log2Ceil(numMasters max 2)
}

// ============================================================================
// Internal messages with routing ID
// ============================================================================

// AW with master ID for response routing
class AXI4LiteAWRouted(p: AXI4LiteXBarParams) extends Bundle {
  val aw  = new AXI4LiteAW(p.axi)
  val src = UInt(p.masterIdW.W)
}

// AR with master ID for response routing
class AXI4LiteARRouted(p: AXI4LiteXBarParams) extends Bundle {
  val ar  = new AXI4LiteAR(p.axi)
  val src = UInt(p.masterIdW.W)
}

// B with master ID for response routing
class AXI4LiteBRouted(p: AXI4LiteXBarParams) extends Bundle {
  val b   = new AXI4LiteB(p.axi)
  val dst = UInt(p.masterIdW.W)
}

// R with master ID for response routing
class AXI4LiteRRouted(p: AXI4LiteXBarParams) extends Bundle {
  val r   = new AXI4LiteR(p.axi)
  val dst = UInt(p.masterIdW.W)
}

// W with slave ID for routing (since W follows AW)
class AXI4LiteWRouted(p: AXI4LiteXBarParams) extends Bundle {
  val w   = new AXI4LiteW(p.axi)
  val dst = UInt(p.slaveIdW.W)
  val src = UInt(p.masterIdW.W)
}

// ============================================================================
// AXI4-Lite XBar Implementation
// ============================================================================

class AXI4LiteXBar(val p: AXI4LiteXBarParams) extends Module {
  val io = IO(new Bundle {
    val masters = Vec(p.numMasters, Flipped(new AXI4LiteMasterIO(p.axi)))
    val slaves  = Vec(p.numSlaves, Flipped(new AXI4LiteSlaveIO(p.axi)))
  })

  // Address decoder: returns slave index for given address
  def addrDecode(addr: UInt): UInt = {
    val slaveIdx = WireDefault(0.U(p.slaveIdW.W))
    for ((mapping, idx) <- p.addrMap.zipWithIndex) {
      val (base, size) = mapping
      when(addr >= base.U && addr < (base + size).U) {
        slaveIdx := idx.U
      }
    }
    slaveIdx
  }

  // ========================================================================
  // Write Address Channel (AW): Master -> Slave
  // ========================================================================

  // Track which slave each master's write is going to (for W channel routing)
  val awPending = Seq.fill(p.numMasters)(RegInit(false.B))
  val awTarget  = Seq.fill(p.numMasters)(RegInit(0.U(p.slaveIdW.W)))

  // AW arbiters: one per slave
  val awArbs = Seq.fill(p.numSlaves)(Module(new RRArbiter(new AXI4LiteAWRouted(p), p.numMasters)))

  for (m <- 0 until p.numMasters) { // 主 <-> 仲裁器
    val targetSlave = addrDecode(io.masters(m).aw.bits.addr)

    // Update pending write tracking
    when(io.masters(m).aw.fire) {
      awPending(m) := true.B
      awTarget(m)  := targetSlave
    }
    when(io.masters(m).w.fire) {
      awPending(m) := false.B
    }

    // Master ready when target slave's arbiter is ready
    io.masters(m).aw.ready := VecInit(awArbs.map(_.io.in(m).ready))(targetSlave)
  }

  for (s <- 0 until p.numSlaves) { // 仲裁器 <-> 从
    awArbs(s).io.in.zipWithIndex.foreach { case (arbIn, m) =>
      val targetSlave = addrDecode(io.masters(m).aw.bits.addr)
      arbIn.bits.aw  := io.masters(m).aw.bits
      arbIn.bits.src := m.U
      arbIn.valid    := io.masters(m).aw.valid && (targetSlave === s.U)
    }
    io.slaves(s).aw.valid := awArbs(s).io.out.valid
    io.slaves(s).aw.bits  := awArbs(s).io.out.bits.aw
    awArbs(s).io.out.ready := io.slaves(s).aw.ready
  }

  // Track master ID for B response routing (per slave)
  val bReturnId = Seq.fill(p.numSlaves)(RegInit(0.U(p.masterIdW.W)))
  for (s <- 0 until p.numSlaves) {
    when(awArbs(s).io.out.fire) {
      bReturnId(s) := awArbs(s).io.out.bits.src
    }
  }

  // ========================================================================
  // Write Data Channel (W): Master -> Slave
  // ========================================================================

  val wArbs = Seq.fill(p.numSlaves)(Module(new RRArbiter(new AXI4LiteW(p.axi), p.numMasters)))

  for (m <- 0 until p.numMasters) {
    // W follows AW, use stored target
    val targetSlave = Mux(awPending(m), awTarget(m), addrDecode(io.masters(m).aw.bits.addr))
    io.masters(m).w.ready := VecInit(wArbs.map(_.io.in(m).ready))(targetSlave) && awPending(m)
  }

  for (s <- 0 until p.numSlaves) {
    wArbs(s).io.in.zipWithIndex.foreach { case (arbIn, m) =>
      arbIn.bits  := io.masters(m).w.bits
      arbIn.valid := io.masters(m).w.valid && awPending(m) && (awTarget(m) === s.U)
    }
    io.slaves(s).w <> wArbs(s).io.out
  }

  // ========================================================================
  // Write Response Channel (B): Slave -> Master
  // ========================================================================

  val bArbs = Seq.fill(p.numMasters)(Module(new RRArbiter(new AXI4LiteB(p.axi), p.numSlaves)))

  for (s <- 0 until p.numSlaves) {
    val targetMaster = bReturnId(s)
    io.slaves(s).b.ready := VecInit(bArbs.map(_.io.in(s).ready))(targetMaster)
  }

  for (m <- 0 until p.numMasters) {
    bArbs(m).io.in.zipWithIndex.foreach { case (arbIn, s) =>
      arbIn.bits  := io.slaves(s).b.bits
      arbIn.valid := io.slaves(s).b.valid && (bReturnId(s) === m.U)
    }
    io.masters(m).b <> bArbs(m).io.out
  }

  // ========================================================================
  // Read Address Channel (AR): Master -> Slave
  // ========================================================================

  val arArbs = Seq.fill(p.numSlaves)(Module(new RRArbiter(new AXI4LiteARRouted(p), p.numMasters)))

  for (m <- 0 until p.numMasters) {
    val targetSlave = addrDecode(io.masters(m).ar.bits.addr)
    io.masters(m).ar.ready := VecInit(arArbs.map(_.io.in(m).ready))(targetSlave)
  }

  for (s <- 0 until p.numSlaves) {
    arArbs(s).io.in.zipWithIndex.foreach { case (arbIn, m) =>
      val targetSlave = addrDecode(io.masters(m).ar.bits.addr)
      arbIn.bits.ar  := io.masters(m).ar.bits
      arbIn.bits.src := m.U
      arbIn.valid    := io.masters(m).ar.valid && (targetSlave === s.U)
    }
    io.slaves(s).ar.valid := arArbs(s).io.out.valid
    io.slaves(s).ar.bits  := arArbs(s).io.out.bits.ar
    arArbs(s).io.out.ready := io.slaves(s).ar.ready
  }

  // Track master ID for R response routing (per slave)
  val rReturnId = Seq.fill(p.numSlaves)(RegInit(0.U(p.masterIdW.W)))
  for (s <- 0 until p.numSlaves) {
    when(arArbs(s).io.out.fire) {
      rReturnId(s) := arArbs(s).io.out.bits.src
    }
  }

  // ========================================================================
  // Read Data Channel (R): Slave -> Master
  // ========================================================================

  val rArbs = Seq.fill(p.numMasters)(Module(new RRArbiter(new AXI4LiteR(p.axi), p.numSlaves)))

  for (s <- 0 until p.numSlaves) {
    val targetMaster = rReturnId(s)
    io.slaves(s).r.ready := VecInit(rArbs.map(_.io.in(s).ready))(targetMaster)
  }

  for (m <- 0 until p.numMasters) {
    rArbs(m).io.in.zipWithIndex.foreach { case (arbIn, s) =>
      arbIn.bits  := io.slaves(s).r.bits
      arbIn.valid := io.slaves(s).r.valid && (rReturnId(s) === m.U)
    }
    io.masters(m).r <> rArbs(m).io.out
  }
}

// ============================================================================
// AXI4-Lite Response Codes
// ============================================================================

object AXI4LiteResp {
  val OKAY   = 0.U(2.W)
  val EXOKAY = 1.U(2.W)
  val SLVERR = 2.U(2.W)
  val DECERR = 3.U(2.W)
}
