package com.example.lifemanager

import android.content.Context
import java.util.Calendar
import java.util.Locale

fun fmt(n: Long): String = String.format(Locale.US, "%,d", n)

fun digits(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
    }
    return sb.toString()
}

fun String.faToLong(): Long? = digits(this).replace(",", "").trim().toLongOrNull()

fun startOfDay(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun smsIds(c: Context): Set<String> = sp(c).getStringSet("sms", emptySet()) ?: emptySet()

    fun addSmsIds(c: Context, ids: Set<String>) {
        sp(c).edit().putStringSet("sms", smsIds(c) + ids).apply()
    }

    fun lastNotifyDay(c: Context): Long = sp(c).getLong("notify_day", 0L)

    fun setLastNotifyDay(c: Context, v: Long) {
        sp(c).edit().putLong("notify_day", v).apply()
    }
}

// ---------- جدول کالری داخلی (وقتی کلید API نیست) ----------

object LocalFood {
    private val per100 = mapOf(
        "برنج" to 130, "پلو" to 130, "چلو" to 130, "نان" to 270, "سنگک" to 250,
        "لواش" to 275, "بربری" to 280, "مرغ" to 165, "جوجه" to 165, "گوشت" to 250,
        "کوبیده" to 250, "ماهی" to 140, "تخم مرغ" to 155, "پنیر" to 300, "ماست" to 60,
        "شیر" to 60, "سیب زمینی" to 90, "سیب" to 52, "موز" to 89, "پرتقال" to 47,
        "خیار" to 15, "گوجه" to 18, "عدس" to 116, "لوبیا" to 110, "نخود" to 120,
        "قورمه سبزی" to 150, "قیمه" to 160, "آبگوشت" to 100, "ماکارونی" to 160,
        "حلوا" to 400, "خرما" to 280, "گردو" to 650, "بادام" to 580, "کره" to 717,
        "روغن" to 884, "عسل" to 304, "شکر" to 387, "پیتزا" to 270, "ساندویچ" to 250,
        "همبرگر" to 250, "املت" to 150, "کتلت" to 220
    )
    private val piece = mapOf("تخم مرغ" to 50, "سیب" to 180, "موز" to 120, "پرتقال" to 150)

    fun estimate(text: String): Pair<List<Food>, List<String>> {
        val now = System.currentTimeMillis()
        val found = mutableListOf<Food>()
        val missing = mutableListOf<String>()
        val t = digits(text).replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک')
        val parts = t.split(Regex("[،,\\n+]|\\sو\\s")).map { it.trim() }.filter { it.isNotEmpty() }
        val keys = per100.keys.sortedByDescending { it.length }
        for (p in parts) {
            val key = keys.firstOrNull { p.contains(it) }
            if (key == null) {
                missing.add(p)
                continue
            }
            val num = Regex("[0-9]+(?:\\.[0-9]+)?").find(p)?.value?.toDoubleOrNull()
            val pw = piece[key]
            val grams: Double = when {
                num == null -> 100.0
                pw != null && (p.contains("عدد") || (num <= 10 && !p.contains("گرم"))) -> num * pw
                else -> num
            }
            val kcal = (grams * (per100[key] ?: 0) / 100.0).toInt()
            found.add(Food(name = p, grams = grams.toInt(), kcal = kcal, date = now))
        }
        return Pair(found, missing)
    }
}
