package com.vaulto.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.vaulto.data.model.SpaceType
import com.vaulto.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

fun formatRupees(amount: Double): String {
    val f = NumberFormat.getNumberInstance(Locale("en", "IN"))
    f.maximumFractionDigits = 0
    return "₹${f.format(amount)}"
}

fun formatDate(ts: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(ts))

@Composable
fun SpaceToggle(active: SpaceType, onToggle: (SpaceType) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(44.dp)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            SpaceTab("👨‍👩‍👧‍👦 Family",  SpaceType.FAMILY,   active, onToggle)
            SpaceTab("🔒 Personal", SpaceType.PERSONAL, active, onToggle)
        }
    }
}

@Composable
private fun SpaceTab(label: String, type: SpaceType, active: SpaceType, onToggle: (SpaceType) -> Unit) {
    val bg by animateColorAsState(
        if (active == type) if (type == SpaceType.FAMILY) FamilyBlue else PersonalPurple
        else Color.Transparent, label = "tabBg"
    )
    Surface(
        onClick = { onToggle(type) },
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active == type) Color.White else TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun BudgetCard(
    spaceType: SpaceType,
    title: String,
    emoji: String,
    budget: Double,
    spent: Double,
    remaining: Double,
    modifier: Modifier = Modifier
) {
    val over = remaining < 0
    val progress by animateFloatAsState(
        if (budget > 0) (spent / budget).coerceIn(0.0, 1.0).toFloat() else 0f,
        animationSpec = tween(900, easing = EaseOutCubic), label = "prog"
    )
    val gradColors = when {
        over -> listOf(Color(0xFFD32F2F), Color(0xFFE57373))
        spaceType == SpaceType.FAMILY -> listOf(FamilyBlue, Color(0xFF6AAEFF))
        else -> listOf(PersonalPurple, Color(0xFFCE93D8))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(gradColors, Offset.Zero, Offset(1000f, 400f)))
            .padding(24.dp)
    ) {
        // Decorative circles
        Box(Modifier.size(130.dp).offset(200.dp, (-30).dp).clip(CircleShape).background(Color.White.copy(.07f)))
        Box(Modifier.size(80.dp).offset(260.dp, 50.dp).clip(CircleShape).background(Color.White.copy(.05f)))

        Column {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            if (spaceType == SpaceType.FAMILY) "Family Budget" else "Pocket Money",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.75f)
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(.2f)) {
                    Text(formatRupees(budget), style = MaterialTheme.typography.titleMedium,
                        color = Color.White, modifier = Modifier.padding(12.dp, 6.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (over) "Over by ${formatRupees(-remaining)}" else formatRupees(remaining),
                style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.ExtraBold
            )
            Text("remaining", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(.8f))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.25f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color.White))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Spent: ${formatRupees(spent)}", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(.9f))
                Text("${(progress * 100).toInt()}% used", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(.9f))
            }
        }
    }
}

@Composable
fun ExpenseRow(
    emoji: String, category: String, note: String, amount: Double,
    date: Long, memberName: String, showMember: Boolean,
    onDelete: () -> Unit
) {
    var showDel by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { showDel = !showDel })
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                Alignment.Center
            ) { Text(emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(category, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showMember) Text("$memberName •", style = MaterialTheme.typography.labelSmall, color = FamilyBlue)
                    Text(formatDate(date), style = MaterialTheme.typography.labelSmall, color = TextSecondary.copy(.7f))
                }
            }
            if (showDel) IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, null, tint = Color(0xFFD32F2F))
            }
            Text("- ${formatRupees(amount)}", style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategoryChip(emoji: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Saffron else MaterialTheme.colorScheme.surfaceVariant, label = "chip")
    val tx by animateColorAsState(if (selected) Color.White else TextPrimary, label = "chipTx")
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = bg, modifier = Modifier.height(40.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), Alignment.CenterVertically, Arrangement.spacedBy(6.dp)) {
            Text(emoji, fontSize = 16.sp)
            Text(name, style = MaterialTheme.typography.labelLarge, color = tx)
        }
    }
}
