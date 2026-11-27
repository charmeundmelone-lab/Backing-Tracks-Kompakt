package show.jangregersen.buehne.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Wohin der Ton gerade geht (27.11., aus dem StageTraxx-Vergleich, Jans Ja): StageTraxx zeigt vor
 * der Show, ob das Audio-Interface dran ist. Bei Jan ist das wichtig, weil oft Bluetooth-Kopfhörer
 * verbunden sind: Dann könnte der Backing-Track im Ohrstöpsel landen statt in der PA, oder im
 * Lautsprecher, wenn der USB-Adapter locker sitzt.
 *
 * Maßgeblich ist das Gerät, an das Android die Tonspur von TrikTrak wirklich geleitet hat
 * ([bestimmen] mit `geroutet`). Gibt es gerade keine Tonspur, wird aus den angeschlossenen Geräten
 * geschätzt (Bluetooth vor USB vor Kabel-Kopfhörer vor Lautsprecher).
 */
object TonAusgang {
    enum class Art { USB, BLUETOOTH, KOPFHOERER, LAUTSPRECHER }

    data class Stand(val art: Art, val name: String) {
        /** Kurz für die Leiste, z. B. „TON: BLUETOOTH“. */
        val text: String get() = when (art) {
            Art.USB -> "USB"
            Art.BLUETOOTH -> "TON: BLUETOOTH"
            Art.KOPFHOERER -> "TON: KOPFHÖRER"
            Art.LAUTSPRECHER -> "TON: LAUTSPRECHER"
        }
        /** Noch kürzer fürs Schild neben dem Zustand, z. B. „LAUTSPRECHER“. */
        val kurz: String get() = text.removePrefix("TON: ")
    }

    fun bestimmen(c: Context, geroutet: AudioDeviceInfo?): Stand {
        geroutet?.let { d -> art(d.type)?.let { return Stand(it, d.productName?.toString().orEmpty()) } }
        val am = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return Stand(Art.LAUTSPRECHER, "")
        val alle = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        for (a in listOf(Art.BLUETOOTH, Art.USB, Art.KOPFHOERER)) {
            alle.firstOrNull { art(it.type) == a }?.let { return Stand(a, it.productName?.toString().orEmpty()) }
        }
        return Stand(Art.LAUTSPRECHER, "")
    }

    private fun art(typ: Int): Art? = when (typ) {
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> Art.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> Art.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_AUX_LINE -> Art.KOPFHOERER
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> Art.LAUTSPRECHER
        else -> null
    }
}
