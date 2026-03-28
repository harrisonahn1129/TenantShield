package com.example.tenantshield.ui.screens.owners

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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tenantshield.ui.theme.ErrorColor
import com.example.tenantshield.ui.theme.OnSurfaceVariant
import com.example.tenantshield.ui.theme.OnTertiaryContainer
import com.example.tenantshield.ui.theme.Primary
import com.example.tenantshield.ui.theme.PrimaryContainer
import com.example.tenantshield.ui.theme.SurfaceContainerLow
import com.example.tenantshield.ui.theme.Tertiary
import com.example.tenantshield.ui.theme.TertiaryContainer

@Composable
fun OwnersScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. HEADER
        item {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OWNERSHIP REVEAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "CLASS C HAZARD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Text(
                    text = "Entity Unmasked",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 2. OWNER REVEAL CARD
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF2ECC71),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(32.dp)
            ) {
                Column {
                    Text(
                        text = "BENEFICIAL OWNER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            letterSpacing = 3.sp
                        ),
                        color = Primary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "George Benton",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = PrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Business,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Primary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "via Greenview Holdings LLC",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Primary.copy(alpha = 0.8f)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.BottomEnd),
                    tint = Primary.copy(alpha = 0.2f)
                )
            }
        }

        // 3. STATS GRID
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left: Portfolio Risk
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = SurfaceContainerLow,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .drawBehind {
                            drawLine(
                                color = Primary,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 4.dp.toPx()
                            )
                        }
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "PORTFOLIO RISK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp
                            ),
                            color = OnSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "84%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+12%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp
                                ),
                                color = ErrorColor
                            )
                        }
                        Text(
                            text = "Above average for district 4",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp
                            ),
                            color = OnSurfaceVariant
                        )
                    }
                }

                // Right: Open Litigations
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = SurfaceContainerLow,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .drawBehind {
                            drawLine(
                                color = Tertiary,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 4.dp.toPx()
                            )
                        }
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "OPEN LITIGATIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp
                            ),
                            color = OnSurfaceVariant
                        )
                        Text(
                            text = "14",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Primary
                        )
                        Text(
                            text = "3 active housing court cases",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp
                            ),
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. ACTION BUTTONS
        item {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            ) {
                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    contentPadding = ButtonDefaults.ContentPadding.let {
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 24.dp,
                            vertical = 16.dp
                        )
                    }
                ) {
                    Text(
                        text = "INITIATE ENFORCEMENT PROTOCOL",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.ArrowForward,
                        contentDescription = null
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(2.dp),
                    contentPadding = ButtonDefaults.ContentPadding.let {
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 24.dp,
                            vertical = 12.dp
                        )
                    }
                ) {
                    Text(
                        text = "View Full Dossier",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}
