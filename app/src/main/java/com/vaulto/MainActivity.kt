// FILE PATH: app/src/main/java/com/vaulto/MainActivity.kt

package com.vaulto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaulto.ui.screens.*
import com.vaulto.ui.theme.Cream
import com.vaulto.ui.theme.Saffron
import com.vaulto.ui.theme.VaultoTheme
import com.vaulto.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    companion object {
        const val GOOGLE_WEB_CLIENT_ID =
            "329764161199-1qsl67npq34p0sj81ha7an7b2svhm8b5.apps.googleusercontent.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VaultoTheme {
                VaultoApp(viewModel)
            }
        }
    }
}

@Composable
fun VaultoApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val currentUser   by viewModel.currentUser.collectAsState()
    val family        by viewModel.family.collectAsState()

    // ✅ FIX: authLoading comes from the ViewModel — it turns false only after
    //    Firebase's AuthStateListener fires its first callback. This is more
    //    reliable than a local boolean that flips after one LaunchedEffect frame,
    //    because Firebase can take more than one recomposition to restore the
    //    cached auth token from disk.
    val authLoading by viewModel.authLoading.collectAsState()

    // Show splash while Firebase resolves the cached auth state.
    // This prevents the login screen from flashing for signed-in users.
    if (authLoading) {
        Box(
            modifier           = Modifier.fillMaxSize().background(Cream),
            contentAlignment   = Alignment.Center
        ) {
            CircularProgressIndicator(color = Saffron)
        }
        return
    }

    // Auth is resolved. Drive all navigation from here — single source of truth.
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(family, currentUser) {
        if (currentUser != null && family != null) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current == "family_setup" || current == "login") {
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val startDestination = when {
        currentUser == null -> "login"
        family == null      -> "family_setup"
        else                -> "home"
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("login") {
            val activity  = LocalContext.current as MainActivity
            var isLoading by remember { mutableStateOf(false) }
            var error     by remember { mutableStateOf<String?>(null) }

            LoginScreen(
                onSignIn = {
                    isLoading = true
                    error     = null
                    viewModel.signInWithGoogle(activity, MainActivity.GOOGLE_WEB_CLIENT_ID) { success ->
                        isLoading = false
                        if (!success) error = "Sign-in failed. Please try again."
                        // On success, LaunchedEffect(family, currentUser) drives navigation.
                    }
                },
                isLoading = isLoading,
                error     = error
            )
        }

        composable("family_setup") {
            FamilySetupScreen(viewModel = viewModel)
        }

        composable("home") {
            HomeScreen(
                viewModel    = viewModel,
                onAddExpense = { navController.navigate("add_expense") },
                onAnalytics  = { navController.navigate("analytics") },
                onSettings   = { navController.navigate("settings") }
            )
        }

        composable("add_expense") {
            AddExpenseScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable("analytics") {
            AnalyticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
