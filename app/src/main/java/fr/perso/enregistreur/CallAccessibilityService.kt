package fr.perso.enregistreur

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * Service d'accessibilité : reste actif en permanence (ce qu'Android autorise
 * pour ce type de service), écoute l'état des appels et lance / arrête
 * l'enregistrement.
 */
class CallAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
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
        val base = Recording.baseName(Date(), if (incoming) "entrant" else "sortant", number)
        val i = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START
            putExtra(RecorderService.EXTRA_BASENAME, base)
        }
        try {
            startForegroundService(i)
            recording = true
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de démarrer l'enregistrement", e)
        }
        // Capture de l'écran d'appel ~1,5 s après le décroché.
        handler.postDelayed({ screenshot(base) }, 1500)
    }

    private fun screenshot(base: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        Thread {
                            val hb = result.hardwareBuffer
                            try {
                                val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace) ?: return@Thread
                                val bmp = hw.copy(Bitmap.Config.ARGB_8888, false)
                                val out = File(Prefs.folder(this@CallAccessibilityService), "$base.jpg")
                                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                            } catch (e: Exception) {
                                Log.e(TAG, "Capture non enregistrée", e)
                            } finally {
                                hb.close()
                            }
                        }.start()
                    }
                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Capture refusée, code=$errorCode")
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Capture impossible", e)
        }
    }

    private fun stopRecording() {
        handler.removeCallbacksAndMessages(null)
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
