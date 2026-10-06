package io.blueeye.core.data.repository

import io.blueeye.core.data.preferences.WatchlistPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ActiveCollectionRepositoryImplStableCoreTest {
    @Test
    fun `stored true preference is exposed when automatic gatt is allowed`() = runTest {
        val preferences: WatchlistPreferences = mock()
        whenever(preferences.autoActiveProbeEnabled).thenReturn(flowOf(true))
        val repository = ActiveCollectionRepositoryImpl(preferences)

        assertTrue(repository.autoActiveProbeEnabled.first())
    }

    @Test
    fun `stable core persists explicit automatic gatt opt in`() = runTest {
        val preferences: WatchlistPreferences = mock()
        val repository = ActiveCollectionRepositoryImpl(preferences)

        val result = repository.setAutoActiveProbeEnabled(true)

        assertTrue(result.isSuccess)
        verify(preferences).setAutoActiveProbeEnabled(true)
    }
}
