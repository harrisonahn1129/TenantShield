package com.example.tenantshield.ui.screens.reports

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.example.tenantshield.agents.firebase.FirestoreService
import com.example.tenantshield.agents.models.ComplaintForm
import com.example.tenantshield.ui.theme.ErrorColor
import com.example.tenantshield.ui.theme.OnPrimary
import com.example.tenantshield.ui.theme.OnSurfaceVariant
import com.example.tenantshield.ui.theme.OutlineVariant
import com.example.tenantshield.ui.theme.Primary
import com.example.tenantshield.ui.theme.PrimaryContainer
import com.example.tenantshield.ui.theme.PrimaryFixed
import com.example.tenantshield.ui.theme.SurfaceContainerHigh
import com.example.tenantshield.ui.theme.SurfaceContainerLow
import com.example.tenantshield.ui.theme.SurfaceContainerLowest
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(modifier: Modifier = Modifier, complaintForm: ComplaintForm? = null) {
    val context = LocalContext.current
    val firestoreService = remember { FirestoreService() }
    var reports by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedReportIndex by remember { mutableStateOf<Int?>(null) }
    val selectedIds = remember { mutableStateListOf<String>() }

    // Load reports from Firestore
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(userId) {
        if (userId != null) {
            firestoreService.getInspectionHistory(userId, object : FirestoreService.DataCallback<List<Map<String, Any>>> {
                override fun onSuccess(data: List<Map<String, Any>>) {
                    reports = data
                    isLoading = false
                }
                override fun onError(message: String) {
                    isLoading = false
                }
            })
        } else {
            isLoading = false
        }
    }

    // If viewing a specific report detail
    if (selectedReportIndex != null && selectedReportIndex!! < reports.size) {
        ReportDetailScreen(
            report = reports[selectedReportIndex!!],
            onBack = { selectedReportIndex = null },
            onDownload = { report ->
                downloadReportPdf(context, report)
            }
        )
        return
    }

    // Report list view
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading reports...", color = OnSurfaceVariant)
                    }
                }
            } else if (reports.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = OnSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No reports yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                        Text(
                            "Complete an inspection to see reports here",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                itemsIndexed(reports, key = { _, report ->
                    (report["_id"] as? String) ?: report.hashCode().toString()
                }) { index, report ->
                    val reportId = (report["_id"] as? String) ?: ""
                    val isSelected = reportId in selectedIds
                    val address = (report["address"] as? String) ?: "Unknown address"
                    val natureOfComplaint = (report["nature_of_complaint"] as? String) ?: ""
                    val hazardLevel = (report["hazard_level"] as? String) ?: ""
                    val createdAt = report["created_at"] as? Long
                    val dateStr = if (createdAt != null) {
                        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(createdAt))
                    } else {
                        "Unknown date"
                    }
                    val docId = (report["document_id"] as? String) ?: ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReportIndex = index }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(SurfaceContainerLowest, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Selection checkbox
                        IconButton(
                            onClick = {
                                if (isSelected) selectedIds.remove(reportId)
                                else selectedIds.add(reportId)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isSelected) Icons.Outlined.CheckBox
                                else Icons.Outlined.CheckBoxOutlineBlank,
                                contentDescription = if (isSelected) "Deselect" else "Select",
                                tint = if (isSelected) Primary else OnSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Hazard badge
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hazardLevel.contains("CLASS_C") -> ErrorColor
                                        hazardLevel.contains("CLASS_B") -> Color(0xFFE67E22)
                                        hazardLevel.contains("CLASS_A") -> Primary
                                        else -> OnSurfaceVariant
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Report info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (docId.isNotEmpty()) "Complaint $docId" else "Inspection Report",
                                style = MaterialTheme.typography.titleSmall,
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Download selected button — only visible when items are selected
        AnimatedVisibility(
            visible = selectedIds.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        val selectedReports = reports.filter { (it["_id"] as? String) in selectedIds }
                        for (report in selectedReports) {
                            downloadReportPdf(context, report)
                        }
                        selectedIds.clear()
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DOWNLOAD ${selectedIds.size} REPORT${if (selectedIds.size > 1) "S" else ""}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportDetailScreen(
    report: Map<String, Any>,
    onBack: () -> Unit,
    onDownload: (Map<String, Any>) -> Unit
) {
    val docId = (report["document_id"] as? String) ?: "N/A"
    val filingDate = (report["filing_date"] as? String) ?: run {
        val createdAt = report["created_at"] as? Long
        if (createdAt != null) SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date(createdAt)).uppercase()
        else "N/A"
    }
    val address = (report["address"] as? String) ?: "N/A"
    val tenantName = (report["tenant_name"] as? String) ?: "N/A"
    val natureOfComplaint = (report["nature_of_complaint"] as? String) ?: "N/A"
    val hazardClass = (report["hazard_class"] as? String) ?: (report["hazard_level"] as? String) ?: ""
    val signatureName = (report["inspector_signature"] as? String) ?: "AI Inspector"
    val rawAnalysis = (report["raw_analysis"] as? String) ?: ""

    Column(modifier = Modifier.fillMaxSize()) {
        // Back bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = Primary)
            }
            Text(
                "Report Detail",
                style = MaterialTheme.typography.titleMedium,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Document content (scrollable)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .background(SurfaceContainerLowest, RoundedCornerShape(2.dp))
                        .border(1.dp, OutlineVariant.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .padding(24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Header
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
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 24.dp),
                            thickness = 2.dp,
                            color = Primary
                        )

                        // Address
                        SectionLabel("ADDRESS:")
                        Text(text = address, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Tenant
                        SectionLabel("TENANT NAME:")
                        Text(text = tenantName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(24.dp))

                        // Hazard
                        if (hazardClass.isNotEmpty()) {
                            SectionLabelWithBar("HAZARD CLASSIFICATION:")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = hazardClass, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ErrorColor)
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Nature of complaint
                        SectionLabelWithBar("NATURE OF COMPLAINT:")
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceContainerLow, RoundedCornerShape(2.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = natureOfComplaint,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // Raw analysis
                        if (rawAnalysis.isNotEmpty()) {
                            SectionLabelWithBar("INSPECTION ANALYSIS:")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = rawAnalysis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Signature
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = signatureName, style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic))
                                HorizontalDivider(modifier = Modifier.padding(end = 16.dp), thickness = 0.5.dp, color = OnSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("CERTIFIED INSPECTOR SIGNATURE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = OnSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Download this report button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { onDownload(report) },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DOWNLOAD THIS REPORT", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp),
        color = Primary
    )
}

@Composable
private fun SectionLabelWithBar(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(2.dp).height(14.dp).background(PrimaryContainer))
        Text(
            text = text,
            modifier = Modifier.padding(start = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp),
            color = Primary
        )
    }
}

private fun downloadReportPdf(context: Context, report: Map<String, Any>) {
    val docId = (report["document_id"] as? String) ?: "report"
    val filingDate = (report["filing_date"] as? String) ?: ""
    val address = (report["address"] as? String) ?: ""
    val tenantName = (report["tenant_name"] as? String) ?: ""
    val natureOfComplaint = (report["nature_of_complaint"] as? String) ?: ""
    val hazardClass = (report["hazard_class"] as? String) ?: (report["hazard_level"] as? String) ?: ""
    val signatureName = (report["inspector_signature"] as? String) ?: "AI Inspector"
    val rawAnalysis = (report["raw_analysis"] as? String) ?: ""

    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
    val page = pdfDocument.startPage(pageInfo)
    val canvas: Canvas = page.canvas

    val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = android.graphics.Color.rgb(0, 28, 62) }
    val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = android.graphics.Color.rgb(0, 28, 62) }
    val bodyPaint = Paint().apply { textSize = 11f; color = android.graphics.Color.rgb(25, 28, 29) }
    val labelPaint = Paint().apply { textSize = 9f; color = android.graphics.Color.rgb(67, 71, 80) }

    var y = 50f
    val x = 40f
    val lineHeight = 16f

    canvas.drawText("OFFICIAL COMPLAINT RECORD", x, y, titlePaint)
    y += lineHeight * 1.5f
    canvas.drawText("Document ID: $docId", x, y, labelPaint)
    y += lineHeight
    canvas.drawText("Date of Filing: $filingDate", x, y, labelPaint)
    y += lineHeight * 2f

    canvas.drawText("ADDRESS:", x, y, headerPaint)
    y += lineHeight
    canvas.drawText(address, x, y, bodyPaint)
    y += lineHeight * 2f

    canvas.drawText("TENANT NAME:", x, y, headerPaint)
    y += lineHeight
    canvas.drawText(tenantName, x, y, bodyPaint)
    y += lineHeight * 2f

    if (hazardClass.isNotEmpty()) {
        canvas.drawText("HAZARD CLASSIFICATION: $hazardClass", x, y, headerPaint)
        y += lineHeight * 2f
    }

    canvas.drawText("NATURE OF COMPLAINT:", x, y, headerPaint)
    y += lineHeight
    // Word wrap the complaint text
    val words = natureOfComplaint.split(" ")
    var line = ""
    for (word in words) {
        val testLine = if (line.isEmpty()) word else "$line $word"
        if (bodyPaint.measureText(testLine) > 515f) {
            canvas.drawText(line, x, y, bodyPaint)
            y += lineHeight
            line = word
        } else {
            line = testLine
        }
        if (y > 780f) break
    }
    if (line.isNotEmpty() && y <= 780f) {
        canvas.drawText(line, x, y, bodyPaint)
        y += lineHeight * 2f
    }

    if (rawAnalysis.isNotEmpty() && y < 700f) {
        canvas.drawText("INSPECTION ANALYSIS:", x, y, headerPaint)
        y += lineHeight
        val analysisWords = rawAnalysis.split(" ")
        var aLine = ""
        for (word in analysisWords) {
            val testLine = if (aLine.isEmpty()) word else "$aLine $word"
            if (bodyPaint.measureText(testLine) > 515f) {
                canvas.drawText(aLine, x, y, bodyPaint)
                y += lineHeight
                aLine = word
            } else {
                aLine = testLine
            }
            if (y > 780f) break
        }
        if (aLine.isNotEmpty() && y <= 780f) {
            canvas.drawText(aLine, x, y, bodyPaint)
            y += lineHeight * 2f
        }
    }

    if (y < 780f) {
        canvas.drawText("Certified Inspector: $signatureName", x, y, labelPaint)
    }

    pdfDocument.finishPage(page)

    // Save to Downloads
    val fileName = "TenantShield_${docId}_${System.currentTimeMillis()}.pdf"
    try {
        val outputStream: OutputStream?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            outputStream = java.io.FileOutputStream(file)
        }

        if (outputStream != null) {
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            Toast.makeText(context, "Report saved to Downloads", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
    }

    pdfDocument.close()
}
