package net.kuama.documentscanner.extensions

import timber.log.Timber

fun logDebug(message: String) {
    Timber.d(message)
}

fun logError(tag: String, message: String?) {
    Timber.tag(tag).e(message)
}