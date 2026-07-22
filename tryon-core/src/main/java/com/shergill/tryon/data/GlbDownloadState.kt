package com.shergill.tryon.data

import java.io.File

sealed class GlbDownloadState {
    data object Idle : GlbDownloadState()
    data class Downloading(val progress: Float) : GlbDownloadState()
    data class Success(val file: File) : GlbDownloadState()
    data class Error(val message: String) : GlbDownloadState()
}
