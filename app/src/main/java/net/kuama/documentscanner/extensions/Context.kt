package net.kuama.documentscanner.extensions

import android.content.Context
import net.kuama.documentscanner.R
import java.io.File

fun Context.outputDirectory() =
    File(filesDir, applicationContext.resources.getString(R.string.app_name)).apply {
        mkdirs()
    }
