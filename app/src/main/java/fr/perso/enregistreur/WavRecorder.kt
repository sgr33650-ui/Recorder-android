package fr.perso.enregistreur

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Enregistre en WAV brut (PCM 16 bits) avec AudioRecord, et mesure le niveau
 * sonore capté. Cela permet de savoir avec certitude si le micro renvoie du
 * son ou du silence pendant un appel.
 */
class WavRecorder(private val ctx: Context) {

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var peak = 0
        private set

    private val sampleRate = 44_100

    /** Démarre ; renvoie true si la source audio a été acceptée. */
    fun start(source: Int, out: File): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            Journal.log(ctx, "Buffer audio invalide")
            return false
        }
        val bufSize = minBuf * 2
        val rec = try {
            @Suppress("MissingPermission")
            AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize)
        } catch (e: Exception) {
            Journal.log(ctx, "Création AudioRecord impossible", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Journal.log(ctx, "Source refusée par le système")
            rec.release()
            return false
        }

        running = true
        peak = 0
        thread = Thread {
            val raf = RandomAccessFile(out, "rw")
            raf.setLength(0)
            writeHeader(raf, 0)
            val buf = ShortArray(bufSize / 2)
            val bytes = ByteArray(buf.size * 2)
            var totalBytes = 0L
            var windowPeak = 0
            var lastLog = System.currentTimeMillis()
            try {
                rec.startRecording()
            } catch (e: Exception) {
                Journal.log(ctx, "Démarrage AudioRecord impossible", e)
                running = false
            }
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) {
                    Journal.log(ctx, "Lecture micro refusée (code $n) : appel protégé")
                    break
                }
                if (n > 0) {
                    var j = 0
                    for (k in 0 until n) {
                        val v = buf[k].toInt()
                        val a = abs(v)
                        if (a > windowPeak) windowPeak = a
                        if (a > peak) peak = a
                        bytes[j++] = (v and 0xFF).toByte()
                        bytes[j++] = ((v shr 8) and 0xFF).toByte()
                    }
                    raf.write(bytes, 0, n * 2)
                    totalBytes += n * 2
                    val now = System.currentTimeMillis()
                    if (now - lastLog >= 2000) {
                        val pct = windowPeak * 100 / 32767
                        Journal.log(ctx, "Niveau micro : $pct %" +
                            if (windowPeak < 60) "  ← SILENCE" else "")
                        windowPeak = 0
                        lastLog = now
                    }
                }
            }
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            writeHeader(raf, totalBytes)
            raf.close()
            val pct = peak * 100 / 32767
            Journal.log(ctx, "WAV terminé. Niveau maximum capté : $pct %" +
                if (peak < 60) "  → micro MUET pendant l'appel" else "  → du son a été capté")
        }
        thread!!.start()
        return true
    }

    fun stop() {
        running = false
        thread?.join(4000)
        thread = null
    }

    private fun writeHeader(raf: RandomAccessFile, dataBytes: Long) {
        val byteRate = sampleRate * 2
        raf.seek(0)
        raf.writeBytes("RIFF")
        writeIntLE(raf, (36 + dataBytes).toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeIntLE(raf, 16)
        writeShortLE(raf, 1)          // PCM
        writeShortLE(raf, 1)          // mono
        writeIntLE(raf, sampleRate)
        writeIntLE(raf, byteRate)
        writeShortLE(raf, 2)          // block align
        writeShortLE(raf, 16)         // bits
        raf.writeBytes("data")
        writeIntLE(raf, dataBytes.toInt())
    }

    private fun writeIntLE(raf: RandomAccessFile, v: Int) {
        raf.write(v and 0xFF); raf.write((v shr 8) and 0xFF)
        raf.write((v shr 16) and 0xFF); raf.write((v shr 24) and 0xFF)
    }

    private fun writeShortLE(raf: RandomAccessFile, v: Int) {
        raf.write(v and 0xFF); raf.write((v shr 8) and 0xFF)
    }
}
