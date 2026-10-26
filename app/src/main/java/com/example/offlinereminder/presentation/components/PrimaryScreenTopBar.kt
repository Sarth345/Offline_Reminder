package com.example.offlinereminder.presentation.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Standardized Settings icon button used across all primary screens.
 * Ensures identical visual style, 48dp standard touch target, Settings gear icon,
 * and accessibility semantics.
 */
@Composable
fun SettingsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Settings"
        }
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings"
        )
    }
}

/**
 * Standard TopAppBar for the primary navigation destinations in Planerize.
 * Automatically houses the consistent Settings navigation button on the top right
 * while allowing custom screen titles and optional extra action buttons (e.g. "Today" jump in Calendar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimaryScreenTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        colors = colors,
        actions = {
            extraActions()
            if (onNavigateToSettings != null) {
                SettingsIconButton(onClick = onNavigateToSettings)
            }
        }
    )
}
