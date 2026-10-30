// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import chisel3._
import chisel3.util.Valid

class RocketInstructionObservation(addressBits: Int, commandBits: Int) extends Bundle {
  val token = UInt(64.W)
  val pc = UInt(addressBits.W)
  val instruction = UInt(32.W)
  val memory = Bool()
  val memoryCommand = UInt(commandBits.W)
}

class RocketMemoryRequestObservation(widths: DCacheObservationWidths) extends Bundle {
  val token = UInt(64.W)
  val tag = UInt(widths.tagBits.W)
  val command = UInt(widths.commandBits.W)
  val size = UInt(widths.sizeBits.W)
  val address = UInt(widths.addressBits.W)
}

class RocketRetirementObservation(widths: DCacheObservationWidths) extends Bundle {
  val instruction = new RocketInstructionObservation(widths.addressBits, widths.commandBits)
  val integerWrite = Bool()
  val deferredIntegerWrite = Bool()
  val destination = UInt(5.W)
  val writeData = UInt(widths.dataBits.W)
  val memory = Bool()
  val memoryCommand = UInt(widths.commandBits.W)
  val floatingPoint = Bool()
  val floatingPointWrite = Bool()
}

class RocketLateWriteObservation(dataBits: Int) extends Bundle {
  val token = UInt(64.W)
  val identityKnown = Bool()
  val destination = UInt(5.W)
  val data = UInt(dataBits.W)
}

class RocketFpWriteObservation extends Bundle {
  val token = UInt(64.W)
  val identityKnown = Bool()
  val destination = UInt(5.W)
  val data = UInt(64.W)
}

class RocketTrapObservation(addressBits: Int, causeBits: Int) extends Bundle {
  val token = UInt(64.W)
  val identityKnown = Bool()
  val pc = UInt(addressBits.W)
  val cause = UInt(causeBits.W)
}

// Pipeline facts. WB retirement can precede a load miss's late writeback; the
// collector must retain both timestamps and wait for the matching final value.
class RocketCoreObservation(widths: DCacheObservationWidths, hartBits: Int) extends Bundle {
  val hart = UInt(hartBits.W)
  val allocate = Valid(new RocketInstructionObservation(widths.addressBits, widths.commandBits))
  val request = Valid(new RocketMemoryRequestObservation(widths))
  val executeKilled = Valid(UInt(64.W))
  val memoryKilled = Valid(UInt(64.W))
  val writebackRejected = Valid(UInt(64.W))
  val retire = Valid(new RocketRetirementObservation(widths))
  val lateWrite = Valid(new RocketLateWriteObservation(widths.dataBits))
  val floatingPointWrites = Vec(2, Valid(new RocketFpWriteObservation))
  val pendingFloatingPointWriteback = Bool()
  val fcsrValue = UInt(8.W)
  // Current registered occupancy; same-edge scoreboard updates appear next cycle.
  val pendingIntegerWriteback = Bool()
  val pipelineEmpty = Bool()
  val wfiWakePending = Bool()
  // A source-side reset witness; the host checks it before assuming mie=0.
  val mieValue = UInt(widths.addressBits.W)
  val trap = Valid(new RocketTrapObservation(widths.addressBits, widths.dataBits))
}
