package com.example.tenantshield.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tenantshield.ui.theme.ErrorColor
import com.example.tenantshield.ui.theme.OnPrimary
import com.example.tenantshield.ui.theme.OnSurfaceVariant
import com.example.tenantshield.ui.theme.OutlineVariant
import com.example.tenantshield.ui.theme.Primary
import com.example.tenantshield.ui.theme.PrimaryContainer
import com.example.tenantshield.ui.theme.SurfaceContainerHigh
import com.example.tenantshield.ui.theme.SurfaceContainerLow
import com.example.tenantshield.ui.theme.SurfaceContainerLowest
import com.example.tenantshield.agents.models.ComplaintForm

@Composable
fun ReportsScreen(modifier: Modifier = Modifier, complaintForm: ComplaintForm? = null) {
    // Use dynamic data if available, otherwise show placeholder
    val docId = complaintForm?.documentId ?: "TS-99283-X"
    val filingDate = complaintForm?.filingDate ?: "OCTOBER 24, 2023"
    val address = complaintForm?.address ?: "742 Evergreen Terrace,\nSpringfield, IL 62704"
    val tenantName = complaintForm?.tenantName ?: "Simpson, Bartholomew J."
    val natureOfComplaint = complaintForm?.natureOfComplaint
        ?: "Persistent moisture accumulation in the North-facing structural wall of the primary bedroom. Possible mold growth suspected behind the drywall. Landlord notified on 10/12 via certified mail, no remediation initiated as of current date."
    val hazardClass = complaintForm?.hazardClass
    val signatureName = complaintForm?.inspectorSignature ?: "E. Montgomery"
    val verificationText = complaintForm?.verificationToken ?: "Digitally Verified"
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                // DOCUMENT CONTAINER
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .background(
                            color = SurfaceContainerLowest,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = OutlineVariant.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        // a. DOCUMENT HEADER
                        Column {
                            Text(
                                text = "OFFICIAL COMPLAINT RECORD",
                                style = MaterialTheme.typography.titleLarge,
                                color = Primary
                            )
                            Text(
                                text = "DOCUMENT ID: $docId",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "DATE OF FILING",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = filingDate,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 24.dp),
                            thickness = 2.dp,
                            color = Primary
                        )

                        // b. ADDRESS FIELD
                        Text(
                            text = "ADDRESS:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 2.sp
                            ),
                            color = Primary
                        )
                        Text(
                            text = address,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // c. TENANT NAME FIELD
                        Text(
                            text = "TENANT NAME:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 2.sp
                            ),
                            color = Primary
                        )
                        Text(
                            text = tenantName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // d. NATURE OF COMPLAINT
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(14.dp)
                                    .background(PrimaryContainer)
                            )
                            Text(
                                text = "NATURE OF COMPLAINT:",
                                modifier = Modifier.padding(start = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp
                                ),
                                color = Primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = SurfaceContainerLow,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                text = natureOfComplaint,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // e. VIOLATION HISTORY
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(14.dp)
                                    .background(PrimaryContainer)
                            )
                            Text(
                                text = "VIOLATION HISTORY:",
                                modifier = Modifier.padding(start = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp
                                ),
                                color = Primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Entry 1
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ErrorColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "CLASS C: STRUCTURAL HAZARD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "08/15/2023",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp
                                    ),
                                    color = OnSurfaceVariant
                                )
                                Text(
                                    text = "Failing support joists in basement sector 4. Enforcement notice issued.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Entry 2
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "CLASS A: MINOR INFRACTION",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "05/22/2022",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp
                                    ),
                                    color = OnSurfaceVariant
                                )
                                Text(
                                    text = "Unmaintained exterior landscaping. Resolved within 14 days.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // f. EVIDENCE DOCUMENTATION
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(14.dp)
                                    .background(PrimaryContainer)
                            )
                            Text(
                                text = "EVIDENCE DOCUMENTATION:",
                                modifier = Modifier.padding(start = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp
                                ),
                                color = Primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(16f / 9f)
                                    .background(
                                        color = SurfaceContainerHigh,
                                        shape = RoundedCornerShape(2.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "Evidence placeholder",
                                    modifier = Modifier.size(48.dp),
                                    tint = OnSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(16f / 9f)
                                    .background(
                                        color = SurfaceContainerHigh,
                                        shape = RoundedCornerShape(2.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "Evidence placeholder",
                                    modifier = Modifier.size(48.dp),
                                    tint = OnSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))

                        // g. SIGNATURE SECTION
                        HorizontalDivider(
                            color = OutlineVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = signatureName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontStyle = FontStyle.Italic
                                    )
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "CERTIFIED INSPECTOR SIGNATURE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp
                                    ),
                                    color = OnSurfaceVariant
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = verificationText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "VERIFICATION TOKEN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp
                                    ),
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. GENERATE PDF BUTTON
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { /* TODO: Generate PDF */ },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = OnPrimary
                ),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GENERATE PDF REPORT",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
