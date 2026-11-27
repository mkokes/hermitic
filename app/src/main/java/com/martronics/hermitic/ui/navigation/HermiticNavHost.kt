package com.martronics.hermitic.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.martronics.hermitic.feature.onboarding.ConnectionScreen
import com.martronics.hermitic.feature.sessions.SessionListScreen
import com.martronics.hermitic.feature.settings.SettingsScreen

object HermiticRoutes {
    const val ONBOARDING = "onboarding"
    const val SESSIONS = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val SETTINGS = "settings"

    fun chatRoute(sessionId: String): String = "chat/${Uri.encode(sessionId)}"
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
            SessionListScreen(
                onOpenSession = { sessionId ->
                    navController.navigate(HermiticRoutes.chatRoute(sessionId))
                },
                onOpenSettings = {
                    navController.navigate(HermiticRoutes.SETTINGS)
                },
            )
        }

        composable(
            route = HermiticRoutes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = Uri.decode(backStackEntry.arguments?.getString("sessionId").orEmpty())
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Session $sessionId") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        text = "Chat stream UI coming in HERM-9",
                        style = MaterialTheme.typography.titleMedium,
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
