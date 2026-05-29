package com.vaulto.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.vaulto.data.model.SpaceType
import com.vaulto.ui.components.formatRupees
import com.vaulto.ui.theme.*
import com.vaulto.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val expenses  by viewModel.expenses.collectAsState()
    val spent     by viewModel.totalSpent.collectAsState()
    val budget    by viewModel.currentBudget.collectAsState()
    val remaining by viewModel.remaining.collectAsState()
    val space     by viewModel.activeSpace.collectAsState()
    val family    by viewModel.family.collectAsState()
    val profile   by viewModel.profile.collectAsState()

    // Group by category
    val byCategory = expenses.groupBy { it.categoryName }
        .mapValues { it.value.sumOf { e -> e.amount } }
        .entries.sortedByDescending { it.value }

    // Group by member (family only)
    val byMember = expenses.groupBy { it.userName }
        .mapValues { it.value.sumOf { e -> e.amount } }
        .entries.sortedByDescending { it.value }

    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text("Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary card
            Surface(shape = RoundedCornerShape(20.dp), color = CardBg, shadowElevation = 3.dp) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        if (space == SpaceType.FAMILY) "👨‍👩‍👧‍👦 ${family?.name ?: "Family"}" else "🔒 ${profile?.name ?: "Personal"}",
                        style = MaterialTheme.typography.titleLarge, color = TextPrimary
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        SummaryCol("Budget", formatRupees(budget), Saffron)
                        Box(Modifier.width(1.dp).height(40.dp).background(DividerColor))
                        SummaryCol("Spent", formatRupees(spent), Color(0xFFD32F2F))
                        Box(Modifier.width(1.dp).height(40.dp).background(DividerColor))
                        SummaryCol("Left", formatRupees(remaining), if (remaining >= 0) DeepGreen else Color(0xFFD32F2F))
                    }
                }
            }

            if (byCategory.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No data yet", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    }
                }
            } else {
                // Category breakdown
                Text("Spending by Category", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Surface(shape = RoundedCornerShape(20.dp), color = CardBg, shadowElevation = 3.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Stacked bar
                        if (spent > 0) {
                            Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(50))) {
                                byCategory.forEachIndexed { i, (_, amt) ->
                                    Box(Modifier.weight((amt / spent).toFloat()).fillMaxHeight().background(BarColors[i % BarColors.size]))
                                }
                            }
                        }
                        byCategory.forEachIndexed { i, (name, amt) ->
                            val cat = expenses.firstOrNull { it.categoryName == name }
                            BreakdownRow(
                                emoji = cat?.categoryEmoji ?: "💰",
                                name = name,
                                amount = amt,
                                percent = if (spent > 0) (amt / spent * 100).toInt() else 0,
                                total = spent,
                                color = BarColors[i % BarColors.size]
                            )
                        }
                    }
                }

                // Member breakdown (family only)
                if (space == SpaceType.FAMILY && byMember.size > 1) {
                    Text("Spending by Member", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Surface(shape = RoundedCornerShape(20.dp), color = CardBg, shadowElevation = 3.dp) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            byMember.forEachIndexed { i, (name, amt) ->
                                val emoji = expenses.firstOrNull { it.userName == name }?.userEmoji ?: "👤"
                                BreakdownRow(
                                    emoji = emoji, name = name, amount = amt,
                                    percent = if (spent > 0) (amt / spent * 100).toInt() else 0,
                                    total = spent, color = BarColors[i % BarColors.size]
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SummaryCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
fun BreakdownRow(emoji: String, name: String, amount: Double, percent: Int, total: Double, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(emoji, fontSize = 16.sp)
                Text(name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$percent%", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Text(formatRupees(amount), style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(color.copy(.15f))) {
            Box(Modifier.fillMaxWidth(if (total > 0) (amount / total).toFloat() else 0f).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
    }
}
