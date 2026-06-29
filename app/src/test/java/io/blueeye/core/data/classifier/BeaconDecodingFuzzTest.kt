package io.blueeye.core.data.classifier

import io.blueeye.core.decoders.TP357SDecoder
import io.blueeye.core.decoders.TheengsInteroperableDecoder
import io.blueeye.core.decoders.apple.AppleAirPodsDecoder
import io.blueeye.core.decoders.beacon.AltBeaconDecoder
import org.junit.Test
import java.util.Random

class BeaconDecodingFuzzTest {
    private val decoders =
        setOf(
            TP357SDecoder(),
            TheengsInteroperableDecoder(),
            AppleAirPodsDecoder(),
            AltBeaconDecoder()
        )

    private val manager = BeaconDecoderManager(decoders)
    private val random = Random()

    @Test
    fun `fuzz test decoding with random garbage`() {
        println("Starting Fuzz Test: 10,000 iterations...")
        // Run 10,000 random packets
        for (i in 0 until 10000) {
            val length = random.nextInt(64) // 0 to 63 bytes (typical BLE payload size)
            val garbage = ByteArray(length)
            random.nextBytes(garbage)

            // Randomly select UUIDs to trick TheengsDecoder into thinking it's Xiaomi (FE95)
            // or let it be empty
            val serviceUuids =
                if (random.nextBoolean()) {
                    listOf("0000fe95-0000-1000-8000-00805f9b34fb")
                } else {
                    emptyList()
                }

            val randomMac = generateRandomMac()

            // Randomly use Apple ID (0x004C) to trigger AppleDecoder, or random junk
            val manufacturerId = if (random.nextInt(5) == 0) 0x004C else random.nextInt()

            try {
                // Must not crash
                manager.decode(
                    mac = randomMac,
                    manufacturerRecords = mapOf(manufacturerId to garbage),
                    serviceUuids = serviceUuids,
                    rawData = garbage
                )
            } catch (e: Throwable) {
                // If it crashes, fail the test with detailed info
                throw AssertionError(
                    "🔥 CRASH DETECTED on iteration $i!\n" +
                        "Data (Hex): ${garbage.joinToString("") { "%02X".format(it) }}\n" +
                        "Length: ${garbage.size}\n" +
                        "UUIDs: $serviceUuids\n" +
                        "Exception: $e",
                    e
                )
            }
        }
        println("Fuzz Test Completed: No crashes found.")
    }

    private fun generateRandomMac(): String {
        return (0..5).joinToString(":") {
            "%02X".format(random.nextInt(256))
        }
    }
}
