package com.nowtuneup.app.service
import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nowtuneup.app.MainActivity
import com.nowtuneup.app.R
class MonitoringService:Service(){companion object{const val CHANNEL="monitoring";const val STOP="com.nowtuneup.app.STOP_MONITORING"}
 override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Vehicle monitoring",NotificationManager.IMPORTANCE_LOW))}
 override fun onStartCommand(i:Intent?,flags:Int,id:Int):Int{if(i?.action==STOP){stopSelf();return START_NOT_STICKY};val stop=PendingIntent.getService(this,1,Intent(this,MonitoringService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE);startForeground(1001,NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("NTU Vehicle Monitoring").setContentText("Connection active • live data monitoring").setContentIntent(open).addAction(0,"Stop monitoring",stop).setOngoing(true).build());return START_STICKY}
 override fun onBind(i:Intent?):IBinder?=null
}
