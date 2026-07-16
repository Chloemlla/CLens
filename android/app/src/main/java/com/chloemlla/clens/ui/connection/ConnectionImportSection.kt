package com.chloemlla.clens.ui.connection

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chloemlla.clens.core.mongo.MongoUriBuilder
import com.chloemlla.clens.core.mongo.UriImportParser
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Clipboard + QR import controls for the connection editor.
 * On success [onUriImported] receives the parsed URI and optional name from JSON payload.
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ConnectionImportSection(
    enabled: Boolean,
    onUriImported: (uri: String, name: String?) -> Unit,
    onImportMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showScanner = true
        } else {
            onImportMessage("未授予相机权限，无法扫描二维码。")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "快速导入",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "从剪贴板或二维码识别 mongodb:// / mongodb+srv://。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val text = readClipboardText(context)
                    applyImportText(
                        raw = text,
                        emptyMessage = "剪贴板为空。",
                        missingUriMessage = "剪贴板中未找到 MongoDB URI。",
                        onUriImported = onUriImported,
                        onImportMessage = onImportMessage,
                    )
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = "剪贴板",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("从剪贴板导入")
            }
            OutlinedButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        showScanner = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "二维码",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("扫描二维码")
            }
        }
    }

    if (showScanner) {
        QrScanDialog(
            onDismiss = { showScanner = false },
            onRawScanned = { raw ->
                showScanner = false
                applyImportText(
                    raw = raw,
                    emptyMessage = "二维码内容为空。",
                    missingUriMessage = "二维码中未识别到 MongoDB URI。",
                    onUriImported = onUriImported,
                    onImportMessage = onImportMessage,
                )
            },
            onScanError = { message ->
                onImportMessage(message)
            },
        )
    }
}

private fun applyImportText(
    raw: String?,
    emptyMessage: String,
    missingUriMessage: String,
    onUriImported: (uri: String, name: String?) -> Unit,
    onImportMessage: (String) -> Unit,
) {
    if (raw.isNullOrBlank()) {
        onImportMessage(emptyMessage)
        return
    }
    val payload = UriImportParser.parseImportPayload(raw)
    if (payload == null) {
        onImportMessage(missingUriMessage)
        return
    }
    onUriImported(payload.uri, payload.name)
    val masked = MongoUriBuilder.maskUri(payload.uri)
    val nameHint = payload.name?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
    onImportMessage("已导入 URI$nameHint：$masked")
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip: ClipData = clipboard.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun QrScanDialog(
    onDismiss: () -> Unit,
    onRawScanned: (String) -> Unit,
    onScanError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            runCatching {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener(
                    {
                        runCatching { future.get().unbindAll() }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }
        }
    }

    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫描二维码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "将二维码置于取景框内，识别到 Mongo URI 后自动填入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener(
                            {
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }

                                    val resolutionSelector = ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                Size(1280, 720),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                            ),
                                        )
                                        .build()

                                    val analysis = ImageAnalysis.Builder()
                                        .setResolutionSelector(resolutionSelector)
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    val options = BarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(
                                            Barcode.FORMAT_QR_CODE,
                                            Barcode.FORMAT_AZTEC,
                                            Barcode.FORMAT_DATA_MATRIX,
                                        )
                                        .build()
                                    val scanner = BarcodeScanning.getClient(options)

                                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage == null || handled.get()) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees,
                                        )
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                if (handled.get()) return@addOnSuccessListener
                                                val raw = barcodes
                                                    .asSequence()
                                                    .mapNotNull { it.rawValue }
                                                    .firstOrNull { it.isNotBlank() }
                                                if (!raw.isNullOrBlank() && handled.compareAndSet(false, true)) {
                                                    onRawScanned(raw)
                                                }
                                            }
                                            .addOnFailureListener { error ->
                                                if (handled.compareAndSet(false, true)) {
                                                    onScanError(
                                                        "二维码识别失败：" +
                                                            (error.message ?: "unknown"),
                                                    )
                                                    onDismiss()
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    }

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis,
                                    )
                                } catch (error: Exception) {
                                    onScanError("无法启动相机：" + (error.message ?: "unknown"))
                                    onDismiss()
                                }
                            },
                            ContextCompat.getMainExecutor(ctx),
                        )
                        previewView
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}
