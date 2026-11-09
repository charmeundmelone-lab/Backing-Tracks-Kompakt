package show.jangregersen.buehne.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Mischt jede Spur fest auf Stereo: Ausgang links = inL*a + inR*b, rechts = inL*c + inR*d.
 * Eine Monospur wird wie (s, s) behandelt. Die Faktoren sind live änderbar (Mixer), ohne
 * die Wiedergabe neu zu starten.
 *
 * - Stereo-Datei (links Band, rechts Click): a = Band, d = Click, b = c = 0.
 * - Bandspur nur links:  a = b = g/2, c = d = 0.
 * - Ohrspur nur rechts:  c = d = g/2, a = b = 0.
 */
@UnstableApi
class KanalMischer : BaseAudioProcessor() {
    @Volatile var a = 1f
    @Volatile var b = 0f
    @Volatile var c = 0f
    @Volatile var d = 1f
    private var eingangsKanaele = 2

    fun setze(a: Float, b: Float, c: Float, d: Float) { this.a = a; this.b = b; this.c = c; this.d = d }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        eingangsKanaele = inputAudioFormat.channelCount
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesProFrame = 2 * eingangsKanaele
        val frames = inputBuffer.remaining() / bytesProFrame
        val out = replaceOutputBuffer(frames * 4)
        val fa = a; val fb = b; val fc = c; val fd = d
        repeat(frames) {
            val l = inputBuffer.getShort().toFloat()
            val r = if (eingangsKanaele == 2) inputBuffer.getShort().toFloat() else l
            out.putShort(begrenzt(l * fa + r * fb))
            out.putShort(begrenzt(l * fc + r * fd))
        }
        out.flip()
    }

    private fun begrenzt(v: Float): Short = when {
        v > 32767f -> 32767
        v < -32768f -> -32768
        else -> v.toInt().toShort()
    }
}
