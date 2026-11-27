package com.martronics.hermitic.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.martronics.hermitic.feature.onboarding.ConnectionScreen
import com.martronics.hermitic.feature.settings.SettingsScreen

object HermiticRoutes {
    const val ONBOARDING = "onboarding"
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermiticNavHost(
    isConfigured: Boolean = false,
) {
    val navController = rememberNavController()
    val startDestination = if (isConfigured) HermiticRoutes.SESSIONS else HermiticRoutes.ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(HermiticRoutes.ONBOARDING) {
            ConnectionScreen(
                canNavigateBack = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
                onConnected = {
                    navController.navigate(HermiticRoutes.SESSIONS) {
                        popUpTo(HermiticRoutes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(HermiticRoutes.SESSIONS) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Hermes Sessions") },
                        actions = {
                            IconButton(onClick = { navController.navigate(HermiticRoutes.SETTINGS) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Connected to Hermes",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Session list screen coming in HERM-7.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        composable(HermiticRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEditConnection = { navController.navigate(HermiticRoutes.ONBOARDING) },
                onWiped = {
                    navController.navigate(HermiticRoutes.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
