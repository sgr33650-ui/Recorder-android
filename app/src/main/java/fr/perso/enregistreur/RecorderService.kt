package fr.perso.enregistreur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Service de premier plan qui enregistre l'audio pendant l'appel. */
class RecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var speakerForced = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                goForeground()
                start(
                    intent.getStringExtra(EXTRA_DIRECTION) ?: "appel",
                    intent.getStringExtra(EXTRA_NUMBER) ?: ""
                )
            }
            ACTION_STOP -> {
                stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Enregistrement en cours", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Appel en cours d'enregistrement")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun start(direction: String, number: String) {
        if (recorder != null) return

        if (Prefs.speaker(this)) {
            val am = getSystemService(AudioManager::class.java)
            if (!am.isSpeakerphoneOn) {
                am.isSpeakerphoneOn = true
                speakerForced = true
            }
        }

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(Date())
        val safeNumber = number.replace(Regex("[^0-9+]"), "").ifEmpty { "inconnu" }
        val out = File(Prefs.folder(this), "${stamp}_${direction}_${safeNumber}.m4a")

        // Essaie d'abord la source choisie, puis les autres.
        val chosen = Prefs.sourceIndex(this)
        val order = listOf(chosen) + Prefs.SOURCES.indices.filter { it != chosen }
        for (idx in order) {
            val r = newRecorder()
            try {
                r.setAudioSource(Prefs.SOURCES[idx].first)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(64_000)
                r.setAudioSamplingRate(44_100)
                r.setOutputFile(out.absolutePath)
                r.prepare()
                r.start()
                recorder = r
                file = out
                Log.i(TAG, "Enregistrement démarré, source=${Prefs.SOURCES[idx].second}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Source ${Prefs.SOURCES[idx].second} refusée", e)
                r.release()
                out.delete()
            }
        }
        Log.e(TAG, "Aucune source audio disponible")
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()

    @Suppress("DEPRECATION")
    private fun stop() {
        recorder?.let {
            try { it.stop() } catch (_: Exception) { file?.delete() }
            it.release()
        }
        recorder = null
        // Supprime les fichiers vides (appel trop court ou échec).
        file?.let { if (it.exists() && it.length() < 2_000) it.delete() }
        file = null
        if (speakerForced) {
            getSystemService(AudioManager::class.java).isSpeakerphoneOn = false
            speakerForced = false
        }
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "fr.perso.enregistreur.START"
        const val ACTION_STOP = "fr.perso.enregistreur.STOP"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_NUMBER = "number"
        private const val CHANNEL = "enregistrement"
        private const val NOTIF_ID = 42
        private const val TAG = "EnregAppels"
    }
}
