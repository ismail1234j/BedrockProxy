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

        // Search for libphantom.so in all possible ABI directories under native library root
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val baseLibDir = nativeLibDir.parentFile ?: nativeLibDir
        val abiList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) Build.SUPPORTED_ABIS.toList() else listOf(Build.CPU_ABI)

        val candidates = mutableListOf<File>().apply {
            add(File(nativeLibDir, "libphantom.so"))
            abiList.forEach { abi -> add(File(baseLibDir, "$abi/libphantom.so")) }
        }

        val soFile = candidates.firstOrNull { it.exists() } ?: return Result.failure(
            RuntimeException("Binary libphantom.so not found in any: ${candidates.map { it.absolutePath }}")
        )

        if (!soFile.canExecute()) {
            Log.w(TAG, "libphantom.so not executable, chmod 755")
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", soFile.absolutePath)).waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to chmod libphantom.so", e)
            }
            if (!soFile.canExecute()) return Result.failure(
                RuntimeException("Failed to set executable permission on ${soFile.absolutePath}")
            )
        }

        Log.i(TAG, "Binary file is ready to execute: ${soFile.absolutePath}")
        return Result.success(soFile)
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
}