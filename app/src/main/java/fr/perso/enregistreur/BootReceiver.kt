package fr.perso.enregistreur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Après un redémarrage, Android interdit de réactiver le micro tout seul :
 * on affiche une notification « Touche pour réactiver ».
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !Prefs.enabled(c)) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("rappel", "Rappel de réactivation", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, "rappel")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Appels Rec en pause")
            .setContentText("Touche ici pour réactiver l'enregistrement des appels")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(44, n) } catch (_: SecurityException) {}
    }
}
