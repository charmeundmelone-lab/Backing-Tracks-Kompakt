package show.jangregersen.buehne.audio

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * "Bitte nicht stören" für die Show (14.11., Jan: am Gig kam ein Mail-Ton laut über die Anlage).
 *
 * Solange TrikTrak die Lautstärke hält (App offen oder etwas läuft), schaltet die App "Bitte nicht
 * stören" so ein, dass nur Medien durchkommen: keine Mail-, SMS- oder Klingeltöne, keine Wecker.
 * Danach kommt genau die Einstellung von vorher zurück. Was vorher galt, steht in einer kleinen
 * Datei: Stürzt die App ab, stellt der nächste Start es trotzdem zurück.
 *
 * Geht nur, wenn Jan TrikTrak den Zugriff einmal erlaubt hat (Einstellungen → "Bitte nicht
 * stören"-Zugriff). Ohne Erlaubnis tut dieser Teil nichts, die Gigs-Seite bittet darum.
 */
object ShowRuhe {
    private const val TAG = "ShowRuhe"
    private const val MERKER = "ruhe-vorher.txt"
    /** Schon eingeschaltet: [an] wird sehr oft aufgerufen (bei jeder Zustandsänderung), dann nichts tun. */
    @Volatile private var ist = false

    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun erlaubt(c: Context): Boolean = nm(c)?.isNotificationPolicyAccessGranted == true

    /** Show-Ruhe an. Merkt sich beim ersten Mal, was vorher eingestellt war. */
    fun an(c: Context) {
        if (ist) return
        val nm = nm(c) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        runCatching {
            val merker = File(c.filesDir, MERKER)
            val p = nm.notificationPolicy
            if (!merker.exists()) {
                merker.writeText(listOf(nm.currentInterruptionFilter, p.priorityCategories, p.priorityCallSenders, p.priorityMessageSenders, p.suppressedVisualEffects).joinToString(";"))
            }
            // Nur Medien dürfen durch (ab Android 9 eigene Kategorie; davor gehen Medien immer durch).
            val nurMedien = if (Build.VERSION.SDK_INT >= 28) NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA else 0
            if (p.priorityCategories != nurMedien) {
                nm.notificationPolicy = NotificationManager.Policy(nurMedien, 0, 0, p.suppressedVisualEffects)
            }
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            ist = true
        }.onFailure { Log.e(TAG, "Bitte nicht stören ließ sich nicht einschalten", it) }
    }

    /** Show-Ruhe aus: genau die Einstellung von vorher zurück. Ohne Merker (nie eingeschaltet) nichts. */
    fun aus(c: Context) {
        ist = false
        val merker = File(c.filesDir, MERKER)
        if (!merker.exists()) return
        val nm = nm(c)
        runCatching {
            val w = merker.readText().trim().split(";").map { it.toInt() }
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.notificationPolicy = NotificationManager.Policy(w[1], w[2], w[3], w[4])
                nm.setInterruptionFilter(if (w[0] in 1..4) w[0] else NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }.onFailure { Log.e(TAG, "Bitte nicht stören ließ sich nicht zurückstellen", it) }
        merker.delete()
    }
}
