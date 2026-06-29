package io.blueeye.core.data.settings

import io.blueeye.core.data.repository.DatabaseDownloadManager
import io.blueeye.core.data.repository.GattRepository
import io.blueeye.core.data.repository.VendorRepository
import io.blueeye.core.domain.settings.ReferenceDatabaseCounts
import io.blueeye.core.domain.settings.ReferenceDatabaseRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReferenceDatabaseRepositoryImpl
    @Inject
    constructor(
        private val vendorRepository: VendorRepository,
        private val gattRepository: GattRepository,
        private val databaseDownloadManager: DatabaseDownloadManager,
    ) : ReferenceDatabaseRepository {
        override suspend fun initialize(): Result<Unit> =
            runCatching {
                vendorRepository.init().getOrThrow()
                gattRepository.init().getOrThrow()
            }

        override suspend fun getCounts(): Result<ReferenceDatabaseCounts> =
            runCatching {
                ReferenceDatabaseCounts(
                    companyIdCount = vendorRepository.getCompanyIdCount(),
                    ouiCount = vendorRepository.getOuiCount(),
                    gattServiceCount = gattRepository.getServiceCount(),
                    gattCharacteristicCount = gattRepository.getCharacteristicCount(),
                )
            }

        override suspend fun updateCompanyIds(): Result<Boolean> =
            updateVendorDatabase {
                databaseDownloadManager.downloadCompanyIds()
            }

        override suspend fun updateOui(): Result<Boolean> =
            updateVendorDatabase {
                databaseDownloadManager.downloadOui()
            }

        override suspend fun updateGattServices(): Result<Boolean> =
            updateGattDatabase {
                databaseDownloadManager.downloadServiceUuids()
            }

        override suspend fun updateGattCharacteristics(): Result<Boolean> =
            updateGattDatabase {
                databaseDownloadManager.downloadCharUuids()
            }

        private suspend fun updateVendorDatabase(download: suspend () -> Boolean): Result<Boolean> =
            runCatching {
                val downloaded = download()
                if (downloaded) {
                    vendorRepository.reload().getOrThrow()
                }
                downloaded
            }

        private suspend fun updateGattDatabase(download: suspend () -> Boolean): Result<Boolean> =
            runCatching {
                val downloaded = download()
                if (downloaded) {
                    gattRepository.reload().getOrThrow()
                }
                downloaded
            }
    }
