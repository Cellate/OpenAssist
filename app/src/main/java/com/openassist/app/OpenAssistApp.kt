package com.openassist.app

import android.app.Application
import android.util.Log

class OpenAssistApp : Application() {
    companion object {
        private const val TAG = "OpenAssistApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OpenAssist initializing")
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread \${thread.name}: \${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
