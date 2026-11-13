package show.jangregersen.werkstatt

import java.io.File
import java.util.concurrent.TimeUnit

/** Der Mudita Kompakt am USB-Kabel (über adb). Diese Fassung liest nur, sie schreibt nichts aufs Gerät. */
object Geraet {
    private const val PAKET = "show.jangregersen.buehne"
    private val adb = File(System.getenv("LOCALAPPDATA") ?: "", "Android/Sdk/platform-tools/adb.exe")

    private fun lauf(vararg args: String, sek: Long = 20): Pair<Int, ByteArray> {
        if (!adb.exists()) throw Fehler("Das Werkzeug für die USB-Verbindung (adb) fehlt auf diesem Laptop.")
        val p = ProcessBuilder(listOf(adb.path) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        var aus = ByteArray(0)
        val leser = Thread { aus = p.inputStream.readBytes() }.apply { start() }
        if (!p.waitFor(sek, TimeUnit.SECONDS)) { p.destroyForcibly(); throw Fehler("Der Mudita antwortet nicht. Ist er entsperrt und das Kabel fest?") }
        leser.join(2000)
        return p.exitValue() to aus
    }

    /** Seriennummer des angeschlossenen Geräts, null = keins da. */
    fun seriennummer(): String? = runCatching {
        String(lauf("devices", sek = 8).second).lines().drop(1)
            .map { it.trim().split(Regex("\\s+")) }.firstOrNull { it.size >= 2 && it[1] == "device" && !it[0].startsWith("emulator") }?.get(0)
    }.getOrNull()

    fun bestandLesen(): String {
        val s = seriennummer() ?: throw Fehler("Kein Mudita gefunden. Bitte per USB-Kabel anschließen und entsperren.")
        val (code, aus) = lauf("-s", s, "exec-out", "run-as", PAKET, "cat", "files/bestand.json")
        val text = String(aus, Charsets.UTF_8)
        if (code != 0 || !text.trimStart().startsWith("{")) throw Fehler("Die TrikTrak-Daten auf dem Mudita konnten nicht gelesen werden.")
        return text
    }
}
