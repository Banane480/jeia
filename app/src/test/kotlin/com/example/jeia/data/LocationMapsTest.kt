package com.example.jeia.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMapsTest {
  @Test
  fun acceptsOnlyRecentLocationTimestamps() {
    val now = 90_000_000_000L
    assertTrue(isRecentLocation(now, now))
    assertTrue(isRecentLocation(now - 30_000_000_000L, now))
    assertFalse(isRecentLocation(now - 30_000_000_001L, now))
    assertFalse(isRecentLocation(now + 1, now))
    assertFalse(isRecentLocation(0, now))
  }
}
