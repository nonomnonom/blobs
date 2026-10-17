package dev.blobs.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blobs.app.data.preferences.PreferencesStore
import dev.blobs.app.data.preferences.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BlobsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val preferencesUnavailable: Boolean = false
)

class BlobsViewModel(preferences: PreferencesStore) : ViewModel() {
    val uiState =
        preferences.themeMode
            .map { BlobsUiState(themeMode = it) }
            .catch { error ->
                if (error !is IOException) throw error
                emit(BlobsUiState(preferencesUnavailable = true))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlobsUiState())
}
