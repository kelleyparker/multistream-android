package com.kelleyparker.multistream.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kelleyparker.multistream.core.Destination
import com.kelleyparker.multistream.core.DestinationJson
import com.kelleyparker.multistream.core.QualityJson
import com.kelleyparker.multistream.core.QualitySettings
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

data class SavedState(
  val destinations: List<Destination>,
  val quality: QualitySettings,
  val setupDone: Boolean,
)

/** Saves destinations (stream keys encrypted), quality settings and whether setup finished. */
class AppStore(private val context: Context) {

  suspend fun load(): SavedState {
    val prefs = context.dataStore.data.first()
    val destinations = DestinationJson.decode(prefs[DESTINATIONS].orEmpty())
      .map { it.copy(streamKey = KeyCipher.decrypt(it.streamKey)) }
    return SavedState(destinations, QualityJson.decode(prefs[QUALITY]), prefs[SETUP_DONE] ?: false)
  }

  suspend fun saveDestinations(destinations: List<Destination>) {
    val sealed = destinations.map { it.copy(streamKey = KeyCipher.encrypt(it.streamKey)) }
    context.dataStore.edit { it[DESTINATIONS] = DestinationJson.encode(sealed) }
  }

  suspend fun saveQuality(quality: QualitySettings) {
    context.dataStore.edit { it[QUALITY] = QualityJson.encode(quality) }
  }

  suspend fun setSetupDone(done: Boolean) {
    context.dataStore.edit { it[SETUP_DONE] = done }
  }

  private companion object {
    val DESTINATIONS = stringPreferencesKey("destinations_v1")
    val QUALITY = stringPreferencesKey("quality_v1")
    val SETUP_DONE = booleanPreferencesKey("setup_done")
  }
}
