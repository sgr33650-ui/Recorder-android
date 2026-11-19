package fr.perso.enregistreur

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilité : reste actif en permanence (ce qu'Android autorise
 * pour ce type de service), écoute l'état des appels et lance / arrête
 * l'enregistrement.
 */
class CallAccessibilityService : AccessibilityService() {

    private var telephony: TelephonyManager? = null
    private var incoming = false
    private var number: String? = null
    private var recording = false

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
                    if (!recording && Prefs.enabled(this@CallAccessibilityService)) {
                        startRecording()
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (recording) stopRecording()
                    incoming = false
                    number = null
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onServiceConnected() {
        super.onServiceConnected()
        telephony = getSystemService(TelephonyManager::class.java)
        try {
            telephony?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission téléphone manquante", e)
        }
    }

    private fun startRecording() {
        val i = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START
            putExtra(RecorderService.EXTRA_DIRECTION, if (incoming) "entrant" else "sortant")
            putExtra(RecorderService.EXTRA_NUMBER, number ?: "")
        }
        try {
            startForegroundService(i)
            recording = true
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de démarrer l'enregistrement", e)
        }
    }

    private fun stopRecording() {
        try {
            startService(Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP
            })
        } catch (e: Exception) {
            Log.e(TAG, "Arrêt impossible", e)
        }
        recording = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* inutilisé */ }

    override fun onInterrupt() {}

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        telephony?.listen(listener, PhoneStateListener.LISTEN_NONE)
        if (recording) stopRecording()
        super.onDestroy()
    }

    companion object { private const val TAG = "EnregAppels" }
}
