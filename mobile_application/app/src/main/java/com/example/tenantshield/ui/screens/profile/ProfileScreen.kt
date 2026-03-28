package com.example.tenantshield.ui.screens.profile

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.example.tenantshield.agents.firebase.FirestoreService
import com.example.tenantshield.agents.models.UserInfo
import com.example.tenantshield.ui.theme.Primary
import com.example.tenantshield.ui.theme.PrimaryContainer
import com.example.tenantshield.ui.theme.PrimaryFixed
import com.example.tenantshield.ui.theme.OnSurfaceVariant
import com.example.tenantshield.ui.theme.SurfaceContainerLow
import com.example.tenantshield.ui.theme.SurfaceContainerLowest
import com.example.tenantshield.ui.theme.OutlineVariant
import com.example.tenantshield.ui.theme.ErrorColor
import com.example.tenantshield.ui.viewmodel.InspectionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    inspectionViewModel: InspectionViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    var userName by remember { mutableStateOf("") }
    var userAddress by remember { mutableStateOf("") }
    var userUnit by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var inspectionHistory by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val firestoreService = remember { FirestoreService() }
    val user = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(Unit) {
        val userId = user?.uid ?: return@LaunchedEffect
        firestoreService.getUserProfile(userId, object : FirestoreService.DataCallback<Map<String, Any>> {
            override fun onSuccess(data: Map<String, Any>) {
                userName = (data["tenant_name"] as? String) ?: ""
                userAddress = (data["address"] as? String) ?: ""
                userUnit = (data["unit_number"] as? String) ?: ""
            }

            override fun onError(message: String) {
                isLoading = false
            }
        })
        firestoreService.getInspectionHistory(userId, object : FirestoreService.DataCallback<List<Map<String, Any>>> {
            override fun onSuccess(data: List<Map<String, Any>>) {
                inspectionHistory = data
                isLoading = false
            }

            override fun onError(message: String) {
                isLoading = false
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.Outlined.Logout,
                            contentDescription = "Sign Out",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(SurfaceContainerLowest),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Section 1: User Info Card
            item(key = "user_info") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Avatar circle
                    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: userName
                    val avatarLetter = displayName.firstOrNull()?.uppercase() ?: "?"

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarLetter,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Display name
                    Text(
                        text = displayName.ifBlank { "Tenant" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Email
                    Text(
                        text = user?.email ?: "No email",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Divider
            item(key = "divider_1") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = OutlineVariant
                )
            }

            // Section 2: Current Address
            item(key = "current_address") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Current Address",
                                style = MaterialTheme.typography.titleLarge,
                                color = Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit Address",
                                tint = Primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditing) {
                        // Editing mode
                        OutlinedTextField(
                            value = userAddress,
                            onValueChange = { userAddress = it },
                            label = { Text("Street Address") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                focusedLabelColor = Primary,
                                cursorColor = Primary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = userUnit,
                            onValueChange = { userUnit = it },
                            label = { Text("Unit Number") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                focusedLabelColor = Primary,
                                cursorColor = Primary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { isEditing = false },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Primary
                                )
                            ) {
                                Text("Cancel")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val userId = user?.uid ?: return@Button
                                    val updatedInfo = UserInfo(
                                        userName,
                                        userAddress,
                                        userUnit,
                                        "",
                                        "",
                                        System.currentTimeMillis()
                                    )
                                    firestoreService.saveUserProfile(
                                        userId,
                                        updatedInfo,
                                        object : FirestoreService.FirestoreCallback {
                                            override fun onSuccess() {
                                                isEditing = false
                                            }

                                            override fun onError(message: String) {
                                                // Keep editing mode open on error
                                            }
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Save")
                            }
                        }
                    } else {
                        // Display mode
                        if (userAddress.isBlank() && userUnit.isBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = SurfaceContainerLow
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "No address registered. Tap edit to add.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = SurfaceContainerLow
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = userAddress,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (userUnit.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Unit $userUnit",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = OnSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Divider
            item(key = "divider_2") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = OutlineVariant
                )
            }

            // Section 3: Inspection History Header
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Inspection History",
                        style = MaterialTheme.typography.titleLarge,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (inspectionHistory.isEmpty()) {
                item(key = "history_empty") {
                    Text(
                        text = if (isLoading) "Loading..." else "No inspection history yet.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(
                    items = inspectionHistory,
                    key = { inspection ->
                        val createdAt = inspection["created_at"]
                        val docId = inspection["document_id"]
                        "${docId ?: ""}_${createdAt ?: System.nanoTime()}"
                    }
                ) { inspection ->
                    InspectionHistoryCard(inspection = inspection)
                }
            }

            // Bottom spacer
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InspectionHistoryCard(inspection: Map<String, Any>) {
    val hazardLevel = (inspection["hazard_level"] as? String) ?: "UNKNOWN"
    val address = (inspection["address"] as? String) ?: "Unknown address"
    val overallSeverity = (inspection["overall_severity"] as? String) ?: ""
    val createdAt = inspection["created_at"]
    val natureOfComplaint = (inspection["nature_of_complaint"] as? String) ?: ""
    val imageUrls = (inspection["image_urls"] as? List<*>) ?: emptyList<Any>()

    // Format timestamp
    val dateString = when (createdAt) {
        is Long -> {
            try {
                val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                sdf.format(Date(createdAt))
            } catch (e: Exception) {
                "Unknown date"
            }
        }
        is Number -> {
            try {
                val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                sdf.format(Date(createdAt.toLong()))
            } catch (e: Exception) {
                "Unknown date"
            }
        }
        else -> "Unknown date"
    }

    // Hazard badge color
    val hazardBadgeColor = when {
        hazardLevel.contains("CLASS_C", ignoreCase = true) -> ErrorColor
        hazardLevel.contains("CLASS_B", ignoreCase = true) -> Color(0xFFE65100) // Orange
        hazardLevel.contains("CLASS_A", ignoreCase = true) -> PrimaryFixed
        else -> OnSurfaceVariant
    }

    val hazardTextColor = when {
        hazardLevel.contains("CLASS_A", ignoreCase = true) -> Primary
        else -> Color.White
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: hazard badge + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hazard level badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(hazardBadgeColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = hazardLevel.replace("_", " "),
                        color = hazardTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = dateString,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Address
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Overall severity
            if (overallSeverity.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Severity: $overallSeverity",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }

            // Nature of complaint
            if (natureOfComplaint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (natureOfComplaint.length > 100) {
                            natureOfComplaint.take(100) + "..."
                        } else {
                            natureOfComplaint
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            // Image count
            if (imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${imageUrls.size} image${if (imageUrls.size != 1) "s" else ""} attached",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}
