package io.blueeye.core.domain.settings

data class ReferenceDatabaseCounts(
    val companyIdCount: Int = 0,
    val ouiCount: Int = 0,
    val gattServiceCount: Int = 0,
    val gattCharacteristicCount: Int = 0,
)

interface ReferenceDatabaseRepository {
    suspend fun initialize(): Result<Unit>

    suspend fun getCounts(): Result<ReferenceDatabaseCounts>

    suspend fun updateCompanyIds(): Result<Boolean>

    suspend fun updateOui(): Result<Boolean>

    suspend fun updateGattServices(): Result<Boolean>

    suspend fun updateGattCharacteristics(): Result<Boolean>
}
