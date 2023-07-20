package net.kuama.documentscanner.extensions

import android.view.View
import timber.log.Timber

fun logDebug(message: String) {
    Timber.d(message)
}

fun logError(tag: String, message: String?) {
    Timber.tag(tag).e(message)
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}
