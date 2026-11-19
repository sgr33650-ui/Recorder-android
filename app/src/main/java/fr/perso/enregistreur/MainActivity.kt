package fr.perso.enregistreur

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var sourceBtn: Button
    private lateinit var list: ListView
    private var items: List<Recording> = emptyList()
    private var player: MediaPlayer? = null
    private var playing: File? = null

    private val permissions: Array<String>
        get() = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 15f }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "1. Autoriser les permissions"
            setOnClickListener { requestPermissions(permissions, 1) }
        })
        root.addView(Button(this).apply {
            text = "2. Désactiver l'optimisation batterie"
            setOnClickListener { askBattery() }
        })

        root.addView(Switch(this).apply {
            text = "Enregistrement automatique des appels"
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, v ->
                Prefs.setEnabled(this@MainActivity, v)
                syncService()
                refreshStatus()
            }
        })
        root.addView(Switch(this).apply {
            text = "Mettre le haut-parleur pendant l'appel (capte mieux l'interlocuteur)"
            isChecked = Prefs.speaker(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setSpeaker(this@MainActivity, v) }
        })

        root.addView(Switch(this).apply {
            text = "Demander « Conserver / Supprimer » à la fin de chaque appel"
            isChecked = Prefs.askAfterCall(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setAskAfterCall(this@MainActivity, v) }
        })

        sourceBtn = Button(this).apply {
            setOnClickListener {
                val next = (Prefs.sourceIndex(this@MainActivity) + 1) % Prefs.SOURCES.size
                Prefs.setSourceIndex(this@MainActivity, next)
                refreshSource()
            }
        }
        root.addView(sourceBtn)

        root.addView(Button(this).apply {
            text = "📋 Exporter la liste des appels (tableau CSV)"
            setOnClickListener { exportCsv() }
        })

        root.addView(TextView(this).apply {
            text = "Enregistrements (appui : lire/arrêter, appui long : partage, suppression)"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 2)
        })

        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        list.setOnItemClickListener { _, _, pos, _ -> togglePlay(items[pos].audio) }
        list.setOnItemLongClickListener { _, _, pos, _ -> actions(items[pos]); true }

        setContentView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        refreshSource()
    }

    override fun onResume() {
        super.onResume()
        syncService()
        refreshStatus()
        refreshList()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        syncService()
        refreshStatus()
    }

    private fun refreshSource() {
        sourceBtn.text = "Source audio : " + Prefs.SOURCES[Prefs.sourceIndex(this)].second
    }

    private fun refreshStatus() {
        val perms = permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        status.text = buildString {
            append(if (perms) "✅ Permissions accordées\n" else "❌ Permissions manquantes\n")
            append(if (RecorderService.running) "✅ Surveillance des appels active" else "❌ Surveillance arrêtée")
            if (perms && RecorderService.running) append("\n\nPrêt : les prochains appels seront enregistrés.")
        }
    }

    /** Démarre ou arrête le service permanent selon le réglage (l'appli est au premier plan ici). */
    private fun syncService() {
        val perms = permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        try {
            if (Prefs.enabled(this) && perms) {
                if (!RecorderService.running) RecorderService.start(this)
            } else if (RecorderService.running) {
                RecorderService.stop(this)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Démarrage impossible : ${e.message}", Toast.LENGTH_LONG).show()
        }
        status.postDelayed({ refreshStatus() }, 500)
    }

    private fun askBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Déjà désactivée", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            @Suppress("BatteryLife")
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun refreshList() {
        items = Recording.all(this)
        val labels = items.map { r ->
            val mark = if (r.audio == playing) "▶ " else ""
            val cam = if (r.screenshot.exists()) "  📷" else ""
            "$mark${r.dateText} à ${r.timeText}\n${r.directionText} · ${r.number} · ${r.durationText}$cam"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
    }

    private fun togglePlay(f: File) {
        val wasPlaying = playing
        stopPlayer()
        if (wasPlaying != f) {
            try {
                player = MediaPlayer().apply {
                    setDataSource(f.absolutePath)
                    setOnCompletionListener { stopPlayer(); refreshList() }
                    prepare()
                    start()
                }
                playing = f
            } catch (e: Exception) {
                Toast.makeText(this, "Lecture impossible", Toast.LENGTH_SHORT).show()
            }
        }
        refreshList()
    }

    private fun stopPlayer() {
        player?.release()
        player = null
        playing = null
    }

    private fun uri(f: File): Uri = FileProvider.getUriForFile(this, "$packageName.files", f)

    private fun actions(r: Recording) {
        AlertDialog.Builder(this)
            .setTitle("${r.dateText} à ${r.timeText} – ${r.number}")
            .setItems(arrayOf("Voir la capture d'écran", "Partager (audio + capture)", "Supprimer")) { _, which ->
                when (which) {
                    0 -> showScreenshot(r)
                    1 -> share(r)
                    2 -> {
                        if (playing == r.audio) stopPlayer()
                        r.audio.delete()
                        r.screenshot.delete()
                        refreshList()
                    }
                }
            }
            .show()
    }

    private fun showScreenshot(r: Recording) {
        if (!r.screenshot.exists()) {
            Toast.makeText(this, "Pas de capture pour cet appel", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri(r.screenshot), "image/jpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun share(r: Recording) {
        val uris = arrayListOf(uri(r.audio))
        if (r.screenshot.exists()) uris.add(uri(r.screenshot))
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("*/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    .putExtra(Intent.EXTRA_TEXT,
                        "Appel ${r.direction} – ${r.number} – ${r.dateText} à ${r.timeText} – ${r.durationText}")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Partager l'appel"
            )
        )
    }

    private fun exportCsv() {
        val list = Recording.all(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "Aucun enregistrement", Toast.LENGTH_SHORT).show()
            return
        }
        val csv = File(Prefs.folder(this), "liste_appels.csv")
        // BOM UTF-8 pour qu'Excel affiche correctement les accents
        csv.writeText("\uFEFF" + Recording.toCsv(list))
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri(csv))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Exporter la liste des appels"
            )
        )
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }
}
