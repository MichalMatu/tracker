package io.blueeye.core.data.tracker.session

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FollowMeSessionManager.
 * Verifies session state management and movement detection.
 */
class FollowMeSessionManagerTest {

    private lateinit var manager: FollowMeSessionManager

    @Before
    fun setup() {
        manager = FollowMeSessionManager()
    }

    // ==================== Session Reset ====================

    @Test
    fun `reset clears all state`() {
        // Record some state
        manager.recordDeviceSighting("device1")
        manager.updateMovement(52.0, 21.0)
        
        // Reset
        manager.resetSession()
        
        // Verify clean state
        assertFalse("User should not have moved after reset", manager.hasUserMoved())
        assertFalse("Device should not be zastane after reset", manager.isDeviceZastane("device1"))
    }

    // ==================== Device Sighting ====================

    @Test
    fun `first sighting returns current timestamp`() {
        val before = System.currentTimeMillis()
        val firstSeen = manager.recordDeviceSighting("device1")
        val after = System.currentTimeMillis()
        
        assertTrue("First seen should be within test bounds", firstSeen in before..after)
    }

    @Test
    fun `subsequent sightings return same timestamp`() {
        val firstSeen = manager.recordDeviceSighting("device1")
        Thread.sleep(10)
        val secondSeen = manager.recordDeviceSighting("device1")
        
        assertEquals("Should return same first-seen time", firstSeen, secondSeen)
    }

    @Test
    fun `different devices get different timestamps`() {
        val firstSeen1 = manager.recordDeviceSighting("device1")
        Thread.sleep(10)
        val firstSeen2 = manager.recordDeviceSighting("device2")
        
        assertTrue("Different devices should have different first-seen", firstSeen2 > firstSeen1)
    }

    // ==================== Zastane (Baseline) Logic ====================

    @Test
    fun `device seen before movement is zastane`() {
        manager.updateMovement(52.0, 21.0)

        // User hasn't moved
        assertFalse(manager.hasUserMoved())
        
        // Record device
        manager.recordDeviceSighting("home_device")
        
        // Should be marked as baseline
        assertTrue("Device seen before movement should be zastane", manager.isDeviceZastane("home_device"))
    }

    @Test
    fun `device seen before first location is not zastane`() {
        manager.recordDeviceSighting("unknown_context_device")

        assertFalse(
            "Device seen before location reference should not be baseline",
            manager.isDeviceZastane("unknown_context_device"),
        )
        assertFalse(manager.hasMovementReference())
    }

    @Test
    fun `device seen after movement is not zastane`() {
        // Set start location
        manager.updateMovement(52.0, 21.0)
        
        // Move 100m away (beyond 50m threshold)
        manager.updateMovement(52.001, 21.0) // ~111m north
        
        assertTrue("User should have moved", manager.hasUserMoved())
        
        // Record new device
        manager.recordDeviceSighting("street_device")
        
        // Should NOT be zastane
        assertFalse("Device seen after movement should NOT be zastane", manager.isDeviceZastane("street_device"))
    }

    // ==================== Movement Detection ====================

    @Test
    fun `no movement when location is null`() {
        val result = manager.updateMovement(null, null)
        assertFalse("No movement when location is null", result)
    }

    @Test
    fun `no movement when distance is below threshold`() {
        manager.updateMovement(52.0, 21.0) // Start
        val result = manager.updateMovement(52.0001, 21.0) // ~11m north
        
        assertFalse("No movement for distance < 50m", result)
        assertFalse(manager.hasUserMoved())
    }

    @Test
    fun `movement detected when distance exceeds threshold`() {
        manager.updateMovement(52.0, 21.0) // Start
        val result = manager.updateMovement(52.001, 21.0) // ~111m north
        
        assertTrue("Movement should be detected for distance > 50m", result)
        assertTrue(manager.hasUserMoved())
    }

    @Test
    fun `movement state persists once set`() {
        manager.updateMovement(52.0, 21.0)
        manager.updateMovement(52.001, 21.0) // Move
        
        // Move back close to start
        manager.updateMovement(52.0001, 21.0)
        
        assertTrue("Movement state should persist even if user returns", manager.hasUserMoved())
    }

    @Test
    fun `first location initializes start point`() {
        val firstResult = manager.updateMovement(52.0, 21.0)
        assertFalse("First location should just initialize, not count as movement", firstResult)
    }
}
