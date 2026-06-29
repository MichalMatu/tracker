package io.blueeye.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.blueeye.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("FunctionNaming")
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    currentScreen: Screen,
    scope: CoroutineScope,
    navigateTo: (Screen) -> Unit,
) {
    ModalDrawerSheet {
        Text(
            "BlueEye Tracker",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))

        NavigationDrawerItem(
            label = { Text("Radar") },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            selected = currentScreen is Screen.Radar,
            onClick = {
                scope.launch { drawerState.close() }
                navigateTo(Screen.Radar)
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        NavigationDrawerItem(
            label = { Text("Watchlist") },
            icon = { Icon(Icons.Default.List, contentDescription = null) },
            selected = currentScreen is Screen.Watchlist,
            onClick = {
                scope.launch { drawerState.close() }
                navigateTo(Screen.Watchlist)
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = currentScreen is Screen.Settings,
            onClick = {
                scope.launch { drawerState.close() }
                navigateTo(Screen.Settings)
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}
