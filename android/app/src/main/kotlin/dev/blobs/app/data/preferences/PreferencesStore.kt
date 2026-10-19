package dev.blobs.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.blobsPreferences by preferencesDataStore(name = "blobs_preferences")

class PreferencesStore(private val store: DataStore<Preferences>) {
    data class Options(
        val model: String = "",
        val scope: String = "*",
        val welcomed: Boolean = false,
        val reducedMotion: Boolean = false,
        val conversation: String = ""
    )

    val options: Flow<Options> = store.data.map {
        Options(
            it[MODEL].orEmpty(),
            it[SCOPE] ?: "*",
            it[WELCOMED] ?: false,
            it[REDUCED_MOTION] ?: false,
            it[CONVERSATION].orEmpty()
        )
    }

    suspend fun selectModel(value: String) {
        store.edit { it[MODEL] = value }
    }
    suspend fun selectScope(value: String) {
        store.edit { it[SCOPE] = value }
    }
    suspend fun welcome() {
        store.edit { it[WELCOMED] = true }
    }
    suspend fun setReducedMotion(value: Boolean) {
        store.edit { it[REDUCED_MOTION] = value }
    }
    suspend fun selectConversation(value: String) {
        store.edit { it[CONVERSATION] = value }
    }
    val themeMode: Flow<ThemeMode> =
        store.data.map { preferences ->
            ThemeMode.entries.find { it.name == preferences[THEME_MODE] } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val MODEL = stringPreferencesKey("model")
        private val SCOPE = stringPreferencesKey("scope")
        private val WELCOMED = booleanPreferencesKey("welcomed")
        private val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val CONVERSATION = stringPreferencesKey("conversation")

        fun create(context: Context): PreferencesStore =
            PreferencesStore(context.applicationContext.blobsPreferences)
    }
}
