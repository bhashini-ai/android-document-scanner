package net.kuama.scannerdemo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import net.kuama.documentscanner.presentation.ScannerActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val myIntent = Intent(this@MainActivity, ScannerActivity::class.java)
        this@MainActivity.startActivity(myIntent)
    }
}