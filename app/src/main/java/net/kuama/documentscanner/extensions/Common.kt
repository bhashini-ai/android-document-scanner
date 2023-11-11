package net.kuama.documentscanner.extensions

import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.squareup.picasso.Picasso
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

fun ImageView.loadImageUri(uri: Uri) {
    Picasso.get()
        .load(uri)
        .fit()
        .centerCrop()
        .into(this)
}
