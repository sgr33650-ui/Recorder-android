package fr.perso.enregistreur

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Service de premier plan permanent (notification « Appels Rec actif »).
 * Il est démarré depuis l'appli (au premier plan), ce qui lui donne le droit
 * d'utiliser le micro ensuite ; il écoute l'état des appels et enregistre.
 */
class RecorderService : Service() {

    private var telephony: TelephonyManager? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var speakerForced = false
    private var incoming = false
    private var number: String? = null

    @Suppress("DEPRECATION")
    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    incoming = true
                    if (!phoneNumber.isNullOrBlank()) number = phoneNumber
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (!phoneNumber.isNullOrBlank()) number = phoneNumber
                    if (recorder == null && Prefs.enabled(this@RecorderService)) {
                        startRecording(
                            Recording.baseName(Date(), if (incoming) "entrant" else "sortant", number)
                        )
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    stopRecording()
                    incoming = false
                    number = null
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        goForeground("Appels Rec actif", "Les appels seront enregistrés automatiquement")
        telephony = getSystemService(TelephonyManager::class.java)
        try {
            @Suppress("DEPRECATION")
            telephony?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission téléphone manquante", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun goForeground(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Surveillance des appels", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
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
    private fun startRecording(base: String) {
        if (Prefs.speaker(this)) {
            val am = getSystemService(AudioManager::class.java)
            if (!am.isSpeakerphoneOn) {
                am.isSpeakerphoneOn = true
                speakerForced = true
            }
        }

        val out = File(Prefs.folder(this), "$base.m4a")
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
                goForeground("🔴 Appel en cours d'enregistrement", Prefs.SOURCES[idx].second)
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
    private fun stopRecording() {
        val r = recorder ?: return
        try { r.stop() } catch (_: Exception) { file?.delete() }
        r.release()
        recorder = null
        if (speakerForced) {
            getSystemService(AudioManager::class.java).isSpeakerphoneOn = false
            speakerForced = false
        }
        file?.let {
            if (it.exists() && it.length() < 2_000) it.delete()
            if (it.exists() && Prefs.askAfterCall(this)) ReviewActivity.prompt(this, it.nameWithoutExtension)
        }
        file = null
        goForeground("Appels Rec actif", "Les appels seront enregistrés automatiquement")
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        telephony?.listen(listener, PhoneStateListener.LISTEN_NONE)
        stopRecording()
        running = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "fr.perso.enregistreur.STOP"
        private const val CHANNEL = "surveillance"
        private const val NOTIF_ID = 42
        private const val TAG = "EnregAppels"

        @Volatile var running = false
            private set

        fun start(c: Context) {
            c.startForegroundService(Intent(c, RecorderService::class.java))
        }

        fun stop(c: Context) {
            c.startService(Intent(c, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
