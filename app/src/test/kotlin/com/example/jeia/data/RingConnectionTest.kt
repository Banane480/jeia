package com.example.jeia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingConnectionTest {
  @Test
  fun decodesLittleEndianUnsignedCounter() {
    assertEquals(0x80ff, alertCounter(byteArrayOf(1, 0xff.toByte(), 0x80.toByte())))
    assertArrayEquals(byteArrayOf(0xff.toByte(), 0x80.toByte(), 0), acknowledgement(0x80ff))
  }

  @Test
  fun rejectsMalformedAndOtherEvents() {
    assertNull(alertCounter(byteArrayOf(1, 0)))
    assertNull(alertCounter(byteArrayOf(2, 0, 0)))
    assertNull(alertCounter(byteArrayOf(1, 0, 0, 0)))
  }
}
