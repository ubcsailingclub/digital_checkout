package com.ubcsc.checkout.ui.admin

import android.Manifest
import android.annotation.SuppressLint
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ubcsc.checkout.ui.theme.LocalKioskColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private data class KioskConfig(
    val waApiKey: String = "",
    val waAccountId: String = "",
    val sheetsScriptUrl: String = "",
    val adminCode: String = ""
)

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeImageProxy(imageProxy: ImageProxy, onQrDetected: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) { imageProxy.close(); return }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    BarcodeScanning.getClient()
        .process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                ?.rawValue
                ?.let(onQrDetected)
        }
        .addOnCompleteListener { imageProxy.close() }
}

@Composable
fun QrScanDialog(
    onResult: (waApiKey: String, waAccountId: String, sheetsScriptUrl: String, adminCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) permissionDenied = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Config QR Code", color = Color.White) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        permissionDenied -> Text(
                            "Camera permission denied. Grant it in device Settings.",
                            color = Color(0xFFCF6679)
                        )
                        !hasCameraPermission -> CircularProgressIndicator(
                            color = LocalKioskColors.current.accent
                        )
                        else -> {
                            val executor   = remember { Executors.newSingleThreadExecutor() }
                            val hasScanned = remember { AtomicBoolean(false) }
                            DisposableEffect(Unit) { onDispose { executor.shutdown() } }

                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val future = ProcessCameraProvider.getInstance(ctx)
                                    future.addListener({
                                        val cameraProvider = future.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val analysis = ImageAnalysis.Builder()
                                            .setTargetResolution(Size(1280, 720))
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                        analysis.setAnalyzer(executor) { imageProxy ->
                                            if (hasScanned.get()) { imageProxy.close(); return@setAnalyzer }
                                            analyzeImageProxy(imageProxy) { raw ->
                                                if (!hasScanned.compareAndSet(false, true)) return@analyzeImageProxy
                                                runCatching {
                                                    Gson().fromJson(raw, KioskConfig::class.java)
                                                }.getOrNull()?.let { cfg ->
                                                    onResult(
                                                        cfg.waApiKey,
                                                        cfg.waAccountId,
                                                        cfg.sheetsScriptUrl,
                                                        cfg.adminCode
                                                    )
                                                }
                                            }
                                        }
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview, analysis
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.e("QrScan", "Camera bind failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Text(
                    "Point the camera at your kiosk config QR code",
                    color = LocalKioskColors.current.textWarm.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalKioskColors.current.textWarm)
            }
        },
        containerColor = Color(0xFF1A2E42)
    )
}
