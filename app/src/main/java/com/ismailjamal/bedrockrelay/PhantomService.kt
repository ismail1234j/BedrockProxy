package com.ismailjamal.bedrockrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ProcessBuilder
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.currentCoroutineContext
import java.io.IOException
import java.lang.reflect.Field
import java.util.StringTokenizer
import android.content.pm.ApplicationInfo
import com.ismailjamal.bedrockrelay.PhantomRepository

class PhantomService : Service() {
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var process: Process? = null
    private var processJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _logFlow = MutableSharedFlow<String>(replay = 50) // Buffer last 50 logs
    val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

    private val TAG = "PhantomService"
    private val BINARY_NAME = "phantom" // Define the expected binary name here

    inner class LocalBinder : Binder() {
        fun getService(): PhantomService = this@PhantomService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        addLog("Service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand received - Action: ${intent?.action}")

        startForeground(NOTIFICATION_ID, createNotification("Processing command..."))

        intent?.let {
            when (it.action) {
                "STOP" -> {
                    Log.i(TAG, "Received STOP action.")
                    addLog("Received STOP command.")
                    stopProcessAndService()
                }
                else -> {
                    val arguments = it.getStringExtra("arguments")
                    
                    if (arguments != null) {
                        Log.i(TAG, "Processing START command.")
                        addLog("Received start request:")
                        addLog("  Args: [$arguments]")
                        startProcess(arguments)
                        updateNotification("Proxy running...")
                    } else {
                        Log.w(TAG, "Start command received without arguments string.")
                        addLog("Error: Start command missing required arguments.")
                        stopProcessAndService()
                    }
                }
            }
        } ?: run {
            Log.w(TAG, "Service started with null intent. Maybe OS restart? Stopping service.")
            addLog("Service started with null intent. Stopping.")
            stopProcessAndService()
        }
        
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Service onUnbind")
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        addLog("Service is being destroyed.")
        stopProcess()
        releaseWakeLock()
        serviceScope.cancel()
        Log.i(TAG, "Service cleanup complete.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Notification channel for Phantom proxy service"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created.")
        }
    }

    private fun createNotification(contentText: String = "Proxy service active"): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bedrock Relay")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification updated: $contentText")
    }

    private fun startProcess(
        arguments: String
    ) {
        if (process?.isAlive == true) {
            Log.w(TAG, "Process already running. Stopping existing one first.")
            addLog("Phantom process already running. Restarting...")
            stopProcess()
        }

        processJob = serviceScope.launch {
            try {
                val repository = PhantomRepository.getInstance(this@PhantomService)
                val binaryResult = repository.getBinaryFile()

                if (binaryResult.isFailure) {
                    val error = binaryResult.exceptionOrNull()
                    Log.e(TAG, "Failed to get binary file from repository", error)
                    addLog("Error: Failed to get binary file: ${error?.message}")
                    stopProcessAndService()
                    return@launch
                }

                val binaryFile = binaryResult.getOrThrow()
                val binaryPath = binaryFile.absolutePath

                // Check file existence and permissions
                if (!binaryFile.exists()) {
                    Log.e(TAG, "Binary file does not exist at: $binaryPath")
                    addLog("Error: Binary file does not exist.")
                    stopProcessAndService()
                    return@launch
                }

                if (!binaryFile.canExecute()) {
                    Log.e(TAG, "Binary file is not executable. Attempting to set executable.")
                    try {
                        binaryFile.setExecutable(true, false)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException while setting binary as executable", e)
                        addLog("Error: SecurityException while setting binary as executable: ${e.message}")
                        stopProcessAndService()
                        return@launch
                    }

                    if (!binaryFile.canExecute()) {
                        Log.e(TAG, "Failed to set binary as executable.")
                        addLog("Error: Failed to set binary as executable.")
                        stopProcessAndService()
                        return@launch
                    }
                }

                Log.i(TAG, "Binary file is executable and ready to run.")

                val command = mutableListOf<String>()
                command.add(binaryPath)

                val parsedArgs = arguments.split(" ").filter { it.isNotBlank() }
                command.addAll(parsedArgs)

                Log.i(TAG, "Executing command: ${command.joinToString(" ")}")
                addLog("Starting Phantom process...")
                addLog("Command: ${command.joinToString(" ")}")

                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectErrorStream(true)
                process = processBuilder.start()
                val pidString = try {
                    val pidField: Field? = process?.javaClass?.getDeclaredField("pid")
                    pidField?.isAccessible = true
                    pidField?.getInt(process).toString()
                } catch (e: Exception) {
                    "N/A"
                }
                Log.i(TAG, "Phantom process started (PID: $pidString).")
                addLog("Phantom process started successfully.")
                
                launch { readProcessOutput(process!!) }

                val exitCode = process?.waitFor()
                Log.i(TAG, "Phantom process exited with code: $exitCode")
                addLog("Phantom process exited with code: $exitCode.")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting or running Phantom process", e)
                addLog("Error running Phantom: ${e.message}")
            } finally {
                Log.d(TAG, "Process coroutine finished.")
                process = null
                if (isActive) {
                    addLog("Process stopped unexpectedly. Stopping service.")
                    stopProcessAndService()
                }
            }
        }
    }

    private suspend fun readProcessOutput(process: Process) {
        Log.d(TAG, "Starting process output reader coroutine.")
        withContext(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    line?.let {
                        Log.d("PhantomProcessLog", it)
                        _logFlow.emit(it)
                    }
                }
                 Log.i(TAG, "Finished reading process output. Coroutine active: $isActive")
            } catch (e: Exception) {
                if (e is IOException && e.message?.contains("Stream closed", ignoreCase = true) == true) {
                    Log.i(TAG, "Process stream closed, likely intentional termination.")
                } else if (!isActive) {
                    Log.i(TAG, "Output reading cancelled.")
                } else {
                    Log.e(TAG, "Error reading process output", e)
                    addLog("Error reading Phantom output: ${e.message}")
                    _logFlow.emit("Error reading Phantom output: ${e.message}")
                }
            } finally {
                try {
                    process.inputStream.close()
                } catch (e: IOException) {
                     Log.w(TAG, "Exception closing input stream", e)
                }
                Log.d(TAG, "Process output reader coroutine finished.")
            }
        }
    }

    private fun stopProcess() {
        serviceScope.launch {
            if (process?.isAlive == true) {
                val pidString = try {
                    val pidField: Field? = process?.javaClass?.getDeclaredField("pid")
                    pidField?.isAccessible = true
                    pidField?.getInt(process).toString()
                } catch (e: Exception) {
                    "N/A"
                }
                Log.i(TAG, "Attempting to stop Phantom process (PID: $pidString).")
                addLog("Stopping Phantom process...")
                process?.destroyForcibly()
                process?.waitFor()
                Log.i(TAG, "Phantom process stopped.")
                addLog("Phantom process stopped.")
            } else {
                Log.d(TAG, "Stop command issued, but process was not running.")
            }
            process = null
            processJob?.cancel()
            processJob = null
        }
    }

    private fun stopProcessAndService() {
        Log.i(TAG, "Stopping process and service.")
        stopProcess()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PhantomService::WakeLock"
        ).apply {
            acquire(60*60*1000L)
        }
        Log.i(TAG, "Partial WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun addLog(message: String) {
        Log.d(TAG, "SERVICE LOG: $message")
        serviceScope.launch {
            try {
                _logFlow.emit(message)
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting log message to flow", e)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "phantom_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_NAME = "Phantom Service"
    }
}