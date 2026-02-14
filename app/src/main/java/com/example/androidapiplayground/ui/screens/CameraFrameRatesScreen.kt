package com.example.androidapiplayground.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.androidapiplayground.ui.components.FeatureScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraFrameRatesScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // State for Camera Logic
    var isRunning by remember { mutableStateOf(false) }
    var selectedMinFps by remember { mutableStateOf(30) }
    var selectedMaxFps by remember { mutableStateOf(30) }
    var actualFps by remember { mutableStateOf(0.0) }
    var statusMessage by remember { mutableStateOf("Ready") }

    // Camera 2 Implementation Details
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    var cameraDevice: CameraDevice? by remember { mutableStateOf(null) }
    var captureSession: CameraCaptureSession? by remember { mutableStateOf(null) }
    var textureView: TextureView? by remember { mutableStateOf(null) }

    // FPS Options
    val fpsOptions = listOf(1, 5, 10, 15, 30, 60)

    fun startCamera() {
        if (!hasPermission || textureView == null) return

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return

            val thread = HandlerThread("CameraBackground").apply { start() }
            val backgroundHandler = Handler(thread.looper)

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val surfaceTexture = textureView!!.surfaceTexture
                        surfaceTexture?.setDefaultBufferSize(1920, 1080) // Simplified for playground
                        val surface = Surface(surfaceTexture)

                        val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        previewRequestBuilder.addTarget(surface)

                        // Set FPS Range
                        previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                            Range(selectedMinFps, selectedMaxFps)
                        )

                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    session.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(
                                                session: CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: TotalCaptureResult
                                            ) {
                                                // Extract Frame Duration to calculate FPS roughly or trust metadata if valid
                                                // Note: Real FPS calculation usually done by measuring time between callbacks.
                                                // Here we can try to read metadata if available, but frame duration is more reliable for "actual"
                                                // For "API Status" let's show what the camera *says* it's doing.
                                                // But let's verify via timestamp diff for "Real"
                                                // Simplified for now:
                                                // val frameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION)
                                            }
                                        },
                                        backgroundHandler
                                    )
                                    statusMessage = "Running: Range [$selectedMinFps, $selectedMaxFps]"
                                } catch (e: Exception) {
                                    statusMessage = "Error starting preview: ${e.message}"
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                statusMessage = "Configuration Failed"
                            }
                        }, backgroundHandler)
                    } catch (e: Exception) {
                        statusMessage = "Error: ${e.message}"
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    statusMessage = "Camera Error: $error"
                }
            }, backgroundHandler)
            isRunning = true

        } catch (e: Exception) {
            statusMessage = "Failed to open camera: ${e.message}"
        }
    }

    fun stopCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        isRunning = false
        statusMessage = "Stopped"
    }

    DisposableEffect(Unit) {
        onDispose {
            stopCamera()
        }
    }

    FeatureScaffold(
        title = "Camera FrameRates",
        onBackClick = onBackClick,
        controlsContent = {
            if (hasPermission) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Min FPS Dropdown
                    var minExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = minExpanded,
                        onExpandedChange = { minExpanded = !minExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedMinFps.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Min FPS") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = minExpanded,
                            onDismissRequest = { minExpanded = false }
                        ) {
                            fpsOptions.forEach { fps ->
                                DropdownMenuItem(
                                    text = { Text(fps.toString()) },
                                    onClick = {
                                        selectedMinFps = fps
                                        if (selectedMaxFps < fps) selectedMaxFps = fps
                                        minExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Max FPS Dropdown
                    var maxExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = maxExpanded,
                        onExpandedChange = { maxExpanded = !maxExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedMaxFps.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Max FPS") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = maxExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = maxExpanded,
                            onDismissRequest = { maxExpanded = false }
                        ) {
                            fpsOptions.forEach { fps ->
                                DropdownMenuItem(
                                    text = { Text(fps.toString()) },
                                    onClick = {
                                        selectedMaxFps = fps
                                        if (selectedMinFps > fps) selectedMinFps = fps
                                        maxExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (isRunning) stopCamera() else startCamera()
                        }
                    ) {
                        Text(if (isRunning) "Stop" else "Go")
                    }
                }
            } else {
                Text("Camera permission required", color = MaterialTheme.colorScheme.error)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        },
        outputContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surface: android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    textureView = this@apply
                                }
                                override fun onSurfaceTextureSizeChanged(s: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(s: android.graphics.SurfaceTexture): Boolean = true
                                override fun onSurfaceTextureUpdated(s: android.graphics.SurfaceTexture) {
                                    // Could calculate FPS here based on update frequency
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = statusMessage,
                            color = Color.Green,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Target: [$selectedMinFps, $selectedMaxFps]",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    )
}
