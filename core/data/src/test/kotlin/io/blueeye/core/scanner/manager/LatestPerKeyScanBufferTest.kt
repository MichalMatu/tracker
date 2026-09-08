package io.blueeye.core.scanner.manager

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestPerKeyScanBufferTest {
    @Test
    fun `dense repeated-device burst coalesces to latest observation`() = runTest {
        val buffer = LatestPerKeyScanBuffer<String, Observation>(capacity = 4_096)
        var enqueued = 0
        var coalesced = 0
        var rejected = 0

        repeat(100) { deviceIndex ->
            repeat(100) { sequence ->
                when (
                    buffer.offer(
                        key = "device-$deviceIndex",
                        value = Observation(deviceIndex = deviceIndex, sequence = sequence),
                    )
                ) {
                    is ScanBufferOfferResult.Enqueued -> enqueued += 1
                    is ScanBufferOfferResult.Coalesced -> coalesced += 1
                    is ScanBufferOfferResult.Rejected -> rejected += 1
                }
            }
        }

        assertEquals(100, enqueued)
        assertEquals(9_900, coalesced)
        assertEquals(0, rejected)
        assertEquals(100, buffer.queueDepth())
        assertEquals(100, buffer.queueHighWaterMark())

        val received = mutableMapOf<Int, Int>()
        repeat(100) {
            val item = buffer.receive()
            received[item.value.deviceIndex] = item.value.sequence
        }

        assertEquals(0, buffer.queueDepth())
        assertEquals(100, received.size)
        assertTrue(received.values.all { sequence -> sequence == 99 })
    }

    @Test
    fun `capacity exhaustion rejects new key without evicting accepted entries`() = runTest {
        val buffer = LatestPerKeyScanBuffer<String, Int>(capacity = 16)
        val outcomes =
            (0 until 20).map { index ->
                buffer.offer(key = "device-$index", value = index)
            }

        assertEquals(16, outcomes.count { it is ScanBufferOfferResult.Enqueued })
        assertEquals(0, outcomes.count { it is ScanBufferOfferResult.Coalesced })
        assertEquals(4, outcomes.count { it is ScanBufferOfferResult.Rejected })
        assertEquals(16, buffer.queueDepth())
        assertEquals(16, buffer.queueHighWaterMark())

        val received = buildSet {
            repeat(16) { add(buffer.receive().value) }
        }
        assertEquals((0 until 16).toSet(), received)
        assertEquals(0, buffer.queueDepth())
    }

    @Test
    fun `same key can enqueue again after previous observation is dequeued`() = runTest {
        val buffer = LatestPerKeyScanBuffer<String, Int>(capacity = 1)

        assertTrue(buffer.offer("device", 1) is ScanBufferOfferResult.Enqueued)
        assertEquals(1, buffer.receive().value)
        assertTrue(buffer.offer("device", 2) is ScanBufferOfferResult.Enqueued)
        assertEquals(2, buffer.receive().value)
    }

    private data class Observation(
        val deviceIndex: Int,
        val sequence: Int,
    )
}
