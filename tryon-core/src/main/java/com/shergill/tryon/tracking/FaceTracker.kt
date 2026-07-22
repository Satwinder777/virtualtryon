package com.shergill.tryon.tracking

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.shergill.tryon.domain.FaceFrame

/**
 * Abstraction over face tracking so MediaPipe can later be swapped for
 * ARCore Augmented Faces (or another backend) without touching UI / render code.
 */
interface FaceTracker {
    fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (FaceFrame?) -> Unit,
    )

    fun stop()
}
