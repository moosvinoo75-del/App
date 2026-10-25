package com.example.lifemanager

import java.util.Calendar
import java.util.Locale

val JALALI_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
    val gDm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
        ((gy2 + 399) / 400) + gd + gDm[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31
        jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30
        jd = 1 + (days - 186) % 30
    }
    return intArrayOf(jy, jm, jd)
}

fun jalaliToGregorian(jyIn: Int, jm: Int, jd: Int): IntArray {
    val jy = jyIn + 1595
    var days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jd +
        (if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186)
    var gy = 400 * (days / 146097)
    days %= 146097
    if (days > 36524) {
        days -= 1
        gy += 100 * (days / 36524)
        days %= 36524
        if (days >= 365) days++
    }
    gy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        gy += (days - 1) / 365
        days = (days - 1) % 365
    }
    var gd = days + 1
    val leap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
    val sal = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var gm = 0
    while (gm < 13 && gd > sal[gm]) {
        gd -= sal[gm]
        gm++
    }
    return intArrayOf(gy, gm, gd)
}

fun jalaliOf(ms: Long): IntArray {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    return gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

// مثلاً 1405/08/03
fun jalaliText(ms: Long): String {
    val j = jalaliOf(ms)
    return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
}

// مثلاً 3 آبان 1405
fun jalaliLabel(ms: Long): String {
    val j = jalaliOf(ms)
    return "${j[2]} ${JALALI_MONTHS[j[1] - 1]} ${j[0]}"
}

fun todayJalali(): String = jalaliText(System.currentTimeMillis())

// تاریخ شمسی تایپ‌شده را به میلی‌ثانیه تبدیل می‌کند؛ اگر نامعتبر بود null
fun parseJalali(s: String): Long? {
    val parts = digits(s).trim().replace('-', '/').replace('.', '/').split("/").map { it.trim() }
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (y < 1300 || y > 1500 || m < 1 || m > 12 || d < 1) return null
    val maxDay = if (m <= 6) 31 else 30
    if (d > maxDay) return null
    val today = jalaliOf(System.currentTimeMillis())
    if (y == today[0] && m == today[1] && d == today[2]) return System.currentTimeMillis()
    val g = jalaliToGregorian(y, m, d)
    val c = Calendar.getInstance()
    c.set(g[0], g[1] - 1, g[2], 12, 0, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
