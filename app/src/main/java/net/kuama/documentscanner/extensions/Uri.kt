package net.kuama.documentscanner.extensions

import android.net.Uri
import androidx.core.net.toFile


fun Uri.delete(): Boolean? = toFileOrNull()?.delete()

fun Uri.toFileOrNull() =
    try {
        toFile()
    } catch (e: IllegalArgumentException) {
        null
    }