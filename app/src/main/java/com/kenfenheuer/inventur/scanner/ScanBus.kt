package com.kenfenheuer.inventur.scanner

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Prozessweiter Eingang fuer Scans, die nicht ueber die Tastatur kommen (Expertenmodus: Bluetooth-
 * Nachrichten des Scanners, auch aus dem Hintergrunddienst). Solange kein ViewModel zuhoert, werden
 * Scans in den Preferences zwischengespeichert und beim naechsten [attach] zugestellt.
 */
object ScanBus {
    private const val PREFS = "scanbus"
    private const val KEY_PENDING = "pending"

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var prefs: SharedPreferences? = null
    private var listener: ((String, Long) -> Unit)? = null

    fun init(context: Context) {
        synchronized(lock) { if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    }

    fun post(code: String, timestamp: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val l = listener
            if (l != null) {
                main.post { l(code, timestamp) }
            } else {
                val arr = readPending()
                arr.put(JSONObject().put("c", code).put("t", timestamp))
                prefs?.edit()?.putString(KEY_PENDING, arr.toString())?.apply()
            }
        }
    }

    fun attach(l: (String, Long) -> Unit) {
        synchronized(lock) {
            listener = l
            val arr = readPending()
            if (arr.length() > 0) {
                prefs?.edit()?.remove(KEY_PENDING)?.apply()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val c = o.getString("c")
                    val t = o.getLong("t")
                    main.post { l(c, t) }
                }
            }
        }
    }

    fun detach() {
        synchronized(lock) { listener = null }
    }

    private fun readPending(): JSONArray = try {
        JSONArray(prefs?.getString(KEY_PENDING, null) ?: "[]")
    } catch (e: Exception) {
        JSONArray()
    }
}

/** Einstellungen fuer den Scanner-Empfang im Hintergrund. */
object ScannerPrefs {
    private fun p(context: Context) = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun backgroundEnabled(context: Context): Boolean = p(context).getBoolean("background_scanner", false)
    fun setBackgroundEnabled(context: Context, on: Boolean) = p(context).edit().putBoolean("background_scanner", on).apply()

    /** Angebot "auf Expertenmodus umstellen" nicht mehr zeigen. */
    fun expertOfferDismissed(context: Context): Boolean = p(context).getBoolean("expert_offer_dismissed", false)
    fun setExpertOfferDismissed(context: Context, v: Boolean) = p(context).edit().putBoolean("expert_offer_dismissed", v).apply()

    /** Merkt sich den Namen je Adresse: das SDK liefert den Namen nur aus der Suche, ein neu angelegtes Geraet hat ihn nicht. */
    fun rememberName(context: Context, mac: String, name: String) = p(context).edit().putString("name_$mac", name).apply()
    fun nameFor(context: Context, mac: String): String? = p(context).getString("name_$mac", null)

    /** MAC des zuletzt verbundenen Scanners (im Expertenmodus eine andere Adresse als die HID-Tastatur). */
    fun lastMac(context: Context): String? = p(context).getString("last_scanner_mac", null)
    fun setLastMac(context: Context, mac: String) = p(context).edit().putString("last_scanner_mac", mac).apply()
}
