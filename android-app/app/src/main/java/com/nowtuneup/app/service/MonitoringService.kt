package com.nowtuneup.app.service

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nowtuneup.app.MainActivity
import com.nowtuneup.app.R

class MonitoringService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Vehicle monitoring", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val timeSlip = intent?.action in setOf(START_TIME_SLIP_SPEED, START_TIME_SLIP_DISTANCE)
        val distanceTest = intent?.action == START_TIME_SLIP_DISTANCE
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
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (timeSlip) "NTU Time Slip กำลังทำงาน" else "NTU Vehicle Monitoring")
            .setContentText(
                when {
                    distanceTest -> "กำลังเก็บ OBD-II, GPS และเซนเซอร์สำหรับการทดสอบระยะทาง"
                    timeSlip -> "กำลังเก็บความเร็ว OBD-II และเซนเซอร์สำหรับการทดสอบ"
                    else -> "Connection active • live data monitoring"
                },
            )
            .setContentIntent(open)
            .addAction(0, if (timeSlip) "หยุดการทดสอบ" else "Stop monitoring", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val serviceType = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0
            distanceTest -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "monitoring"
        const val STOP = "com.nowtuneup.app.STOP_MONITORING"
        const val START_TIME_SLIP_SPEED = "com.nowtuneup.app.START_TIME_SLIP_SPEED"
        const val START_TIME_SLIP_DISTANCE = "com.nowtuneup.app.START_TIME_SLIP_DISTANCE"
        private const val NOTIFICATION_ID = 1001

        fun startTimeSlip(context: Context, requiresGps: Boolean) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitoringService::class.java).setAction(
                    if (requiresGps) START_TIME_SLIP_DISTANCE else START_TIME_SLIP_SPEED,
                ),
            )
        }

        fun stopTimeSlip(context: Context) {
            runCatching { context.stopService(Intent(context, MonitoringService::class.java)) }
        }
    }
}
