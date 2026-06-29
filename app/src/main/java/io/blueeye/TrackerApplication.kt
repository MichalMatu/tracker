package io.blueeye

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * Główna klasa aplikacji.
 *
 * Odpowiada za:
 * - Inicjalizację Hilt (Dependency Injection)
 * - Tworzenie kanałów powiadomień
 * - Inicjalizację baz danych producentów
 */
@HiltAndroidApp
class TrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Kanał dla Foreground Service (niski priorytet - nie przeszkadza)
            val scannerChannel =
                NotificationChannel(
                    "scanner_channel",
                    "Background Scanner",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Powiadomienie o aktywnym skanowaniu w tle"
                    setShowBadge(false)
                }

            notificationManager.createNotificationChannels(listOf(scannerChannel))
        }
    }
}
