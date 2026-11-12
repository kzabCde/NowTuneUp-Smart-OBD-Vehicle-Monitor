package com.nowtuneup.app.ui.navigation

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Stable persisted keys, independent of a destination's display order or translated title. */
enum class AppDestination(val key: String, val title: String, val englishTitle: String, val icon: ImageVector) {
    HOME("home", "หน้าหลัก", "Home", Icons.Default.Home),
    PERFORMANCE("performance", "สมรรถนะ", "Performance", Icons.Default.Timer),
    HISTORY("history", "ประวัติ", "History", Icons.Default.History),
    GARAGE("garage", "โรงรถ", "Garage", Icons.Default.DirectionsCar),
    SETTINGS("settings", "ตั้งค่า", "Settings", Icons.Default.Settings);
    companion object {
        fun fromKey(key: String): AppDestination = entries.firstOrNull { it.key == key } ?: HOME
    }
}

@Composable
fun AppBottomNavigation(selected: AppDestination, onSelect: (AppDestination) -> Unit, enabled: Boolean = true) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(selected = selected == destination, enabled = enabled,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = true,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = destination.englishTitle })
        }
    }
}

@Composable
fun AppNavigationRail(selected: AppDestination, onSelect: (AppDestination) -> Unit, enabled: Boolean = true) {
    NavigationRail(modifier = Modifier.verticalScroll(rememberScrollState()), containerColor = MaterialTheme.colorScheme.surface) {
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(selected = selected == destination, enabled = enabled,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.semantics { contentDescription = destination.englishTitle })
        }
    }
}
