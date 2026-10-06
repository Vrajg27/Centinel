package com.centinel.app.ui.scanner

import android.Manifest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centinel.app.data.model.ScanResult
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.ScanResultCard
import com.centinel.app.ui.common.UiState
import com.centinel.app.ui.components.*
import com.centinel.app.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QrScannerScreenViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ScanResult>>(UiState.Idle)
    val state: StateFlow<UiState<ScanResult>> = _state
    var lastDecoded by mutableStateOf<String?>(null); private set

    fun onDecoded(text: String) {
        if (text == lastDecoded) return
        lastDecoded = text
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanQr(text)) {
                is ApiResult.Success -> {
                    _state.value = UiState.Success(res.data)
                    repo.getAnalytics() // Live update global stats
                }
                is ApiResult.Error -> _state.value = UiState.Error(res.message)
            }
        }
    }

    fun reset() {
        lastDecoded = null
        _state.value = UiState.Idle
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(repo: CentinelRepository, onBack: () -> Unit) {
    val vm: QrScannerScreenViewModel = viewModel(factory = ViewModelFactory(repo))
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val state by vm.state.collectAsState()

    CentinelScannerBase(
        title = "QR Decryptor",
        subtitle = "Safely analyze optical data blocks for threats.",
        icon = Icons.Default.QrCodeScanner,
        onBack = onBack
    ) {
        if (!cameraPermission.status.isGranted) {
            CentinelGlassCard(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                glowColor = WarningOrange.copy(alpha = 0.2f)
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("PERMISSION REQUIRED", style = MaterialTheme.typography.labelSmall, color = WarningOrange, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Clearance for neural camera input is needed to process QR identifiers.", color = TextSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(24.dp))
                    CentinelButton("Grant Clearance", onClick = { cameraPermission.launchPermissionRequest() }, colors = listOf(WarningOrange, Color(0xFF996600)))
                }
            }
        } else if (state is UiState.Idle || state is UiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(SurfaceVariant)
                    .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            val scanner = BarcodeScanning.getClient()
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            barcodes.firstOrNull()?.rawValue?.let { vm.onDecoded(it) }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                                )
                            } catch (_: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
                
                // Redesigned Scanning Frame
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.7f)
                        .align(Alignment.Center)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(CyanGradient),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    // Corner Accents
                    Box(Modifier.size(30.dp).align(Alignment.TopStart).border(4.dp, AccentCyan, RoundedCornerShape(topStart = 24.dp)))
                    Box(Modifier.size(30.dp).align(Alignment.TopEnd).border(4.dp, AccentCyan, RoundedCornerShape(topEnd = 24.dp)))
                    Box(Modifier.size(30.dp).align(Alignment.BottomStart).border(4.dp, AccentCyan, RoundedCornerShape(bottomStart = 24.dp)))
                    Box(Modifier.size(30.dp).align(Alignment.BottomEnd).border(4.dp, AccentCyan, RoundedCornerShape(bottomEnd = 24.dp)))
                }

                if (state is UiState.Loading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan, strokeWidth = 3.dp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Align protocol target within the scanning matrix",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                letterSpacing = 1.sp
            )
        } else {
            when (val s = state) {
                is UiState.Success<com.centinel.app.data.model.ScanResult> -> ScanResultCard(s.data)
                is UiState.Error -> ErrorBanner(s.message)
                else -> {}
            }
            Spacer(Modifier.height(32.dp))
            CentinelButton(
                "Reset Scanner",
                onClick = { vm.reset() },
                icon = Icons.Default.Refresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
