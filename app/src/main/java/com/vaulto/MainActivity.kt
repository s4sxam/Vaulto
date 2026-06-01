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
        // Web client ID (type 3) from google-services.json — used by Credential Manager
        // to identify the server-side OAuth 2.0 client that should receive the ID token.
        // This is NOT the Android client ID (type 1).
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
    val navController  = rememberNavController()
    val currentUser    by viewModel.currentUser.collectAsState()
    val family         by viewModel.family.collectAsState()
    val familySkipped  by viewModel.familySkipped.collectAsState()

    // ✅ FIX — Splash gate:
    //    authLoading stays true until Firebase's AuthStateListener fires AND
    //    the Firestore profile load completes. This prevents BOTH:
    //      (a) the login screen flashing for signed-in users, and
    //      (b) the family_setup screen flashing for users who already have a family.
    val authLoading by viewModel.authLoading.collectAsState()

    if (authLoading) {
        Box(
            modifier         = Modifier.fillMaxSize().background(Cream),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Saffron)
        }
        return
    }

    // ── Navigation decision tree ─────────────────────────────────────────────
    //    Single source of truth: drive ALL navigation from here, not from
    //    individual screens. Each LaunchedEffect targets one logical state
    //    transition to avoid ordering bugs between multiple effects.

    // 1. Signed out → login
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // 2. Signed in, has family → home
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

    // 3. Signed in, no family, but user skipped setup → home (personal-only mode)
    LaunchedEffect(familySkipped, currentUser) {
        if (currentUser != null && familySkipped) {
            val current = navController.currentBackStackEntry?.destination?.route
            if (current == "family_setup" || current == "login") {
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Compute start destination once auth is resolved.
    val startDestination = when {
        currentUser == null                    -> "login"
        family != null || familySkipped        -> "home"
        else                                   -> "family_setup"
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
                        // On success LaunchedEffect(family, currentUser) drives navigation.
                    }
                },
                isLoading = isLoading,
                error     = error
            )
        }

        composable("family_setup") {
            FamilySetupScreen(
                viewModel = viewModel,
                onSkip    = { viewModel.skipFamily() }
            )
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
