package dev.blobs.app.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.blobs.app.data.preferences.PreferencesStore
import dev.blobs.app.data.preferences.ThemeMode
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlobsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savedAppearanceIsExposedToTheUi() = runTest(dispatcher) {
        val store = ReadOnlyStore(
            flowOf(
                preferencesOf(
                    stringPreferencesKey("theme_mode") to "DARK"
                )
            )
        )
        val model = BlobsViewModel(PreferencesStore(store))
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        assertEquals(ThemeMode.DARK, model.uiState.value.themeMode)
    }

    @Test
    fun unreadablePreferencesShowAProblemWithoutOverwritingData() = runTest(dispatcher) {
        val store = ReadOnlyStore(flow { throw IOException("unavailable storage") })
        val model = BlobsViewModel(PreferencesStore(store))
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        assertTrue(model.uiState.value.preferencesUnavailable)
        assertEquals(ThemeMode.SYSTEM, model.uiState.value.themeMode)
        assertEquals(0, store.writes)
    }

    @Test
    fun unknownFutureThemeKeepsSystemAppearanceWithoutRewritingPreferences() = runTest(dispatcher) {
        val store = ReadOnlyStore(
            flowOf(
                preferencesOf(
                    stringPreferencesKey("theme_mode") to "FUTURE_THEME"
                )
            )
        )
        val model = BlobsViewModel(PreferencesStore(store))
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        assertEquals(ThemeMode.SYSTEM, model.uiState.value.themeMode)
        assertEquals(0, store.writes)
    }

    private class ReadOnlyStore(override val data: Flow<Preferences>) : DataStore<Preferences> {
        var writes = 0

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            writes++
            error("UI observation must not overwrite preferences")
        }
    }
}
