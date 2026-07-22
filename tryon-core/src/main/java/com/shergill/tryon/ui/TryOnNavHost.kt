package com.shergill.tryon.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shergill.tryon.domain.AccessoryType
import java.io.File

/**
 * Top-level navigation host for the try-on flow: form → live try-on.
 */
@Composable
fun TryOnNavHost(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val formViewModel: TryOnFormViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return TryOnFormViewModel(app) as T
            }
        },
    )

    var session by remember { mutableStateOf<TryOnSession?>(null) }

    if (session == null) {
        TryOnFormScreen(
            viewModel = formViewModel,
            onNavigateToTryOn = { file, type ->
                session = TryOnSession(file, type)
            },
            modifier = modifier.fillMaxSize(),
        )
    } else {
        val current = session!!
        val tryOnViewModel: TryOnViewModel = viewModel(
            key = "${current.file.absolutePath}-${current.type}",
            factory = TryOnViewModelFactory(app, current.file, current.type),
        )
        TryOnScreen(
            viewModel = tryOnViewModel,
            onBack = { session = null },
            modifier = modifier.fillMaxSize(),
        )
    }
}

private data class TryOnSession(
    val file: File,
    val type: AccessoryType,
)
