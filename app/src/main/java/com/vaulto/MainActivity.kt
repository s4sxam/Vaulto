package com.vaulto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaulto.ui.screens.*
import com.vaulto.ui.theme.VaultoTheme
import com.vaulto.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
    val currentUser by viewModel.currentUser.collectAsState()
    val family by viewModel.family.collectAsState()

    val startDestination = if (currentUser != null) {
        if (family == null) "family_setup" else "home"
    } else {
        "login"
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            var isLoading by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            LoginScreen(
                onSignIn = {
                    isLoading = true
                    error = null
                    viewModel.signInWithGoogle("YOUR_WEB_CLIENT_ID_HERE") { success ->
                        isLoading = false
                        if (success) {
                            navController.navigate("family_setup") {
                                popUpTo("login") { inclusive = true }
                            }
                        } else {
                            error = "Sign-in failed. Please try again."
                        }
                    }
                },
                isLoading = isLoading,
                error = error
            )
        }
        composable("family_setup") {
            FamilySetupScreen(viewModel = viewModel)
            LaunchedEffect(family) {
                if (family != null) {
                    navController.navigate("home") {
                        popUpTo("family_setup") { inclusive = true }
                    }
                }
            }
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onAddExpense = { navController.navigate("add_expense") },
                onAnalytics = { navController.navigate("analytics") },
                onSettings = { navController.navigate("settings") }
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
