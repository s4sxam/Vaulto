// FILE PATH: app/src/main/java/com/vaulto/ui/screens/FamilySetupScreen.kt

package com.vaulto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.vaulto.ui.theme.*
import com.vaulto.viewmodel.MainViewModel

@Composable
fun FamilySetupScreen(viewModel: MainViewModel) {
    var tab        by remember { mutableStateOf(0) }   // 0 = Create, 1 = Join
    var familyName by remember { mutableStateOf("") }
    var budget     by remember { mutableStateOf("") }
    var joinCode   by remember { mutableStateOf("") }
    var joinError  by remember { mutableStateOf("") }
    var loading    by remember { mutableStateOf(false) }

    val profile by viewModel.profile.collectAsState()

    // ✅ FIX: Scaffold ensures the entire screen gets the Cream background from
    //    the theme (including system bar areas on edge-to-edge builds). Without
    //    this, the OS default white/black background shows through on some devices.
    Scaffold(containerColor = Cream) { padding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("👨‍👩‍👧‍👦", fontSize = 56.sp)
                Text(
                    "Set Up Your Family",
                    style      = MaterialTheme.typography.headlineMedium,
                    color      = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Hi ${profile?.name?.split(" ")?.first() ?: "there"}! " +
                    "Create a new family group or join an existing one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                TabRow(
                    selectedTabIndex = tab,
                    containerColor   = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor     = Saffron,
                    modifier         = Modifier.fillMaxWidth()
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Create Family") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Join Family") })
                }

                if (tab == 0) {
                    // ── Create ───────────────────────────────────────────────
                    OutlinedTextField(
                        value         = familyName,
                        onValueChange = { familyName = it },
                        label         = { Text("Family Name") },
                        placeholder   = { Text("e.g. Sharma Family") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Saffron,
                            focusedLabelColor  = Saffron
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value           = budget,
                        onValueChange   = { budget = it },
                        label           = { Text("Monthly Family Budget (₹)") },
                        placeholder     = { Text("e.g. 30000") },
                        leadingIcon     = { Text("₹", modifier = Modifier.padding(start = 8.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(14.dp),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Saffron,
                            focusedLabelColor  = Saffron
                        ),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (familyName.isNotBlank()) {
                                viewModel.createFamily(familyName.trim(), budget.toDoubleOrNull() ?: 0.0)
                            }
                        },
                        enabled  = familyName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = FamilyBlue)
                    ) { Text("Create Family Group", fontWeight = FontWeight.Bold) }

                } else {
                    // ── Join ─────────────────────────────────────────────────
                    Text(
                        "Ask a family member for their Family ID from the app settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value         = joinCode,
                        onValueChange = { joinCode = it; joinError = "" },
                        label         = { Text("Family ID") },
                        placeholder   = { Text("Paste family ID here") },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(14.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FamilyBlue,
                            focusedLabelColor  = FamilyBlue
                        ),
                        isError    = joinError.isNotEmpty(),
                        singleLine = true
                    )
                    if (joinError.isNotEmpty()) {
                        Text(joinError, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            loading = true
                            viewModel.joinFamily(joinCode.trim()) { success ->
                                loading = false
                                if (!success) joinError = "Family not found. Check the ID and try again."
                            }
                        },
                        enabled  = !loading && joinCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = FamilyBlue)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Text("Join Family", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
