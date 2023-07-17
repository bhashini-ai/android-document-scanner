package net.kuama.documentscanner.presentation

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import net.kuama.documentscanner.R
import net.kuama.documentscanner.data.OpenCVLoader
import net.kuama.documentscanner.databinding.ActivityScannerBinding
import net.kuama.documentscanner.enums.EFlashStatus
import net.kuama.documentscanner.extensions.outputDirectory
import net.kuama.documentscanner.viewmodels.ScannerViewModel
import java.io.File
import java.io.FileOutputStream

abstract class BaseScannerActivity : AppCompatActivity() {
    lateinit var viewModel: ScannerViewModel
    internal lateinit var binding: ActivityScannerBinding

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val bitmapUri =
                    result.data?.extras?.getString("croppedPath") ?: error("invalid path")

                val image = File(bitmapUri)
                val bmOptions = BitmapFactory.Options()
                val bitmap = BitmapFactory.decodeFile(image.absolutePath, bmOptions)

                val uri = Uri.fromFile(File(bitmapUri))

                //todo: improve
                viewModel.urisList.value = viewModel.urisList.value?.plus(uri) ?: listOf(uri)
                val urisList  = viewModel.urisList.value ?: listOf(uri)
                onDocumentAccepted(bitmap, urisList)
                //todo: delete the image files when they're not needed anymore
            } else {
                viewModel.onViewCreated(OpenCVLoader(this), this, binding.viewFinder)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScannerBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val viewModel: ScannerViewModel by viewModels()

        viewModel.isBusy.observe(this) { isBusy ->
            binding.progress.visibility = if (isBusy) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        }

        viewModel.lastUri.observe(this) {
            val intent = Intent(this, CropperActivity::class.java)
            intent.putExtra("lastUri", it.toString())
            intent.putExtra("screenOrientationDeg", viewModel.screenOrientationDeg.value)

            resultLauncher.launch(intent)
        }

        viewModel.errors.observe(this) {
            onError(it)
            Log.e(ScannerActivity::class.java.simpleName, it.message, it)
        }

        viewModel.corners.observe(this) {
            it?.let { corners ->
                binding.hud.onCornersDetected(corners)
            } ?: run {
                binding.hud.onCornersNotDetected()
            }
        }

        viewModel.flashStatus.observe(this) { status ->
            binding.flashToggle.setImageResource(
                when (status) {
                    EFlashStatus.ON -> R.drawable.ic_flash_on
                    EFlashStatus.OFF -> R.drawable.ic_flash_off
                    else -> R.drawable.ic_flash_off
                }
            )
        }

        binding.flashToggle.setOnClickListener {
            viewModel.onFlashToggle()
        }

        binding.shutter.setOnClickListener {
            viewModel.onTakePicture(this.outputDirectory(), this)
        }

        binding.done.setOnClickListener {
           onDoneClicked()
        }

        binding.closeScanner.setOnClickListener {
            closePreview()
        }
        this.viewModel = viewModel
        orientationEventListener.enable()
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener.enable()
        viewModel.onViewCreated(OpenCVLoader(this), this, binding.viewFinder)
    }

    private fun closePreview() {
        binding.rootView.visibility = View.GONE
        viewModel.onClosePreview()
        orientationEventListener.disable()
        finish()
    }

    private fun onDoneClicked() {
        viewModel.urisList.observe(this) { list ->
            val bitmapsList = mutableListOf<Bitmap>()
            list.forEach { uri ->
                bitmapsList.add(getBitmapFromImageUri(uri))
            }
            convertBitmapsToPdf(bitmapsList)
        }

        // todo: pass the PDF document
        setResult(RESULT_OK)
        finish()
    }

    // todo: getBitmap is deprecated, check what to use
    private fun getBitmapFromImageUri(uri: Uri): Bitmap =  MediaStore.Images.Media.getBitmap(this.contentResolver, uri)

    fun convertBitmapsToPdf(bitmaps: List<Bitmap>) {
        // Works for the emulator
        val outputPath =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/output.pdf"
        // todo:Check why this is not working out for the physical device
        //   val outputPath = applicationContext.cacheDir.absolutePath + "/output.pdf"

        val document = PdfDocument()
        for ((index, bitmap) in bitmaps.withIndex()) {
            // for normal ordering of the pages, otherwise the pages are reversed
            val pageNumber = bitmaps.size - index
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
        }
        // todo: check if document exists
        //todo: try/catch
        val fileOutputStream = FileOutputStream(outputPath)
        document.writeTo(fileOutputStream)

        document.close()
        fileOutputStream.close()
    }

    private val orientationEventListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val rotationDegree = when (orientation) {
                    ORIENTATION_UNKNOWN -> return
                    in 45 until 135 -> 270
                    in 135 until 225 -> 180
                    in 225 until 315 -> 90
                    else -> Surface.ROTATION_0
                }

                viewModel.onScreenOrientationDegChange(rotationDegree)
            }
        }
    }

    abstract fun onError(throwable: Throwable)
    abstract fun onDocumentAccepted(bitmap: Bitmap, urisList : List<Uri>? = null)
    abstract fun onClose()
}
