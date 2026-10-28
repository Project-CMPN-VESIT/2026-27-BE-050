package com.example.capstone.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.capstone.data.disaster.DisasterEvidence
import com.example.capstone.data.disaster.DisasterEvent
import com.example.capstone.data.disaster.DisasterSourceStatus
import com.example.capstone.data.repository.LiveDisasterRepository
import com.example.capstone.data.repository.NearbyAlertSummary
import kotlinx.coroutines.launch

sealed class LoadState<out T> {
    object Idle : LoadState<Nothing>()
    object Loading : LoadState<Nothing>()
    data class Success<T>(val data: T) : LoadState<T>()
    data class Empty(val message: String) : LoadState<Nothing>()
    data class Error(val message: String) : LoadState<Nothing>()
}

class LiveDisasterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LiveDisasterRepository()

    private val _globalEvents = MutableLiveData<LoadState<List<DisasterEvent>>>(LoadState.Idle)
    val globalEvents: LiveData<LoadState<List<DisasterEvent>>> = _globalEvents

    private val _event = MutableLiveData<LoadState<DisasterEvent>>(LoadState.Idle)
    val event: LiveData<LoadState<DisasterEvent>> = _event

    private val _evidence = MutableLiveData<LoadState<List<DisasterEvidence>>>(LoadState.Idle)
    val evidence: LiveData<LoadState<List<DisasterEvidence>>> = _evidence

    private val _nearbyEvents = MutableLiveData<LoadState<List<DisasterEvent>>>(LoadState.Idle)
    val nearbyEvents: LiveData<LoadState<List<DisasterEvent>>> = _nearbyEvents

    private val _nearbyEvidence = MutableLiveData<LoadState<List<DisasterEvidence>>>(LoadState.Idle)
    val nearbyEvidence: LiveData<LoadState<List<DisasterEvidence>>> = _nearbyEvidence

    private val _nearbyAlertSummary = MutableLiveData<LoadState<NearbyAlertSummary>>(LoadState.Idle)
    val nearbyAlertSummary: LiveData<LoadState<NearbyAlertSummary>> = _nearbyAlertSummary

    private val _sourceStatus = MutableLiveData<LoadState<List<DisasterSourceStatus>>>(LoadState.Idle)
    val sourceStatus: LiveData<LoadState<List<DisasterSourceStatus>>> = _sourceStatus

    fun loadGlobalEvents() {
        _globalEvents.value = LoadState.Loading
        viewModelScope.launch {
            try {
                val events = repository.fetchGlobalLiveEvents()
                _globalEvents.postValue(
                    if (events.isEmpty()) {
                        LoadState.Empty("No live disaster events available right now.")
                    } else {
                        LoadState.Success(events)
                    }
                )
            } catch (e: Exception) {
                _globalEvents.postValue(LoadState.Error(messageFor(e)))
            }
        }
    }

    fun loadIndiaEvents() {
        _globalEvents.value = LoadState.Loading
        viewModelScope.launch {
            try {
                val events = repository.fetchIndiaDisasters()
                _globalEvents.postValue(
                    if (events.isEmpty()) {
                        LoadState.Empty("No live disaster events available for India right now.")
                    } else {
                        LoadState.Success(events)
                    }
                )
            } catch (e: Exception) {
                _globalEvents.postValue(LoadState.Error(messageFor(e)))
            }
        }
    }

    fun loadEvent(eventId: String) {
        _event.value = LoadState.Loading
        viewModelScope.launch {
            try {
                val event = repository.fetchLiveEvent(eventId)
                if (event == null) {
                    _event.postValue(LoadState.Empty("Event not found."))
                } else {
                    _event.postValue(LoadState.Success(event))
                }
            } catch (e: Exception) {
                _event.postValue(LoadState.Error(messageFor(e)))
            }
        }
    }

    fun loadEvidence(eventId: String) {
        _evidence.value = LoadState.Loading
        viewModelScope.launch {
            try {
                val evidence = repository.fetchEvidence(eventId)
                _evidence.postValue(
                    if (evidence.isEmpty()) {
                        LoadState.Empty("No evidence was returned for this event.")
                    } else {
                        LoadState.Success(evidence)
                    }
                )
            } catch (e: Exception) {
                _evidence.postValue(LoadState.Error(messageFor(e)))
            }
        }
    }

    fun loadNearby(latitude: Double, longitude: Double, radiusKm: Double = 250.0) {
        _nearbyEvents.value = LoadState.Loading
        _nearbyEvidence.value = LoadState.Loading
        _nearbyAlertSummary.value = LoadState.Loading
        viewModelScope.launch {
            try {
                val events = repository.fetchNearbyEvents(latitude, longitude, radiusKm)
                val evidence = repository.fetchNearbyEvidence(latitude, longitude, radiusKm)
                val summary = repository.fetchNearbyAlertSummary(latitude, longitude, radiusKm)
                _nearbyEvents.postValue(
                    if (events.isEmpty()) LoadState.Empty("No nearby live events found.") else LoadState.Success(events)
                )
                _nearbyEvidence.postValue(
                    if (evidence.isEmpty()) LoadState.Empty("No nearby evidence available.") else LoadState.Success(evidence)
                )
                _nearbyAlertSummary.postValue(LoadState.Success(summary))
            } catch (e: Exception) {
                val error = LoadState.Error(messageFor(e))
                _nearbyEvents.postValue(error)
                _nearbyEvidence.postValue(error)
                _nearbyAlertSummary.postValue(error)
            }
        }
    }

    fun loadSourceStatus() {
        _sourceStatus.value = LoadState.Loading
        viewModelScope.launch {
            try {
                val status = repository.getSourceStatus()
                _sourceStatus.postValue(
                    if (status.isEmpty()) {
                        LoadState.Empty("No source status information available.")
                    } else {
                        LoadState.Success(status)
                    }
                )
            } catch (e: Exception) {
                _sourceStatus.postValue(LoadState.Error(messageFor(e)))
            }
        }
    }

    private fun messageFor(exception: Exception): String {
        return exception.message?.takeIf { it.isNotBlank() }
            ?: "The live disaster backend is unavailable."
    }
}
