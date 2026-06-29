package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.blueeye.core.data.db.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object dla tabeli watchlist. */
@Dao
interface WatchlistDao {
    /** Pobierz wszystkie wpisy watchlist */
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<WatchlistEntity>>

    /** Pobierz wpis po fingerprint urządzenia */
    @Query("SELECT * FROM watchlist WHERE deviceFingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): WatchlistEntity?

    /** Sprawdź czy urządzenie jest na watchlist */
    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE deviceFingerprint = :fingerprint)")
    suspend fun isOnWatchlist(fingerprint: String): Boolean

    /** Pobierz wpisy z włączonym Smart Home */
    @Query("SELECT * FROM watchlist WHERE triggerSmartHome = 1")
    suspend fun getSmartHomeEnabled(): List<WatchlistEntity>

    /** Dodaj do watchlist */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WatchlistEntity)

    /** Aktualizuj wpis */
    @Update suspend fun update(entry: WatchlistEntity)

    /** Usuń z watchlist */
    @Delete suspend fun delete(entry: WatchlistEntity)

    /** Usuń po fingerprint */
    @Query("DELETE FROM watchlist WHERE deviceFingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)
}
