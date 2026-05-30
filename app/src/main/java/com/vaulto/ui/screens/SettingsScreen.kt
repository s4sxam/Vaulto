// FILE PATH: app/src/main/java/com/vaulto/ui/screens/SettingsScreen.kt

package com.vaulto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.vaulto.ui.components.formatRupees
import com.vaulto.ui.theme.*
import com.vaulto.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private val EMOJIS = listOf(
    "👨", "👩", "👦", "👧", "👴", "👵",
    "🧑", "🧒", "🧔", "👸", "🤴", "🧙", "🦸", "🧝", "🧚"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val profile        by viewModel.profile.collectAsState()
    val family         by viewModel.family.collectAsState()
    val personalBudget by viewModel.personalBudget.collectAsState()

    var personalBudgetInput by remember { mutableStateOf("") }
    var familyBudgetInput   by remember { mutableStateOf("") }
    val clipboard            = LocalClipboardManager.current
    var copied              by remember { mutableStateOf(false) }

    // ✅ FIX: Auto-reset the "copied" checkmark after 2 seconds so the icon
    //    doesn't stay as a permanent checkmark for the rest of the session.
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title          = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } },
                colors         = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Profile ──────────────────────────────────────────────────────
            SettingsSection("Your Profile") {
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(profile?.emoji ?: "👤", fontSize = 36.sp)
                        Column {
                            Text(
                                profile?.name  ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Text(
                                profile?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Change Avatar", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EMOJIS) { emoji ->
                        Surface(
                            onClick  = { viewModel.updateEmoji(emoji) },
                            shape    = RoundedCornerShape(12.dp),
                            color    = if (profile?.emoji == emoji) Saffron
                                       else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }

            // ── Personal budget ──────────────────────────────────────────────
            SettingsSection("🔒 Personal Budget") {
                Text(
                    "Current: ${formatRupees(personalBudget)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value           = personalBudgetInput,
                        onValueChange   = { personalBudgetInput = it },
                        label           = { Text("New Monthly Budget (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier        = Modifier.weight(1f),
                        shape           = RoundedCornerShape(14.dp),
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PersonalPurple,
                            focusedLabelColor  = PersonalPurple
                        ),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            personalBudgetInput.toDoubleOrNull()?.let {
                                viewModel.setPersonalBudget(it)
                                personalBudgetInput = ""
                            }
                        },
                        colors   = ButtonDefaults.buttonColors(containerColor = PersonalPurple),
                        shape    = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) { Text("Set") }
                }
            }

            // ── Family section ───────────────────────────────────────────────
            family?.let { fam ->
                SettingsSection("👨‍👩‍👧‍👦 Family: ${fam.name}") {
                    Surface(shape = RoundedCornerShape(12.dp), color = FamilyBlue.copy(.08f)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Family ID",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = FamilyBlue
                                )
                                Text(
                                    fam.id.take(16) + "…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(fam.id))
                                copied = true
                            }) {
                                Icon(
                                    if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = if (copied) "Copied" else "Copy Family ID",
                                    tint = FamilyBlue
                                )
                            }
                        }
                    }
                    Text(
                        "Share this Family ID with family members so they can join in the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value           = familyBudgetInput,
                            onValueChange   = { familyBudgetInput = it },
                            label           = { Text("Family Budget (₹)") },
                            placeholder     = { Text("Current: ${formatRupees(fam.monthlyBudget)}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier        = Modifier.weight(1f),
                            shape           = RoundedCornerShape(14.dp),
                            colors          = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FamilyBlue,
                                focusedLabelColor  = FamilyBlue
                            ),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                familyBudgetInput.toDoubleOrNull()?.let {
                                    viewModel.updateFamilyBudget(it)
                                    familyBudgetInput = ""
                                }
                            },
                            colors   = ButtonDefaults.buttonColors(containerColor = FamilyBlue),
                            shape    = RoundedCornerShape(14.dp),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) { Text("Update") }
                    }
                }
            }

            // ── Sign out ─────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick  = { viewModel.signOut() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
            ) {
                Icon(Icons.Rounded.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardBg, shadowElevation = 2.dp) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style      = MaterialTheme.typography.titleLarge,
                color      = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = DividerColor)
            content()
        }
    }
}
