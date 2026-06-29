package io.blueeye.core.domain.repository

import io.blueeye.core.model.SignalSample
import kotlinx.coroutines.flow.Flow

interface SignalSampleRepository {
    suspend fun getAllSignalSamples(): Result<List<SignalSample>>

    fun getAllSignalSamplesFlow(): Flow<Result<List<SignalSample>>>
}
