package lab5

import chisel3._
import chisel3.util._

object KeycodeToAscii {
  def apply(keycode: UInt): UInt = {
    MuxLookup(keycode, 0.U)(
      Seq(
        0x15.U -> 'q'.U, // 'q'
        0x1d.U -> 'w'.U,
        0x24.U -> 'e'.U,
        0x2d.U -> 'r'.U,
        0x2c.U -> 't'.U,
        0x35.U -> 'y'.U,
        0x3c.U -> 'u'.U,
        0x43.U -> 'i'.U,
        0x44.U -> 'o'.U,
        0x4d.U -> 'p'.U,
        0x1c.U -> 'a'.U, // 'a'
        0x1b.U -> 's'.U,
        0x23.U -> 'd'.U,
        0x2b.U -> 'f'.U,
        0x34.U -> 'g'.U,
        0x33.U -> 'h'.U,
        0x3b.U -> 'j'.U,
        0x42.U -> 'k'.U,
        0x4b.U -> 'l'.U,
        0x1a.U -> 'z'.U, // 'z'
        0x22.U -> 'x'.U,
        0x21.U -> 'c'.U,
        0x2a.U -> 'v'.U,
        0x32.U -> 'b'.U,
        0x31.U -> 'n'.U,
        0x3a.U -> 'm'.U
      )
    )
  }
}
