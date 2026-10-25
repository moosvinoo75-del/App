package com.example.lifemanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- تغذیه ----------

@Composable
fun FoodScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by vm.todayFoods.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val total = list.sumOf { it.kcal }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("کالری امروز: ${fmt(total.toLong())}", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("مثلاً: ۲۰۰ گرم برنج و ۱۰۰ گرم مرغ") },
                minLines = 2, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(enabled = !busy, onClick = {
                if (text.isNotBlank()) {
                    busy = true
                    msg = ""
                    scope.launch {
                        try {
                            val key = Prefs.apiKey(ctx)
                            if (key.isNotBlank()) {
                                val out = Ai.ask(key, Ai.MODEL_FAST, Ai.FOOD_PROMPT, text)
                                val arr = JSONArray(out.substring(out.indexOf('['), out.lastIndexOf(']') + 1))
                                val now = System.currentTimeMillis()
                                val foods = (0 until arr.length()).map { i ->
                                    val o = arr.getJSONObject(i)
                                    Food(
                                        name = o.getString("name"), grams = o.optInt("grams", 0),
                                        kcal = o.getDouble("kcal").toInt(), date = now
                                    )
                                }
                                vm.addFoods(foods)
                            } else {
                                val (foods, missing) = LocalFood.estimate(text)
                                vm.addFoods(foods)
                                if (missing.isNotEmpty()) {
                                    msg = "کالری این‌ها پیدا نشد: " + missing.joinToString("، ") +
                                        " — برای تخمین دقیق‌تر کلید API را در تب «بیشتر» بگذار."
                                }
                            }
                            text = ""
                        } catch (e: Exception) {
                            msg = "خطا: ${e.message}"
                        }
                        busy = false
                    }
                }
            }) { Text(if (busy) "در حال محاسبه..." else "محاسبه و ثبت") }
        }
        if (msg.isNotBlank()) item { Text(msg) }
        items(list, key = { it.id }) { f ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name)
                        if (f.grams > 0) Text("${f.grams} گرم", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${f.kcal} کالری")
                    TextButton(onClick = { vm.delFood(f) }) { Text("حذف") }
                }
            }
        }
    }
}

// ---------- دستیار هوشمند ----------

@Composable
fun AssistantScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val txns by vm.txns.collectAsState(initial = emptyList())
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    val foods by vm.todayFoods.collectAsState(initial = emptyList())
    var q by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            label = { Text("سؤالت را بنویس") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = !busy, onClick = {
            val key = Prefs.apiKey(ctx)
            if (key.isBlank()) {
                answer = "اول کلید API را در تب «بیشتر» وارد کن."
            } else if (q.isNotBlank()) {
                busy = true
                scope.launch {
                    try {
                        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val data = buildString {
                            append("امروز: ${df.format(Date())}\n\nتراکنش‌ها (حداکثر ۱۰۰ مورد اخیر):\n")
                            txns.take(100).forEach {
                                append("${df.format(Date(it.date))} ${if (it.isIncome) "درآمد" else "هزینه"} ${it.amount} ${it.category} ${it.note}\n")
                            }
                            append("\nکارها:\n")
                            tasks.forEach { append("${if (it.done) "[انجام شد]" else "[مانده]"} ${it.title}\n") }
                            append("\nکالری امروز: ${foods.sumOf { it.kcal }}\n")
                        }
                        val system = "تو دستیار شخصی فارسی‌زبان هستی. فقط بر اساس داده‌های زیر جواب بده، کوتاه و دقیق.\n\n$data"
                        answer = Ai.ask(key, Ai.MODEL_SMART, system, q)
                    } catch (e: Exception) {
                        answer = "خطا: ${e.message}"
                    }
                    busy = false
                }
            }
        }) { Text(if (busy) "در حال فکر کردن..." else "بپرس") }
        if (answer.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) { Text(answer, Modifier.padding(16.dp)) }
        }
        Text(
            "نمونه: «این ماه چقدر خرج خوراک کردم؟» یا «چه کارهایی مانده؟»",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------- پیامک ----------

data class ParsedSms(val id: String, val amount: Long, val income: Boolean, val body: String, val date: Long)

fun parseBankSms(body: String): Pair<Long, Boolean>? {
    val b = digits(body).replace('٬', ',')
    val cut = Regex("مانده|موجودی").find(b)
    val main = if (cut != null) b.substring(0, cut.range.first) else b
    val m = Regex("[0-9]{1,3}(?:,[0-9]{3})+").find(main) ?: return null
    val amount = m.value.replace(",", "").toLongOrNull() ?: return null
    val inWords = listOf("واریز", "دریافت", "افزایش", "برگشت")
    val outWords = listOf("برداشت", "خرید", "پرداخت", "انتقال", "کاهش", "کارمزد")
    val i = inWords.map { main.indexOf(it) }.filter { it >= 0 }.minOrNull()
    val o = outWords.map { main.indexOf(it) }.filter { it >= 0 }.minOrNull()
    val income = when {
        i != null && o != null -> i < o
        i != null -> true
        o != null -> false
        Regex("\\+\\s*[0-9]|[0-9]\\s*\\+").containsMatchIn(main) -> true
        Regex("-\\s*[0-9]|[0-9]\\s*-").containsMatchIn(main) -> false
        else -> return null
    }
    return Pair(amount, income)
}

fun readSms(ctx: Context, days: Int, rial: Boolean): List<ParsedSms> {
    val out = mutableListOf<ParsedSms>()
    val from = System.currentTimeMillis() - days * 86_400_000L
    ctx.contentResolver.query(
        Uri.parse("content://sms/inbox"),
        arrayOf("_id", "body", "date"),
        "date > ?", arrayOf(from.toString()), "date DESC"
    )?.use { c ->
        while (c.moveToNext()) {
            val id = c.getString(0)
            val body = c.getString(1) ?: continue
            val date = c.getLong(2)
            val p = parseBankSms(body) ?: continue
            out.add(ParsedSms(id, if (rial) p.first / 10 else p.first, p.second, body, date))
        }
    }
    return out
}

// ---------- تنظیمات، پیامک، بکاپ ----------

@Composable
fun MoreScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(Prefs.apiKey(ctx)) }
    var msg by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<ParsedSms>>(emptyList()) }
    var rial by remember { mutableStateOf(true) }

    fun loadSms() {
        scope.launch {
            try {
                val all = withContext(Dispatchers.IO) { readSms(ctx, 60, rial) }
                val done = Prefs.smsIds(ctx)
                found = all.filter { it.id !in done }
                msg = if (found.isEmpty()) "پیامک مالی جدیدی پیدا نشد" else "${found.size} پیامک مالی پیدا شد"
            } catch (e: Exception) {
                msg = "خطا: ${e.message}"
            }
        }
    }

    val smsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) loadSms() else msg = "مجوز خواندن پیامک داده نشد"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val s = vm.exportJson()
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(s.toByteArray()) }
                }
                msg = "بکاپ ذخیره شد"
            } catch (e: Exception) {
                msg = "خطا: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                }
                vm.importJson(s)
                msg = "بازیابی انجام شد"
            } catch (e: Exception) {
                msg = "خطا: ${e.message}"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (msg.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) { Text(msg, Modifier.padding(12.dp)) }
        }

        Text("هوش مصنوعی (اختیاری)", style = MaterialTheme.typography.titleMedium)
        Text(
            "کلید API از console.anthropic.com. بدون کلید، تغذیه با جدول داخلی کار می‌کند و دستیار غیرفعال است.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("کلید API") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { Prefs.setApiKey(ctx, key); msg = "کلید ذخیره شد" }) { Text("ذخیره کلید") }

        HorizontalDivider()
        Text("پیامک بانکی", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rial, onCheckedChange = { rial = it })
            Text("مبلغ پیامک‌ها ریال است (تقسیم بر ۱۰ و تبدیل به تومان)")
        }
        Button(onClick = {
            if (ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                loadSms()
            } else {
                smsPerm.launch(Manifest.permission.READ_SMS)
            }
        }) { Text("خواندن پیامک‌های ۶۰ روز اخیر") }
        if (found.isNotEmpty()) {
            found.take(5).forEach {
                Text((if (it.income) "+" else "-") + fmt(it.amount) + "   " + it.body.take(40))
            }
            Button(onClick = {
                vm.addTxns(found.map {
                    Txn(amount = it.amount, isIncome = it.income, category = "پیامک",
                        note = it.body.take(60), date = it.date)
                })
                Prefs.addSmsIds(ctx, found.map { it.id }.toSet())
                msg = "${found.size} تراکنش اضافه شد"
                found = emptyList()
            }) { Text("افزودن همه (${found.size})") }
        }

        HorizontalDivider()
        Text("بکاپ", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { exportLauncher.launch("lifemanager-backup.json") }) { Text("گرفتن بکاپ") }
        Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("بازیابی (جایگزین همه‌ی داده‌ها)") }
    }
}
