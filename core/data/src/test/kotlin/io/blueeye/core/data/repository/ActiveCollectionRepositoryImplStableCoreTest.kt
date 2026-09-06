package io.blueeye.core.data.repository

import io.blueeye.core.data.preferences.WatchlistPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ActiveCollectionRepositoryImplStableCoreTest {
    @Test
    fun `stored true preference is exposed as disabled in stable core`() = runTest {
        val preferences: WatchlistPreferences = mock()
        whenever(preferences.autoActiveProbeEnabled).thenReturn(flowOf(true))
        val repository = ActiveCollectionRepositoryImpl(preferences)

        assertFalse(repository.autoActiveProbeEnabled.first())
    }

    @Test
    fun `stable core rejects attempts to enable automatic active collection`() = runTest {
        val preferences: WatchlistPreferences = mock()
        val repository = ActiveCollectionRepositoryImpl(preferences)

        val result = repository.setAutoActiveProbeEnabled(true)

        assertTrue(result.isFailure)
        verify(preferences, never()).setAutoActiveProbeEnabled(true)
    }
}
