package com.despacho.palletscanner.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun BarcodeScanner(
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // NUEVO: Estados para controlar detecciones múltiples
    var lastDetectedCode by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var detectionTime by remember { mutableStateOf(0L) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // NUEVO: Función wrapper que evita duplicados
    val handleBarcodeDetected: (String) -> Unit = { code: String ->
        val currentTime = System.currentTimeMillis()

        // Solo procesar si:
        // 1. No está procesando actualmente
        // 2. Es un código diferente al anterior
        // 3. Ha pasado al menos 2 segundos desde la última detección
        if (!isProcessing &&
            (lastDetectedCode != code || currentTime - detectionTime > 2000)) {

            isProcessing = true
            lastDetectedCode = code
            detectionTime = currentTime

            Log.d("BarcodeScanner", "🎯 Código detectado y procesado: $code")
            onBarcodeDetected(code)

            // Cerrar cámara inmediatamente después de procesar
            onClose()
        } else {
            Log.d("BarcodeScanner", "🚫 Código ignorado (duplicado): $code")
        }
    }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraPreview(
            onBarcodeDetected = handleBarcodeDetected, // Usar función wrapper
            onClose = onClose,
            context = context,
            lifecycleOwner = lifecycleOwner
        )
    } else {
        PermissionDeniedContent(onClose = onClose)
    }
}

@Composable
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit,
    context: Context,
    lifecycleOwner: LifecycleOwner
) {
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    processImageProxy(imageProxy, onBarcodeDetected)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    // Manejar error
                }

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay con instrucciones y botón cerrar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Botón cerrar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onClose) {
                    Text("Cerrar")
                }
            }

            // Instrucciones
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = "Apunta la cámara hacia el código de barras del pallet",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Se requiere permiso de cámara para escanear códigos de barras",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onClose) {
            Text("Cerrar")
        }
    }
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    onBarcodeDetected: (String) -> Unit
) {
    val inputImage = InputImage.fromMediaImage(
        imageProxy.image!!,
        imageProxy.imageInfo.rotationDegrees
    )

    val scanner = BarcodeScanning.getClient()

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                when (barcode.valueType) {
                    Barcode.TYPE_TEXT -> {
                        barcode.displayValue?.let { value ->
                            onBarcodeDetected(value)
                        }
                    }
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}