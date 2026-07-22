package com.shergill.tryon.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shergill.tryon.render.FilamentAccessoryRenderer
import com.shergill.tryon.tracking.FaceTracker
import com.shergill.tryon.tracking.NoFrontCameraException

@Composable
fun TryOnScreen(
    viewModel: TryOnViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            permissionPermanentlyDenied = true
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.noFrontCamera -> {
                UnsupportedDeviceMessage(
                    message = "This device has no front camera. Virtual try-on is not supported.",
                    onBack = onBack,
                )
            }
            !permissionGranted -> {
                CameraPermissionDenied(
                    permanentlyDenied = permissionPermanentlyDenied,
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                    onBack = onBack,
                )
            }
            else -> {
                val renderer = remember {
                    FilamentAccessoryRenderer(context.applicationContext)
                }
                val tracker: FaceTracker = remember { viewModel.createTracker() }

                DisposableEffect(previewViewRef, surfaceViewRef) {
                    val preview = previewViewRef
                    val surface = surfaceViewRef
                    if (preview != null && surface != null) {
                        renderer.attach(surface)
                        viewModel.attachRenderer(renderer)
                        try {
                            tracker.start(lifecycleOwner, preview) { face ->
                                viewModel.onFrame(face)
                            }
                            viewModel.onTrackerStarted()
                        } catch (e: NoFrontCameraException) {
                            viewModel.onNoFrontCamera()
                        } catch (t: Throwable) {
                            viewModel.handleTrackerStartError(t)
                        }
                    }
                    onDispose {
                        tracker.stop()
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        renderer.destroy()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val filamentSurface = SurfaceView(ctx).apply {
                            setZOrderMediaOverlay(true)
                            holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                        }
                        previewViewRef = previewView
                        surfaceViewRef = filamentSurface
                        FrameLayout(ctx).apply {
                            addView(
                                previewView,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                ),
                            )
                            addView(
                                filamentSurface,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (state.showAlignHint) {
                    Text(
                        text = "Align your face in the frame",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = onBack) { Text("Back", color = Color.White) }
                        Text(
                            text = if (state.faceDetected) {
                                // 478 = full MediaPipe mesh (468 + 10 iris). Not a partial mesh.
                                "Face ✓  mesh=${state.landmarkCount}/478"
                            } else {
                                "Face not detected — center your face"
                            },
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (state.reducedQuality) "LQ" else "HQ",
                            color = Color.White,
                        )
                        Switch(
                            checked = state.reducedQuality,
                            onCheckedChange = viewModel::setReducedQuality,
                        )
                        TextButton(onClick = { showCalibration = !showCalibration }) {
                            Text(if (showCalibration) "Hide" else "Calibrate", color = Color.White)
                        }
                    }
                    if (showCalibration) {
                        CalibrationControls(
                            offsets = state.calibration,
                            onChange = viewModel::updateCalibration,
                            onReset = viewModel::resetCalibration,
                        )
                    }
                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionDenied(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Camera permission is required for virtual try-on.",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequest,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(if (permanentlyDenied) "Open Settings" else "Grant Camera Permission")
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun UnsupportedDeviceMessage(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Back")
        }
    }
}
