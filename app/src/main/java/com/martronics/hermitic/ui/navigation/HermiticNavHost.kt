package com.martronics.hermitic.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.martronics.hermitic.feature.chat.ChatScreen
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
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
            )
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
