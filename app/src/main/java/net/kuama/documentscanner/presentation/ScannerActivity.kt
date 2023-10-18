package net.kuama.documentscanner.presentation

import android.widget.Toast
import net.kuama.documentscanner.R
import net.kuama.documentscanner.exceptions.MissingSquareException

open class ScannerActivity : BaseScannerActivity() {
    override fun onError(throwable: Throwable) {
        when (throwable) {
            is MissingSquareException -> Toast.makeText(
                this,
                R.string.ds_null_corners, Toast.LENGTH_LONG
            ).show()
            else -> Toast.makeText(this, throwable.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onClose() {
        finish()
    }
}
