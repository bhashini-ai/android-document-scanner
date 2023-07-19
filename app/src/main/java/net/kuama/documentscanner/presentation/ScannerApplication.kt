package net.kuama.documentscanner.presentation

import android.app.Application
import timber.log.Timber

class ScannerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        plantTimberTree()
    }

    private fun plantTimberTree() {
        // todo: add `if (DEBUG)`
        Timber.plant(Timber.DebugTree())
    }
}