package com.nowtuneup.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nowtuneup.app.MainActivity
import com.nowtuneup.app.R
import com.nowtuneup.app.data.obd.session.ObdSessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground owner for the singleton OBD session.
 * Keeping polling in this service prevents an Activity/ViewModel recreation from ending a live run.
 */
@AndroidEntryPoint
class MonitoringService : Service() {
    @Inject lateinit var session: ObdSessionManager

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Vehicle monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            session.setPerformanceSampling(false)
            session.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        session.startPolling()
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitoringService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val performance = session.performanceSampling.value
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("NTU Vehicle Monitoring")
                .setContentText(if (performance) "Time Slip performance sampling active" else "Connection active • live OBD monitoring")
                .setContentIntent(open)
                .addAction(0, "Stop monitoring", stop)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build(),
        )
        return START_STICKY
    }

    override fun onDestroy() {
        session.setPerformanceSampling(false)
        session.pause()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "monitoring"
        const val STOP = "com.nowtuneup.app.STOP_MONITORING"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitoringService::class.java).setAction(STOP))
        }
    }
}
