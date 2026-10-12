package org.robbiemed.zones

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone

/**
 * Any town on earth, e.g. "Hilo, HI". Open-Meteo's keyless geocoder (GeoNames data) returns
 * coordinates and the IANA zone; only the typed name leaves the phone. Without network, the
 * built-in cities and zones still work.
 */
object Places {
    /** Calls back on the main thread: matches (maybe none), or null when the lookup couldn't reach the server. */
    fun search(q: String, done: (List<City>?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val l = runCatching { fetch(q) }.getOrNull()
            main.post { done(l) }
        }.start()
    }

    private fun fetch(q: String): List<City> {
        val parts = q.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.firstOrNull() ?: return emptyList()
        val where = parts.drop(1).map { it.lowercase() }
        if (name.length < 2) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=100&format=json" +
            "&language=" + Locale.getDefault().language + "&name=" + URLEncoder.encode(name, "UTF-8")
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 6000; con.readTimeout = 6000
        val body = try { con.inputStream.bufferedReader().readText() } finally { con.disconnect() }
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<City>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tz = o.optString("timezone"); if (tz.isEmpty() || TimeZone.getTimeZone(tz).id != tz) continue
            val admin = o.optString("admin1"); val country = o.optString("country"); val cc = o.optString("country_code")
            if (where.isNotEmpty()) {
                val tags = listOf(admin, country, cc, ABBR[admin] ?: "").map { it.lowercase() }.filter { it.isNotEmpty() }
                if (!where.all { w -> tags.any { it == w || it.startsWith(w) } }) continue
            }
            fun clean(s: String) = s.replace('|', ' ').replace('\n', ' ').trim()
            out += City(clean(o.getString("name")), clean(listOf(admin, country).filter { it.isNotEmpty() }.joinToString(", ")),
                tz, o.getDouble("latitude"), o.getDouble("longitude"))
        }
        return out.distinctBy { it.name + it.country }.take(20)
    }

    /** So "Hilo, HI" or "Banff, AB" narrow to the right state or province. */
    private val ABBR = mapOf(
        "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA",
        "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE", "District of Columbia" to "DC",
        "Washington, D.C." to "DC", "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
        "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS", "Kentucky" to "KY",
        "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI",
        "Minnesota" to "MN", "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE",
        "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
        "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK", "Oregon" to "OR",
        "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD",
        "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA",
        "Washington" to "WA", "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY",
        "Puerto Rico" to "PR",
        "Alberta" to "AB", "British Columbia" to "BC", "Manitoba" to "MB", "New Brunswick" to "NB",
        "Newfoundland and Labrador" to "NL", "Nova Scotia" to "NS", "Ontario" to "ON", "Prince Edward Island" to "PE",
        "Quebec" to "QC", "Saskatchewan" to "SK", "Northwest Territories" to "NT", "Nunavut" to "NU", "Yukon" to "YT",
        "New South Wales" to "NSW", "Victoria" to "VIC", "Queensland" to "QLD", "South Australia" to "SA",
        "Western Australia" to "WA", "Tasmania" to "TAS", "Northern Territory" to "NT", "Australian Capital Territory" to "ACT",
    )
}
