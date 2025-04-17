package com.ismailjamal.bedrockrelay

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import android.os.Build
import java.io.IOException

class PhantomRepository private constructor(private val context: Context) {
    private val TAG = "PhantomRepository"
    private var binaryPath: String? = null

    companion object {
        private var instance: PhantomRepository? = null

        fun getInstance(context: Context): PhantomRepository {
            return instance ?: synchronized(this) {
                instance ?: PhantomRepository(context).also { instance = it }
            }
        }
    }

    init {
        // Initialize any necessary resources
    }

    fun setBinaryPath(path: String): Result<File> {
        Log.d(TAG, "Setting binary path to: $path")
        return try {
            val file = File(path)
            if (!file.exists()) {
                Log.e(TAG, "Binary file does not exist at path: $path")
                return Result.failure(IllegalStateException("Binary file does not exist"))
            }
            if (!file.canExecute()) {
                Log.w(TAG, "Binary file is not executable, attempting to set permissions")
                if (!file.setExecutable(true, true)) {
                    Log.e(TAG, "Failed to set executable permissions using setExecutable")
                    // Set executable permissions using chmod as a fallback
                    try {
                        Runtime.getRuntime().exec("chmod 755 ${file.absolutePath}").waitFor()
                        Log.i(TAG, "Set executable permissions using chmod")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set permissions using chmod", e)
                        return Result.failure(IllegalStateException("Binary file is not executable"))
                    }
                }
            }
            binaryPath = path
            Log.i(TAG, "Binary path set successfully")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting binary path", e)
            Result.failure(e)
        }
    }

    fun getBinaryFile(): Result<File> {
        Log.d(TAG, "Entering getBinaryFile")

        // Get the standard native library directory for this application.
        // Android automatically places the correct ABI version here upon installation.
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val soFile = File(nativeLibDir, "libphantom.so")

        Log.d(TAG, "Checking for binary at: ${soFile.absolutePath}")

        if (!soFile.exists()) {
            Log.e(TAG, "Binary ${soFile.name} not found at expected location: ${soFile.absolutePath}")

            // --- Start Enhanced Logging ---
            Log.d(TAG, "--- Directory Tree Dump ---")
            Log.d(TAG, "Attempting to log native library directory structure:")
            try {
                logDirectoryTree(nativeLibDir, TAG, "nativeLibDir: ")

                // Also log the parent directory to see sibling ABI folders
                val baseLibDir = nativeLibDir.parentFile
                if (baseLibDir != null && baseLibDir.exists() && baseLibDir.isDirectory) {
                     Log.d(TAG, "Parent directory structure:")
                     logDirectoryTree(baseLibDir, TAG, "baseLibDir: ")
                } else {
                    Log.w(TAG, "Could not access or find parent directory of nativeLibDir: ${baseLibDir?.absolutePath}")
                }

                // Optionally log the main data directory (can be verbose)
                // val dataDir = File(context.applicationInfo.dataDir)
                // Log.d(TAG, "Application data directory structure:")
                // logDirectoryTree(dataDir, TAG, "dataDir: ")

            } catch (e: Exception) {
                 Log.e(TAG, "Error occurred while trying to log directory structure", e)
            }
            Log.d(TAG, "--- End Directory Tree Dump ---")
            // --- End Enhanced Logging ---

            return Result.failure(
                RuntimeException("Binary ${soFile.name} not found at ${soFile.absolutePath}")
            )
        }

        // Check and set executable permissions if needed
        if (!soFile.canExecute()) {
            Log.w(TAG, "${soFile.name} not executable, attempting chmod 755")
            try {
                // Use array form for exec to handle paths with spaces, though less likely here
                val process = Runtime.getRuntime().exec(arrayOf("chmod", "755", soFile.absolutePath))
                val exitCode = process.waitFor()
                Log.i(TAG, "chmod 755 executed for ${soFile.absolutePath} with exit code $exitCode")
                // Re-check execute permission after chmod
                // Need to create a new File object to potentially get updated state
                val updatedSoFile = File(soFile.absolutePath)
                if (!updatedSoFile.canExecute()) {
                     Log.e(TAG, "Failed to make ${updatedSoFile.absolutePath} executable even after chmod.")
                     return Result.failure(
                         RuntimeException("Failed to set executable permission on ${updatedSoFile.absolutePath}")
                     )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to chmod ${soFile.name}", e)
                 return Result.failure(
                     RuntimeException("Failed to set executable permission on ${soFile.absolutePath} due to: ${e.message}")
                 )
            }
        }

        Log.i(TAG, "Binary file is ready to execute: ${soFile.absolutePath}")
        return Result.success(soFile) // Return the original File object
    }

    fun validateServerAddress(address: String): Boolean {
        Log.d(TAG, "Validating server address: $address")
        return try {
            val parts = address.split(":")
            if (parts.size != 2) {
                Log.w(TAG, "Invalid address format: $address")
                return false
            }
            val port = parts[1].toInt()
            port in 1..65535
        } catch (e: Exception) {
            Log.e(TAG, "Error validating server address", e)
            false
        }
    }

    // Helper function to log directory tree
    private fun logDirectoryTree(startDir: File, tag: String, prefix: String = "") {
        if (!startDir.exists()) {
            Log.d(tag, "$prefix[Directory does not exist: ${startDir.absolutePath}]")
            return
        }
        if (!startDir.isDirectory) {
            Log.d(tag, "$prefix[Not a directory: ${startDir.absolutePath}]")
            return
        }

        Log.d(tag, "$prefix${startDir.name}/")
        val files = try { startDir.listFiles() } catch (e: SecurityException) { null } // Handle potential permission errors
        if (files == null) {
            Log.w(tag, "$prefix  [Could not list files - possible permission issue]")
            return
        }
         if (files.isEmpty()) {
             Log.d(tag, "$prefix  [Directory is empty]")
             return
         }

        files.sortedBy { it.name }.forEachIndexed { index, file -> // Sort for consistent output
            val connector = if (index == files.size - 1) "└── " else "├── "
            val indent = prefix + if (index == files.size - 1) "    " else "│   "
            if (file.isDirectory) {
                 Log.d(tag, "$prefix$connector${file.name}/")
                 logDirectoryTree(file, tag, indent) // Recurse for subdirectories
            } else {
                Log.d(tag, "$prefix$connector${file.name}") // Log file name
            }
        }
    }
}