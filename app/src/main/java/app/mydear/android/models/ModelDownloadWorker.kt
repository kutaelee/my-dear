package app.mydear.android.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.mydear.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.MINUTES)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tier = runCatching { GemmaTier.valueOf(inputData.getString(KEY_TIER).orEmpty()) }.getOrNull()
            ?: return@withContext Result.failure(errorData("알 수 없는 모델 등급이에요"))
        val artifact = ModelCatalog.artifact(tier)
        val root = applicationContext.filesDir.resolve("models").canonicalFile
        val stagingRoot = root.resolve("staging").apply { mkdirs() }.canonicalFile
        val installedRoot = root.resolve("installed").apply { mkdirs() }.canonicalFile
        val staging = stagingRoot.resolve("${artifact.id}-${artifact.revision}.download").canonicalFile
        if (staging.parentFile != stagingRoot) return@withContext Result.failure(errorData("모델 저장 경로가 올바르지 않아요"))
        if (applicationContext.filesDir.usableSpace < artifact.bytes + MIN_FREE_AFTER_INSTALL) {
            return@withContext Result.failure(errorData("저장 공간이 부족해요. 최소 ${formatGiB(artifact.bytes + MIN_FREE_AFTER_INSTALL)}가 필요해요."))
        }

        try {
            setForeground(createForegroundInfo(tier, 0))
            if (staging.exists()) staging.deleteRecursively()
            check(staging.mkdirs()) { "모델 임시 폴더를 만들 수 없어요" }
            val partial = staging.resolve("${artifact.fileName}.part")
            val request = Request.Builder().url(artifact.downloadUrl).header("Accept", "application/octet-stream").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "모델 서버가 응답하지 않았어요 (${response.code})" }
                val body = response.body
                val advertised = body.contentLength()
                check(advertised == -1L || advertised == artifact.bytes) { "모델 크기 정보가 달라요" }
                body.byteStream().buffered().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var total = 0L
                        var lastPercent = -1
                        while (true) {
                            if (isStopped) throw InterruptedException("다운로드가 취소되었어요")
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            check(total <= artifact.bytes) { "모델 파일이 예상보다 커요" }
                            output.write(buffer, 0, count)
                            val percent = ((total * 100) / artifact.bytes).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                setProgress(Data.Builder().putInt(KEY_PROGRESS, percent).build())
                                setForeground(createForegroundInfo(tier, percent))
                            }
                        }
                        check(total == artifact.bytes) { "모델 다운로드가 완전하지 않아요" }
                    }
                }
            }
            check(partial.sha256() == artifact.sha256) { "모델 무결성 확인에 실패했어요" }
            val complete = staging.resolve(artifact.fileName)
            Files.move(partial.toPath(), complete.toPath(), StandardCopyOption.ATOMIC_MOVE)

            val target = installedRoot.resolve("${artifact.id}-${artifact.revision}").canonicalFile
            check(target.parentFile == installedRoot) { "모델 설치 경로가 올바르지 않아요" }
            if (target.exists()) {
                val existing = target.resolve(artifact.fileName)
                check(existing.isFile && existing.length() == artifact.bytes && existing.sha256() == artifact.sha256) {
                    "기존 모델 폴더가 예상과 달라 자동으로 덮어쓰지 않았어요"
                }
                staging.deleteRecursively()
            } else {
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            val pointer = root.resolve("active-${artifact.id}.txt")
            val pending = root.resolve(".${pointer.name}.new")
            pending.writeText(target.name)
            Files.move(pending.toPath(), pointer.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            Result.success(Data.Builder().putString(KEY_INSTALLED_TIER, tier.name).build())
        } catch (cancelled: InterruptedException) {
            staging.deleteRecursively()
            Result.failure(errorData(cancelled.message ?: "다운로드가 취소되었어요"))
        } catch (error: Exception) {
            staging.deleteRecursively()
            Result.failure(errorData(error.message?.take(180) ?: "모델을 설치하지 못했어요"))
        }
    }

    private fun createForegroundInfo(tier: GemmaTier, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "AI 모델 설치", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${tier.label} 설치 중")
            .setContentText("$progress% · Wi-Fi 연결을 유지해 주세요")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
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

    private fun errorData(message: String): Data = Data.Builder().putString(KEY_ERROR, message).build()
    private fun formatGiB(bytes: Long): String = "%.1fGB".format(bytes.toDouble() / (1024 * 1024 * 1024))

    companion object {
        const val KEY_TIER = "tier"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_INSTALLED_TIER = "installed_tier"
        private const val CHANNEL_ID = "model_install"
        private const val NOTIFICATION_ID = 4102
        private const val MIN_FREE_AFTER_INSTALL = 768L * 1024 * 1024

        fun enqueue(context: Context, tier: GemmaTier) = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(Data.Builder().putString(KEY_TIER, tier.name).build())
            .build()
            .also { request ->
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "gemma-${tier.name.lowercase()}-install",
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }
    }
}
