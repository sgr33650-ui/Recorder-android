package fr.perso.enregistreur

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream

/**
 * Version complète : un service d'accessibilité actif permet à l'appli
 * de capter le micro pendant les appels (Android ne la coupe plus)
 * et de prendre une capture d'écran de l'appel.
 */
class CallAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Journal.log(this, "Accessibilité active")
        Screenshotter.take = { base -> screenshot(base) }
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
                                Journal.log(this@CallAccessibilityService, "Capture d'écran enregistrée")
                            } catch (e: Exception) {
                                Journal.log(this@CallAccessibilityService, "Capture non enregistrée", e)
                            } finally {
                                hb.close()
                            }
                        }.start()
                    }
                    override fun onFailure(errorCode: Int) {
                        Journal.log(this@CallAccessibilityService, "Capture refusée, code $errorCode")
                    }
                })
        } catch (e: Exception) {
            Journal.log(this, "Capture impossible", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        Screenshotter.take = null
        Journal.log(this, "Accessibilité arrêtée")
        super.onDestroy()
    }
}
