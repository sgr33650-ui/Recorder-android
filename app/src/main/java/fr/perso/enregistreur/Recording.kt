package fr.perso.enregistreur

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Un appel enregistré : fichier audio + capture d'écran éventuelle,
 * nommés "AAAA-MM-JJ_HH-MM-SS_sens_numero".
 */
class Recording(val audio: File) {
    val base: String = audio.nameWithoutExtension
    val screenshot: File = File(audio.parentFile, "$base.jpg")
    val date: Date? = runCatching { FILE_FMT.parse(base.take(19)) }.getOrNull()
    val direction: String = base.split('_').getOrNull(2) ?: "?"
    val number: String = base.split('_').getOrNull(3) ?: "inconnu"

    val durationSec: Long by lazy {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(audio.absolutePath)
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L) / 1000
        } catch (_: Exception) {
            0L
        } finally {
            r.release()
        }
    }

    val dateText: String get() = date?.let { DATE_FMT.format(it) } ?: "?"
    val timeText: String get() = date?.let { TIME_FMT.format(it) } ?: "?"
    val durationText: String get() = "%d min %02d s".format(durationSec / 60, durationSec % 60)
    val directionText: String get() = when (direction) {
        "entrant" -> "📥 Entrant"
        "sortant" -> "📤 Sortant"
        else -> direction
    }

    companion object {
        private val FILE_FMT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE)
        private val DATE_FMT = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

        fun baseName(date: Date, direction: String, number: String?): String {
            val n = (number ?: "").replace(Regex("[^0-9+]"), "").ifEmpty { "inconnu" }
            return "${FILE_FMT.format(date)}_${direction}_$n"
        }

        fun all(c: Context): List<Recording> =
            Prefs.folder(c).listFiles { f -> f.isFile && f.extension == "m4a" }
                ?.map { Recording(it) }
                ?.sortedByDescending { it.audio.lastModified() }
                ?: emptyList()

        /** Liste au format CSV (ouvrable dans Excel / Google Sheets). */
        fun toCsv(list: List<Recording>): String = buildString {
            append("Date;Heure;Sens;Numéro;Durée (s);Fichier audio;Capture\n")
            for (r in list) {
                append("${r.dateText};${r.timeText};${r.direction};${r.number};${r.durationSec};")
                append("${r.audio.name};${if (r.screenshot.exists()) r.screenshot.name else ""}\n")
            }
        }
    }
}
