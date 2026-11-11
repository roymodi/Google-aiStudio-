package com.example

import com.example.model.LogEventType
import com.example.model.NetworkLogEvent
import com.example.model.NetworkStatusInfo
import com.example.model.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun test5GNetworkClassification() {
        assertTrue("5G SA must be classified as 5G", NetworkType.FIVE_G_SA.is5G)
        assertTrue("5G NSA must be classified as 5G", NetworkType.FIVE_G_NSA.is5G)
        assertTrue("5G+ Ultra must be classified as 5G", NetworkType.FIVE_G_PLUS.is5G)

        assertFalse("4G LTE must NOT be classified as 5G", NetworkType.FOUR_G_LTE.is5G)
        assertFalse("3G/2G must NOT be classified as 5G", NetworkType.THREE_G_TWO_G.is5G)
        assertFalse("Wi-Fi must NOT be classified as 5G", NetworkType.WIFI_ACTIVE.is5G)
        assertFalse("Disconnected must NOT be classified as 5G", NetworkType.DISCONNECTED.is5G)
    }

    @Test
    fun testCellularCategoryClassification() {
        assertTrue(NetworkType.FIVE_G_SA.isCellular)
        assertTrue(NetworkType.FIVE_G_NSA.isCellular)
        assertTrue(NetworkType.FOUR_G_LTE.isCellular)
        assertFalse(NetworkType.WIFI_ACTIVE.isCellular)
        assertFalse(NetworkType.DISCONNECTED.isCellular)
    }

    @Test
    fun testNetworkLogEventCreation() {
        val event = NetworkLogEvent(
            type = LogEventType.DROPPED_TO_4G,
            title = "Dropped to 4G LTE",
            description = "Connection degraded from 5G to 4G LTE",
            previousType = NetworkType.FIVE_G_SA,
            currentType = NetworkType.FOUR_G_LTE
        )
        assertNotNull(event.id)
        assertEquals(NetworkType.FIVE_G_SA, event.previousType)
        assertEquals(NetworkType.FOUR_G_LTE, event.currentType)
        assertEquals(LogEventType.DROPPED_TO_4G, event.type)
    }

    @Test
    fun testNetworkStatusInfoDefaults() {
        val status = NetworkStatusInfo(
            networkType = NetworkType.FIVE_G_SA,
            carrierName = "T-Mobile 5G",
            is5G = true
        )
        assertTrue(status.is5G)
        assertEquals("T-Mobile 5G", status.carrierName)
        assertEquals(NetworkType.FIVE_G_SA, status.networkType)
    }
}
