package io.blueeye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import io.blueeye.core.permission.PermissionManager
import io.blueeye.core.ui.theme.BlueEyeTheme
import io.blueeye.navigation.AppNavigation
import io.blueeye.service.ScannerServiceController

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject
    lateinit var appPreferences: io.blueeye.core.data.preferences.AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by appPreferences.themeMode.collectAsState(initial = "System")
            val colorSchemeName by appPreferences.colorScheme.collectAsState(initial = "Classic")
            val useDynamicColors by appPreferences.useDynamicColors.collectAsState(initial = false)

            val darkTheme =
                when (themeMode) {
                    "Light" -> false
                    "Dark" -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

            BlueEyeTheme(
                darkTheme = darkTheme,
                colorSchemeName = colorSchemeName,
                dynamicColor = useDynamicColors,
            ) {
                val launcher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions(),
                        onResult = {
                            if (!PermissionManager.hasScannerStartupPermissions(this@MainActivity)) {
                                ScannerServiceController.reportMissingPermissions(this@MainActivity)
                            }
                        },
                    )

                LaunchedEffect(Unit) {
                    val missingPermissions =
                        PermissionManager.getMissingScannerStartupPermissions(this@MainActivity)

                    if (missingPermissions.isNotEmpty()) {
                        launcher.launch(missingPermissions.toTypedArray())
                    }
                }

                AppNavigation()
            }
        }
    }
}
