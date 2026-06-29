package io.blueeye.core.data.repository

import io.blueeye.core.data.db.dao.SignalSampleDao
import io.blueeye.core.data.mapper.toDomain
import io.blueeye.core.data.utils.asResult
import io.blueeye.core.domain.repository.SignalSampleRepository
import io.blueeye.core.model.SignalSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalSampleRepositoryImpl
    @Inject
    constructor(
        private val signalSampleDao: SignalSampleDao,
    ) : SignalSampleRepository {
        override suspend fun getAllSignalSamples(): Result<List<SignalSample>> =
            runCatching {
                signalSampleDao.getAllSamples().toDomain()
            }

        override fun getAllSignalSamplesFlow(): Flow<Result<List<SignalSample>>> =
            signalSampleDao.getAllSamplesFlow()
                .map { samples -> samples.toDomain() }
                .asResult()
    }
