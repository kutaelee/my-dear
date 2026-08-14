package app.mydear.android.runtime.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.mydear.android.MainActivity
import app.mydear.android.R

class ScreenShareService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(NOTIFICATION_ID, notification())
                }
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val result = if (resultData == null) {
                    Result.failure(IllegalArgumentException("화면 공유 허용 정보를 찾을 수 없어요."))
                } else {
                    ScreenShareSession.start(this, intent.getIntExtra(EXTRA_RESULT_CODE, 0), resultData) { stopSelf() }
                }
                if (result.isFailure) stopSelf()
            }
            ACTION_STOP -> {
                ScreenShareSession.stop()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ScreenShareSession.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "화면 공유", NotificationManager.IMPORTANCE_LOW).apply {
            description = "내새끼가 사용자가 허용한 화면을 함께 보는 동안 표시됩니다."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("내새끼가 화면을 함께 보고 있어요")
            .setContentText("질문할 때 현재 화면을 기기 안에서 함께 봐요")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "화면 공유 멈추기", stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screen_share"
        private const val NOTIFICATION_ID = 3104
        private const val ACTION_START = "app.mydear.android.screen.START"
        private const val ACTION_STOP = "app.mydear.android.screen.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenShareService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenShareService::class.java).setAction(ACTION_STOP))
        }
    }
}
