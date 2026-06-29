package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.blueeye.core.data.db.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object dla tabeli devices. */
@Dao
interface DeviceDao : DeviceUpdateDao, DeviceActionDao, DeviceSearchDao {
    // === Queries ===

    /** Pobierz wszystkie urządzenia jako Flow (live updates) */
    @Query("SELECT * FROM devices ORDER BY lastRssi DESC")
    fun getAllDevicesFlow(): Flow<List<DeviceEntity>>

    /** Pobierz wszystkie urządzenia jako List (snapshot) */
    @Query("SELECT * FROM devices ORDER BY firstSeenAt DESC")
    suspend fun getAllDevices(): List<DeviceEntity>

    /** Pobierz urządzenie po fingerprint */
    @Query("SELECT * FROM devices WHERE fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): DeviceEntity?

    /** Pobierz urządzenie po fingerprint (Flow) */
    @Query("SELECT * FROM devices WHERE fingerprint = :fingerprint")
    fun getFlowByFingerprint(fingerprint: String): Flow<DeviceEntity?>

    /** Pobierz pierwsze urządzenie o podanej nazwie lub jej alternatywnej wersji (np. apostrof).
     *  Preferuj urządzenia w Watchlist, a potem najstarsze (żeby scalać do głównego).
     */
    @Query("SELECT * FROM devices WHERE lastDeviceName = :name OR lastDeviceName = :altName ORDER BY isInWatchlist DESC, firstSeenAt ASC LIMIT 1")
    suspend fun getByNameOrAlt(name: String, altName: String): DeviceEntity?

    // === Insert/Update ===

    /** Wstaw lub zastąp urządzenie */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    /** Wstaw wiele urządzeń */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<DeviceEntity>)

    /** Aktualizuj urządzenie */
    @Update
    suspend fun update(device: DeviceEntity)
}
