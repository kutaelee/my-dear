package app.mydear.android.runtime.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ScreenShareState {
    data object Inactive : ScreenShareState
    data object Starting : ScreenShareState
    data object Active : ScreenShareState
    data class Failed(val message: String) : ScreenShareState
}

data class SharedScreenFrame(
    val jpegBytes: ByteArray,
    val recognizedText: String,
)

/** Owns one user-approved MediaProjection session. Frames and OCR text are never written to disk. */
object ScreenShareSession {
    private val stateFlow = MutableStateFlow<ScreenShareState>(ScreenShareState.Inactive)
    val state: StateFlow<ScreenShareState> = stateFlow.asStateFlow()

    private val captureMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Synchronized
    fun start(context: Context, resultCode: Int, resultData: Intent, onStopped: () -> Unit): Result<Unit> = runCatching {
        require(resultCode == Activity.RESULT_OK) { "화면 공유가 허용되지 않았어요." }
        release(stopProjection = true)
        stateFlow.value = ScreenShareState.Starting

        val manager = context.getSystemService(MediaProjectionManager::class.java)
        val nextProjection = manager.getMediaProjection(resultCode, resultData)
            ?: error("화면 공유를 시작하지 못했어요.")
        projection = nextProjection
        nextProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                synchronized(this@ScreenShareSession) {
                    release(stopProjection = false)
                }
                onStopped()
            }
        }, mainHandler)

        val windowManager = context.getSystemService(WindowManager::class.java)
        val bounds = windowManager.maximumWindowMetrics.bounds
        val width = bounds.width().coerceAtLeast(1)
        val height = bounds.height().coerceAtLeast(1)
        val densityDpi = context.resources.configuration.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        display = nextProjection.createVirtualDisplay(
            "내새끼 화면 공유",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            mainHandler,
        )
        stateFlow.value = ScreenShareState.Active
    }.onFailure {
        release(stopProjection = true)
        stateFlow.value = ScreenShareState.Failed("화면 공유를 시작하지 못했어요. 다시 눌러 주세요.")
    }

    suspend fun captureFrame(): Result<SharedScreenFrame> = captureMutex.withLock {
        runCatching {
            val reader = synchronized(this) {
                check(stateFlow.value == ScreenShareState.Active) { "화면 공유가 꺼져 있어요." }
                checkNotNull(imageReader) { "공유 화면을 읽을 수 없어요." }
            }
            val image = withTimeout(FRAME_TIMEOUT_MS) { awaitLatestImage(reader) }
            val bitmap = withContext(Dispatchers.Default) { image.use(::toBitmap) }
            try {
                val jpegBytes = withContext(Dispatchers.Default) { encodeForVision(bitmap) }
                val recognizedText = recognize(bitmap).trim().take(MAX_OCR_CHARS)
                SharedScreenFrame(jpegBytes, recognizedText)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Synchronized
    fun stop() {
        release(stopProjection = true)
        stateFlow.value = ScreenShareState.Inactive
    }

    @Synchronized
    fun dismissFailure() {
        if (stateFlow.value is ScreenShareState.Failed) stateFlow.value = ScreenShareState.Inactive
    }

    private fun release(stopProjection: Boolean) {
        display?.release()
        display = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        val currentProjection = projection
        projection = null
        if (stopProjection) runCatching { currentProjection?.stop() }
        if (stateFlow.value !is ScreenShareState.Failed) stateFlow.value = ScreenShareState.Inactive
    }

    private suspend fun awaitLatestImage(reader: ImageReader): Image = suspendCancellableCoroutine { continuation ->
        reader.acquireLatestImage()?.let {
            continuation.resume(it)
            return@suspendCancellableCoroutine
        }
        reader.setOnImageAvailableListener({ availableReader ->
            val image = runCatching { availableReader.acquireLatestImage() }.getOrNull()
            if (image != null && continuation.isActive) {
                availableReader.setOnImageAvailableListener(null, null)
                continuation.resume(image)
            } else {
                image?.close()
            }
        }, mainHandler)
        continuation.invokeOnCancellation { reader.setOnImageAvailableListener(null, null) }
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = image.width + (rowStride - pixelStride * image.width) / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        padded.copyPixelsFromBuffer(plane.buffer)
        if (paddedWidth == image.width) return padded
        return Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
    }

    private fun encodeForVision(bitmap: Bitmap): ByteArray {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scale = if (longest > MAX_VISION_EDGE) MAX_VISION_EDGE.toFloat() / longest else 1f
        val encodedBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else bitmap
        return try {
            ByteArrayOutputStream().use { output ->
                check(encodedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "공유 화면을 준비하지 못했어요." }
                output.toByteArray()
            }
        } finally {
            if (encodedBitmap !== bitmap) encodedBitmap.recycle()
        }
    }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            .addOnCanceledListener { continuation.cancel() }
    }

    private const val FRAME_TIMEOUT_MS = 5_000L
    private const val MAX_OCR_CHARS = 6_000
    private const val MAX_VISION_EDGE = 1_536
}
