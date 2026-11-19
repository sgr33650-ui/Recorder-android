package fr.perso.enregistreur

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Fenêtre affichée à la fin de chaque appel :
 * Conserver ou Supprimer (avec confirmation).
 */
class ReviewActivity : Activity() {

    private lateinit var rec: Recording
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false) // oblige à choisir

        val base = intent.getStringExtra(EXTRA_BASE)
        val audio = base?.let { File(Prefs.folder(this), "$it.m4a") }
        if (audio == null || !audio.exists()) { finish(); return }
        rec = Recording(audio)
        title = "Appel terminé"

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "${rec.directionText} · ${rec.number}\n" +
                "${rec.dateText} à ${rec.timeText}\nDurée : ${rec.durationText}"
            textSize = 16f
        })

        val thumb = ImageView(this).apply { adjustViewBounds = true }
        root.addView(thumb, LinearLayout.LayoutParams(MATCH_PARENT, (220 * resources.displayMetrics.density).toInt()))
        loadThumb(thumb, 0)

        root.addView(Button(this).apply {
            text = "▶ Écouter"
            setOnClickListener { togglePlay(this) }
        })

        root.addView(TextView(this).apply {
            text = "Conserver cet enregistrement ?"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 2)
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "✅ Conserver"
            setOnClickListener { keep() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "🗑 Supprimer"
            setOnClickListener { confirmDelete() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row)

        setContentView(root)
    }

    /** La capture peut arriver une fraction de seconde après : on réessaie. */
    private fun loadThumb(view: ImageView, attempt: Int) {
        if (rec.screenshot.exists()) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(rec.screenshot.absolutePath, opts)?.let { view.setImageBitmap(it) }
        } else if (attempt < 2) {
            view.postDelayed({ loadThumb(view, attempt + 1) }, 600)
        } else {
            view.visibility = android.view.View.GONE
        }
    }

    private fun togglePlay(btn: Button) {
        if (player != null) {
            stopPlayer(); btn.text = "▶ Écouter"; return
        }
        try {
            player = MediaPlayer().apply {
                setDataSource(rec.audio.absolutePath)
                setOnCompletionListener { stopPlayer(); btn.text = "▶ Écouter" }
                prepare(); start()
            }
            btn.text = "⏹ Arrêter"
        } catch (e: Exception) {
            Toast.makeText(this, "Lecture impossible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayer() { player?.release(); player = null }

    private fun keep() {
        Toast.makeText(this, "Enregistrement conservé", Toast.LENGTH_SHORT).show()
        done()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Supprimer définitivement ?")
            .setMessage("L'enregistrement de cet appel sera effacé. Cette action est irréversible.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Oui, supprimer") { _, _ ->
                stopPlayer()
                rec.audio.delete()
                rec.screenshot.delete()
                Toast.makeText(this, "Enregistrement supprimé", Toast.LENGTH_SHORT).show()
                done()
            }
            .show()
    }

    private fun done() {
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        finish()
    }

    // Retour = on garde (rien n'est jamais supprimé sans confirmation).
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { keep() }

    override fun onDestroy() { stopPlayer(); super.onDestroy() }

    companion object {
        const val EXTRA_BASE = "base"
        private const val CHANNEL = "fin_appel"
        private const val NOTIF_ID = 43

        /** Ouvre la fenêtre ; une notification sert de secours si Android bloque l'ouverture. */
        fun prompt(c: Context, base: String) {
            val intent = Intent(c, ReviewActivity::class.java)
                .putExtra(EXTRA_BASE, base)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Fin d'appel", NotificationManager.IMPORTANCE_HIGH)
            )
            val pi = PendingIntent.getActivity(
                c, base.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Appel enregistré")
                .setContentText("Conserver ou supprimer ? Appuie pour choisir.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) {}

            try {
                c.startActivity(intent)
            } catch (e: Exception) {
                Log.w("EnregAppels", "Ouverture de la fenêtre bloquée, notification seule", e)
            }
        }
    }
}
