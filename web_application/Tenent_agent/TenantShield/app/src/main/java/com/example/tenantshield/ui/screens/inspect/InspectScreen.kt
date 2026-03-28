package com.example.tenantshield.ui.screens.inspect

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import com.example.tenantshield.agents.orchestrator.OrchestratorState
import com.example.tenantshield.ui.theme.Primary
import com.example.tenantshield.ui.theme.PrimaryFixed
import com.example.tenantshield.ui.theme.SecondaryContainer
import com.example.tenantshield.ui.theme.OnSecondaryContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.tenantshield.ui.viewmodel.ConversationMessage
import com.example.tenantshield.ui.viewmodel.InspectionViewModel
import java.io.File

private fun loadOrientedBitmap(filePath: String): ImageBitmap? {
    val raw = BitmapFactory.decodeFile(filePath) ?: return null
    val exif = ExifInterface(filePath)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    return if (rotation == 0f) {
        raw.asImageBitmap()
    } else {
        val matrix = Matrix().apply { postRotate(rotation) }
        android.graphics.Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            .asImageBitmap()
    }
}

@Composable
fun InspectScreen(
    modifier: Modifier = Modifier,
    inspectionViewModel: InspectionViewModel? = null,
    onClose: () -> Unit = {}
) {
    val inspectionUiState = inspectionViewModel?.uiState?.collectAsState()?.value
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    // Mic permission state
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var showMicPermissionDialog by remember { mutableStateOf(!hasMicPermission) }
    var micPermissionDenied by remember { mutableStateOf(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showFlash by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var showSentConfirm by remember { mutableStateOf(false) }
    val capturedFilePaths = remember { mutableStateListOf<String>() }
    var selectedForSubmission = remember { mutableStateListOf<String>() }

    // Flash effect timer
    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(150)
            showFlash = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) cameraPermissionDenied = true
        // After camera permission resolved, check mic permission
        if (granted && !hasMicPermission) {
            showMicPermissionDialog = true
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) micPermissionDenied = true
    }

    // When transitioning to AWAITING_IMAGES, prompt for camera permission if needed
    val pipelineState = inspectionUiState?.pipelineState
    LaunchedEffect(pipelineState) {
        if (pipelineState == OrchestratorState.AWAITING_IMAGES && !hasCameraPermission) {
            showCameraPermissionDialog = true
        }
    }

    // Permission consent dialog
    if (showCameraPermissionDialog && !hasCameraPermission) {
        AlertDialog(
            onDismissRequest = {
                showCameraPermissionDialog = false
                if (!hasCameraPermission) onClose()
            },
            icon = {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Camera Access Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Primary
                )
            },
            text = {
                Text(
                    "TenantShield needs access to your camera to perform live building inspections and scan property conditions. Your camera feed is processed on-device and is not stored or transmitted.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCameraPermissionDialog = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) {
                    Text("ALLOW CAMERA", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCameraPermissionDialog = false
                    onClose()
                }) {
                    Text("DENY", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Camera permission denied dialog
    if (cameraPermissionDenied && !hasCameraPermission) {
        AlertDialog(
            onDismissRequest = { onClose() },
            title = {
                Text(
                    "Camera Permission Denied",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Primary
                )
            },
            text = {
                Text(
                    "The inspection feature requires camera access to function. You can grant camera permission in your device Settings > Apps > TenantShield > Permissions.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { onClose() }) {
                    Text("GO BACK", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
        return
    }

    // Mic permission consent dialog
    if (showMicPermissionDialog && !hasMicPermission) {
        AlertDialog(
            onDismissRequest = {
                showMicPermissionDialog = false
            },
            icon = {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Microphone Access",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Primary
                )
            },
            text = {
                Text(
                    "TenantShield uses your microphone to collect inspection details through voice conversation. You can speak your name, address, and complaint instead of typing. Audio is processed on-device for speech recognition and is not recorded or stored.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMicPermissionDialog = false
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text("ALLOW MICROPHONE", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMicPermissionDialog = false
                }) {
                    Text("SKIP", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Mic permission denied info (non-blocking)
    if (micPermissionDenied && !hasMicPermission) {
        AlertDialog(
            onDismissRequest = { micPermissionDenied = false },
            title = {
                Text(
                    "Microphone Not Available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Primary
                )
            },
            text = {
                Text(
                    "Voice input is disabled. You can still type your responses manually in the text field. To enable voice, grant microphone permission in Settings > Apps > TenantShield > Permissions.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { micPermissionDenied = false }) {
                    Text("OK", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // "Sent to LLM" confirmation dialog
    if (showSentConfirm) {
        val count = selectedForSubmission.size
        AlertDialog(
            onDismissRequest = { showSentConfirm = false },
            icon = {
                Icon(
                    Icons.Outlined.Send,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    if (count == 1) "Image Submitted" else "$count Images Submitted",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Primary
                )
            },
            text = {
                Text(
                    if (count == 1)
                        "The captured image has been sent to the AI for inspection analysis. Results will appear shortly."
                    else
                        "All $count captured images have been sent to the AI for inspection analysis. Results will appear shortly.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSentConfirm = false
                    // Clean up unselected files
                    capturedFilePaths.filter { it !in selectedForSubmission }.forEach { File(it).delete() }
                    capturedFilePaths.clear()
                    selectedForSubmission.clear()
                    // TODO: navigate to results screen with selectedForSubmission
                }) {
                    Text("OK", color = Primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // ── CONVERSATION SCREEN (shown during COLLECTING_INFO) ─────────────
    val showConversation = inspectionUiState != null && (
        inspectionUiState.pipelineState == OrchestratorState.COLLECTING_INFO
        || (inspectionUiState.pipelineState == OrchestratorState.ERROR && inspectionUiState.userInfo == null)
    )
    if (showConversation) {
        ConversationScreen(
            inspectionUiState = inspectionUiState,
            inspectionViewModel = inspectionViewModel!!,
            onClose = onClose
        )
        return
    }

    // ── REVIEW SCREEN ─────────────────────────────────────────────────
    if (showReview && capturedFilePaths.isNotEmpty()) {
        CaptureReviewScreen(
            filePaths = capturedFilePaths.toList(),
            onRetake = {
                val lastPath = capturedFilePaths.removeLastOrNull()
                if (lastPath != null) File(lastPath).delete()
                showReview = false
            },
            onCaptureMore = {
                showReview = false
            },
            onUse = { selectedPaths ->
                selectedForSubmission.clear()
                selectedForSubmission.addAll(selectedPaths)
                showReview = false
                if (inspectionViewModel != null) {
                    inspectionViewModel.submitImages(selectedPaths)
                } else {
                    showSentConfirm = true
                }
            }
        )
        return
    }

    // ── MAIN CAMERA SCREEN ────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF16213E))
    ) {
        // Camera preview
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        imageCapture = capture
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(context))
                }
            )
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.30f), CircleShape)
            ) {
                Icon(Icons.Outlined.Close, "Close", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unit 402 — Live Scan",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "BUILDING ID: TS-9921",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    letterSpacing = 2.sp
                )
            }
            IconButton(
                onClick = {
                    camera?.let {
                        if (it.cameraInfo.hasFlashUnit()) {
                            isFlashOn = !isFlashOn
                            it.cameraControl.enableTorch(isFlashOn)
                        }
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isFlashOn) Color.White.copy(alpha = 0.30f)
                        else Color.Black.copy(alpha = 0.30f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Outlined.FlashOn, "Flashlight",
                    tint = if (isFlashOn) Color.Yellow else Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SecondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, "Profile", tint = OnSecondaryContainer)
            }
        }

        // Scanning corners
        val cornerColor = PrimaryFixed
        val strokeW = 3.dp
        Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 36.dp, top = 152.dp).size(40.dp)
            .drawBehind { val s = strokeW.toPx(); drawLine(cornerColor, Offset(0f, s/2), Offset(size.width, s/2), s); drawLine(cornerColor, Offset(s/2, 0f), Offset(s/2, size.height), s) })
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 36.dp, top = 152.dp).size(40.dp)
            .drawBehind { val s = strokeW.toPx(); drawLine(cornerColor, Offset(0f, s/2), Offset(size.width, s/2), s); drawLine(cornerColor, Offset(size.width - s/2, 0f), Offset(size.width - s/2, size.height), s) })
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 280.dp).size(40.dp)
            .drawBehind { val s = strokeW.toPx(); drawLine(cornerColor, Offset(0f, size.height - s/2), Offset(size.width, size.height - s/2), s); drawLine(cornerColor, Offset(s/2, 0f), Offset(s/2, size.height), s) })
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 280.dp).size(40.dp)
            .drawBehind { val s = strokeW.toPx(); drawLine(cornerColor, Offset(0f, size.height - s/2), Offset(size.width, size.height - s/2), s); drawLine(cornerColor, Offset(size.width - s/2, 0f), Offset(size.width - s/2, size.height), s) })

        // Capture count badge
        if (capturedFilePaths.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 72.dp, end = 16.dp)
                    .background(Primary.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${capturedFilePaths.size} photo${if (capturedFilePaths.size > 1) "s" else ""} captured",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Bottom section: text + capture button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .background(Primary.copy(alpha = 0.80f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE FEED ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        letterSpacing = 3.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scan for property records...",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = {
                        if (isCapturing) return@FloatingActionButton
                        val capture = imageCapture ?: return@FloatingActionButton
                        isCapturing = true

                        val photoFile = File(
                            context.cacheDir,
                            "inspection_${System.currentTimeMillis()}.jpg"
                        )
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    capturedFilePaths.add(photoFile.absolutePath)
                                    isCapturing = false
                                    showFlash = true
                                    showReview = true
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                }
                            }
                        )
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    containerColor = Color.White,
                    contentColor = Primary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Capture",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Conversation overlay
        if (inspectionUiState != null) {
            val state = inspectionUiState.pipelineState
            val error = inspectionUiState.errorMessage
            val messages = inspectionUiState.conversationMessages
            val isListening = inspectionUiState.isListening
            val isSpeaking = inspectionUiState.isSpeaking
            val awaitingConfirmation = inspectionUiState.awaitingUserConfirmation
            val transcript = inspectionUiState.lastUserTranscript
            var editableText by remember(transcript) { mutableStateOf(transcript) }

            val showOverlay = state == OrchestratorState.COLLECTING_INFO
                || state == OrchestratorState.EXPLAINING_RESULTS
                || state == OrchestratorState.INSPECTING
                || state == OrchestratorState.FILING
                || error != null

            if (showOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 140.dp, start = 12.dp, end = 12.dp)
                        .background(Primary.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        // Status header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            error != null -> Color(0xFFBA1A1A)
                                            isListening -> Color(0xFF2ECC71)
                                            isSpeaking -> Color.Yellow
                                            else -> Color.White
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    error != null -> "ERROR"
                                    state == OrchestratorState.COLLECTING_INFO && isListening -> "LISTENING..."
                                    state == OrchestratorState.COLLECTING_INFO && isSpeaking -> "SPEAKING..."
                                    state == OrchestratorState.COLLECTING_INFO && awaitingConfirmation -> "CONFIRM YOUR RESPONSE"
                                    state == OrchestratorState.COLLECTING_INFO -> "COLLECTING INFO..."
                                    state == OrchestratorState.INSPECTING -> "ANALYZING IMAGES..."
                                    state == OrchestratorState.EXPLAINING_RESULTS -> "EXPLAINING RESULTS..."
                                    state == OrchestratorState.FILING -> "GENERATING REPORT..."
                                    else -> "PROCESSING..."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Error display
                        if (error != null) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFB4AC)
                            )
                        }

                        // Conversation messages (scrollable, max height)
                        if (messages.isNotEmpty()) {
                            val listState = rememberLazyListState()
                            LaunchedEffect(messages.size) {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(messages.size) { index ->
                                    val msg = messages[index]
                                    val isAgent = msg.sender == "agent"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isAgent) Color.White.copy(alpha = 0.15f)
                                                    else Color(0xFF2ECC71).copy(alpha = 0.3f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                                .weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // User transcript confirmation (editable text field + send/retry)
                        if (awaitingConfirmation && state == OrchestratorState.COLLECTING_INFO) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Your response (edit if needed):",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White.copy(alpha = 0.5f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    cursorColor = Color.White
                                ),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Re-speak button
                                OutlinedButton(
                                    onClick = { inspectionViewModel?.retryListening() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("RE-SPEAK", style = MaterialTheme.typography.labelSmall)
                                }
                                // Send button
                                Button(
                                    onClick = {
                                        if (editableText.isNotBlank()) {
                                            inspectionViewModel?.confirmUserInput(editableText)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    enabled = editableText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2ECC71),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Outlined.Send, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SEND", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Flash effect
        AnimatedVisibility(visible = showFlash, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }
    }
}

@Composable
private fun ConversationScreen(
    inspectionUiState: InspectionViewModel.UiState,
    inspectionViewModel: InspectionViewModel,
    onClose: () -> Unit
) {
    val messages = inspectionUiState.conversationMessages
    val isListening = inspectionUiState.isListening
    val isSpeaking = inspectionUiState.isSpeaking
    val awaitingConfirmation = inspectionUiState.awaitingUserConfirmation
    val transcript = inspectionUiState.lastUserTranscript
    val error = inspectionUiState.errorMessage
    var editableText by remember(transcript) { mutableStateOf(transcript) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "TenantShield Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            error != null -> "ERROR"
                            isListening -> "LISTENING..."
                            isSpeaking -> "SPEAKING..."
                            awaitingConfirmation -> "REVIEW YOUR RESPONSE"
                            else -> "COLLECTING INFORMATION"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 2.sp
                    )
                }
            }

            // Status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                error != null -> Color(0xFFBA1A1A)
                                isListening -> Color(0xFF2ECC71)
                                isSpeaking -> Color.Yellow
                                else -> Color.White.copy(alpha = 0.5f)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when {
                        error != null -> "An error occurred"
                        isListening -> "Listening to your response..."
                        isSpeaking -> "Assistant is speaking..."
                        awaitingConfirmation -> "Please confirm or edit your response below"
                        messages.isEmpty() -> "Connecting to assistant..."
                        else -> "Processing..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error display
            if (error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(Color(0xFFBA1A1A).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4AC))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Conversation messages (scrollable, takes remaining space)
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(messages.size) { index ->
                    val msg = messages[index]
                    val isAgent = msg.sender == "agent"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    if (isAgent) Color.White.copy(alpha = 0.12f)
                                    else Color(0xFF2ECC71).copy(alpha = 0.25f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .weight(1f, fill = false)
                        ) {
                            Text(
                                text = if (isAgent) "Assistant" else "You",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAgent) Color.White.copy(alpha = 0.5f)
                                else Color(0xFF2ECC71).copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Input area at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(16.dp)
            ) {
                if (awaitingConfirmation) {
                    // Editable transcript
                    Text(
                        text = "Your response (edit if needed):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editableText,
                        onValueChange = { editableText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color.White
                        ),
                        maxLines = 3,
                        placeholder = {
                            Text("Type your response here...",
                                color = Color.White.copy(alpha = 0.3f))
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { inspectionViewModel.retryListening() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Outlined.Mic, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RE-SPEAK", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = {
                                if (editableText.isNotBlank()) {
                                    inspectionViewModel.confirmUserInput(editableText)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = editableText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2ECC71),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Outlined.Send, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SEND", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else if (isListening) {
                    // Listening indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = Color(0xFF2ECC71),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Speak now...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                } else if (isSpeaking) {
                    // Speaking indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Assistant is speaking...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Waiting state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (messages.isEmpty()) "Connecting..." else "Processing...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureReviewScreen(
    filePaths: List<String>,
    onRetake: () -> Unit,
    onCaptureMore: () -> Unit,
    onUse: (selectedPaths: List<String>) -> Unit
) {
    val bitmaps = remember(filePaths) {
        filePaths.mapNotNull { path ->
            loadOrientedBitmap(path)?.let { path to it }
        }
    }

    // All images selected by default
    val selectedPaths = remember(filePaths) {
        filePaths.toMutableStateList()
    }

    val pagerState = rememberPagerState(
        initialPage = bitmaps.lastIndex.coerceAtLeast(0),
        pageCount = { bitmaps.size }
    )

    val selectedCount = selectedPaths.size
    val hasMultiple = filePaths.size > 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Image pager with selection overlay
        if (bitmaps.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val path = bitmaps[page].first
                val isSelected = path in selectedPaths

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (hasMultiple) Modifier.clickable {
                                if (isSelected) selectedPaths.remove(path)
                                else selectedPaths.add(path)
                            } else Modifier
                        )
                ) {
                    Image(
                        bitmap = bitmaps[page].second,
                        contentDescription = "Captured photo ${page + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (hasMultiple && !isSelected)
                                    Modifier.background(Color.Black.copy(alpha = 0.5f))
                                else Modifier
                            ),
                        contentScale = ContentScale.Fit,
                        alpha = if (hasMultiple && !isSelected) 0.4f else 1f
                    )

                    // Selection indicator (only for multiple images)
                    if (hasMultiple) {
                        Icon(
                            imageVector = if (isSelected) Icons.Outlined.CheckCircle
                            else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 72.dp, end = 24.dp)
                                .size(32.dp)
                        )
                    }
                }
            }
        }

        // Top bar: label + page indicator
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "REVIEW CAPTURE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    letterSpacing = 3.sp
                )
            }
            if (hasMultiple) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${bitmaps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Page dots with selection state
        if (hasMultiple) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bitmaps.forEachIndexed { index, (path, _) ->
                    val isCurrent = index == pagerState.currentPage
                    val isSelected = path in selectedPaths
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent && isSelected -> Color(0xFF2ECC71)
                                    isCurrent && !isSelected -> Color.White
                                    isSelected -> Color(0xFF2ECC71).copy(alpha = 0.5f)
                                    else -> Color.White.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
            }
        }

        // Selection hint for multiple images
        if (hasMultiple) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp)
                    .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Tap image to select/deselect  •  $selectedCount of ${filePaths.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Bottom buttons
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.70f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        !hasMultiple -> "Use this photo for inspection?"
                        selectedCount == 0 -> "Select photos to use for inspection"
                        selectedCount == 1 -> "Use 1 selected photo for inspection?"
                        else -> "Use $selectedCount selected photos for inspection?"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Row 1: Retake + Capture More
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "RETAKE",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onCaptureMore,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, PrimaryFixed.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            Icons.Outlined.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MORE",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2: Use selected photos
                Button(
                    onClick = { onUse(selectedPaths.toList()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Color.White,
                        disabledContainerColor = Primary.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = when {
                            selectedCount == 0 -> "SELECT PHOTOS"
                            selectedCount == 1 -> "USE PHOTO"
                            else -> "USE $selectedCount PHOTOS"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
