package net.kuama.documentscanner.extensions

import android.net.Uri
import androidx.core.net.toFile

fun Uri.isLocalFile() = (scheme?.startsWith("http") == false)

fun Uri.deleteIfLocal(): Boolean? = if (isLocalFile()) {
    toFileOrNull()?.delete()
} else {
    null
}

fun Uri.toFileOrNull() =
    try {
        toFile()
    } catch (e: IllegalArgumentException) {
        null
    }