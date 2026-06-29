package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.blueeye.core.data.db.entity.SignalSampleEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object dla tabeli signal_samples. */
@Dao
@Suppress("TooManyFunctions")
interface SignalSampleDao {
    /** Wstaw próbkę sygnału */
    @Insert suspend fun insert(sample: SignalSampleEntity)

    /** Wstaw wiele próbek */
    @Insert suspend fun insertAll(samples: List<SignalSampleEntity>)

    /** Pobierz próbki dla urządzenia (do wykresu RSSI) */
    @Query(
        """
        SELECT * FROM signal_samples 
        WHERE deviceFingerprint = :fingerprint 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """,
    )
    fun getSamplesForDevice(
        fingerprint: String,
        limit: Int = 100,
    ): Flow<List<SignalSampleEntity>>

    /** Pobierz próbki z lokalizacją (do mapy) */
    @Query(
        """
        SELECT * FROM signal_samples 
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT :limit
    """,
    )
    fun getSamplesWithLocation(limit: Int = 500): Flow<List<SignalSampleEntity>>

    /** Pobierz próbki dla urządzenia z określonego przedziału czasowego */
    @Query(
        """
        SELECT * FROM signal_samples 
        WHERE deviceFingerprint = :fingerprint 
          AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp ASC
    """,
    )
    suspend fun getSamplesInTimeRange(
        fingerprint: String,
        startTime: Long,
        endTime: Long,
    ): List<SignalSampleEntity>

    /** Średnie RSSI dla urządzenia (ostatnie X próbek) */
    @Query(
        """
        SELECT AVG(rssi) FROM (
            SELECT rssi FROM signal_samples 
            WHERE deviceFingerprint = :fingerprint 
            ORDER BY timestamp DESC 
            LIMIT :sampleCount
        )
    """,
    )
    suspend fun getAverageRssi(
        fingerprint: String,
        sampleCount: Int = 10,
    ): Float?

    /** Usuń stare próbki (starsze niż X dni) */
    @Query("DELETE FROM signal_samples WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldSamples(beforeTimestamp: Long): Int

    /** Liczba próbek dla urządzenia */
    @Query("SELECT COUNT(*) FROM signal_samples WHERE deviceFingerprint = :fingerprint")
    suspend fun countSamplesForDevice(fingerprint: String): Int

    /** Wyczyść całą tabelę */
    @Query("DELETE FROM signal_samples")
    suspend fun deleteAll()

    /** Usuń próbki dla nieistniejących urządzeń (sieroty) */
    @Query("DELETE FROM signal_samples WHERE deviceFingerprint NOT IN (SELECT fingerprint FROM devices)")
    suspend fun deleteOrphanedSamples()

    /** Pobierz wszystkie próbki (do eksportu) */
    @Query("SELECT * FROM signal_samples ORDER BY timestamp DESC")
    suspend fun getAllSamples(): List<SignalSampleEntity>

    /** Pobierz wszystkie próbki jako Flow (do podsumowania sesji) */
    @Query("SELECT * FROM signal_samples ORDER BY timestamp DESC")
    fun getAllSamplesFlow(): Flow<List<SignalSampleEntity>>
}
