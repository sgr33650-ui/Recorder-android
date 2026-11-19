package fr.perso.enregistreur

import android.content.Context
import android.media.MediaRecorder
import java.io.File

object Prefs {
    private const val NAME = "reglages"

    /** Sources audio proposées, dans l'ordre d'essai. */
    val SOURCES = listOf(
        MediaRecorder.AudioSource.MIC to "Micro",
        MediaRecorder.AudioSource.CAMCORDER to "Caméra (sans filtrage)",
        MediaRecorder.AudioSource.UNPROCESSED to "Brut (sans filtrage)",
        MediaRecorder.AudioSource.VOICE_RECOGNITION to "Reconnaissance vocale",
        MediaRecorder.AudioSource.VOICE_COMMUNICATION to "Communication vocale",
        MediaRecorder.AudioSource.VOICE_CALL to "Appel (rooté / constructeur)",
    )

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun speaker(c: Context) = sp(c).getBoolean("speaker", false)
    fun setSpeaker(c: Context, v: Boolean) = sp(c).edit().putBoolean("speaker", v).apply()

    fun askAfterCall(c: Context) = sp(c).getBoolean("ask", true)
    fun setAskAfterCall(c: Context, v: Boolean) = sp(c).edit().putBoolean("ask", v).apply()

    fun sourceIndex(c: Context) = sp(c).getInt("source", 0).coerceIn(0, SOURCES.lastIndex)
    fun setSourceIndex(c: Context, v: Int) = sp(c).edit().putInt("source", v).apply()

    fun folder(c: Context): File =
        File(c.getExternalFilesDir(null), "Enregistrements").apply { mkdirs() }
}
