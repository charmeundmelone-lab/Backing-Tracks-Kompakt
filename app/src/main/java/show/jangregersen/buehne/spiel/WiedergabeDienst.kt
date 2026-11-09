package show.jangregersen.buehne.spiel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import show.jangregersen.buehne.MainActivity
import show.jangregersen.buehne.R

/**
 * Hält den Prozess am Leben, solange gespielt wird (Mudita bremst Hintergrund-Apps).
 * Enthält keine Logik, nur die Pflicht-Mitteilung.
 */
class WiedergabeDienst : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "Wiedergabe", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        val oeffnen = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_mitteilung)
            .setContentTitle("Backing-Tracks spielt")
            .setContentIntent(oeffnen)
            .setOngoing(true)
            .build()
        val typ = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, 1, n, typ)
        return START_NOT_STICKY
    }

    companion object { private const val KANAL = "wiedergabe" }
}
