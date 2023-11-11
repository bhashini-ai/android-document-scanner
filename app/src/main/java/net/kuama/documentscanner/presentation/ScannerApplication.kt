package net.kuama.documentscanner.presentation

import android.app.Application
import net.kuama.documentscanner.BuildConfig.DEBUG
import timber.log.Timber

class ScannerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        plantTimberTree()
    }

    private fun plantTimberTree() {
        if (DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
