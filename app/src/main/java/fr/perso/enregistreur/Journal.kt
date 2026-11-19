package fr.perso.enregistreur

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Journal de diagnostic lisible dans l'appli. */
object Journal {
    private val FMT = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

    private fun file(c: Context) = File(c.filesDir, "journal.txt")

    @Synchronized
    fun log(c: Context, msg: String, e: Throwable? = null) {
        Log.i("EnregAppels", msg, e)
        val line = "${FMT.format(Date())}  $msg" + (e?.let { " → ${it.javaClass.simpleName}: ${it.message}" } ?: "")
        try {
            val f = file(c)
            val lines = (if (f.exists()) f.readLines() else emptyList()).takeLast(199) + line
            f.writeText(lines.joinToString("\n") + "\n")
        } catch (_: Exception) {}
    }

    fun read(c: Context): String =
        file(c).takeIf { it.exists() }?.readLines()?.reversed()?.joinToString("\n") ?: "(vide)"

    fun clear(c: Context) { file(c).delete() }
}
