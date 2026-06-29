package io.blueeye.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface ActiveCollectionRepository {
    val autoActiveProbeEnabled: Flow<Boolean>

    suspend fun setAutoActiveProbeEnabled(enabled: Boolean): Result<Unit>
}
