package net.kuama.documentscanner.extensions

import android.content.Context
import android.widget.Toast
import java.io.File

fun Context.outputDirectory() =
    File(filesDir, "Scanner").apply {
        mkdirs()
    }

fun Context.toast(message:String){
    Toast.makeText(this,message, Toast.LENGTH_LONG).show()
}
