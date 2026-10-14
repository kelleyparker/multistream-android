package com.kelleyparker.multistream.ui

import android.app.Application
import android.view.SurfaceView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kelleyparker.multistream.core.Destination
import com.kelleyparker.multistream.core.DestinationStatus
import com.kelleyparker.multistream.core.EncoderCaps
import com.kelleyparker.multistream.core.QualitySettings
import com.kelleyparker.multistream.core.SettingsValidator
import com.kelleyparker.multistream.core.TierPlan
import com.kelleyparker.multistream.core.TierPlanner
import com.kelleyparker.multistream.core.Validation
import com.kelleyparker.multistream.data.AppStore
import com.kelleyparker.multistream.stream.ConnectionCheck
import com.kelleyparker.multistream.stream.EncoderCapabilities
import com.kelleyparker.multistream.stream.StreamController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the screens draw. Screens are stateless so they can be screenshot-tested. */
data class UiState(
  val loaded: Boolean = false,
  val setupDone: Boolean = false,
  val destinations: List<Destination> = emptyList(),
  val quality: QualitySettings = QualitySettings(),
  val caps: EncoderCaps = EncoderCaps.BASIC,
  val statuses: Map<String, DestinationStatus> = emptyMap(),
  val isLive: Boolean = false,
  val muted: Boolean = false,
  val engineError: String? = null,
  val checks: Map<String, CheckState> = emptyMap(),
) {
  val plan: TierPlan = TierPlanner.plan(destinations, quality, caps)
  val validation: Validation = SettingsValidator.validate(plan, caps)

  fun statusOf(id: String): DestinationStatus = statuses[id] ?: DestinationStatus.Idle
}

sealed interface CheckState {
  data object Running : CheckState
  data class Done(val ok: Boolean, val message: String) : CheckState
}

private data class Saved(
  val loaded: Boolean = false,
  val setupDone: Boolean = false,
  val destinations: List<Destination> = emptyList(),
  val quality: QualitySettings = QualitySettings(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
  private val store = AppStore(app)
  private val controller = StreamController(app)
  private val caps = EncoderCapabilities.probe()
  private val saved = MutableStateFlow(Saved())
  private val checks = MutableStateFlow<Map<String, CheckState>>(emptyMap())
  /** The camera and microphone can only be prepared once the user has allowed them. */
  private val permissionsGranted = MutableStateFlow(false)

  val state: StateFlow<UiState> = combine(
    saved, controller.statuses, controller.isLive, controller.muted,
    combine(controller.error, checks) { error, checks -> error to checks },
  ) { s, statuses, live, muted, (error, checks) ->
    UiState(s.loaded, s.setupDone, s.destinations, s.quality, caps, statuses, live, muted, error, checks)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState(caps = caps))

  init {
    viewModelScope.launch {
      val loaded = store.load()
      saved.value = Saved(true, loaded.setupDone, loaded.destinations, loaded.quality)
    }
    // Rebuild the encoders whenever the plan changes while not live.
    viewModelScope.launch {
      combine(state.map { it.plan }.distinctUntilChanged(), permissionsGranted) { plan, granted -> plan.takeIf { granted } }
        .distinctUntilChanged()
        .collect { plan -> if (plan != null) controller.configure(plan) }
    }
  }

  fun finishSetup(destinations: List<Destination>) {
    saved.update { it.copy(destinations = destinations, setupDone = true) }
    viewModelScope.launch {
      store.saveDestinations(destinations)
      store.setSetupDone(true)
    }
  }

  fun upsertDestination(destination: Destination) = editDestinations { list ->
    if (list.any { it.id == destination.id }) list.map { if (it.id == destination.id) destination else it } else list + destination
  }

  fun removeDestination(id: String) = editDestinations { list -> list.filterNot { it.id == id } }

  fun setEnabled(id: String, enabled: Boolean) = editDestinations { list ->
    list.map { if (it.id == id) it.copy(enabled = enabled) else it }
  }

  private fun editDestinations(change: (List<Destination>) -> List<Destination>) {
    if (state.value.isLive) return
    val next = change(saved.value.destinations)
    saved.update { it.copy(destinations = next) }
    viewModelScope.launch { store.saveDestinations(next) }
  }

  fun setQuality(quality: QualitySettings) {
    if (state.value.isLive) return
    saved.update { it.copy(quality = quality) }
    viewModelScope.launch { store.saveQuality(quality) }
  }

  fun checkConnection(destination: Destination) {
    checks.update { it + (destination.id to CheckState.Running) }
    viewModelScope.launch {
      val result = when (val r = ConnectionCheck.check(destination.serverUrl)) {
        is ConnectionCheck.Result.Reachable -> CheckState.Done(true, "Server reachable (${r.millis} ms). Nothing was broadcast.")
        is ConnectionCheck.Result.Unreachable -> CheckState.Done(false, r.message)
      }
      checks.update { it + (destination.id to result) }
    }
  }

  fun onPermissionsResult(granted: Boolean) {
    permissionsGranted.value = granted
  }

  fun attachPreview(view: SurfaceView) = controller.attachPreview(view)
  fun detachPreview() = controller.detachPreview()
  fun goLive() = controller.goLive()
  fun stopAll() = controller.stopAll()
  fun stopOne(id: String) = controller.stopOne(id)
  fun retryOne(id: String) = controller.retryOne(id)
  fun switchCamera() = controller.switchCamera()
  fun toggleMute() = controller.setMuted(!state.value.muted)

  override fun onCleared() {
    controller.release()
  }
}
