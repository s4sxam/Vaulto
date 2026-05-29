package com.vaulto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.vaulto.ui.screens.*
import com.vaulto.ui.theme.VaultoTheme
import com.vaulto.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultoTheme {
                VaultoApp()
            }
        }
    }
}

@Composable
fun VaultoApp() {
    val vm: MainViewModel = viewModel()
    val user   by vm.currentUser.collectAsState()
    val family by vm.family.collectAsState()
    val nav    = rememberNavController()

    // Determine start destination
    val start = when {
        user == null -> "login"
        family == null -> "family_setup"
        else -> "home"
    }

    // React to auth state changes
    LaunchedEffect(user, family) {
        when {
            user == null -> nav.navigate("login") { popUpTo(0) }
            family == null -> nav.navigate("family_setup") { popUpTo(0) }
            else -> nav.navigate("home") { popUpTo(0) }
        }
    }

    var loginLoading by remember { mutableStateOf(false) }
    var loginError   by remember { mutableStateOf<String?>(null) }

    NavHost(nav, startDestination = start) {
        composable("login") {
            LoginScreen(
                onSignIn = {
                    loginLoading = true
                    loginError = null
                    vm.signInWithGoogle(
                        webClientId = "YOUR_WEB_CLIENT_ID_HERE"  // ← replace after Firebase setup
                    ) { success ->
                        loginLoading = false
                        if (!success) loginError = "Sign-in failed. Please try again."
                    }
                },
                isLoading = loginLoading,
                error = loginError
            )
        }
        composable("family_setup") {
            FamilySetupScreen(vm)
        }
        composable("home") {
            HomeScreen(
                viewModel = vm,
                onAddExpense = { nav.navigate("add_expense") },
                onAnalytics  = { nav.navigate("analytics") },
                onSettings   = { nav.navigate("settings") }
            )
        }
        composable("add_expense") {
            AddExpenseScreen(vm) { nav.popBackStack() }
        }
        composable("analytics") {
            AnalyticsScreen(vm) { nav.popBackStack() }
        }
        composable("settings") {
            SettingsScreen(vm) { nav.popBackStack() }
        }
    }
}
