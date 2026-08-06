package com.centinel.app.ui.scanner

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.centinel.app.data.model.ScanResult
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import com.centinel.app.di.ViewModelFactory
import com.centinel.app.ui.common.ErrorBanner
import com.centinel.app.ui.common.ScanResultCard
import com.centinel.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QrScannerScreenViewModel(private val repo: CentinelRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ScanResult>>(UiState.Idle)
    val state: StateFlow<UiState<ScanResult>> = _state
    var lastDecoded by mutableStateOf<String?>(null); private set

    fun onDecoded(text: String) {
        if (text == lastDecoded) return // avoid re-scanning the same code on every frame
        lastDecoded = text
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val res = repo.scanQr(text)) {
                is ApiResult.Success -> _state.value = UiState.Success(res.data)
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

    Scaffold(topBar = {
        TopAppBar(title = { Text("QR Code Scanner") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!cameraPermission.status.isGranted) {
                Column(Modifier.padding(24.dp)) {
                    Text("Camera permission is required to scan QR codes.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) { Text("Grant Camera Permission") }
                }
            } else if (state is UiState.Idle || state is UiState.Loading) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
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
                    if (state is UiState.Loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter))
                    }
                }
                Text(
                    "Point the camera at a QR code",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                Column(Modifier.padding(16.dp)) {
                    when (val s = state) {
                        is UiState.Success -> ScanResultCard(s.data)
                        is UiState.Error -> ErrorBanner(s.message)
                        else -> {}
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Scan Another Code") }
                }
            }
        }
    }
}
