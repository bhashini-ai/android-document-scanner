package net.kuama.documentscanner.extensions

import timber.log.Timber

fun logDebug(message: String) {
    Timber.d(message)
}

fun logError(message: String) {
    Timber.e(message)
}