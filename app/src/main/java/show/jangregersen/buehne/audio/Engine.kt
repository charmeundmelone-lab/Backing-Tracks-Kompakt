package show.jangregersen.buehne.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlin.math.pow

private const val TAG = "Engine"

/**
 * Spielt einen Song (eine Stereo-Datei oder mehrere Einzelspuren) über je einen ExoPlayer pro
 * Spur ab. Jede Spur bekommt einen [KanalMischer], der Band fest nach links und Click/Ansage
 * fest nach rechts legt. Nur vom Haupt-Thread aus benutzen (ExoPlayer-Regel).
 */
@UnstableApi
class Engine(private val context: Context) {

    private class SpurSpieler(val spur: Spur, val player: ExoPlayer, val mischer: KanalMischer)
    private class Geladen(val songId: Long, val spieler: List<SpurSpieler>)

    private var aktuell: Geladen? = null
    private var vorrat: Geladen? = null

    var onFehler: ((String) -> Unit)? = null

    val geladeneSongId: Long? get() = aktuell?.songId
    val hatSpuren: Boolean get() = aktuell?.spieler?.isNotEmpty() == true

    private val leit: ExoPlayer? get() = aktuell?.spieler?.firstOrNull()?.player

    val spielt: Boolean get() = leit?.playWhenReady == true && leit?.playbackState != Player.STATE_ENDED
    val positionMs: Long get() = leit?.currentPosition ?: 0L
    val dauerMs: Long get() = leit?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
    val istZuEnde: Boolean get() = leit?.playbackState == Player.STATE_ENDED

    // ── Laden ────────────────────────────────────────────────────────────────

    fun laden(songId: Long, spuren: List<Spur>, mix: Map<String, Int>) {
        val v = vorrat
        if (v != null && v.songId == songId && v.spieler.size == spuren.size) {
            vorrat = null
            freigeben(aktuell)
            aktuell = v
        } else {
            freigeben(aktuell)
            aktuell = Geladen(songId, spuren.map { bauen(it) })
        }
        mixSetzen(mix)
        aktuell?.spieler?.forEach { it.player.seekTo(0L) }
    }

    fun vorladen(songId: Long, spuren: List<Spur>, mix: Map<String, Int>) {
        if (vorrat?.songId == songId || aktuell?.songId == songId) return
        freigeben(vorrat)
        vorrat = Geladen(songId, spuren.map { bauen(it) }).also { g -> mixAnwenden(g, mix) }
    }

    fun entladen() { freigeben(aktuell); aktuell = null }

    // ── Wiedergabe ───────────────────────────────────────────────────────────

    fun play() {
        val sp = aktuell?.spieler ?: return
        if (istZuEnde) sp.forEach { it.player.seekTo(0L) }
        sp.forEach { it.player.play() }
    }

    fun pause() { aktuell?.spieler?.forEach { it.player.pause() } }

    fun stop() { pause(); aktuell?.spieler?.forEach { it.player.seekTo(0L) } }

    fun seek(ms: Long) {
        val ziel = ms.coerceIn(0L, maxOf(0L, dauerMs - 50))
        aktuell?.spieler?.forEach { it.player.seekTo(ziel) }
    }

    // ── Mixer ────────────────────────────────────────────────────────────────

    fun mixSetzen(mix: Map<String, Int>) { aktuell?.let { mixAnwenden(it, mix) } }

    private fun mixAnwenden(g: Geladen, mix: Map<String, Int>) {
        g.spieler.forEach { s ->
            when (s.spur.rolle) {
                Rolle.STEREO -> s.mischer.setze(verstaerkung(mix[Spuren.KANAL_BAND]), 0f, 0f, verstaerkung(mix[Spuren.KANAL_CLICK]))
                Rolle.BAND -> { val v = verstaerkung(mix[s.spur.name.lowercase()]) / 2f; s.mischer.setze(v, v, 0f, 0f) }
                Rolle.OHR -> { val v = verstaerkung(mix[s.spur.name.lowercase()]) / 2f; s.mischer.setze(0f, 0f, v, v) }
            }
        }
    }

    // ── Sprachnotizen (Ansagen, Warnton): hart rechts ────────────────────────

    private var notiz: MediaPlayer? = null

    val notizPositionMs: Long get() = notiz?.let { runCatching { it.currentPosition.toLong() }.getOrDefault(0L) } ?: 0L
    val notizDauerMs: Long get() = notiz?.let { runCatching { it.duration.toLong() }.getOrDefault(0L) } ?: 0L
    val notizLaeuft: Boolean get() = notiz != null

    fun notizSpielen(pfad: String, lautstaerke: Float = 1f, fertig: () -> Unit) {
        notizStoppen()
        if (pfad.isBlank()) { fertig(); return }
        val mp = MediaPlayer()
        try {
            mp.setDataSource(pfad)
            @Suppress("DEPRECATION") mp.setVolume(0f, lautstaerke.coerceIn(0f, 1f))
            mp.setOnCompletionListener { notizStoppen(); fertig() }
            mp.setOnErrorListener { _, _, _ -> notizStoppen(); fertig(); true }
            mp.prepare()
            mp.start()
            notiz = mp
        } catch (e: Exception) {
            Log.e(TAG, "Notiz nicht abspielbar: $pfad", e)
            runCatching { mp.release() }
            fertig()
        }
    }

    fun notizStoppen() {
        notiz?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        notiz = null
    }

    fun freigebenAlles() { freigeben(aktuell); freigeben(vorrat); aktuell = null; vorrat = null; notizStoppen() }

    // ── Bauen ────────────────────────────────────────────────────────────────

    private fun freigeben(g: Geladen?) { g?.spieler?.forEach { runCatching { it.player.release() } } }

    private fun bauen(spur: Spur): SpurSpieler {
        val mischer = KanalMischer()
        val renderer = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf<AudioProcessor>(mischer)).build()
        }
        val p = ExoPlayer.Builder(context, renderer)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.setSeekParameters(SeekParameters.EXACT)
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val wurzel = generateSequence<Throwable>(error) { it.cause }.last()
                val text = "${spur.name}: ${wurzel.javaClass.simpleName} ${wurzel.message ?: error.errorCodeName}"
                Log.e(TAG, "Wiedergabefehler $text", error)
                onFehler?.invoke(text)
            }
        })
        p.setMediaItem(MediaItem.fromUri(Uri.parse(spur.uri)))
        p.prepare()
        return SpurSpieler(spur, p, mischer)
    }

    companion object {
        /** Stufe 10 = volle Lautstärke, jede Stufe darunter 3 dB leiser, negativ = stumm. */
        fun verstaerkung(stufe: Int?): Float {
            val s = stufe ?: 10
            if (s <= 0) return 0f
            return 10f.pow((s.coerceAtMost(10) - 10) * 3f / 20f)
        }
    }
}
