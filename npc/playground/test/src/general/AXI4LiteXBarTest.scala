package general

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// ============================================================================
// AXI4-Lite XBar Unit Tests (using Chisel 7 built-in simulator)
// ============================================================================

class AXI4LiteXBarTest extends AnyFlatSpec with Matchers {

  // Default test parameters
  val defaultAxiParams = AXI4LiteParams(
    addrWidth = 32,
    dataWidth = 32
  )

  // 2 masters, 2 slaves configuration
  val twoMasterTwoSlaveParams = AXI4LiteXBarParams(
    axi = defaultAxiParams,
    numMasters = 2,
    numSlaves = 2,
    addrMap = Seq(
      (BigInt(0x00000000L), BigInt(0x10000)),  // Slave 0: 0x0000_0000 - 0x0000_FFFF
      (BigInt(0x00010000L), BigInt(0x10000))   // Slave 1: 0x0001_0000 - 0x0001_FFFF
    )
  )

  // 1 master, 2 slaves configuration
  val oneMasterTwoSlaveParams = AXI4LiteXBarParams(
    axi = defaultAxiParams,
    numMasters = 1,
    numSlaves = 2,
    addrMap = Seq(
      (BigInt(0x00000000L), BigInt(0x10000)),
      (BigInt(0x00010000L), BigInt(0x10000))
    )
  )

  // 1 master, 1 slave configuration
  val oneMasterOneSlaveParams = AXI4LiteXBarParams(
    axi = defaultAxiParams,
    numMasters = 1,
    numSlaves = 1,
    addrMap = Seq(
      (BigInt(0x00000000L), BigInt(0x10000))
    )
  )

  // ========================================================================
  // Helper Methods
  // ========================================================================

  /** Initialize all master ports to idle state */
  def initMasters(dut: AXI4LiteXBar): Unit = {
    for (m <- 0 until dut.p.numMasters) {
      dut.io.masters(m).aw.valid.poke(false.B)
      dut.io.masters(m).w.valid.poke(false.B)
      dut.io.masters(m).b.ready.poke(true.B)
      dut.io.masters(m).ar.valid.poke(false.B)
      dut.io.masters(m).r.ready.poke(true.B)
    }
  }

  /** Initialize all slave ports to idle state */
  def initSlaves(dut: AXI4LiteXBar): Unit = {
    for (s <- 0 until dut.p.numSlaves) {
      dut.io.slaves(s).aw.ready.poke(true.B)
      dut.io.slaves(s).w.ready.poke(true.B)
      dut.io.slaves(s).b.valid.poke(false.B)
      dut.io.slaves(s).ar.ready.poke(true.B)
      dut.io.slaves(s).r.valid.poke(false.B)
    }
  }

  /** Issue a write address transaction from a master */
  def issueWriteAddr(
    dut: AXI4LiteXBar,
    masterId: Int,
    addr: Long,
    prot: Int = 0
  ): Unit = {
    dut.io.masters(masterId).aw.valid.poke(true.B)
    dut.io.masters(masterId).aw.bits.addr.poke(addr.U)
    dut.io.masters(masterId).aw.bits.prot.poke(prot.U)
  }

  /** Issue a write data transaction from a master */
  def issueWriteData(
    dut: AXI4LiteXBar,
    masterId: Int,
    data: Long,
    strb: Int = 0xF
  ): Unit = {
    dut.io.masters(masterId).w.valid.poke(true.B)
    dut.io.masters(masterId).w.bits.data.poke(data.U)
    dut.io.masters(masterId).w.bits.strb.poke(strb.U)
  }

  /** Issue a read address transaction from a master */
  def issueReadAddr(
    dut: AXI4LiteXBar,
    masterId: Int,
    addr: Long,
    prot: Int = 0
  ): Unit = {
    dut.io.masters(masterId).ar.valid.poke(true.B)
    dut.io.masters(masterId).ar.bits.addr.poke(addr.U)
    dut.io.masters(masterId).ar.bits.prot.poke(prot.U)
  }

  /** Provide read response from a slave */
  def provideReadResp(
    dut: AXI4LiteXBar,
    slaveId: Int,
    data: Long,
    resp: Int = 0
  ): Unit = {
    dut.io.slaves(slaveId).r.valid.poke(true.B)
    dut.io.slaves(slaveId).r.bits.data.poke(data.U)
    dut.io.slaves(slaveId).r.bits.resp.poke(resp.U)
  }

  /** Provide write response from a slave */
  def provideWriteResp(
    dut: AXI4LiteXBar,
    slaveId: Int,
    resp: Int = 0
  ): Unit = {
    dut.io.slaves(slaveId).b.valid.poke(true.B)
    dut.io.slaves(slaveId).b.bits.resp.poke(resp.U)
  }

  // ========================================================================
  // Basic Instantiation Tests
  // ========================================================================

  behavior of "AXI4LiteXBar"

  it should "instantiate a 1x1 crossbar without errors" in {
    simulate(new AXI4LiteXBar(oneMasterOneSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(5)
    }
  }

  it should "instantiate a 1x2 crossbar without errors" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(5)
    }
  }

  it should "instantiate a 2x2 crossbar without errors" in {
    simulate(new AXI4LiteXBar(twoMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(5)
    }
  }

  // ========================================================================
  // Write Transaction Tests
  // ========================================================================

  it should "route a write transaction to slave 0" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue write address to slave 0's address range
      issueWriteAddr(dut, masterId = 0, addr = 0x00001000L)
      dut.clock.step(1)

      // Check that slave 0 received the address
      assert(dut.io.slaves(0).aw.valid.peek().litToBoolean, "Slave 0 should see valid AW")
      assert(!dut.io.slaves(1).aw.valid.peek().litToBoolean, "Slave 1 should not see valid AW")
      assert(dut.io.slaves(0).aw.bits.addr.peek().litValue == 0x00001000L)

      // Clear address - need to step for awPending register to update
      dut.io.masters(0).aw.valid.poke(false.B)
      dut.clock.step(1)

      // Now issue write data (awPending is now true)
      issueWriteData(dut, masterId = 0, data = 0xDEADBEEFL)
      dut.clock.step(1)

      // Check that slave 0 received the data
      assert(dut.io.slaves(0).w.valid.peek().litToBoolean, "Slave 0 should see valid W")
      assert(dut.io.slaves(0).w.bits.data.peek().litValue == 0xDEADBEEFL)

      dut.io.masters(0).w.valid.poke(false.B)
      dut.clock.step(1)

      // Slave 0 provides response
      provideWriteResp(dut, slaveId = 0, resp = 0)
      dut.clock.step(1)

      // Master should receive response
      assert(dut.io.masters(0).b.valid.peek().litToBoolean, "Master should see valid B")
      assert(dut.io.masters(0).b.bits.resp.peek().litValue == 0)
    }
  }

  it should "route a write transaction to slave 1" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue write address to slave 1's address range
      issueWriteAddr(dut, masterId = 0, addr = 0x00010100L)
      dut.clock.step(1)

      // Check that slave 1 received the address
      assert(!dut.io.slaves(0).aw.valid.peek().litToBoolean, "Slave 0 should not see valid AW")
      assert(dut.io.slaves(1).aw.valid.peek().litToBoolean, "Slave 1 should see valid AW")
      assert(dut.io.slaves(1).aw.bits.addr.peek().litValue == 0x00010100L)

      dut.io.masters(0).aw.valid.poke(false.B)
      dut.clock.step(1)

      issueWriteData(dut, masterId = 0, data = 0xCAFEBABEL)
      dut.clock.step(1)

      assert(dut.io.slaves(1).w.valid.peek().litToBoolean, "Slave 1 should see valid W")
      assert(dut.io.slaves(1).w.bits.data.peek().litValue == 0xCAFEBABEL)

      dut.io.masters(0).w.valid.poke(false.B)
      dut.clock.step(1)

      provideWriteResp(dut, slaveId = 1, resp = 0)
      dut.clock.step(1)

      assert(dut.io.masters(0).b.valid.peek().litToBoolean, "Master should see valid B")
    }
  }

  // ========================================================================
  // Read Transaction Tests
  // ========================================================================

  it should "route a read transaction to slave 0" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue read address to slave 0
      issueReadAddr(dut, masterId = 0, addr = 0x00002000L)
      dut.clock.step(1)

      // Check slave 0 received the address
      assert(dut.io.slaves(0).ar.valid.peek().litToBoolean, "Slave 0 should see valid AR")
      assert(!dut.io.slaves(1).ar.valid.peek().litToBoolean, "Slave 1 should not see valid AR")
      assert(dut.io.slaves(0).ar.bits.addr.peek().litValue == 0x00002000L)

      dut.io.masters(0).ar.valid.poke(false.B)
      dut.clock.step(1)

      // Slave 0 provides read data
      provideReadResp(dut, slaveId = 0, data = 0x12345678L)
      dut.clock.step(1)

      // Master should receive data
      assert(dut.io.masters(0).r.valid.peek().litToBoolean, "Master should see valid R")
      assert(dut.io.masters(0).r.bits.data.peek().litValue == 0x12345678L)
    }
  }

  it should "route a read transaction to slave 1" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue read address to slave 1
      issueReadAddr(dut, masterId = 0, addr = 0x00010200L)
      dut.clock.step(1)

      // Check slave 1 received the address
      assert(!dut.io.slaves(0).ar.valid.peek().litToBoolean, "Slave 0 should not see valid AR")
      assert(dut.io.slaves(1).ar.valid.peek().litToBoolean, "Slave 1 should see valid AR")
      assert(dut.io.slaves(1).ar.bits.addr.peek().litValue == 0x00010200L)

      dut.io.masters(0).ar.valid.poke(false.B)
      dut.clock.step(1)

      // Slave 1 provides read data
      provideReadResp(dut, slaveId = 1, data = 0xABCDEF00L)
      dut.clock.step(1)

      // Master should receive data
      assert(dut.io.masters(0).r.valid.peek().litToBoolean, "Master should see valid R")
      assert(dut.io.masters(0).r.bits.data.peek().litValue == 0xABCDEF00L)
    }
  }

  // ========================================================================
  // Multi-Master Arbitration Tests
  // ========================================================================

  it should "arbitrate between two masters accessing the same slave" in {
    simulate(new AXI4LiteXBar(twoMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Both masters try to write to slave 0 simultaneously
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      issueWriteAddr(dut, masterId = 1, addr = 0x00000100L)
      dut.clock.step(1)

      // One should win arbitration
      assert(dut.io.slaves(0).aw.valid.peek().litToBoolean, "Slave 0 should see valid AW")
      val master0Won = dut.io.masters(0).aw.ready.peek().litToBoolean
      val master1Won = dut.io.masters(1).aw.ready.peek().litToBoolean

      // At least one should win (round-robin arbiter)
      assert(master0Won || master1Won, "At least one master should win arbitration")
    }
  }

  it should "allow two masters to access different slaves simultaneously" in {
    simulate(new AXI4LiteXBar(twoMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Master 0 writes to slave 0, Master 1 writes to slave 1
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      issueWriteAddr(dut, masterId = 1, addr = 0x00010000L)
      dut.clock.step(1)

      // Both should succeed
      assert(dut.io.slaves(0).aw.valid.peek().litToBoolean, "Slave 0 should see valid AW")
      assert(dut.io.slaves(1).aw.valid.peek().litToBoolean, "Slave 1 should see valid AW")
      assert(dut.io.slaves(0).aw.bits.addr.peek().litValue == 0x00000000L)
      assert(dut.io.slaves(1).aw.bits.addr.peek().litValue == 0x00010000L)

      // Both masters should see ready
      assert(dut.io.masters(0).aw.ready.peek().litToBoolean, "Master 0 should see ready")
      assert(dut.io.masters(1).aw.ready.peek().litToBoolean, "Master 1 should see ready")
    }
  }

  it should "allow simultaneous read and write to different slaves" in {
    simulate(new AXI4LiteXBar(twoMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Master 0 writes to slave 0, Master 1 reads from slave 1
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      issueReadAddr(dut, masterId = 1, addr = 0x00010000L)
      dut.clock.step(1)

      // Both should succeed
      assert(dut.io.slaves(0).aw.valid.peek().litToBoolean, "Slave 0 should see valid AW")
      assert(dut.io.slaves(1).ar.valid.peek().litToBoolean, "Slave 1 should see valid AR")
    }
  }

  // ========================================================================
  // Response Routing Tests
  // ========================================================================

  it should "route write response back to the correct master" in {
    simulate(new AXI4LiteXBar(twoMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Master 0 writes to slave 0
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      dut.io.masters(0).aw.valid.poke(false.B)
      dut.clock.step(1)

      issueWriteData(dut, masterId = 0, data = 0xFFFFFFFFL)
      dut.clock.step(1)

      dut.io.masters(0).w.valid.poke(false.B)
      dut.clock.step(1)

      // Slave 0 sends response
      provideWriteResp(dut, slaveId = 0, resp = 0)
      dut.clock.step(1)

      // Only master 0 should receive the response
      assert(dut.io.masters(0).b.valid.peek().litToBoolean, "Master 0 should see valid B")
    }
  }

  it should "route read response back to the correct master" in {
    simulate(new AXI4LiteXBar(twoMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Master 1 reads from slave 1
      issueReadAddr(dut, masterId = 1, addr = 0x00010000L)
      dut.clock.step(1)

      assert(dut.io.slaves(1).ar.valid.peek().litToBoolean, "Slave 1 should see valid AR")

      dut.io.masters(1).ar.valid.poke(false.B)
      dut.clock.step(1)

      // Slave 1 sends response
      provideReadResp(dut, slaveId = 1, data = 0xABCDEF01L)
      dut.clock.step(1)

      // Only master 1 should receive the response
      assert(dut.io.masters(1).r.valid.peek().litToBoolean, "Master 1 should see valid R")
      assert(dut.io.masters(1).r.bits.data.peek().litValue == 0xABCDEF01L)
    }
  }

  // ========================================================================
  // Backpressure Tests
  // ========================================================================

  it should "handle backpressure from slave on AW channel" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)

      // Slave 0 not ready
      dut.io.slaves(0).aw.ready.poke(false.B)
      dut.clock.step(1)

      // Issue write address
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      // Master should not see ready
      assert(!dut.io.masters(0).aw.ready.peek().litToBoolean, "Master should not see ready")
      // Slave should see valid
      assert(dut.io.slaves(0).aw.valid.peek().litToBoolean, "Slave should see valid")

      // Now slave becomes ready
      dut.io.slaves(0).aw.ready.poke(true.B)
      dut.clock.step(1)

      // Master should now see ready
      assert(dut.io.masters(0).aw.ready.peek().litToBoolean, "Master should see ready")
    }
  }

  it should "handle backpressure from slave on W channel" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue write address first
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      dut.io.masters(0).aw.valid.poke(false.B)
      dut.clock.step(1)  // Wait for awPending to update

      // Slave 0 W not ready
      dut.io.slaves(0).w.ready.poke(false.B)

      // Issue write data
      issueWriteData(dut, masterId = 0, data = 0x12345678L)
      dut.clock.step(1)

      // Master should not see ready on W (slave not ready)
      assert(!dut.io.masters(0).w.ready.peek().litToBoolean, "Master should not see W ready")

      // Now slave becomes ready
      dut.io.slaves(0).w.ready.poke(true.B)
      dut.clock.step(1)

      // Master should now see ready
      assert(dut.io.masters(0).w.ready.peek().litToBoolean, "Master should see W ready")
    }
  }

  it should "handle backpressure from master on R channel" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue read address
      issueReadAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      dut.io.masters(0).ar.valid.poke(false.B)

      // Master not ready to receive
      dut.io.masters(0).r.ready.poke(false.B)
      dut.clock.step(1)

      // Slave provides data
      provideReadResp(dut, slaveId = 0, data = 0x12345678L)
      dut.clock.step(1)

      // Data should be valid but slave should see backpressure
      assert(dut.io.masters(0).r.valid.peek().litToBoolean, "Master should see valid R")
      assert(!dut.io.slaves(0).r.ready.peek().litToBoolean, "Slave should see backpressure")

      // Master becomes ready
      dut.io.masters(0).r.ready.poke(true.B)
      dut.clock.step(1)

      // Now should flow through
      assert(dut.io.slaves(0).r.ready.peek().litToBoolean, "Slave should see ready")
    }
  }

  it should "handle backpressure from master on B channel" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Complete a write transaction
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      dut.io.masters(0).aw.valid.poke(false.B)
      dut.clock.step(1)  // Wait for awPending to update

      issueWriteData(dut, masterId = 0, data = 0x12345678L)
      dut.clock.step(1)

      dut.io.masters(0).w.valid.poke(false.B)

      // Master not ready to receive B
      dut.io.masters(0).b.ready.poke(false.B)
      dut.clock.step(1)

      // Slave provides B response
      provideWriteResp(dut, slaveId = 0, resp = 0)
      dut.clock.step(1)

      // B should be valid but slave should see backpressure
      assert(dut.io.masters(0).b.valid.peek().litToBoolean, "Master should see valid B")
      assert(!dut.io.slaves(0).b.ready.peek().litToBoolean, "Slave should see backpressure")

      // Master becomes ready
      dut.io.masters(0).b.ready.poke(true.B)
      dut.clock.step(1)

      // Now should flow through
      assert(dut.io.slaves(0).b.ready.peek().litToBoolean, "Slave should see ready")
    }
  }

  // ========================================================================
  // Error Response Tests
  // ========================================================================

  it should "propagate SLVERR response correctly" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue read address
      issueReadAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      dut.io.masters(0).ar.valid.poke(false.B)
      dut.clock.step(1)

      // Slave returns SLVERR
      dut.io.slaves(0).r.valid.poke(true.B)
      dut.io.slaves(0).r.bits.data.poke(0.U)
      dut.io.slaves(0).r.bits.resp.poke(2.U)  // SLVERR
      dut.clock.step(1)

      // Master should see the error response
      assert(dut.io.masters(0).r.valid.peek().litToBoolean)
      assert(dut.io.masters(0).r.bits.resp.peek().litValue == 2)
    }
  }

  it should "propagate write SLVERR response correctly" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Complete write
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      dut.io.masters(0).aw.valid.poke(false.B)
      dut.clock.step(1)  // Wait for awPending to update

      issueWriteData(dut, masterId = 0, data = 0x12345678L)
      dut.clock.step(1)

      dut.io.masters(0).w.valid.poke(false.B)
      dut.clock.step(1)

      // Slave returns SLVERR
      dut.io.slaves(0).b.valid.poke(true.B)
      dut.io.slaves(0).b.bits.resp.poke(2.U)  // SLVERR
      dut.clock.step(1)

      // Master should see the error response
      assert(dut.io.masters(0).b.valid.peek().litToBoolean)
      assert(dut.io.masters(0).b.bits.resp.peek().litValue == 2)
    }
  }

  // ========================================================================
  // Sequential Transaction Tests
  // ========================================================================

  it should "handle multiple sequential write transactions" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      for (i <- 0 until 4) {
        val addr = (i * 4).toLong
        val data = (0x11111111L * (i + 1))

        // Write address
        issueWriteAddr(dut, masterId = 0, addr = addr)
        dut.clock.step(1)

        dut.io.masters(0).aw.valid.poke(false.B)
        dut.clock.step(1)  // Wait for awPending to update

        // Write data
        issueWriteData(dut, masterId = 0, data = data)
        dut.clock.step(1)

        dut.io.masters(0).w.valid.poke(false.B)
        dut.clock.step(1)

        // Get response
        provideWriteResp(dut, slaveId = 0, resp = 0)
        dut.clock.step(1)

        assert(dut.io.masters(0).b.valid.peek().litToBoolean, s"Should receive B for transaction $i")

        dut.io.slaves(0).b.valid.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  it should "handle multiple sequential read transactions" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      for (i <- 0 until 4) {
        val addr = (i * 4).toLong
        val data = (0xAAAAAAAAL + i)

        // Read address
        issueReadAddr(dut, masterId = 0, addr = addr)
        dut.clock.step(1)

        dut.io.masters(0).ar.valid.poke(false.B)
        dut.clock.step(1)

        // Get response
        provideReadResp(dut, slaveId = 0, data = data)
        dut.clock.step(1)

        assert(dut.io.masters(0).r.valid.peek().litToBoolean, s"Should receive R for transaction $i")
        assert(dut.io.masters(0).r.bits.data.peek().litValue == data, s"Data should match for transaction $i")

        dut.io.slaves(0).r.valid.poke(false.B)
        dut.clock.step(1)
      }
    }
  }

  // ========================================================================
  // Protection Signal Tests
  // ========================================================================

  it should "forward protection signals correctly" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue write with specific prot value
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L, prot = 0x5)
      dut.clock.step(1)

      // Check that prot is forwarded
      assert(dut.io.slaves(0).aw.bits.prot.peek().litValue == 0x5)

      dut.io.masters(0).aw.valid.poke(false.B)

      // Issue read with specific prot value
      issueReadAddr(dut, masterId = 0, addr = 0x00000000L, prot = 0x3)
      dut.clock.step(1)

      // Check that prot is forwarded
      assert(dut.io.slaves(0).ar.bits.prot.peek().litValue == 0x3)
    }
  }

  // ========================================================================
  // Strobe Signal Tests
  // ========================================================================

  it should "forward write strobe signals correctly" in {
    simulate(new AXI4LiteXBar(oneMasterTwoSlaveParams)) { dut =>
      initMasters(dut)
      initSlaves(dut)
      dut.clock.step(1)

      // Issue write address first
      issueWriteAddr(dut, masterId = 0, addr = 0x00000000L)
      dut.clock.step(1)

      dut.io.masters(0).aw.valid.poke(false.B)
      dut.clock.step(1)  // Wait for awPending to update

      // Issue write data with specific strobe
      issueWriteData(dut, masterId = 0, data = 0x12345678L, strb = 0xA)  // Only bytes 1 and 3
      dut.clock.step(1)

      // Check that strobe is forwarded
      assert(dut.io.slaves(0).w.bits.strb.peek().litValue == 0xA)
    }
  }
}

// ============================================================================
// Stress Tests
// ============================================================================

class AXI4LiteXBarStressTest extends AnyFlatSpec with Matchers {

  val stressParams = AXI4LiteXBarParams(
    axi = AXI4LiteParams(addrWidth = 32, dataWidth = 32),
    numMasters = 4,
    numSlaves = 4,
    addrMap = Seq(
      (BigInt(0x00000000L), BigInt(0x10000)),
      (BigInt(0x00010000L), BigInt(0x10000)),
      (BigInt(0x00020000L), BigInt(0x10000)),
      (BigInt(0x00030000L), BigInt(0x10000))
    )
  )

  behavior of "AXI4LiteXBar Stress"

  it should "instantiate a 4x4 crossbar without errors" in {
    simulate(new AXI4LiteXBar(stressParams)) { dut =>
      // Just verify it instantiates correctly
      dut.clock.step(10)
    }
  }

  it should "handle all masters accessing all slaves in sequence" in {
    simulate(new AXI4LiteXBar(stressParams)) { dut =>
      // Initialize
      for (m <- 0 until 4) {
        dut.io.masters(m).aw.valid.poke(false.B)
        dut.io.masters(m).w.valid.poke(false.B)
        dut.io.masters(m).b.ready.poke(true.B)
        dut.io.masters(m).ar.valid.poke(false.B)
        dut.io.masters(m).r.ready.poke(true.B)
      }
      for (s <- 0 until 4) {
        dut.io.slaves(s).aw.ready.poke(true.B)
        dut.io.slaves(s).w.ready.poke(true.B)
        dut.io.slaves(s).b.valid.poke(false.B)
        dut.io.slaves(s).ar.ready.poke(true.B)
        dut.io.slaves(s).r.valid.poke(false.B)
      }
      dut.clock.step(1)

      // Each master accesses each slave
      for (m <- 0 until 4) {
        for (s <- 0 until 4) {
          val addr = (s * 0x10000).toLong

          // Read transaction
          dut.io.masters(m).ar.valid.poke(true.B)
          dut.io.masters(m).ar.bits.addr.poke(addr.U)
          dut.io.masters(m).ar.bits.prot.poke(0.U)

          dut.clock.step(1)

          assert(dut.io.slaves(s).ar.valid.peek().litToBoolean, s"Slave $s should see AR from master $m")
          dut.io.masters(m).ar.valid.poke(false.B)

          dut.clock.step(1)

          // Slave responds
          dut.io.slaves(s).r.valid.poke(true.B)
          dut.io.slaves(s).r.bits.data.poke((m * 16 + s).U)
          dut.io.slaves(s).r.bits.resp.poke(0.U)

          dut.clock.step(1)

          assert(dut.io.masters(m).r.valid.peek().litToBoolean, s"Master $m should see R from slave $s")
          dut.io.slaves(s).r.valid.poke(false.B)

          dut.clock.step(1)
        }
      }
    }
  }
}
