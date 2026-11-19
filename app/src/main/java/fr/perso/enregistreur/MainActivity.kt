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
    private var files: List<File> = emptyList()
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
            text = "2. Activer le service d'accessibilité"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "3. Désactiver l'optimisation batterie"
            setOnClickListener { askBattery() }
        })

        root.addView(Switch(this).apply {
            text = "Enregistrement automatique des appels"
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setEnabled(this@MainActivity, v) }
        })
        root.addView(Switch(this).apply {
            text = "Mettre le haut-parleur pendant l'appel (capte mieux l'interlocuteur)"
            isChecked = Prefs.speaker(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setSpeaker(this@MainActivity, v) }
        })

        sourceBtn = Button(this).apply {
            setOnClickListener {
                val next = (Prefs.sourceIndex(this@MainActivity) + 1) % Prefs.SOURCES.size
                Prefs.setSourceIndex(this@MainActivity, next)
                refreshSource()
            }
        }
        root.addView(sourceBtn)

        root.addView(TextView(this).apply {
            text = "Enregistrements (appui : lire/arrêter, appui long : partager/supprimer)"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 2)
        })

        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        list.setOnItemClickListener { _, _, pos, _ -> togglePlay(files[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ -> actions(files[pos]); true }

        setContentView(root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        refreshSource()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshList()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        refreshStatus()
    }

    private fun refreshSource() {
        sourceBtn.text = "Source audio : " + Prefs.SOURCES[Prefs.sourceIndex(this)].second
    }

    private fun refreshStatus() {
        val perms = permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val access = accessibilityOn()
        status.text = buildString {
            append(if (perms) "✅ Permissions accordées\n" else "❌ Permissions manquantes\n")
            append(if (access) "✅ Service d'accessibilité actif" else "❌ Service d'accessibilité inactif")
            if (perms && access) append("\n\nPrêt : les prochains appels seront enregistrés.")
        }
    }

    private fun accessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/") &&
            enabled.contains(CallAccessibilityService::class.java.simpleName)
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
        files = Prefs.folder(this).listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        val labels = files.map { f ->
            val kb = f.length() / 1024
            val mark = if (f == playing) "▶ " else ""
            "$mark${f.nameWithoutExtension.replace('_', ' ')}  ($kb Ko)"
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

    private fun actions(f: File) {
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(arrayOf("Partager", "Supprimer")) { _, which ->
                when (which) {
                    0 -> share(f)
                    1 -> {
                        if (playing == f) stopPlayer()
                        f.delete()
                        refreshList()
                    }
                }
            }
            .show()
    }

    private fun share(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("audio/mp4")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Partager l'enregistrement"
            )
        )
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }
}
