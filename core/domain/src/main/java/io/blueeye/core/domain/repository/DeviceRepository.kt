package io.blueeye.core.domain.repository

/**
 * Interfejs repozytorium zarządzającego urządzeniami. Definiuje operacje dostępne dla warstwy
 * biznesowej (UseCases).
 */
interface DeviceRepository :
    DeviceQueryRepository,
    DeviceAlertEvidenceRepository,
    DeviceManageRepository,
    DeviceScanRepository
