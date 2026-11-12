package com.example.jeia.data.ble

import com.example.jeia.domain.AckStatus
import com.example.jeia.domain.AlertAck
import com.example.jeia.domain.AlertEvent
import com.example.jeia.domain.AlertType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JeiaBleContractTest {

  @Test
  fun decodeAlertEvent_littleEndianCounter() {
    val event = JeiaBleContract.decodeAlertEvent(byteArrayOf(1, 0x34, 0x12))
    assertEquals(AlertEvent(AlertType.ALERT, 0x1234), event)
  }

  @Test
  fun decodeAlertEvent_cancelType() {
    val event = JeiaBleContract.decodeAlertEvent(byteArrayOf(2, 5, 0))
    assertEquals(AlertEvent(AlertType.CANCEL, 5), event)
  }

  @Test
  fun decodeAlertEvent_invalidPacketsReturnNull() {
    assertNull(JeiaBleContract.decodeAlertEvent(byteArrayOf(1, 0)))
    assertNull(JeiaBleContract.decodeAlertEvent(byteArrayOf(9, 0, 0)))
  }

  @Test
  fun encodeAlertAck_counterThenStatus() {
    val bytes = JeiaBleContract.encodeAlertAck(AlertAck(0x0102, AckStatus.SENT))
    assertArrayEquals(byteArrayOf(0x02, 0x01, 0x00), bytes)
  }
}
