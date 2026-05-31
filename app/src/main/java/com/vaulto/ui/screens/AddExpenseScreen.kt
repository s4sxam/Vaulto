// FILE PATH: app/src/main/java/com/vaulto/ui/screens/AddExpenseScreen.kt

package com.vaulto.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.vaulto.data.model.Category
import com.vaulto.data.model.SpaceType
import com.vaulto.ui.components.*
import com.vaulto.ui.theme.*
import com.vaulto.viewmodel.MainViewModel

// Maximum single-expense amount guard — prevents accidental 7-digit entries.
private const val MAX_EXPENSE_AMOUNT = 999_999.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val categories by viewModel.allCategories.collectAsState()
    val remaining  by viewModel.remaining.collectAsState()
    val space      by viewModel.activeSpace.collectAsState()

    var amount   by remember { mutableStateOf("") }
    var note     by remember { mutableStateOf("") }
    var selCat   by remember { mutableStateOf<Category?>(null) }
    var showNew  by remember { mutableStateOf(false) }
    var newName  by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("Add Expense", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Space badge
            Surface(
                shape = RoundedCornerShape(50),
                color = if (space == SpaceType.FAMILY) FamilyBlue.copy(.12f) else PersonalPurple.copy(.12f)
            ) {
                Text(
                    if (space == SpaceType.FAMILY) "👨‍👩‍👧‍👦  Adding to Family" else "🔒  Adding to Personal",
                    style    = MaterialTheme.typography.labelLarge,
                    color    = if (space == SpaceType.FAMILY) FamilyBlue else PersonalPurple,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            // Remaining hint
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text("💰 Remaining", style = MaterialTheme.typography.titleMedium, color = SaffronDark)
                    Text(
                        formatRupees(remaining),
                        style      = MaterialTheme.typography.titleLarge,
                        color      = if (remaining < 0) Color(0xFFD32F2F) else DeepGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Amount
            OutlinedTextField(
                value           = amount,
                onValueChange   = { v ->
                    // Only allow numeric input with a single decimal point
                    if (v.isEmpty() || v.matches(Regex("^\\d{0,7}(\\.\\d{0,2})?\$"))) {
                        amount = v
                        error  = ""
                    }
                },
                label           = { Text("Amount (₹)") },
                placeholder     = { Text("e.g. 350") },
                leadingIcon     = { Text("₹", modifier = Modifier.padding(start = 8.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(16.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Saffron,
                    focusedLabelColor  = Saffron
                ),
                singleLine = true,
                isError    = error.isNotEmpty()
            )

            // Note
            OutlinedTextField(
                value           = note,
                onValueChange   = { if (it.length <= 100) note = it },
                label           = { Text("Note (optional)") },
                placeholder     = { Text("e.g. Reliance Fresh groceries") },
                leadingIcon     = { Icon(Icons.Rounded.Edit, null) },
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(16.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Saffron,
                    focusedLabelColor  = Saffron
                ),
                singleLine      = true,
                supportingText  = {
                    if (note.length > 80) {
                        Text("${note.length}/100", color = if (note.length == 100) Color(0xFFD32F2F) else TextSecondary)
                    }
                }
            )

            // Categories
            Text("Category", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    CategoryChip(cat.emoji, cat.name, selCat?.id == cat.id) { selCat = cat }
                }
                item {
                    CategoryChip("➕", "Custom", false) { showNew = true }
                }
            }

            // New category form
            AnimatedVisibility(showNew) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("New Category", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                newEmoji, { if (it.length <= 2) newEmoji = it },
                                label    = { Text("Emoji") },
                                modifier = Modifier.width(80.dp),
                                shape    = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                newName, { if (it.length <= 20) newName = it },
                                label    = { Text("Name") },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    viewModel.addCustomCategory(newName.trim(), newEmoji.ifBlank { "🏷️" })
                                    newName = ""; newEmoji = ""; showNew = false
                                }
                            },
                            colors   = ButtonDefaults.buttonColors(containerColor = Saffron),
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Add") }
                    }
                }
            }

            if (error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    when {
                        amt == null || amt <= 0       -> error = "Enter a valid amount greater than ₹0"
                        amt > MAX_EXPENSE_AMOUNT       -> error = "Amount cannot exceed ${formatRupees(MAX_EXPENSE_AMOUNT)}"
                        selCat == null                -> error = "Please select a category"
                        else -> { viewModel.addExpense(selCat!!, amt, note.trim()); onBack() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Saffron)
            ) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Expense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
