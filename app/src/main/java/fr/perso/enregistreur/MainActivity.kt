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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var journal: TextView
    private lateinit var sourceBtn: Button
    private lateinit var listBox: LinearLayout
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
            text = "Journal de diagnostic"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 4)
        })
        journal = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            maxLines = 8
            setTextIsSelectable(true)
        }
        root.addView(journal)
        val jrow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        jrow.addView(Button(this).apply {
            text = "Copier le journal"
            setOnClickListener {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("journal", Journal.read(this@MainActivity)))
                Toast.makeText(this@MainActivity, "Journal copié : colle-le à Claude", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        jrow.addView(Button(this).apply {
            text = "Test micro 5 s"
            setOnClickListener { micTest() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(jrow)

        root.addView(TextView(this).apply {
            text = "Enregistrements (appui : écouter · appui long : partager, supprimer)"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 2)
        })

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        // Tout l'écran défile : la liste reste accessible même avec beaucoup de réglages.
        setContentView(android.widget.ScrollView(this).apply { addView(root) })
        refreshSource()
    }

    override fun onResume() {
        super.onResume()
        journal.text = Journal.read(this)
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

    /** Enregistre 5 s hors appel pour vérifier que le micro fonctionne. */
    private fun micTest() {
        val f = File(cacheDir, "test.m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(this)
                    else @Suppress("DEPRECATION") android.media.MediaRecorder()
            r.setAudioSource(Prefs.SOURCES[Prefs.sourceIndex(this)].first)
            r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            Toast.makeText(this, "Parle… (5 s)", Toast.LENGTH_SHORT).show()
            journal.postDelayed({
                try { r.stop() } catch (_: Exception) {}
                r.release()
                Journal.log(this, "Test micro hors appel : ${f.length() / 1024} Ko")
                journal.text = Journal.read(this)
                togglePlay(f)
            }, 5000)
        } catch (e: Exception) {
            Journal.log(this, "Test micro ÉCHEC", e)
            journal.text = Journal.read(this)
        }
    }

    /** Démarre ou arrête le service permanent selon le réglage (l'appli est au premier plan ici). */
    private fun syncService() {
        val perms = permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!perms) Journal.log(this, "Permissions manquantes : " +
            permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                .joinToString { it.substringAfterLast('.') })
        try {
            if (Prefs.enabled(this) && perms) {
                if (!RecorderService.running) RecorderService.start(this)
            } else if (RecorderService.running) {
                RecorderService.stop(this)
            }
        } catch (e: Exception) {
            Journal.log(this, "Démarrage du service impossible", e)
            Toast.makeText(this, "Démarrage impossible : ${e.message}", Toast.LENGTH_LONG).show()
        }
        status.postDelayed({ refreshStatus(); journal.text = Journal.read(this) }, 800)
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
        listBox.removeAllViews()
        val pad = (12 * resources.displayMetrics.density).toInt()
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply { text = "Aucun enregistrement pour l'instant." })
        }
        for (r in items) {
            listBox.addView(TextView(this).apply {
                text = "▶  ${r.dateText} à ${r.timeText}\n     ${r.directionText} · ${r.number} · ${r.durationText}"
                textSize = 15f
                setPadding(pad / 2, pad, pad / 2, pad)
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { playerDialog(r) }
                setOnLongClickListener { actions(r); true }
            })
            listBox.addView(android.view.View(this).apply { setBackgroundColor(0x22000000) },
                LinearLayout.LayoutParams(MATCH_PARENT, 1))
        }
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
            .setItems(arrayOf("Écouter", "Ouvrir avec une autre appli", "Partager", "Supprimer")) { _, which ->
                when (which) {
                    0 -> playerDialog(r)
                    1 -> openWith(r)
                    2 -> share(r)
                    3 -> confirmDelete(r)
                }
            }
            .show()
    }

    private fun confirmDelete(r: Recording) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer définitivement ?")
            .setMessage("${r.dateText} à ${r.timeText} – ${r.number}\nCette action est irréversible.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Oui, supprimer") { _, _ ->
                if (playing == r.audio) stopPlayer()
                r.audio.delete()
                r.screenshot.delete()
                refreshList()
            }
            .show()
    }

    private fun openWith(r: Recording) {
        try {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri(r.audio), "audio/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Écouter avec…"))
        } catch (e: Exception) {
            Journal.log(this, "Ouverture externe impossible", e)
            Toast.makeText(this, "Aucune appli pour lire ce fichier", Toast.LENGTH_SHORT).show()
        }
    }

    /** Petit lecteur : lecture/pause, position, ouvrir ailleurs. */
    private fun playerDialog(r: Recording) {
        stopPlayer()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(r.audio.absolutePath)
            mp.prepare()
        } catch (e: Exception) {
            mp.release()
            Journal.log(this, "Lecture impossible : ${r.audio.name} (${r.audio.length() / 1024} Ko)", e)
            journal.text = Journal.read(this)
            AlertDialog.Builder(this)
                .setTitle("Lecture impossible ici")
                .setMessage("Essayer avec une autre appli (lecteur de musique, VLC…) ?")
                .setPositiveButton("Ouvrir avec…") { _, _ -> openWith(r) }
                .setNegativeButton("Fermer", null)
                .show()
            return
        }
        player = mp
        playing = r.audio
        fun fmt(ms: Int) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
        val info = TextView(this).apply {
            textSize = 18f
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        val tick = object : Runnable {
            override fun run() {
                val cur = player ?: return
                info.text = "${if (cur.isPlaying) "▶ Lecture" else "⏸ Pause"}   ${fmt(cur.currentPosition)} / ${fmt(cur.duration)}" +
                    "\n\nMonte le volume « Média » si tu n'entends rien."
                info.postDelayed(this, 500)
            }
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("${r.dateText} à ${r.timeText} – ${r.number}")
            .setView(info)
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Ouvrir avec…") { _, _ -> stopPlayer(); openWith(r) }
            .setNegativeButton("Pause / Lecture", null)
            .setOnDismissListener { stopPlayer() }
            .create()
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.start() }
        }
        mp.setOnCompletionListener { it.seekTo(0) }
        mp.start()
        info.post(tick)
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
