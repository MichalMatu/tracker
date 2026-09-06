package io.blueeye.core.data.repository

import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.domain.repository.ActiveCollectionRepository
import io.blueeye.core.domain.scanner.ScannerRuntimePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveCollectionRepositoryImpl @Inject constructor(
    private val watchlistPreferences: WatchlistPreferences,
) : ActiveCollectionRepository {
    override val autoActiveProbeEnabled: Flow<Boolean> =
        if (ScannerRuntimePolicy.allowsAutomaticActiveProbe) {
            watchlistPreferences.autoActiveProbeEnabled
        } else {
            flowOf(false)
        }

    override suspend fun setAutoActiveProbeEnabled(enabled: Boolean): Result<Unit> =
        runCatching {
            if (enabled && !ScannerRuntimePolicy.allowsAutomaticActiveProbe) {
                error("Automatic active collection is unavailable in ${ScannerRuntimePolicy.profile}")
            }
            watchlistPreferences.setAutoActiveProbeEnabled(enabled)
        }
}
