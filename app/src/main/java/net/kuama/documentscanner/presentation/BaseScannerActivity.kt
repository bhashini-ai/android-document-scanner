package net.kuama.documentscanner.presentation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.StackFrom
import com.yuyakaido.android.cardstackview.SwipeableMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kuama.documentscanner.R
import net.kuama.documentscanner.data.OpenCVLoader
import net.kuama.documentscanner.databinding.ActivityScannerBinding
import net.kuama.documentscanner.enums.EFlashStatus
import net.kuama.documentscanner.extensions.delete
import net.kuama.documentscanner.extensions.hide
import net.kuama.documentscanner.extensions.logError
import net.kuama.documentscanner.extensions.outputDirectory
import net.kuama.documentscanner.extensions.show
import net.kuama.documentscanner.extensions.toast
import net.kuama.documentscanner.viewmodels.ScannerViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

abstract class BaseScannerActivity : AppCompatActivity() {
    companion object {
        private val TAG = ScannerActivity::class.java.simpleName
        const val EXTRA_URI = "ScannedDocumentUri"
    }

    lateinit var viewModel: ScannerViewModel
    private lateinit var binding: ActivityScannerBinding
    private val takenPhotosAdapter = StackViewAdapter()

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val bitmapUri =
                    result.data?.extras?.getString("croppedPath") ?: error("invalid path")

                val uri = Uri.fromFile(File(bitmapUri))
                viewModel.savePhoto(uri)
            } else {
                logError(TAG, "resultLauncher: ${result.resultCode}")
                viewModel.onViewCreated(OpenCVLoader(this), this, binding.viewFinder)
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val viewModel: ScannerViewModel by viewModels()

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) binding.progress.show() else binding.progress.hide()
        }

        viewModel.lastUri.observe(this) {
            val intent = Intent(this, CropperActivity::class.java)
            intent.putExtra("lastUri", it.toString())
            intent.putExtra("screenOrientationDeg", viewModel.screenOrientationDeg.value)

            resultLauncher.launch(intent)
        }

        viewModel.errors.observe(this) {
            onError(it)
            logError(TAG, it.message)
        }

        viewModel.corners.observe(this) {
            it?.let { corners ->
                binding.hud.onCornersDetected(corners)
            } ?: run {
                binding.hud.onCornersNotDetected()
            }
        }

        viewModel.flashStatus.observe(this) { status ->
            binding.flashMode.setImageResource(
                when (status) {
                    EFlashStatus.ON -> R.drawable.flash_on
                    EFlashStatus.OFF -> R.drawable.flash_off
                    else -> R.drawable.flash_off
                }
            )
        }

        setUpPreviewAdapter()
        setOnFlashModeClicked()
        setOnTakePictureClicked()
        setOnDoneClicked()
        setOnCloseClicked()
        setOnPreviewStackClicked()
        setFullscreen()
        this.viewModel = viewModel
        observeCameraViewState()
        orientationEventListener.enable()
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener.enable()
        performCleanUp()
        viewModel.onViewCreated(OpenCVLoader(this), this, binding.viewFinder)
    }

    override fun onPause() {
        super.onPause()
        dismissDialog()
    }

    private fun observeCameraViewState() {
        viewModel.takenPhotos.observe(this) { photos ->
            updateUiElements(photos)
        }
    }

    private fun updateUiElements(photos: List<Uri>) {
        takenPhotosAdapter.addImageUris(*photos.toTypedArray())
        if (photos.isEmpty()) {
            binding.apply {
                cameraElementsWrapper.setBackgroundColor(Color.TRANSPARENT)
                takePicture.show()
                done.hide()
                previewStack.hide()
            }
        } else {
            binding.apply {
                cameraElementsWrapper.setBackgroundColor(
                    ContextCompat.getColor(
                        this@BaseScannerActivity,
                        R.color.darkGray
                    )
                )
                done.show()
                previewStack.show()
            }
        }
    }

    private fun updateDialog() {
        val dialogFragment =
            this.supportFragmentManager.findFragmentByTag(ReviewTakenPhotosDialog::class.simpleName) as? DialogFragment

        if (dialogFragment?.dialog != null) {
            dialogFragment.dismiss()
            ReviewTakenPhotosDialog.show(
                this,
                takenPhotosAdapter.imageUris,
                onReceiveDeletedPhotoIndex = { removedItemIndex ->
                    viewModel.deletePhoto(removedItemIndex)
                },
                onDialogResumed = { viewModel.disableImageAnalysisUseCase() },
                onDialogDismissed = { viewModel.enableImageAnalysisUseCase() })
        }
    }

    private fun dismissDialog() {
        val dialogFragment =
            this.supportFragmentManager.findFragmentByTag(ReviewTakenPhotosDialog::class.simpleName) as? DialogFragment
        if (dialogFragment?.dialog?.isShowing == false) {
            dialogFragment.dismiss()
        }
    }

    private fun performCleanUp(){
        updateDialog()
        binding.previewOverlay.hide()
        viewModel.clearCorners()
        viewModel.clearFlashStatus()
    }
    private fun setFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            this.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.window.setDecorFitsSystemWindows(false)
            this.window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else
            @Suppress("DEPRECATION")
            this.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
    }

    private fun setOnFlashModeClicked() {
        binding.flashMode.setOnClickListener {
            viewModel.onFlashToggle()
        }
    }

    private fun setOnTakePictureClicked() {
        binding.takePicture.setOnClickListener {
            viewModel.onTakePicture(
                this.outputDirectory(),
                this,
                binding.viewFinder
            ) { viewFinderBitmap ->
                binding.previewOverlay.show()
                binding.previewOverlay.setImageBitmap(viewFinderBitmap)
            }
        }
    }

    private fun setOnDoneClicked() {
        binding.done.setOnClickListener {
            onDoneClicked()
        }
    }

    private fun setOnCloseClicked() {
        binding.closeScanner.setOnClickListener {
            onClosePreview()
        }
    }

    private fun setUpPreviewAdapter() {
        val layoutManager = CardStackLayoutManager(this).apply {
            setStackFrom(StackFrom.TopAndRight)
            setSwipeableMethod(SwipeableMethod.None)
            setVisibleCount(3)
        }
        binding.previewStack.layoutManager = layoutManager
        binding.previewStack.adapter = takenPhotosAdapter
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setOnPreviewStackClicked() {
        binding.previewStack.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                viewModel.disableImageAnalysisUseCase()
                ReviewTakenPhotosDialog.show(
                    this,
                    takenPhotosAdapter.imageUris,
                    onReceiveDeletedPhotoIndex = { removedItemIndex ->
                        viewModel.deletePhoto(removedItemIndex)
                    },
                    onDialogResumed = { viewModel.disableImageAnalysisUseCase() },
                    onDialogDismissed = { viewModel.enableImageAnalysisUseCase() }
                )
                return@setOnTouchListener true
            }
            return@setOnTouchListener true
        }
    }

    private fun onDoneClicked() {
        viewModel.takenPhotos.observe(this) { photos ->
            lifecycleScope.launch {
                val bitmapsList = mutableListOf<Bitmap>()
                photos.forEach { uri ->
                    val bitmap =
                        getBitmapFromImageUri(uri) ?: return@forEach
                    bitmapsList.add(bitmap)
                    uri.delete()
                }
               val successfullyDeletedFolder = outputDirectory().deleteRecursively()
                toast("successfullyDeletedFolder : $successfullyDeletedFolder")
                // for normal ordering of the pages, otherwise the pages are reversed
                bitmapsList.reverse()
                withContext(Dispatchers.IO) {
                    convertBitmapsToPdf(bitmapsList)
                }
                val uri = getScannedDocumentUri()
                if (uri == null) {
                    toast("Uri is null")
                    setResult(RESULT_CANCELED)
                    finish()
                } else {
                    val intent = intent.putExtra(EXTRA_URI, uri)
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getBitmapFromImageUri(uri: Uri): Bitmap? {
        return try {
            val contentResolver: ContentResolver = this.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(contentResolver, uri)
                ) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun convertBitmapsToPdf(bitmaps: List<Bitmap>) {
        val date = SimpleDateFormat(this.getString(R.string.file_name_format), Locale.GERMANY)
            .format(System.currentTimeMillis())
        val outputPath = applicationContext.cacheDir.absolutePath + "/ScannedDocument-$date.pdf"
        val document = PdfDocument()
        for ((index, bitmap) in bitmaps.withIndex()) {
            val pageNumber = index + 1
            val pageInfo =
                PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            canvas.drawBitmap(bitmap, 0f, 0f, null)
            document.finishPage(page)
        }
        val file = File(outputPath)
        if (file.exists()) {
            val isDeleted = file.delete()
            if (!isDeleted) {
                logError(TAG, "convertBitmapsToPdf: The file was not deleted")
            }
        }
        try {
            FileOutputStream(file).use { fileOutputStream ->
                document.writeTo(fileOutputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
    }

    private fun getScannedDocumentUri(): Uri? {
        val scannedDocument =
            cacheDir.listFiles()?.last { file -> file.name.startsWith("ScannedDocument") }
        return Uri.fromFile(scannedDocument)
    }

    private fun onClosePreview() {
        binding.rootView.visibility = View.GONE
        viewModel.onClosePreview()
//        this.outputDirectory().deleteRecursively()
        val successfullyDeletedFolder = outputDirectory().deleteRecursively()
        toast("successfullyDeletedFolder : $successfullyDeletedFolder")
        orientationEventListener.disable()
        finish()
    }

    abstract fun onError(throwable: Throwable)
    abstract fun onClose()
}
