package com.shavlegi.fakegps

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FavoritePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class LastPoint(
    val latitude: Double,
    val longitude: Double
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fake_gps_prefs"
)

/**
 * Favorites, last picked point and language choice, stored locally
 * on the phone (DataStore). Nothing is sent anywhere.
 */
class FavoritesStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val favoritesKey = stringPreferencesKey("favorites_json")
    private val lastPointKey = stringPreferencesKey("last_point_json")
    private val languageKey = stringPreferencesKey("language")

    val favorites: Flow<List<FavoritePlace>> = context.dataStore.data.map { prefs ->
        val raw = prefs[favoritesKey] ?: return@map emptyList()
        try {
            json.decodeFromString<List<FavoritePlace>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val lastPoint: Flow<LastPoint?> = context.dataStore.data.map { prefs ->
        val raw = prefs[lastPointKey] ?: return@map null
        try {
            json.decodeFromString<LastPoint>(raw)
        } catch (e: Exception) {
            null
        }
    }

    val language: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[languageKey]
    }

    suspend fun saveFavorites(list: List<FavoritePlace>) {
        context.dataStore.edit { prefs ->
            prefs[favoritesKey] = json.encodeToString(list)
        }
    }

    suspend fun saveLastPoint(point: LastPoint) {
        context.dataStore.edit { prefs ->
            prefs[lastPointKey] = json.encodeToString(point)
        }
    }

    suspend fun saveLanguage(code: String) {
        context.dataStore.edit { prefs ->
            prefs[languageKey] = code
        }
    }
}
