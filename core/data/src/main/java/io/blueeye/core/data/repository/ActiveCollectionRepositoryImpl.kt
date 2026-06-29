package io.blueeye.core.data.repository

import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.domain.repository.ActiveCollectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveCollectionRepositoryImpl @Inject constructor(
    private val watchlistPreferences: WatchlistPreferences,
) : ActiveCollectionRepository {
    override val autoActiveProbeEnabled: Flow<Boolean> = watchlistPreferences.autoActiveProbeEnabled

    override suspend fun setAutoActiveProbeEnabled(enabled: Boolean): Result<Unit> =
        runCatching {
            watchlistPreferences.setAutoActiveProbeEnabled(enabled)
        }
}
