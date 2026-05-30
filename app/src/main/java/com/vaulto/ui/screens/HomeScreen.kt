package com.vaulto.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.vaulto.data.model.SpaceType
import com.vaulto.ui.components.*
import com.vaulto.ui.theme.*
import com.vaulto.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAddExpense: () -> Unit,
    onAnalytics: () -> Unit,
    onSettings: () -> Unit
) {
    val profile   by viewModel.profile.collectAsState()
    val family    by viewModel.family.collectAsState()
    val space     by viewModel.activeSpace.collectAsState()
    val expenses  by viewModel.expenses.collectAsState()
    val spent     by viewModel.totalSpent.collectAsState()
    val remaining by viewModel.remaining.collectAsState()
    val budget    by viewModel.currentBudget.collectAsState()
    val month     by viewModel.month.collectAsState()
    val year      by viewModel.year.collectAsState()

    // ✅ BUG 14 FIX: Compute whether we're already at the current month so we
    //    can disable the "forward" chevron. Allowing future months is confusing
    //    (they will always show zero expenses) and a poor UX for a finance app.
    val nowCal = remember { Calendar.getInstance() }
    val isCurrentMonth = remember(month, year) {
        month == nowCal.get(Calendar.MONTH) + 1 && year == nowCal.get(Calendar.YEAR)
    }

    val monthLabel = remember(month, year) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
            Calendar.getInstance()
                .apply { set(Calendar.MONTH, month - 1); set(Calendar.YEAR, year) }
                .time
        )
    }

    Scaffold(
        containerColor = Cream,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = onAddExpense,
                containerColor = Saffron,
                contentColor   = Color.White,
                icon           = { Icon(Icons.Rounded.Add, null) },
                text           = { Text("Add Expense", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
                    Column {
                        Text("Vaulto 💰", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text(monthLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Row {
                        // Previous month — always enabled
                        IconButton(onClick = {
                            val c = Calendar.getInstance().apply {
                                set(Calendar.MONTH, month - 1)
                                set(Calendar.YEAR, year)
                                add(Calendar.MONTH, -1)
                            }
                            viewModel.setMonth(c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
                        }) {
                            Icon(Icons.Rounded.ChevronLeft, null, tint = TextSecondary)
                        }
                        // ✅ BUG 14 FIX: Disable the next-month button when already
                        //    on the current month. There are no future expenses to show.
                        IconButton(
                            onClick  = {
                                val c = Calendar.getInstance().apply {
                                    set(Calendar.MONTH, month - 1)
                                    set(Calendar.YEAR, year)
                                    add(Calendar.MONTH, 1)
                                }
                                viewModel.setMonth(c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
                            },
                            enabled = !isCurrentMonth
                        ) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                null,
                                tint = if (isCurrentMonth) TextSecondary.copy(alpha = 0.3f) else TextSecondary
                            )
                        }
                        IconButton(onClick = onAnalytics) {
                            Icon(Icons.Rounded.PieChart, null, tint = Saffron)
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Rounded.Settings, null, tint = TextSecondary)
                        }
                    }
                }
            }

            // ── Space toggle ─────────────────────────────────────────────────
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    Alignment.Center
                ) {
                    SpaceToggle(active = space, onToggle = { viewModel.setSpace(it) })
                }
            }

            // ── Budget card ──────────────────────────────────────────────────
            item {
                val cardTitle = if (space == SpaceType.FAMILY) family?.name ?: "Family" else profile?.name ?: "Personal"
                val cardEmoji = if (space == SpaceType.FAMILY) "👨‍👩‍👧‍👦" else profile?.emoji ?: "👤"
                BudgetCard(
                    spaceType = space,
                    title     = cardTitle,
                    emoji     = cardEmoji,
                    budget    = budget,
                    spent     = spent,
                    remaining = remaining,
                    modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── Quick stats ──────────────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    Arrangement.spacedBy(12.dp)
                ) {
                    MiniStat("📊", "Transactions", expenses.size.toString(), Modifier.weight(1f))
                    MiniStat(
                        "📅", "Daily Avg",
                        formatRupees(
                            if (expenses.isNotEmpty())
                                spent / maxOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH), 1)
                            else 0.0
                        ),
                        Modifier.weight(1f)
                    )
                }
            }

            // ── Expenses ─────────────────────────────────────────────────────
            if (expenses.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("No expenses yet!", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                            Text("Tap + to record your first expense", style = MaterialTheme.typography.bodyMedium, color = TextSecondary.copy(.7f))
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Recent Expenses",
                        style    = MaterialTheme.typography.headlineSmall,
                        color    = TextPrimary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                    )
                }
                // ✅ BUG 7 FIX: The original code had
                //      Spacer(Modifier.height(6.dp).padding(horizontal = 16.dp))
                //    which is wrong — padding on a Spacer is a no-op. The padding
                //    is now correctly applied to the ExpenseRow's own container via
                //    the horizontalPadding modifier passed through the component.
                items(expenses, key = { it.id }) { exp ->
                    ExpenseRow(
                        emoji      = exp.categoryEmoji,
                        category   = exp.categoryName,
                        note       = exp.note,
                        amount     = exp.amount,
                        date       = exp.date,
                        memberName = exp.userName,
                        showMember = space == SpaceType.FAMILY,
                        onDelete   = { viewModel.deleteExpense(exp.id) },
                        modifier   = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun MiniStat(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 2.dp) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(label, style = MaterialTheme.typography.labelSmall,  color = TextSecondary)
            }
        }
    }
}
