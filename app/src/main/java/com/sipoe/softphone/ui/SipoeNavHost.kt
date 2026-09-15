package com.sipoe.softphone.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sipoe.softphone.sip.CallController
import com.sipoe.softphone.ui.account.AccountScreen
import com.sipoe.softphone.ui.call.InCallScreen
import com.sipoe.softphone.ui.diagnostics.DiagnosticsScreen
import com.sipoe.softphone.ui.dialer.DialerScreen
import com.sipoe.softphone.ui.history.HistoryScreen

object SipoeRoutes {
    const val DIALER = "dialer"
    const val HISTORY = "history"
    const val ACCOUNT = "account"
    const val CALL = "call"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun SipoeNavHost() {
    val navController = rememberNavController()
    val activeCall by CallController.state.collectAsStateWithLifecycle()

    LaunchedEffect(activeCall) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (activeCall != null && currentRoute != SipoeRoutes.CALL) {
            navController.navigate(SipoeRoutes.CALL) { launchSingleTop = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = SipoeRoutes.DIALER,
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220), initialOffsetX = { it / 6 })
        },
        exitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(160), targetOffsetX = { -it / 8 }) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(160), targetOffsetX = { it / 6 }) },
    ) {
        composable(SipoeRoutes.DIALER) {
            DialerScreen(
                onOpenHistory = { navController.navigate(SipoeRoutes.HISTORY) },
                onOpenAccount = { navController.navigate(SipoeRoutes.ACCOUNT) },
            )
        }
        composable(SipoeRoutes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(SipoeRoutes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onOpenDiagnostics = { navController.navigate(SipoeRoutes.DIAGNOSTICS) },
            )
        }
        composable(SipoeRoutes.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
        composable(SipoeRoutes.CALL) {
            InCallScreen(
                onFinished = {
                    if (navController.currentBackStackEntry?.destination?.route == SipoeRoutes.CALL) {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
