package app.mydear.android.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mydear.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SupertonicDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.MINUTES)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val root = applicationContext.filesDir.resolve("models").canonicalFile
        val stagingRoot = root.resolve("staging").apply { mkdirs() }.canonicalFile
        val installedRoot = root.resolve("installed").apply { mkdirs() }.canonicalFile
        val staging = stagingRoot.resolve("$MODEL_ID-$REVISION.download").canonicalFile
        if (staging.parentFile != stagingRoot) return@withContext Result.failure(errorData("목소리 모델 경로가 올바르지 않아요"))
        if (applicationContext.filesDir.usableSpace < REQUIRED_FREE_BYTES) {
            return@withContext Result.failure(errorData("목소리 설치를 위한 저장 공간이 부족해요"))
        }
        try {
            setForeground(foreground(0))
            if (staging.exists()) staging.deleteRecursively()
            check(staging.mkdirs()) { "목소리 임시 폴더를 만들 수 없어요" }
            val archive = staging.resolve("supertonic.tar.bz2.part")
            client.newCall(Request.Builder().url(ARCHIVE_URL).build()).execute().use { response ->
                check(response.isSuccessful) { "목소리 서버가 응답하지 않았어요 (${response.code})" }
                response.body.byteStream().buffered().use { input ->
                    archive.outputStream().buffered().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var total = 0L
                        var lastProgress = -1
                        while (true) {
                            if (isStopped) throw InterruptedException("다운로드가 취소되었어요")
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= ARCHIVE_BYTES) { "목소리 파일이 예상보다 커요" }
                            output.write(buffer, 0, count)
                            val progress = ((total * 70) / ARCHIVE_BYTES).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                setProgress(Data.Builder().putInt(KEY_PROGRESS, progress).build())
                                setForeground(foreground(progress))
                            }
                        }
                        check(total == ARCHIVE_BYTES) { "목소리 다운로드가 완전하지 않아요" }
                    }
                }
            }
            check(archive.sha256() == ARCHIVE_SHA256) { "목소리 파일 무결성 확인에 실패했어요" }
            extractAllowlisted(archive, staging)
            check(archive.delete()) { "임시 압축 파일을 정리하지 못했어요" }
            REQUIRED_FILES.forEach { (name, expected) ->
                val file = staging.resolve(name)
                check(file.isFile && file.length() == expected.first && file.sha256() == expected.second) {
                    "목소리 모델 파일이 손상되었어요: $name"
                }
            }

            val target = installedRoot.resolve("$MODEL_ID-$REVISION").canonicalFile
            check(target.parentFile == installedRoot) { "목소리 설치 경로가 올바르지 않아요" }
            if (target.exists()) {
                REQUIRED_FILES.forEach { (name, expected) ->
                    val file = target.resolve(name)
                    check(file.isFile && file.length() == expected.first && file.sha256() == expected.second) {
                        "기존 목소리 폴더가 예상과 달라 자동으로 덮어쓰지 않았어요"
                    }
                }
                staging.deleteRecursively()
            } else {
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            val pointer = root.resolve("active-$MODEL_ID.txt")
            val pending = root.resolve(".${pointer.name}.new")
            pending.writeText(target.name)
            Files.move(pending.toPath(), pointer.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            setProgress(Data.Builder().putInt(KEY_PROGRESS, 100).build())
            Result.success()
        } catch (error: Exception) {
            staging.deleteRecursively()
            Result.failure(errorData(error.message?.take(180) ?: "목소리 모델을 설치하지 못했어요"))
        }
    }

    private fun extractAllowlisted(archive: File, staging: File) {
        val extracted = mutableSetOf<String>()
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                val expected = REQUIRED_FILES[name] ?: continue
                check(name !in extracted && entry.size == expected.first) { "목소리 압축 파일 구성이 올바르지 않아요" }
                val destination = staging.resolve(name).canonicalFile
                check(destination.parentFile == staging.canonicalFile) { "목소리 압축 경로가 올바르지 않아요" }
                destination.outputStream().buffered().use { output ->
                    val copied = tar.copyTo(output, bufferSize = 128 * 1024)
                    check(copied == expected.first) { "목소리 파일 압축 해제가 완전하지 않아요" }
                }
                extracted += name
            }
        }
        check(extracted == REQUIRED_FILES.keys) { "목소리 압축 파일에 필요한 항목이 없어요" }
    }

    private fun foreground(progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "목소리 설치", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("한국어 목소리 설치 중")
            .setContentText("$progress% · 설치가 끝날 때까지 기다려 주세요")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun errorData(message: String) = Data.Builder().putString(KEY_ERROR, message).build()

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val MODEL_ID = "supertonic-3-int8-ko"
        private const val REVISION = "sherpa-int8-2026-05-11"
        private const val ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
        private const val ARCHIVE_BYTES = 128_774_318L
        private const val ARCHIVE_SHA256 = "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427"
        private const val REQUIRED_FREE_BYTES = 512L * 1024 * 1024
        private const val CHANNEL_ID = "tts_install"
        private const val NOTIFICATION_ID = 4103
        const val WORK_NAME = "supertonic-install"
        private val REQUIRED_FILES = mapOf(
            "duration_predictor.int8.onnx" to (3_700_147L to "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"),
            "text_encoder.int8.onnx" to (36_416_150L to "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"),
            "tts.json" to (8_253L to "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"),
            "unicode_indexer.bin" to (262_144L to "8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30"),
            "vector_estimator.int8.onnx" to (78_400_833L to "20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd"),
            "vocoder.int8.onnx" to (25_991_073L to "e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152"),
            "voice.bin" to (517_168L to "67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8"),
        )

        fun enqueue(context: Context) = OneTimeWorkRequestBuilder<SupertonicDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
            .also { request ->
                WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            }
    }
}
