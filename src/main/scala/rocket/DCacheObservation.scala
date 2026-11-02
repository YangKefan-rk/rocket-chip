// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import chisel3._
import chisel3.util.Valid

case class DCacheObservationWidths(
    addressBits: Int, dataBits: Int, tagBits: Int, commandBits: Int, sizeBits: Int) {
  require(addressBits > 0 && tagBits > 0 && commandBits > 0 && sizeBits > 0)
  require(dataBits > 0 && dataBits % 8 == 0)
}

class DCacheAccessObservation(widths: DCacheObservationWidths) extends Bundle {
  val requestId = UInt(64.W)
  val address = UInt(widths.addressBits.W)
  val tag = UInt(widths.tagBits.W)
  val command = UInt(widths.commandBits.W)
  val size = UInt(widths.sizeBits.W)
}

class DCacheRequestObservation(widths: DCacheObservationWidths) extends Bundle {
  val access = new DCacheAccessObservation(widths)
  val arrayRead = Bool()
}

class DCacheResultObservation(widths: DCacheObservationWidths) extends Bundle {
  val access = new DCacheAccessObservation(widths)
  val hit = Bool()
  val cached = Bool()
  val killed = Bool()
  val nacked = Bool()
  val exception = Bool()
}

class DCacheLoadObservation(widths: DCacheObservationWidths) extends Bundle {
  val access = new DCacheAccessObservation(widths)
  val rawData = UInt(widths.dataBits.W)
  val data = UInt(widths.dataBits.W)
}

// Address includes the original byte offset; data/mask lanes are relative to
// address rounded down to the data word, not relative to that byte offset.
class DCacheStoreObservation(widths: DCacheObservationWidths) extends Bundle {
  val requestId = UInt(64.W)
  val address = UInt(widths.addressBits.W)
  val data = UInt(widths.dataBits.W)
  val mask = UInt((widths.dataBits / 8).W)
}

class DCacheDrainObservation(widths: DCacheObservationWidths) extends Bundle {
  val store = new DCacheStoreObservation(widths)
  // Actual SRAM byte lanes, including ECC read-modify-write preservation bytes.
  // store.mask remains the logical-store byte mask.
  val physicalMask = UInt((widths.dataBits / 8).W)
  val secondSlot = Bool()
  val maintenance = Bool()
}

// Values accompanying a source-side TileLink fire. Source and sink IDs are
// scoped to this cache port and may be reused; the consumer must pair them
// with hart, epoch, channel and a fresh transaction generation. D/E do not
// carry an address; their address field is zero until paired with A/B/C.
class SfuzzRocketTLFire(addressBits: Int) extends Bundle {
  val valid = Bool()
  val first = Bool()
  val last = Bool()
  val source = UInt(16.W)
  val sink = UInt(16.W)
  val opcode = UInt(4.W)
  val param = UInt(4.W)
  val size = UInt(4.W)
  val address = UInt(addressBits.W)
}

class SfuzzRocketDCacheObservation(widths: DCacheObservationWidths) extends Bundle {
  val pstore1Held = Bool()
  val cachedGrantWait = Bool()
  val rawNack = Valid(new DCacheAccessObservation(widths))
  val probeOffered = Bool()
  val probeReady = Bool()
  val probeBlockReasons = UInt(5.W)
  val a = new SfuzzRocketTLFire(widths.addressBits)
  val b = new SfuzzRocketTLFire(widths.addressBits)
  val c = new SfuzzRocketTLFire(widths.addressBits)
  val d = new SfuzzRocketTLFire(widths.addressBits)
  val e = new SfuzzRocketTLFire(widths.addressBits)
}

// Native cache facts, not a transport ABI or a memory-model verdict. Request
// IDs identify cache attempts (including non-core requestors), not instructions.
// The collector timestamps the request's SRAM read edge and retains that source
// position until the corresponding qualified load result arrives.
class DCacheObservation(widths: DCacheObservationWidths, enableSfuzz: Boolean = false) extends Bundle {
  val sfuzz = if (enableSfuzz) Some(new SfuzzRocketDCacheObservation(widths)) else None
  val available = Bool()
  // Local cache quiescence only; fabric and observation delivery close separately.
  val idle = Bool()
  // Actual selected data ECC granule, in bytes, for private cache collectors.
  val eccBytes = UInt(4.W)
  val request = Valid(new DCacheRequestObservation(widths))
  val s1Rejected = Valid(UInt(64.W))
  val result = Valid(new DCacheResultObservation(widths))
  val load = Valid(new DCacheLoadObservation(widths))
  // A successful AMO obtains old data from the earlier request's SRAM read.
  // The S2 result cycle reports that data, not the SRAM read timestamp.
  val atomicRead = Valid(new DCacheLoadObservation(widths))
  // Final AMOALU output, distinct from storeAccepted.data (the right operand).
  // Its physical publication is still observed separately at storeDrain.
  val atomicWrite = Valid(new DCacheStoreObservation(widths))
  val storeAccepted = Valid(new DCacheStoreObservation(widths))
  val storeAdvance = Bool()
  // Valid on storeAdvance: source pstore1 ID moved into pstore2 at this edge.
  // A simultaneous drain still names the old pre-edge pstore2 entry.
  val storeAdvanceRequestId = UInt(64.W)
  // True when advance is caused by an ECC correction, not a logical store.
  val storeAdvanceMaintenance = Bool()
  val storeMerge = Bool()
  // Valid on storeMerge: existing pstore2 anchor; incoming ID is storeAccepted.
  // A merge cannot coincide with a drain or advance.
  val storeMergeTargetRequestId = UInt(64.W)
  val storeDrain = Valid(new DCacheDrainObservation(widths))
}
