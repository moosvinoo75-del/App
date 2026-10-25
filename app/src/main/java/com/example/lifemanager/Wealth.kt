package com.example.lifemanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.Locale

// انواع حسابی که ارزششان = مقدار × قیمت واحد است
val PRICED_TYPES = setOf("طلا", "ارز", "سهام")

fun Account.isPriced(): Boolean = type in PRICED_TYPES && (quantity > 0.0 || unitPrice > 0L)

// ارزش حساب: برای طلا/ارز/سهام مقدار × قیمت، برای بقیه همان موجودی
fun Account.value(): Long = if (isPriced()) Math.round(quantity * unitPrice) else balance

data class Wealth(
    val assets: Long,
    val debts: Long,
    val net: Long,
    val byType: List<Pair<String, Long>>
)

// ثروت خالص = مجموع ارزش دارایی‌ها − مجموع بدهی‌ها
fun computeWealth(list: List<Account>): Wealth {
    val assets = list.filter { it.type != "بدهی" }.sumOf { it.value() }
    val debts = list.filter { it.type == "بدهی" }.sumOf { it.value() }
    val byType = list.filter { it.type != "بدهی" }
        .groupBy { it.type }
        .map { (k, v) -> k to v.sumOf { it.value() } }
        .sortedByDescending { it.second }
    return Wealth(assets, debts, assets - debts, byType)
}

// ---------- برچسب‌ها ----------

fun unitName(type: String): String = when (type) {
    "طلا" -> "گرم"
    "ارز" -> "واحد"
    else -> "سهم"
}

fun qtyLabel(type: String): String = when (type) {
    "طلا" -> "مقدار (گرم)"
    "ارز" -> "مقدار (واحد ارز، مثلاً دلار)"
    else -> "تعداد سهم"
}

fun priceLabel(type: String): String = when (type) {
    "طلا" -> "قیمت هر گرم (تومان)"
    "ارز" -> "قیمت هر واحد (تومان)"
    else -> "قیمت هر سهم (تومان)"
}

fun parseQty(s: String): Double? =
    digits(s).replace(",", "").replace('٫', '.').replace('/', '.').trim().toDoubleOrNull()

fun qtyText(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString()
    else String.format(Locale.US, "%.3f", q).trimEnd('0').trimEnd('.')

// ---------- عدد به حروف ----------

private val ONES = listOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
private val TEENS = listOf(
    "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"
)
private val TENS = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
private val HUNDREDS = listOf(
    "", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"
)
private val SCALES = listOf("", "هزار", "میلیون", "میلیارد", "تریلیون")

private fun threeDigits(n: Int): String {
    val parts = mutableListOf<String>()
    val h = n / 100
    val t = (n % 100) / 10
    val o = n % 10
    if (h > 0) parts.add(HUNDREDS[h])
    if (t == 1) {
        parts.add(TEENS[o])
    } else {
        if (t > 1) parts.add(TENS[t])
        if (o > 0) parts.add(ONES[o])
    }
    return parts.joinToString(" و ")
}

fun numberToWords(n: Long): String {
    if (n == 0L) return "صفر"
    if (n < 0) return "منفی " + numberToWords(-n)
    val groups = mutableListOf<Int>()
    var x = n
    while (x > 0) {
        groups.add((x % 1000).toInt())
        x /= 1000
    }
    if (groups.size > SCALES.size) return fmt(n)
    val parts = mutableListOf<String>()
    for (i in groups.indices.reversed()) {
        val g = groups[i]
        if (g == 0) continue
        val w = threeDigits(g)
        parts.add(
            when {
                i == 0 -> w
                i == 1 && g == 1 -> "هزار"
                else -> w + " " + SCALES[i]
            }
        )
    }
    return parts.joinToString(" و ")
}

@Composable
fun AmountWords(n: Long) {
    if (n != 0L) {
        Text(
            "${numberToWords(n)} تومان",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AmountHint(text: String) {
    val n = text.faToLong()
    if (n != null) AmountWords(n)
}
