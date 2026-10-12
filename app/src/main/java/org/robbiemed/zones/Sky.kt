package org.robbiemed.zones

import android.graphics.Color
import org.robbiemed.clock.core.Palette
import org.robbiemed.clock.core.mix
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Sky {
    /** Solar elevation in degrees (low-precision NOAA-style; good to ~0.5°). */
    fun elevation(lat: Double, lon: Double, utcMs: Long): Double {
        val d = utcMs / 86_400_000.0 - 10_957.5
        val g = Math.toRadians((357.529 + 0.98560028 * d) % 360)
        val q = (280.459 + 0.98564736 * d) % 360
        val l = Math.toRadians((q + 1.915 * sin(g) + 0.020 * sin(2 * g)) % 360)
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = atan2(cos(e) * sin(l), cos(l))
        val dec = asin(sin(e) * sin(l))
        val gmst = (18.697374558 + 24.06570982441908 * d) % 24
        val ha = Math.toRadians(gmst * 15 + lon) - ra
        val la = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(la) * sin(dec) + cos(la) * cos(dec) * cos(ha)))
    }

    // Elevation → colour stops: deep night, astronomical, civil dusk (violet), horizon (ember), morning, high day.
    private val stops = listOf(
        -18.0 to 0xFF0A0E1C.toInt(), -10.0 to 0xFF101838.toInt(), -4.0 to 0xFF2A2152.toInt(),
        0.0 to 0xFF7A3B5A.toInt(), 4.0 to 0xFFB8603C.toInt(), 12.0 to 0xFF2F5D8A.toInt(), 40.0 to 0xFF3B76AC.toInt(),
    )

    private fun natural(el: Double): Int {
        if (el <= stops.first().first) return stops.first().second
        for (i in 1 until stops.size) {
            val (e1, c1) = stops[i]
            if (el <= e1) {
                val (e0, c0) = stops[i - 1]
                return mix(c0, c1, ((el - e0) / (e1 - e0)).toFloat())
            }
        }
        return stops.last().second
    }

    /** The sky, re-voiced in the active theme (Night = luminance in red only). */
    fun color(el: Double, pal: Palette, theme: String): Int {
        val c = natural(el)
        val lum = (0.30f * Color.red(c) + 0.59f * Color.green(c) + 0.11f * Color.blue(c)) / 255f
        val day = ((el + 12) / 30).toFloat().coerceIn(0f, 1f)
        return when (theme) {
            "Night" -> Color.rgb((40 + 150 * lum * 1.6f).toInt().coerceAtMost(200), 0, 0).let { mix(0xFF080000.toInt(), it, 0.25f + 0.5f * day) }
            "Mono" -> mix(0xFF050505.toInt(), 0xFF5A5A5A.toInt(), day * 0.9f)
            "Paper" -> mix(pal.surface, c, 0.28f)
            "Ember" -> mix(c, 0xFF3A1A06.toInt(), 0.45f)
            "Glacier" -> mix(c, 0xFF06223A.toInt(), 0.35f)
            else -> mix(c, pal.deep, 0.18f)
        }
    }
}
