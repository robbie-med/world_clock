package org.robbiemed.zones

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.robbiemed.clock.core.Flex
import org.robbiemed.clock.core.Palette
import org.robbiemed.clock.core.clock
import org.robbiemed.clock.core.dp
import org.robbiemed.clock.core.edgeToEdge
import java.util.Calendar
import java.util.TimeZone

/** Type-to-find any city or time zone, or any town the geocoder knows. */
class PickActivity : Activity() {
    private lateinit var all: List<City>
    private var shown = listOf<City>()
    private var local = listOf<City>()
    private var places = listOf<City>()
    private var asked = 0
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        edgeToEdge()
        val pal = Palette(this, 1)
        window.decorView.setBackgroundColor(pal.bg)
        val ids = City.known.map { it.tz }.toSet()
        all = City.known + TimeZone.getAvailableIDs()
            .filter { it.contains('/') && it !in ids && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() }
            .map { City.fromZone(it) }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val field = EditText(this).apply {
            hint = "Any city, town or zone"
            setHintTextColor(pal.faint); setTextColor(pal.ink); textSize = 20f
            typeface = Flex.face(420f, 105f, 20f)
            background = GradientDrawable().apply { cornerRadius = dp(28f); setColor(pal.surface) }
            setPadding(dp(24f).toInt(), dp(16f).toInt(), dp(24f).toInt(), dp(16f).toInt())
            setSingleLine(); highlightColor = pal.deep
        }
        val list = ListView(this).apply { divider = null; selector = GradientDrawable().apply { setColor(pal.surface) } }
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: ViewGroup): View {
                val row = (v as? LinearLayout) ?: LinearLayout(this@PickActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(28f).toInt(), dp(14f).toInt(), dp(28f).toInt(), dp(14f).toInt())
                    addView(TextView(context).apply { textSize = 19f; setTextColor(pal.ink); typeface = Flex.face(480f, 105f, 19f) })
                    addView(TextView(context).apply { textSize = 14f; setTextColor(pal.dim); typeface = Flex.face(400f, 100f, 14f) })
                }
                val c = shown[i]
                val cal = Calendar.getInstance(c.zone)
                val (hm, ap) = clock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                (row.getChildAt(0) as TextView).text = c.name
                (row.getChildAt(1) as TextView).text = "${c.country}  ·  $hm$ap"
                return row
            }
        }
        val status = TextView(this).apply {
            isClickable = false
            textSize = 14f; setTextColor(pal.faint); typeface = Flex.face(420f, 100f, 14f)
            setPadding(dp(28f).toInt(), dp(14f).toInt(), dp(28f).toInt(), dp(28f).toInt())
        }
        list.addFooterView(status, null, false)
        list.adapter = adapter
        val hereHidden = intent.getBooleanExtra("hereHidden", false)
        if (hereHidden) {
            val tz = TimeZone.getDefault()
            val cal = Calendar.getInstance(tz)
            val (hm, ap) = clock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(28f).toInt(), dp(14f).toInt(), dp(28f).toInt(), dp(14f).toInt())
                addView(TextView(context).apply { textSize = 19f; setTextColor(pal.accent); typeface = Flex.face(560f, 105f, 19f); text = "Show Here again" })
                addView(TextView(context).apply { textSize = 14f; setTextColor(pal.dim); typeface = Flex.face(400f, 100f, 14f); text = "This phone's time  ·  $hm$ap" })
            }
            list.addHeaderView(head, "here", true)
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val i = pos - list.headerViewsCount
            if (pos < list.headerViewsCount) { setResult(RESULT_OK, Intent().putExtra("here", true)); finish() }
            else if (i < shown.size) { setResult(RESULT_OK, Intent().putExtra("city", shown[i].encode())); finish() }
        }
        fun show() {
            shown = local + places.filter { p -> local.none { it.name.equals(p.name, true) && it.tz == p.tz } }
            adapter.notifyDataSetChanged()
        }
        fun filter(q: String) {
            val s = q.trim().lowercase()
            local = if (s.isEmpty()) City.known else all.filter {
                it.name.lowercase().contains(s) || it.country.lowercase().contains(s) || it.tz.lowercase().contains(s)
            }.sortedBy { if (it.name.lowercase().startsWith(s)) 0 else 1 }
            places = emptyList()
            val n = ++asked
            main.removeCallbacksAndMessages(null)
            status.setOnClickListener(null)
            if (s.length >= 3) {
                status.text = "Looking further afield…"
                main.postDelayed({
                    Places.search(q.trim()) { found ->
                        if (n != asked) return@search
                        places = found ?: emptyList(); show()
                        status.text = when {
                            found == null -> "Can't reach the town finder: offline, or Network is off for World. Tap for settings."
                            found.isEmpty() && local.isEmpty() -> "Nothing by that name."
                            else -> ""
                        }
                        if (found == null) status.setOnClickListener {
                            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
                        }
                    }
                }, 450)
            } else status.text = ""
            show()
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = filter(s.toString())
        })
        filter("")
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(8f).toInt()) }
        root.addView(field, lp)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.gravity = Gravity.TOP
        root.setOnApplyWindowInsetsListener { v, ins ->
            val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(0, i.top, 0, i.bottom); ins
        }
        setContentView(root)
        field.requestFocus()
    }

    override fun onDestroy() { main.removeCallbacksAndMessages(null); asked = -1; super.onDestroy() }
}
