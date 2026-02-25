package org.bdcloud.clash.core

import android.util.Log

/**
 * JNI helper for low-level process operations.
 */
object NativeHelper {

    private const val TAG = "NativeHelper"

    init {
        try {
            System.loadLibrary("bdcloud_native")
            Log.d(TAG, "Loaded bdcloud_native library")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }

    /**
     * Fork a child that inherits a specific fd via dup2.
     * Returns child PID or -1 on error.
     */
    @JvmStatic
    external fun forkExecWithFd(vpnFd: Int, childFd: Int, path: String, args: Array<String>, logPath: String?): Int

    /**
     * Wait for child process. Returns:
     *   >= 0: normal exit code
     *   -1001 to -1064: killed by signal (e.g., -1011 = SIGSEGV)
     *   -999: waitpid failed
     */
    @JvmStatic
    external fun waitForProcess(pid: Int): Int

    @JvmStatic
    external fun killProcess(pid: Int)

    /** Decode the exit result into a human-readable string */
    fun decodeExitResult(result: Int): String {
        return when {
            result >= 0 -> "exit code $result"
            result in -1064..-1001 -> {
                val sig = -(result + 1000)
                val sigName = when (sig) {
                    1 -> "SIGHUP"
                    2 -> "SIGINT"
                    4 -> "SIGILL"
                    6 -> "SIGABRT"
                    9 -> "SIGKILL"
                    11 -> "SIGSEGV"
                    15 -> "SIGTERM"
                    else -> "signal $sig"
                }
                "killed by $sigName ($sig)"
            }
            else -> "unknown ($result)"
        }
    }
}
