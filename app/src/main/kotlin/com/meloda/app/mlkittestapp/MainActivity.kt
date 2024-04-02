package com.meloda.app.mlkittestapp

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.meloda.app.mlkittestapp.ui.theme.MLKitTestAppTheme
import androidx.camera.core.Preview as CameraPreview

class MainActivity : ComponentActivity() {

    private val cameraExecutor by lazy {
        ContextCompat.getMainExecutor(this)
    }

    private var previewView: PreviewView? = null

    private var cameraProvider: ProcessCameraProvider? = null

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MLKitTestAppTheme {
                val context = LocalContext.current

                val cameraPermission =
                    rememberPermissionState(permission = Manifest.permission.CAMERA)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var cameraEnabled by remember {
                        mutableStateOf(false)
                    }

                    var latestResult: String? by remember {
                        mutableStateOf(null)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    when {
                                        cameraPermission.status.isGranted -> {
                                            Toast.makeText(
                                                context,
                                                "Permission already granted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        else -> cameraPermission.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "Request permission")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Button(
                                    onClick = {
                                        if (!cameraPermission.status.isGranted) {
                                            Toast.makeText(
                                                context,
                                                "Camera permission is missing",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            cameraEnabled = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Start camera")
                                }

                                Button(
                                    onClick = {
                                        cameraEnabled = false
                                        cameraProvider?.unbindAll()
                                        cameraProvider = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Stop camera")
                                }
                            }

                            if (latestResult != null) {
                                Text(text = "Result: $latestResult")
                            }
                        }

                        if (cameraEnabled) {
                            CameraPreview(
                                onResult = { result ->
                                    latestResult = result
                                    cameraEnabled = false

                                    Log.d("StartCamera", "onCreate: latestResult: $latestResult")
                                }
                            )
                        }
                    }
                }
            }
        }
    }


    @Composable
    fun CameraPreview(
        onResult: (String) -> Unit
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current

        val cameraController = LifecycleCameraController(context)
        cameraController.bindToLifecycle(lifecycleOwner)

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        val barcodeScanner = BarcodeScanning.getClient(options)

        var handlingResult = false

        val analyzer = MlKitAnalyzer(
            listOf(barcodeScanner),
            CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
            cameraExecutor
        ) { result: MlKitAnalyzer.Result? ->
            if (handlingResult) return@MlKitAnalyzer
            handlingResult = true

            val barcodeResults = result?.getValue(barcodeScanner)
            if ((barcodeResults == null) ||
                (barcodeResults.size == 0) ||
                (barcodeResults.first() == null)
            ) {
                handlingResult = false
                return@MlKitAnalyzer
            }

            val barcode = barcodeResults[0]

            when (val type = barcode.valueType) {
                Barcode.TYPE_TEXT -> {
                    val resultText = barcode.displayValue ?: "null"
                    Log.d("StartCamera", "StartCamera: result: $resultText")

                    cameraProvider?.unbindAll()
                    cameraProvider = null

                    onResult(resultText)
                }

                else -> {
                    Log.e(
                        "StartCamera",
                        "StartCamera: unsupported type: $type; result: ${barcode.rawValue}"
                    )
                }
            }

            handlingResult = false
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, analyzer)
            }

        cameraController.setImageAnalysisAnalyzer(
            cameraExecutor,
            analyzer
        )

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val addCameraProviderListener: (PreviewView) -> Unit = { previewView ->
            cameraProviderFuture.addListener(
                {
                    cameraProvider = cameraProviderFuture.get()

                    val preview = CameraPreview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider?.unbindAll()

                        cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview, imageAnalyzer
                        )
                    } catch (e: Exception) {
                        Log.e("StartCamera", "StartCamera: error: $e")
                    }
                },
                cameraExecutor
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).also { preview ->
                    preview.controller = cameraController
                    addCameraProviderListener.invoke(preview)

                    previewView = preview
                }
            }
        )
    }


    override fun onDestroy() {
        super.onDestroy()

        previewView = null
    }
}
