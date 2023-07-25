package net.kuama.documentscanner.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.utils.futures.FutureCallback
import androidx.camera.core.impl.utils.futures.Futures.addCallback
import androidx.camera.view.CameraController.IMAGE_ANALYSIS
import androidx.camera.view.CameraController.IMAGE_CAPTURE
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kuama.documentscanner.R
import net.kuama.documentscanner.data.Corners
import net.kuama.documentscanner.data.OpenCVLoader
import net.kuama.documentscanner.domain.FindPaperSheetContours
import net.kuama.documentscanner.enums.EFlashStatus
import net.kuama.documentscanner.enums.EOpenCvStatus
import net.kuama.documentscanner.extensions.delete
import net.kuama.documentscanner.extensions.logDebug
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class ScannerViewModel : ViewModel() {
    private lateinit var controller: LifecycleCameraController

    /**
     * Observable data
     */
    val isLoading = MutableLiveData<Boolean>()
    private val openCv = MutableLiveData<EOpenCvStatus>()
    val corners = MutableLiveData<Corners?>()
    val errors = MutableLiveData<Throwable>()
    val flashStatus = MutableLiveData<EFlashStatus>()
    var lastUri = MutableLiveData<Uri>()
    private val _takenPhotos = MutableStateFlow<List<Uri>>(emptyList())
    var takenPhotos = _takenPhotos.asLiveData()
    val screenOrientationDeg = MutableLiveData<Int>()

    private var didLoadOpenCv = false

    /**
     * Use cases
     */
    private val findPaperSheetUseCase: FindPaperSheetContours = FindPaperSheetContours()

    /**
     * Tries to load OpenCv native libraries
     */
    fun onViewCreated(
        openCVLoader: OpenCVLoader,
        scannerActivity: AppCompatActivity,
        viewFinder: PreviewView
    ) {
        isLoading.value = true
        setupCamera(scannerActivity, viewFinder) {
            if (!didLoadOpenCv) {
                openCVLoader.load {
                    isLoading.value = false
                    openCv.value = it
                    didLoadOpenCv = true
                }
            } else {
                isLoading.value = false
            }
        }
    }

    fun onFlashToggle() {
        flashStatus.value?.let { currentValue ->
            flashStatus.value = when (currentValue) {
                EFlashStatus.ON -> EFlashStatus.OFF
                EFlashStatus.OFF -> EFlashStatus.ON
            }
        } ?: // default flash status is off
        run {
            // default flash status is off
            // default flash status is off
            flashStatus.value = EFlashStatus.ON
        }
        when (flashStatus.value) {
            EFlashStatus.ON -> controller.enableTorch(true)
            EFlashStatus.OFF -> controller.enableTorch(false)
            null -> controller.enableTorch(false)
        }
    }

    fun onTakePicture(outputDirectory: File, context: Context) {
        isLoading.value = true
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                context.getString(R.string.file_name_format), Locale.GERMANY
            ).format(System.currentTimeMillis()) + ".jpg"
        )
        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        disableImageAnalysis()
        controller.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    errors.value = exc
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    //todo: check why it's using `lastUri.value = Uri.fromFile(photoFile)`
                    // when `output.savedUri` returns the same path on debug
                    lastUri.value = Uri.fromFile(photoFile)
                    logDebug("Photo capture succeeded :  output.savedUri is ${output.savedUri}")
                    logDebug("Photo capture succeeded : lastUri.value is ${lastUri.value}")
                }
            })
    }

    @SuppressLint("RestrictedApi", "UnsafeExperimentalUsageError")
    private fun setupCamera(
        lifecycleOwner: AppCompatActivity,
        viewFinder: PreviewView,
        then: () -> Unit
    ) {
        isLoading.value = true

        val executor: Executor = ContextCompat.getMainExecutor(lifecycleOwner)
        controller = LifecycleCameraController(lifecycleOwner)
        controller.setImageAnalysisAnalyzer(executor) { proxy: ImageProxy ->
            // could not find a performing way to transform
            // the proxy to a bitmap, so we are reading
            // the bitmap directly from the preview view

            viewFinder.bitmap?.let {
                analyze(it, onSuccess = {
                    proxy.close()
                })
            } ?: run {
                corners.value = null
                proxy.close()
            }
        }
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.setEnabledUseCases(IMAGE_CAPTURE or IMAGE_ANALYSIS)

        controller.bindToLifecycle(lifecycleOwner)
        viewFinder.controller = controller
        addCallback(
            controller.initializationFuture,
            object : FutureCallback<Void> {
                override fun onSuccess(result: Void?) {
                    then()
                }

                override fun onFailure(t: Throwable) {
                    errors.value = t
                }
            },
            executor
        )
        then.invoke()
    }

    private fun analyze(
        bitmap: Bitmap,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            findPaperSheetUseCase(FindPaperSheetContours.Params(bitmap)) { resultingCorners: Corners? ->
                corners.value = resultingCorners
                onSuccess?.invoke()
            }
        }
    }

    private fun disableImageAnalysis() {
        controller.clearImageAnalysisAnalyzer()
    }

    fun clearCorners() {
        corners.value = null
    }

    fun onClosePreview() {
        _takenPhotos.value.forEach { uri ->
            uri.delete()
        }
    }

    fun onScreenOrientationDegChange(orientationDeg: Int) {
        screenOrientationDeg.value = orientationDeg
    }

    fun savePhoto(uri: Uri) {
        _takenPhotos.update {
            listOf(uri) + it
        }
    }

    fun deletePhoto(index: Int) {
        _takenPhotos.update { photos ->
            val updatedPhotos = photos.toMutableList()
            updatedPhotos.apply {
                removeAt(index).also { removedPhoto -> removedPhoto.delete() }
            }
            updatedPhotos
        }
    }
}
