package com.ismailjamal.bedrockrelay

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

sealed class PhantomUiState {
    object Initial : PhantomUiState()
    object Ready : PhantomUiState()
    object Starting : PhantomUiState()
    object Running : PhantomUiState()
    object Stopping : PhantomUiState()
    data class Error(val message: String) : PhantomUiState()
}

class PhantomViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PhantomViewModel"
    private val _uiState = MutableStateFlow<PhantomUiState>(PhantomUiState.Initial)
    val uiState: StateFlow<PhantomUiState> = _uiState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var service: PhantomService? = null
    private var isBound = false
    private var logCollectionJob: Job? = null

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected")
            val serviceBinder = binder as PhantomService.LocalBinder
            service = serviceBinder.getService()
            isBound = true
            if (_uiState.value is PhantomUiState.Initial || _uiState.value is PhantomUiState.Error) {
                _uiState.value = PhantomUiState.Ready
            }
            startCollectingLogs()
            addLog("Service connected successfully")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            service = null
            isBound = false
            logCollectionJob?.cancel()
            if (_uiState.value != PhantomUiState.Initial && _uiState.value != PhantomUiState.Stopping) {
                _uiState.value = PhantomUiState.Error("Service unexpectedly disconnected")
                addLog("Service disconnected unexpectedly")
            }
            _isServiceRunning.value = false
        }
    }

    init {
        Log.d(TAG, "ViewModel initialized")
        _uiState.value = PhantomUiState.Initial
        bindService()
    }

    private fun bindService() {
        Log.d(TAG, "Attempting to bind service")
        val intent = Intent(getApplication(), PhantomService::class.java)
        val bound = getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "Service bind result: $bound")
        if (!bound) {
            addLog("Fatal: Failed to bind to service")
            _uiState.value = PhantomUiState.Error("Failed to bind to service")
        } else {
            addLog("Binding to service...")
        }
    }

    private fun startCollectingLogs() {
        logCollectionJob?.cancel()
        Log.d(TAG, "Starting log collection")
        service?.logFlow?.let { flow ->
            logCollectionJob = flow.onEach { logLine ->
                _logs.value = (_logs.value + logLine).takeLast(MAX_LOG_LINES)
            }.launchIn(viewModelScope)
            Log.i(TAG, "Log collection coroutine launched.")
        } ?: run {
            Log.w(TAG, "Service not available to start collecting logs.")
            addLog("Warning: Could not start log collection, service not bound.")
        }
    }

    fun startProxyWithArgs(arguments: String) {
        Log.d(TAG, "Attempting to start proxy with args: [$arguments]")
        if (arguments.isBlank()) {
            Log.e(TAG, "Arguments are empty.")
            addLog("Error: Arguments cannot be empty.")
            _uiState.value = PhantomUiState.Error("Please enter arguments for Phantom.")
            return
        }

        addLog("Attempting to start proxy...")
        addLog("Arguments: $arguments")

        viewModelScope.launch {
            try {
                _uiState.value = PhantomUiState.Starting
                val intent = Intent(getApplication(), PhantomService::class.java).apply {
                    putExtra("arguments", arguments)
                }
                getApplication<Application>().startForegroundService(intent)
                if (!isBound) {
                    Log.i(TAG, "Service not bound, rebinding...")
                    bindService()
                }
                _uiState.value = PhantomUiState.Running
                _isServiceRunning.value = true
                addLog("Proxy start command sent successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending start command to service", e)
                addLog("Error sending start command: ${e.message}")
                _uiState.value = PhantomUiState.Error("Failed to send start command: ${e.message}")
                _isServiceRunning.value = false
            }
        }
    }

    fun stopProxy() {
        Log.d(TAG, "Sending stop command to service")
        addLog("Sending stop command to proxy service")
        _uiState.value = PhantomUiState.Stopping
        viewModelScope.launch {
            try {
                val intent = Intent(getApplication(), PhantomService::class.java).apply {
                    action = "STOP"
                }
                getApplication<Application>().startService(intent)
                _isServiceRunning.value = false
                addLog("Proxy stop command sent successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending stop command to service", e)
                addLog("Error sending stop command: ${e.message}")
                _uiState.value = PhantomUiState.Error("Failed to send stop command: ${e.message}")
                _isServiceRunning.value = false
            }
        }
    }

    private fun addLog(message: String) {
        Log.d(TAG, "UI LOG: $message")
        _logs.value = (_logs.value + message).takeLast(MAX_LOG_LINES)
    }

    override fun onCleared() {
        Log.d(TAG, "ViewModel cleared")
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
                Log.i(TAG, "Service unbound in onCleared.")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Error unbinding service in onCleared, perhaps already unbound?", e)
            }
            isBound = false
        }
        logCollectionJob?.cancel()
        Log.d(TAG, "Log collection job cancelled in onCleared")
        super.onCleared()
    }

    companion object {
        private const val MAX_LOG_LINES = 500
    }
} 