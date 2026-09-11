package io.blueeye.core.data.tracker.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FollowMeSessionManagerTest {
    private lateinit var manager: FollowMeSessionManager

    @Before
    fun setup() {
        manager = FollowMeSessionManager()
    }

    @Test
    fun `reset clears movement baseline and device state`() {
        manager.updateMovement(52.0, 21.0, 5f, NOW)
        manager.recordDeviceSighting("device1", NOW + 100L)
        manager.updateMovement(52.001, 21.0, 5f, NOW + 1_000L)
        manager.recordDeviceSighting("device2", NOW + 1_100L)

        manager.resetSession()

        assertFalse(manager.hasUserMoved())
        assertFalse(manager.hasMovementReference())
        assertFalse(manager.isDeviceZastane("device1"))
        assertEquals(0L, manager.getObservedWhileMovingDurationMs("device2"))
        assertEquals(0, manager.getMovingEncounterCount("device2"))
    }

    @Test
    fun `subsequent sightings preserve session first seen timestamp`() {
        val firstSeen = manager.recordDeviceSighting("device1", NOW)
        val secondSeen = manager.recordDeviceSighting("device1", NOW + 10_000L)

        assertEquals(NOW, firstSeen)
        assertEquals(firstSeen, secondSeen)
    }

    @Test
    fun `device seen before movement is baseline after location reference exists`() {
        manager.updateMovement(52.0, 21.0, 5f, NOW)

        manager.recordDeviceSighting("home_device", NOW + 100L)

        assertTrue(manager.isDeviceZastane("home_device"))
        assertFalse(manager.hasUserMoved())
    }

    @Test
    fun `device seen before first location is not baseline`() {
        manager.recordDeviceSighting("unknown_context_device", NOW)

        assertFalse(manager.isDeviceZastane("unknown_context_device"))
        assertFalse(manager.hasMovementReference())
    }

    @Test
    fun `device first seen after movement is not baseline`() {
        establishMovement()

        manager.recordDeviceSighting("street_device", NOW + 2_000L)

        assertFalse(manager.isDeviceZastane("street_device"))
    }

    @Test
    fun `coarse GPS jitter below combined uncertainty does not latch movement`() {
        manager.updateMovement(52.0, 21.0, 80f, NOW)

        val moved = manager.updateMovement(52.0006, 21.0, 80f, NOW + 10_000L)

        assertFalse(moved)
        assertFalse(manager.hasUserMoved())
    }

    @Test
    fun `real displacement with good accuracy latches historical movement`() {
        manager.updateMovement(52.0, 21.0, 5f, NOW)

        val moved = manager.updateMovement(52.001, 21.0, 5f, NOW + 1_000L)

        assertTrue(moved)
        assertTrue(manager.hasUserMoved())
        assertTrue(manager.isUserMoving(NOW + 1_000L))
    }

    @Test
    fun `historical movement stays latched but current movement expires`() {
        establishMovement()

        assertTrue(manager.hasUserMoved())
        assertTrue(manager.isUserMoving(NOW + 60_000L))
        assertFalse(manager.isUserMoving(NOW + 122_000L))
        assertTrue(manager.hasUserMoved())
    }

    @Test
    fun `observed duration accumulates only between consecutive moving sightings`() {
        establishMovement()

        manager.recordDeviceSighting("tracker", NOW + 2_000L)
        manager.recordDeviceSighting("tracker", NOW + 12_000L)
        manager.recordDeviceSighting("tracker", NOW + 22_000L)

        assertEquals(20_000L, manager.getObservedWhileMovingDurationMs("tracker"))
        assertEquals(3, manager.getMovingEncounterCount("tracker"))
    }

    @Test
    fun `long observation gap is never counted as Follow-Me duration`() {
        establishMovement()

        manager.recordDeviceSighting("tracker", NOW + 2_000L)
        manager.recordDeviceSighting("tracker", NOW + 12_000L)
        manager.recordDeviceSighting("tracker", NOW + 72_000L)
        manager.recordDeviceSighting("tracker", NOW + 82_000L)

        assertEquals(20_000L, manager.getObservedWhileMovingDurationMs("tracker"))
        assertEquals(4, manager.getMovingEncounterCount("tracker"))
    }

    @Test
    fun `stationary sightings do not bridge into a later movement interval`() {
        establishMovement()
        manager.recordDeviceSighting("tracker", NOW + 2_000L)
        manager.recordDeviceSighting("tracker", NOW + 12_000L)
        assertEquals(10_000L, manager.getObservedWhileMovingDurationMs("tracker"))

        val stationaryAt = NOW + 130_000L
        assertFalse(manager.isUserMoving(stationaryAt))
        manager.recordDeviceSighting("tracker", stationaryAt)

        manager.updateMovement(52.002, 21.0, 5f, NOW + 131_000L)
        assertTrue(manager.isUserMoving(NOW + 132_000L))
        manager.recordDeviceSighting("tracker", NOW + 132_000L)
        manager.recordDeviceSighting("tracker", NOW + 142_000L)

        assertEquals(20_000L, manager.getObservedWhileMovingDurationMs("tracker"))
        assertEquals(4, manager.getMovingEncounterCount("tracker"))
    }

    @Test
    fun `stationary post-walk encounters are excluded from movement encounter count`() {
        establishMovement()
        manager.recordDeviceSighting("tracker", NOW + 2_000L)
        manager.recordDeviceSighting("tracker", NOW + 12_000L)

        manager.recordDeviceSighting("tracker", NOW + 130_000L)
        manager.recordDeviceSighting("tracker", NOW + 140_000L)

        assertEquals(2, manager.getMovingEncounterCount("tracker"))
        assertEquals(10_000L, manager.getObservedWhileMovingDurationMs("tracker"))
    }

    private fun establishMovement() {
        manager.updateMovement(52.0, 21.0, 5f, NOW)
        manager.updateMovement(52.001, 21.0, 5f, NOW + 1_000L)
        assertTrue(manager.hasUserMoved())
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
