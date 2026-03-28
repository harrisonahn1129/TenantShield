package com.example.tenantshield.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tenantshield.ui.theme.OnPrimaryFixedVariant
import com.example.tenantshield.ui.theme.OnTertiaryFixedVariant
import com.example.tenantshield.ui.theme.PrimaryFixed
import com.example.tenantshield.ui.theme.TertiaryFixed

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onStartInspection: () -> Unit = {}) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // ── 1. HERO SECTION ─────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                colorScheme.primary,
                                colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(32.dp)
            ) {
                Column {
                    Text(
                        text = "System\nReady.",
                        style = typography.headlineLarge.copy(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Deploying local oversight for the Bronx District. Current patrol active in Sector 4.",
                        style = typography.bodyMedium,
                        color = colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onStartInspection,
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.surfaceContainerLowest,
                            contentColor = colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START INSPECTION",
                            style = typography.labelLarge
                        )
                    }
                }
            }
        }

        // ── 2. EMERGENCY HOTLINES SECTION ───────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 32.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Emergency Hotlines",
                    style = typography.headlineMedium,
                    color = colorScheme.primary
                )
                Text(
                    text = "REGION: NYC METROPOLITAN",
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        val hotlines = listOf(
            HotlineData(
                title = "Flood & Water Safety",
                status = "Active - Response time < 15m",
                phone = "311-EXT-9",
                label = "Direct Connect",
                icon = Icons.Outlined.WaterDrop,
                iconBg = PrimaryFixed,
                iconTint = colorScheme.primary
            ),
            HotlineData(
                title = "HVAC Critical Failure",
                status = "Critical - High Volume Today",
                phone = "800-SAFE-AIR",
                label = "Emergency Line",
                icon = Icons.Outlined.Air,
                iconBg = TertiaryFixed,
                iconTint = colorScheme.tertiary
            ),
            HotlineData(
                title = "Grid Integrity Taskforce",
                status = "Operational - Status Green",
                phone = "311-EXT-2",
                label = "Support Desk",
                icon = Icons.Outlined.Bolt,
                iconBg = PrimaryFixed,
                iconTint = colorScheme.primary
            )
        )

        items(hotlines.size) { index ->
            val hotline = hotlines[index]
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceContainerLowest)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(hotline.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = hotline.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = hotline.iconTint
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hotline.title,
                        style = typography.titleLarge,
                        color = colorScheme.primary
                    )
                    Text(
                        text = hotline.status,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = hotline.phone,
                        style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.primary
                    )
                    Text(
                        text = hotline.label.uppercase(),
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colorScheme.outline
                    )
                }
            }
        }

        // ── 3. STATS SECTION ────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Priority Alerts
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(TertiaryFixed)
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "PRIORITY ALERTS",
                        style = typography.labelSmall,
                        color = OnTertiaryFixedVariant
                    )
                    Text(
                        text = "14",
                        style = typography.displaySmall.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = colorScheme.tertiary
                    )
                    Text(
                        text = "OPEN VIOLATIONS\nNEARBY",
                        style = typography.labelLarge.copy(fontSize = 13.sp),
                        color = colorScheme.tertiary
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .align(Alignment.BottomEnd),
                    tint = colorScheme.tertiary.copy(alpha = 0.10f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Efficiency Index
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(PrimaryFixed)
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "EFFICIENCY INDEX",
                        style = typography.labelSmall,
                        color = OnPrimaryFixedVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "92",
                            style = typography.displaySmall.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = colorScheme.primary
                        )
                        Text(
                            text = "%",
                            style = typography.displaySmall.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = "COMPLIANCE RATE\nCURRENT ZONE",
                        style = typography.labelLarge.copy(fontSize = 13.sp),
                        color = colorScheme.primary
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.Verified,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .align(Alignment.BottomEnd),
                    tint = colorScheme.primary.copy(alpha = 0.10f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Daily Briefing
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .padding(24.dp)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "Daily Briefing",
                        style = typography.titleLarge,
                        color = colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You have 4 scheduled inspections for today. No severe structural alerts reported since 08:00 AM.",
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "View Schedule",
                        style = typography.labelMedium.copy(
                            textDecoration = TextDecoration.Underline
                        ),
                        color = colorScheme.primary
                    )
                }
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private data class HotlineData(
    val title: String,
    val status: String,
    val phone: String,
    val label: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
