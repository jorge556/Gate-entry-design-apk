package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraOcrScanner(
    onPlateScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Matched scanning overlay state to show feedback
    var detectedTextLog by remember { mutableStateOf("Position a license plate in frame...") }
    var activeDetectedPlate by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    // Preview
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // OCR Frame Analyzer
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, textRecognizer, { plate ->
                            activeDetectedPlate = plate
                            detectedTextLog = "Scan successful: $plate"
                            onPlateScanned(plate)
                        }, { msg ->
                            detectedTextLog = msg
                        })
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        detectedTextLog = "Failed to open camera: ${e.localizedMessage}"
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Overlay Guidelines Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Focus bracket
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(130.dp)
                        .border(
                            width = 3.dp,
                            color = if (activeDetectedPlate != null) Color.Green else MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(Color.Black.copy(alpha = 0.15f))
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Real-time parsed OCR text guidance
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.75f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = detectedTextLog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Top Actions Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live OCR Scanner",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Camera Scanner",
                    tint = Color.White
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    imageProxy: ImageProxy,
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    onPlateFound: (String) -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                val matchedPlates = extractLicensePlatePatterns(rawText)
                if (matchedPlates.isNotEmpty()) {
                    onPlateFound(matchedPlates.first())
                } else {
                    if (rawText.trim().isNotEmpty()) {
                        val previewText = if (rawText.length > 20) rawText.take(20) + "..." else rawText
                        onStatusUpdate("Reading: $previewText")
                    } else {
                        onStatusUpdate("Position a license plate in frame...")
                    }
                }
            }
            .addOnFailureListener { e ->
                onStatusUpdate("Scanning error: ${e.localizedMessage}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

// Regex license plate extractor
private fun extractLicensePlatePatterns(text: String): List<String> {
    // Look for alphanumeric codes between 4 and 10 characters
    // Strips out spaces, common brackets, noise
    val cleanedText = text.uppercase()
    val words = cleanedText.split("\\s+".toRegex())
        .map { word -> word.filter { it.isLetterOrDigit() } }
        .filter { it.length in 4..10 }

    val results = mutableListOf<String>()
    val licensePlateRegex = Regex("^[A-Z0-9]{4,10}\$")
    
    for (word in words) {
        if (licensePlateRegex.matches(word)) {
            // High fidelity rule: must contain at least one digit and one letter to prevent purely matching generic text labels
            val hasDigits = word.any { it.isDigit() }
            val hasLetters = word.any { it.isLetter() }
            if (hasDigits && hasLetters) {
                results.add(word)
            }
        }
    }
    return results
}
