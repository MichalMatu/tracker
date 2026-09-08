package io.blueeye.core.scanner.manager

import kotlinx.coroutines.channels.Channel

internal sealed interface ScanBufferOfferResult {
    val queueDepth: Int
    val queueHighWaterMark: Int

    data class Enqueued(
        override val queueDepth: Int,
        override val queueHighWaterMark: Int,
    ) : ScanBufferOfferResult

    data class Coalesced(
        override val queueDepth: Int,
        override val queueHighWaterMark: Int,
    ) : ScanBufferOfferResult

    data class Rejected(
        override val queueDepth: Int,
        override val queueHighWaterMark: Int,
    ) : ScanBufferOfferResult
}

internal data class ScanBufferItem<V>(
    val value: V,
    val queueDepthAfterDequeue: Int,
)

/**
 * Bounded latest-per-key queue for high-rate scan ingest.
 *
 * A key occupies at most one queue slot. Repeated observations for a key that is still pending
 * replace its value in place, so the processor receives the newest observation without allowing
 * one noisy device to fill the queue. New keys are explicitly rejected when every slot is used;
 * no already-accepted entry is silently evicted.
 */
internal class LatestPerKeyScanBuffer<K : Any, V : Any>(capacity: Int) {
    private val lock = Any()
    private val pending = HashMap<K, V>(capacity)
    private val keys = Channel<K>(capacity = capacity)
    private var highWaterMark = 0

    fun offer(
        key: K,
        value: V,
    ): ScanBufferOfferResult =
        synchronized(lock) {
            if (pending.containsKey(key)) {
                pending[key] = value
                return@synchronized ScanBufferOfferResult.Coalesced(
                    queueDepth = pending.size,
                    queueHighWaterMark = highWaterMark,
                )
            }

            pending[key] = value
            val enqueueResult = keys.trySend(key)
            if (enqueueResult.isSuccess) {
                highWaterMark = maxOf(highWaterMark, pending.size)
                ScanBufferOfferResult.Enqueued(
                    queueDepth = pending.size,
                    queueHighWaterMark = highWaterMark,
                )
            } else {
                pending.remove(key)
                ScanBufferOfferResult.Rejected(
                    queueDepth = pending.size,
                    queueHighWaterMark = highWaterMark,
                )
            }
        }

    suspend fun receive(): ScanBufferItem<V> {
        while (true) {
            val key = keys.receive()
            val item =
                synchronized(lock) {
                    pending.remove(key)?.let { value ->
                        ScanBufferItem(
                            value = value,
                            queueDepthAfterDequeue = pending.size,
                        )
                    }
                }
            if (item != null) return item
        }
    }

    fun queueDepth(): Int = synchronized(lock) { pending.size }

    fun queueHighWaterMark(): Int = synchronized(lock) { highWaterMark }
}
