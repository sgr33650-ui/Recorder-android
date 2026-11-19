package fr.perso.enregistreur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Reçoit l'annonce système « état du téléphone ». Contrairement à un écouteur,
 * Android réveille l'appli pour la lui livrer, même si HyperOS l'a gelée.
 */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val service = RecorderService.instance
        if (service == null) {
            Journal.log(c, "Appel détecté ($state) mais surveillance arrêtée : ouvre l'appli")
            return
        }
        service.onCallState(state, number)
    }
}
