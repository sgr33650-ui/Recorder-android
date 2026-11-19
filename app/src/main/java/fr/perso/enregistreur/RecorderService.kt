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

    private var lastState: String? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var speakerForced = false
    private var incoming = false
    private var number: String? = null

    /** Appelé par CallReceiver à chaque changement d'état. */
    fun onCallState(state: String, phoneNumber: String?) {
        val nom = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> "SONNERIE"
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "DÉCROCHÉ"
            else -> "RACCROCHÉ / REPOS"
        }
        if (state != lastState) {
            Journal.log(this, "État appel : $nom (numéro ${if (phoneNumber.isNullOrBlank()) "masqué" else "reçu"})")
        }
        lastState = state
        if (!phoneNumber.isNullOrBlank()) number = phoneNumber
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> incoming = true
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (recorder == null && Prefs.enabled(this)) {
                    startRecording(
                        Recording.baseName(Date(), if (incoming) "entrant" else "sortant", number)
                    )
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                stopRecording()
                incoming = false
                number = null
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Journal.log(this, "Service démarré")
        goForeground("Appels Rec actif", "Les appels seront enregistrés automatiquement")
        instance = this
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
                Journal.log(this, "Enregistrement démarré, source : ${Prefs.SOURCES[idx].second}")
                return
            } catch (e: Exception) {
                Journal.log(this, "Source refusée : ${Prefs.SOURCES[idx].second}", e)
                r.release()
                out.delete()
            }
        }
        Journal.log(this, "ÉCHEC : aucune source audio n'a fonctionné")
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()

    @Suppress("DEPRECATION")
    private fun stopRecording() {
        val r = recorder ?: return
        try { r.stop() } catch (e: Exception) {
            Journal.log(this, "Erreur à l'arrêt (fichier vide ?)", e)
            file?.delete()
        }
        r.release()
        recorder = null
        if (speakerForced) {
            getSystemService(AudioManager::class.java).isSpeakerphoneOn = false
            speakerForced = false
        }
        file?.let {
            Journal.log(this, "Enregistrement arrêté : ${if (it.exists()) "${it.length() / 1024} Ko" else "aucun fichier"}")
            if (it.exists() && it.length() < 2_000) { it.delete(); Journal.log(this, "Fichier trop petit, supprimé") }
            if (it.exists() && Prefs.askAfterCall(this)) ReviewActivity.prompt(this, it.nameWithoutExtension)
        }
        file = null
        goForeground("Appels Rec actif", "Les appels seront enregistrés automatiquement")
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        instance = null
        stopRecording()
        running = false
        Journal.log(this, "Service arrêté")
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "fr.perso.enregistreur.STOP"
        private const val CHANNEL = "surveillance"
        private const val NOTIF_ID = 42
        private const val TAG = "EnregAppels"

        @Volatile var instance: RecorderService? = null
            private set

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
