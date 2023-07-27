package net.kuama.documentscanner.extensions

import android.content.Context
import android.widget.Toast
import net.kuama.documentscanner.R
import java.io.File

fun Context.outputDirectory() =
    File(filesDir, applicationContext.resources.getString(R.string.app_name)).apply {
        mkdirs()
    }

fun Context.toast(message:String){
    Toast.makeText(this,message, Toast.LENGTH_LONG).show()
}
