package com.globalmmorpg.game

import android.app.Application
import com.google.firebase.FirebaseApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Requires a REAL google-services.json downloaded from your Firebase project
        // (Firebase Console -> Project Settings -> Your apps -> google-services.json)
        FirebaseApp.initializeApp(this)
        installCrashLogger()
    }

    /**
     * Writes any uncaught crash's full stack trace to a plain text file so it
     * can be read from any file manager without needing adb/logcat. The app
     * still crashes normally afterward via the default system handler.
     */
    private fun installCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val logDir = getExternalFilesDir(null) ?: filesDir
                val logFile = File(logDir, "crash_log.txt")
                logFile.appendText("\n===== CRASH at $timestamp =====\n$sw\n")
            } catch (_: Exception) {
                // Never let the crash logger itself cause a secondary crash.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
