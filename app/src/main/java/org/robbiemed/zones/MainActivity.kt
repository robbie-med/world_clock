package org.robbiemed.zones

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.OverScroller
import org.robbiemed.clock.core.Flex
import org.robbiemed.clock.core.Flex.axes
import org.robbiemed.clock.core.Stage
import org.robbiemed.clock.core.Themes
import org.robbiemed.clock.core.alpha
import org.robbiemed.clock.core.clock
import org.robbiemed.clock.core.edgeToEdge
import org.robbiemed.clock.core.no
import org.robbiemed.clock.core.store
import org.robbiemed.clock.core.tick
import org.robbiemed.clock.core.tock
import org.robbiemed.clock.core.yes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class MainActivity : Activity() {
    private lateinit var v: ZonesView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        edgeToEdge()
        v = ZonesView(this)
        setContentView(v)
    }

    override fun onResume() { super.onResume(); v.restyle(); v.load() }

    @Deprecated("framework") override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        if (data?.getBooleanExtra("here", false) == true) { store("zones").edit().putBoolean("hereOff", false).apply(); v.load(); return }
        val c = data?.getStringExtra("city")?.let { City.decode(it) } ?: return
        val list = saved() .filter { it.encode() != c.encode() } + c
        store("zones").edit().putString("cities", list.joinToString("\n") { it.encode() }).apply()
        v.load()
    }

    private fun saved(): List<City> =
        (store("zones").getString("cities", null) ?: "Seoul|South Korea|Asia/Seoul|37.6|127.0\nLondon|UK|Europe/London|51.5|-0.1")
            .lines().mapNotNull { City.decode(it) }

    /** The phone's own zone gets a custom name only while the phone is still in that zone. */
    private fun hereName(tz: String): String? =
        store("zones").getString("hereName", null)?.split('|', limit = 2)?.takeIf { it.size == 2 && it[0] == tz }?.get(1)

    private fun save(l: List<City>) = store("zones").edit().putString("cities", l.joinToString("\n") { it.encode() }).apply()

    private class Row(val city: City, val here: Boolean) { val r = RectF() }

    private inner class ZonesView(c: Context) : Stage(c, 1) {
        private var rows = listOf<Row>()
        private var offset = 0f          // scrubbed minutes from now
        private var offsetTarget = 0f
        private var scrubbing = false
        private var removing: Row? = null
        private val chipR = RectF(); private val addR = RectF(); private val removeR = RectF(); private val renameR = RectF()

        private val kicker = Flex.paint(sp(13f), 600f, 120f).apply { letterSpacing = 0.22f }
        private val name = Flex.paint(sp(19f), 560f, 108f)
        private val rel = Flex.paint(sp(14f), 420f, 100f)
        private val time = Flex.paint(sp(60f), 260f, 125f)
        private val ampm = Flex.paint(sp(17f), 520f, 100f)
        private val chipT = Flex.paint(sp(14f), 560f, 105f)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strip = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

        private var scroll = 0f; private var maxScroll = 0f
        private val scroller = OverScroller(c)
        private var vt: VelocityTracker? = null
        private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
        private var mode = 0              // 0 undecided, 1 scrub, 2 scroll, 3 long-pressed
        private var startOffset = 0f
        private val longPress = Runnable { onLongPress() }

        fun load() {
            val local = TimeZone.getDefault().id
            val auto = City.fromZone(local).let { if (it.tz == local) it else it.copy(tz = local) }
            val here = hereName(local)?.let { auto.copy(name = it) } ?: auto
            val hidden = store("zones").getBoolean("hereOff", false)
            val now = System.currentTimeMillis()
            rows = ((if (hidden) emptyList() else listOf(Row(here, true))) + saved().map { Row(it, false) })
                .sortedBy { it.city.zone.getOffset(now) }
            invalidate()
        }

        override fun paint(c: Canvas): Boolean {
            kicker.color = pal.accent; name.color = pal.ink; time.color = pal.ink; ampm.color = alpha(pal.ink, 0.7f)
            rel.color = alpha(pal.ink, 0.68f); chipT.color = pal.bg
            val theme = Themes.resolve(context)
            var anim = false
            if (!scrubbing) {
                val n = ease(offset, offsetTarget, 10f); anim = n != offset; offset = n
            }
            if (scroller.computeScrollOffset()) { scroll = scroller.currY.toFloat(); anim = true }
            val realNow = System.currentTimeMillis()
            val now = realNow + (offset * 60_000).toLong()
            val side = dp(12f)

            // Header
            val localCal = Calendar.getInstance().apply { timeInMillis = now }
            val hy = insetTop + dp(40f)
            c.text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(localCal.time).uppercase(), dp(24f), hy, kicker)
            if (abs(offsetTarget) >= 1f || scrubbing) {
                val mins = offset.toLong()
                val sign = if (mins < 0) "−" else "+"
                val am = abs(mins)
                val label = sign + (if (am >= 60) "${am / 60} h " else "") + (if (am % 60 != 0L || am < 60) "${am % 60} min" else "").trim() + "   ·   back to now"
                val w = chipT.measureText(label) + dp(36f); val h = dp(36f)
                chipR.set(dp(20f), hy + dp(14f), dp(20f) + w, hy + dp(14f) + h)
                fill.color = pal.accent; c.drawRoundRect(chipR, h / 2, h / 2, fill)
                c.text(label, chipR.centerX(), chipR.centerY() + chipT.textSize * 0.36f, chipT, Paint.Align.CENTER)
            } else {
                chipR.setEmpty()
                rel.color = pal.dim
                c.text("drag sideways to travel in time", dp(24f), hy + dp(30f), rel)
                rel.color = alpha(pal.ink, 0.68f)
            }

            // Rows
            val top = insetTop + dp(100f)
            val addH = dp(64f)
            val avail = height - top - insetBottom - addH - dp(28f)
            val gap = dp(8f)
            val rowH = max(dp(132f), (avail - gap * (rows.size - 1)) / rows.size.coerceAtLeast(1)).coerceAtMost(dp(230f))
            time.textSize = min(sp(64f), rowH * 0.42f); time.axes(240f, 125f)
            var y = top - scroll
            c.save(); c.clipRect(0f, top - dp(4f), width.toFloat(), height.toFloat())
            val hereOff = TimeZone.getDefault().getOffset(now)
            for (row in rows) {
                val ct = row.city
                row.r.set(side, y, width - side, y + rowH)
                if (row.r.bottom > 0 && row.r.top < height) {
                    val el = Sky.elevation(ct.lat, ct.lon, now)
                    val sky = Sky.color(el, pal, theme)
                    val skyHi = Sky.color(el + 6, pal, theme)
                    fill.shader = LinearGradient(0f, row.r.top, 0f, row.r.bottom, skyHi, sky, Shader.TileMode.CLAMP)
                    c.drawRoundRect(row.r, dp(30f), dp(30f), fill)
                    fill.shader = null
                    if (el < -4) stars(c, row.r, ct.name.hashCode(), ((-4 - el) / 10).toFloat().coerceIn(0f, 1f))

                    val cal = Calendar.getInstance(ct.zone).apply { timeInMillis = now }
                    val (hm, ap) = context.clock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                    val px = row.r.left + dp(22f)
                    val nameY = row.r.top + dp(38f)
                    if (row.here) { fill.color = pal.accent; c.drawCircle(px + dp(4f), nameY - name.textSize * 0.33f, dp(4f), fill) }
                    c.text(ct.name, px + if (row.here) dp(16f) else 0f, nameY, name)
                    c.text(relative(ct.zone.getOffset(now) - hereOff, cal, localCal, row.here), px, nameY + dp(24f), rel)

                    if (removing === row) {
                        val bw = dp(120f); val bh = dp(52f)
                        removeR.set(row.r.right - dp(20f) - bw, row.r.centerY() - bh / 2, row.r.right - dp(20f), row.r.centerY() + bh / 2)
                        fill.color = pal.ink; c.drawRoundRect(removeR, bh / 2, bh / 2, fill)
                        chipT.color = pal.bg
                        c.text(if (row.here) "Hide" else "Remove", removeR.centerX(), removeR.centerY() + chipT.textSize * 0.36f, chipT, Paint.Align.CENTER)
                        if (row.here) {
                            renameR.set(removeR.left - dp(12f) - bw, removeR.top, removeR.left - dp(12f), removeR.bottom)
                            line.color = pal.ink; line.strokeWidth = dp(1.5f)
                            c.drawRoundRect(renameR, bh / 2, bh / 2, line)
                            chipT.color = pal.ink
                            c.text("Rename", renameR.centerX(), renameR.centerY() + chipT.textSize * 0.36f, chipT, Paint.Align.CENTER)
                        }
                    } else {
                        val ty = row.r.bottom - dp(34f)
                        val apW = if (ap.isEmpty()) 0f else ampm.measureText(ap) + dp(6f)
                        c.text(hm, row.r.right - dp(22f) - apW, ty, time, Paint.Align.RIGHT)
                        if (ap.isNotEmpty()) c.text(ap, row.r.right - dp(22f) - ampm.measureText(ap), ty, ampm)
                    }

                    // 24-hour daylight strip, now in the middle
                    val sx0 = row.r.left + dp(22f); val sx1 = row.r.right - dp(22f)
                    val sy = row.r.bottom - dp(16f)
                    val n = 25
                    val cols = IntArray(n) { k -> Sky.color(Sky.elevation(ct.lat, ct.lon, now + (k - 12) * 3_600_000L), pal, theme) }
                    val bright = IntArray(n) { k -> lift(cols[k]) }
                    strip.shader = LinearGradient(sx0, 0f, sx1, 0f, bright, null, Shader.TileMode.CLAMP)
                    c.drawRoundRect(sx0, sy - dp(3f), sx1, sy + dp(3f), dp(3f), dp(3f), strip)
                    strip.shader = null
                    line.color = pal.ink; line.strokeWidth = dp(2f)
                    val mx = (sx0 + sx1) / 2
                    c.drawLine(mx, sy - dp(7f), mx, sy + dp(7f), line)
                }
                y += rowH + gap
            }

            // Add
            addR.set(side, y, width - side, y + addH)
            line.color = pal.faint; line.strokeWidth = dp(1.5f)
            c.drawRoundRect(addR, addH / 2, addH / 2, line)
            rel.color = pal.dim
            c.text("+  Add a city", addR.centerX(), addR.centerY() + rel.textSize * 0.36f, rel, Paint.Align.CENTER)
            c.restore()
            maxScroll = max(0f, (y + scroll + addH + dp(24f)) - (height - insetBottom))

            if (!anim && !scrubbing) postInvalidateDelayed(60_000 - realNow % 60_000 + 50)
            return anim || scrubbing
        }

        /** A few fixed stars per city, fading in as the sun drops below the horizon. */
        private fun stars(c: Canvas, r: RectF, seed: Int, k: Float) {
            var x = seed.toLong() and 0xffffffffL
            fill.color = alpha(pal.ink, 0.55f * k)
            for (i in 0 until 22) {
                x = (x * 1103515245 + 12345) and 0x7fffffff
                val fx = (x % 1000) / 1000f
                x = (x * 1103515245 + 12345) and 0x7fffffff
                val fy = (x % 1000) / 1000f
                x = (x * 1103515245 + 12345) and 0x7fffffff
                val sz = dp(0.7f) + dp(1.1f) * ((x % 100) / 100f)
                c.drawCircle(r.left + dp(24f) + fx * (r.width() - dp(48f)), r.top + dp(14f) + fy * (r.height() - dp(48f)), sz, fill)
            }
        }

        /** Strip colours a little brighter than the card so the day shows up. */
        private fun lift(c: Int) = org.robbiemed.clock.core.mix(c, if (pal.light) pal.ink else pal.ink, if (pal.light) 0.05f else 0.18f)

        private fun relative(diffMs: Int, cal: Calendar, here: Calendar, isHere: Boolean): String {
            val dd = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR) - (here.get(Calendar.YEAR) * 400 + here.get(Calendar.DAY_OF_YEAR))
            val day = when { dd > 0 -> "Tomorrow"; dd < 0 -> "Yesterday"; else -> "Today" }
            if (isHere) return "Here · $day"
            val m = diffMs / 60_000
            if (m == 0) return "$day · same time"
            val h = abs(m) / 60; val r = abs(m) % 60
            return "$day · " + (if (m > 0) "+" else "−") + (if (r == 0) "$h h" else "$h h $r m")
        }

        private fun onLongPress() {
            if (mode != 0) return
            val row = rows.firstOrNull { it.r.contains(downX, downY) } ?: return
            tock(); mode = 3; removing = row; invalidate()
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (themeTap(e)) return true
            if (vt == null) vt = VelocityTracker.obtain()
            vt!!.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    scroller.forceFinished(true)
                    downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; mode = 0; startOffset = offset
                    postDelayed(longPress, 450)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - downX; val dy = e.y - downY
                    if (mode == 0 && (abs(dx) > dp(10f) || abs(dy) > dp(10f))) {
                        removeCallbacks(longPress)
                        mode = if (abs(dx) > abs(dy)) 1 else 2
                        if (mode == 1) { scrubbing = true; removing = null; parent?.requestDisallowInterceptTouchEvent(true) }
                    }
                    if (mode == 1) {
                        // One screen width = 12 hours. Drag left → later.
                        val before = (offset / 15f).toInt()
                        offset = startOffset - dx / width * 720f
                        val after = (offset / 15f).toInt()
                        if (before != after) { if (after % 4 == 0) tock() else tick() }
                        offsetTarget = offset
                        invalidate()
                    } else if (mode == 2) {
                        scroll = (scroll - (e.y - lastY)).coerceIn(0f, maxScroll); invalidate()
                    }
                    lastX = e.x; lastY = e.y
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    when (mode) {
                        1 -> { scrubbing = false; offsetTarget = ((offset / 15f).roundToLong() * 15).toFloat(); invalidate() }
                        2 -> {
                            vt!!.computeCurrentVelocity(1000)
                            scroller.fling(0, scroll.toInt(), 0, -vt!!.yVelocity.toInt(), 0, 0, 0, maxScroll.toInt()); invalidate()
                        }
                        0 -> tap(e.x, e.y)
                    }
                    vt?.recycle(); vt = null
                }
                MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); scrubbing = false; vt?.recycle(); vt = null }
            }
            return true
        }

        private fun tap(x: Float, y: Float) {
            val rm = removing
            if (rm != null) {
                removing = null
                if (rm.here) when {
                    removeR.contains(x, y) -> { yes(); store("zones").edit().putBoolean("hereOff", true).apply(); load() }
                    renameR.contains(x, y) -> { tock(); rename(rm.city) }
                } else if (removeR.contains(x, y)) { yes(); save(saved().filter { it.encode() != rm.city.encode() }); load() }
                invalidate(); return
            }
            when {
                chipR.contains(x, y) -> { tock(); offsetTarget = 0f; invalidate() }
                addR.contains(x, y) -> {
                    yes()
                    val i = Intent(context, PickActivity::class.java).putExtra("hereHidden", store("zones").getBoolean("hereOff", false))
                    @Suppress("DEPRECATION") startActivityForResult(i, 1)
                }
            }
        }

        private fun rename(c: City) {
            val tz = TimeZone.getDefault().id
            val auto = City.fromZone(tz).name
            val input = EditText(context).apply { setText(c.name); setSingleLine(); setSelection(text.length); hint = auto }
            val box = FrameLayout(context).apply { setPadding(dp(24f).toInt(), dp(8f).toInt(), dp(24f).toInt(), 0); addView(input) }
            val theme = if (pal.light) android.R.style.Theme_DeviceDefault_Light_Dialog_Alert else android.R.style.Theme_DeviceDefault_Dialog_Alert
            AlertDialog.Builder(context, theme).setTitle("Name for here").setMessage("Leave blank for $auto.").setView(box)
                .setPositiveButton("OK") { _, _ ->
                    val n = input.text.toString().replace('|', ' ').replace('\n', ' ').trim()
                    store("zones").edit().apply { if (n.isEmpty() || n == auto) remove("hereName") else putString("hereName", "$tz|$n") }.apply()
                    load()
                }
                .setNegativeButton("Cancel", null)
                .show().window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            input.requestFocus()
        }
    }
}
