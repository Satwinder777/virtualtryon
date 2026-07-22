package com.shergill.virtualtryon.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.shergill.tryon.tracking.MediaPipeFaceTracker
import com.shergill.virtualtryon.ui.theme.VirtualTryOnTheme

/**
 * Temporary debug Activity: prints landmark count + face detected status.
 * Launch via adb:
 *   adb shell am start -n com.shergill.virtualtryon/.debug.FaceTrackingDebugActivity
 */
class FaceTrackingDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VirtualTryOnTheme {
                FaceTrackingDebugScreen()
            }
        }
    }
}

@Composable
private fun FaceTrackingDebugScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf("face detected: false\nlandmarks: 0") }
    val tracker = remember { MediaPipeFaceTracker(context.applicationContext) }

    DisposableEffect(Unit) {
        onDispose { tracker.release() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    tracker.start(lifecycleOwner, preview) { face ->
                        status = if (face == null) {
                            "face detected: false\nlandmarks: 0"
                        } else {
                            "face detected: ${face.faceDetected}\nlandmarks: ${face.landmarks.size}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = status,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
        )
    }
}
