package com.vaulto.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.vaulto.ui.theme.*

@Composable
fun LoginScreen(onSignIn: () -> Unit, isLoading: Boolean, error: String?) {

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        1f, 1.06f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "emojiPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFF8F0), Cream))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("💰", fontSize = 80.sp, modifier = Modifier.scale(scale))

            Spacer(Modifier.height(8.dp))

            Text(
                "Vaulto",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Track family & personal expenses\ntogether — in real time",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Features
            listOf(
                "👨‍👩‍👧‍👦" to "Shared family budget",
                "🔒" to "Private pocket money",
                "📊" to "Spending analytics",
                "☁️" to "Syncs across all phones"
            ).forEach { (emoji, text) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (error != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFEBEE)) {
                    Text(error, color = Color(0xFFD32F2F), modifier = Modifier.padding(12.dp, 8.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = onSignIn,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Saffron)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Text("🔑  Sign in with Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
