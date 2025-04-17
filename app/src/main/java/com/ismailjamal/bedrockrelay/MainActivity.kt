package com.ismailjamal.bedrockrelay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: PhantomViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d(TAG, "Notification permission result: $isGranted")
        if (isGranted) {
            Log.i(TAG, "Notification permission granted.")
        } else {
            Log.w(TAG, "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate")

        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
            ) {
                SimplifiedPhantomApp(viewModel)
            }
        }

        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        Log.d(TAG, "Checking notification permission")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.w(TAG, "Should show rationale for notification permission (Not implemented)")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.d(TAG, "Requesting notification permission")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            Log.d(TAG, "Android version < TIRAMISU, no POST_NOTIFICATIONS permission needed.")
        }
    }
}

@Composable
fun SimplifiedPhantomApp(
    viewModel: PhantomViewModel
) {
    var phantomArguments by remember { mutableStateOf("") }
    var serviceRunning by remember { mutableStateOf(false) }
    var logMessages by remember { mutableStateOf(emptyList<String>()) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.logs.collectLatest { newLogs ->
            logMessages = newLogs
        }
    }

    LaunchedEffect(Unit) {
        viewModel.isServiceRunning.collectLatest { running ->
            serviceRunning = running
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bedrock Proxy", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phantomArguments,
            onValueChange = { phantomArguments = it },
            label = { Text("Phantom Arguments (e.g., -server ip:port)") },
            placeholder = { Text("-server example.com:19132")},
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (serviceRunning) {
                    viewModel.stopProxy()
                } else {
                    viewModel.startProxyWithArgs(phantomArguments)
                }
            },
            enabled = !serviceRunning || phantomArguments.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // Added green color
        ) {
            Text(if (serviceRunning) "Stop Service" else "Start Service")
        }

        val stateText = when (uiState) {
            is PhantomUiState.Error -> "Error: ${(uiState as PhantomUiState.Error).message}"
            PhantomUiState.Initial -> "Ready"
            PhantomUiState.Ready -> "Ready to start"
            PhantomUiState.Running -> "Service Running"
            PhantomUiState.Starting -> "Starting..."
            PhantomUiState.Stopping -> "Stopping..."
        }
        Text(stateText, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Console Output", style = MaterialTheme.typography.titleMedium)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            LazyColumn(
                modifier = Modifier.padding(8.dp),
                reverseLayout = true
            ) {
                items(logMessages.reversed()) { log ->
                    Text(
                        text = log,
                        modifier = Modifier.padding(vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp)) // Added spacer
        Text( // Added credits placeholder
            text = "https://github.com/ismail1234j/bedrockproxy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}