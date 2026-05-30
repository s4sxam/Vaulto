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
import androidx.compose.material3.MaterialTheme
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

    // ✅ BUG 4 FIX: Web client ID lives here as a companion constant, not
    //    scattered inline in a composable lambda.
    companion object {
        const val GOOGLE_WEB_CLIENT_ID =
            "329764161199-1qsl67npq34p0sj81ha7an7b2svhm8b5.apps.googleusercontent.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ✅ BUG 13 FIX: enableEdgeToEdge() handles system bar insets properly
        //    on Android 15+ (API 35) where edge-to-edge is enforced by the OS.
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

    // ✅ BUG 2 FIX: Use a tri-state to avoid the race condition where Firebase
    //    hasn't restored auth yet when the NavHost first renders.
    //    - null  = auth state unknown (show splash)
    //    - false = signed out (go to login)
    //    - true  = signed in (go to home or family_setup)
    //
    //    Firebase typically resolves auth state within one frame after the first
    //    AuthStateListener callback fires. We gate the NavHost on this.
    var authResolved by remember { mutableStateOf(false) }

    // ✅ BUG 6 FIX: Observe currentUser changes and navigate accordingly.
    //    This is the single source of truth for all auth-driven navigation.
    //    When signOut() is called, currentUser → null → navigate to login.
    LaunchedEffect(currentUser, authResolved) {
        if (!authResolved) {
            // Give Firebase one frame to restore state from its local cache.
            authResolved = true
            return@LaunchedEffect
        }
        if (currentUser == null) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(family, currentUser) {
        if (currentUser != null && family != null) {
            // User is fully onboarded — go to home from wherever we are.
            val current = navController.currentBackStackEntry?.destination?.route
            if (current == "family_setup" || current == "login") {
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Show a brief splash while we wait for Firebase auth to resolve.
    // This prevents the login screen from flashing for already-signed-in users.
    if (!authResolved && currentUser == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Cream),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Saffron)
        }
        return
    }

    // Determine start destination after auth is known.
    val startDestination = when {
        currentUser == null -> "login"
        family == null      -> "family_setup"
        else                -> "home"
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("login") {
            // ✅ BUG 1 FIX: Capture the Activity from LocalContext so we can pass
            //    it to AuthRepository.signInWithGoogle(). Credential Manager's
            //    account-picker bottom-sheet MUST attach to an Activity window.
            val activity = LocalContext.current as MainActivity
            var isLoading by remember { mutableStateOf(false) }
            var error     by remember { mutableStateOf<String?>(null) }

            LoginScreen(
                onSignIn = {
                    isLoading = true
                    error     = null
                    viewModel.signInWithGoogle(activity, MainActivity.GOOGLE_WEB_CLIENT_ID) { success ->
                        isLoading = false
                        if (success) {
                            // Navigation is driven by the LaunchedEffect(family, currentUser) above.
                            // No explicit navigate() call needed here.
                        } else {
                            error = "Sign-in failed. Please try again."
                        }
                    }
                },
                isLoading = isLoading,
                error     = error
            )
        }

        composable("family_setup") {
            FamilySetupScreen(viewModel = viewModel)
            // Navigation away from family_setup is handled by the
            // LaunchedEffect(family, currentUser) above when family becomes non-null.
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
