package com.example.lifemanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- تغذیه ----------

@Composable
fun FoodScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by vm.todayFoods.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var showCatalog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("") }
    var pickedRef by remember { mutableStateOf<FoodRef?>(null) }

    val total = list.sumOf { it.kcal }

    val filteredCatalog = remember(searchQuery, selectedCat) {
        val q = digits(searchQuery).replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک').trim()
        LocalFood.catalog.filter { item ->
            (selectedCat.isEmpty() || item.category == selectedCat) &&
                (q.isEmpty() ||
                    item.name.replace('\u200c', ' ').contains(q) ||
                    item.category.replace('\u200c', ' ').contains(q))
        }
    }

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
                label = { Text("مثلاً: ۲۰۰ گرم برنج و ۱ سیخ کباب کوبیده") },
                minLines = 2, modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (text.isNotBlank()) {
                            busy = true
                            msg = ""
                            scope.launch {
                                val input = text
                                try {
                                    // ابتدا بررسی با بانک اطلاعاتی جامع داخلی
                                    val (localFoods, missing) = LocalFood.estimate(input)
                                    if (missing.isEmpty() && localFoods.isNotEmpty()) {
                                        vm.addFoods(localFoods)
                                        text = ""
                                    } else if (AiHub.isReady(ctx)) {
                                        try {
                                            val out = AiHub.ask(ctx, AiHub.FOOD_PROMPT, input)
                                            val aiFoods = AiHub.parseFoodJson(out)
                                            if (aiFoods.isNotEmpty()) {
                                                vm.addFoods(aiFoods)
                                                text = ""
                                            } else if (localFoods.isNotEmpty()) {
                                                vm.addFoods(localFoods)
                                                text = ""
                                            }
                                        } catch (aiErr: Exception) {
                                            // در صورت قطع اینترنت یا خطای API، موارد پیدا شده در بانک داخلی ثبت می‌شوند
                                            if (localFoods.isNotEmpty()) {
                                                vm.addFoods(localFoods)
                                                text = ""
                                                msg = "هوش مصنوعی در دسترس نبود؛ موارد شناخته‌شده با بانک داخلی ثبت شد."
                                            } else {
                                                msg = "خطای هوش مصنوعی: ${aiErr.message}"
                                            }
                                        }
                                    } else {
                                        vm.addFoods(localFoods)
                                        text = ""
                                        if (missing.isNotEmpty()) {
                                            msg = "کالری این‌ها پیدا نشد: " + missing.joinToString("، ") +
                                                " — از دکمه‌ی «بانک غذاها» انتخاب کن یا کلید API را در تب «بیشتر» بگذار."
                                        }
                                    }
                                } catch (e: Exception) {
                                    msg = "خطا: ${e.message}"
                                }
                                busy = false
                            }
                        }
                    }
                ) { Text(if (busy) "در حال محاسبه..." else "محاسبه و ثبت") }

                OutlinedButton(
                    onClick = { showCatalog = !showCatalog },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (showCatalog) "بستن بانک غذاها" else "📖 بانک غذاها (${LocalFood.catalog.size})")
                }
            }
        }
        if (msg.isNotBlank()) item { Text(msg) }

        if (showCatalog) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("انتخاب از بانک غذاها", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("جستجوی نام غذا (مثلاً قرمه سبزی، پیتزا، شیر...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedCat.isEmpty(),
                                onClick = { selectedCat = "" },
                                label = { Text("همه (${LocalFood.catalog.size})") }
                            )
                            LocalFood.categories.forEach { c ->
                                FilterChip(
                                    selected = selectedCat == c,
                                    onClick = { selectedCat = if (selectedCat == c) "" else c },
                                    label = { Text(c) }
                                )
                            }
                        }
                    }
                }
            }
            items(filteredCatalog, key = { "ref_${it.name}" }) { ref ->
                Card(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { pickedRef = ref }
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(ref.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${ref.category} · ${ref.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "پروتئین: ${qtyText(ref.protein.toDouble())}g · کربوهیدرات: ${qtyText(ref.carbs.toDouble())}g · چربی: ${qtyText(ref.fat.toDouble())}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${ref.calories} kcal",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "＋ افزودن",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
        }

        if (list.isNotEmpty()) {
            item {
                Text("ثبت‌شده‌های امروز", style = MaterialTheme.typography.titleMedium)
            }
        }
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

    pickedRef?.let { ref ->
        var byGram by remember(ref) { mutableStateOf(false) }
        var amountText by remember(ref) { mutableStateOf("1") }

        val num = digits(amountText).replace('٫', '.').replace('/', '.').trim().toDoubleOrNull() ?: 0.0
        val factor = if (byGram && ref.unitGrams > 0) num / ref.unitGrams else num
        val calcKcal = Math.round(factor * ref.calories).toInt()
        val calcGrams = if (byGram) num.toInt() else Math.round(factor * ref.unitGrams).toInt()
        val calcProt = factor * ref.protein
        val calcCarb = factor * ref.carbs
        val calcFat = factor * ref.fat

        AlertDialog(
            onDismissRequest = { pickedRef = null },
            title = { Text(ref.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "واحد استاندارد: ${ref.unit} (${ref.calories} کالری)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ref.unitGrams > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !byGram,
                                onClick = { byGram = false; amountText = "1" },
                                label = { Text("بر اساس واحد / پُرس") }
                            )
                            FilterChip(
                                selected = byGram,
                                onClick = { byGram = true; amountText = ref.unitGrams.toString() },
                                label = { Text("بر اساس گرم") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(if (byGram) "وزن (گرم)" else "تعداد واحد (مثلاً 1 یا 0.5 یا 2)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!byGram) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("0.5" to "نصف", "1" to "۱ واحد", "1.5" to "۱.۵ واحد", "2" to "۲ واحد", "3" to "۳ واحد")
                                .forEach { (v, l) ->
                                    FilterChip(
                                        selected = amountText == v,
                                        onClick = { amountText = v },
                                        label = { Text(l) }
                                    )
                                }
                        }
                    }
                    if (num > 0) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "کالری: ${fmt(calcKcal.toLong())} kcal",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "پروتئین: ${qtyText(calcProt)}g · کربوهیدرات: ${qtyText(calcCarb)}g · چربی: ${qtyText(calcFat)}g",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (num > 0) {
                        vm.addFoods(
                            listOf(
                                Food(
                                    name = ref.name,
                                    grams = calcGrams,
                                    kcal = calcKcal,
                                    date = System.currentTimeMillis()
                                )
                            )
                        )
                        pickedRef = null
                    }
                }) { Text("ثبت در امروز") }
            },
            dismissButton = {
                TextButton(onClick = { pickedRef = null }) { Text("انصراف") }
            }
        )
    }
}

// ---------- دستیار هوشمند ----------

@Composable
fun AssistantScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val txns by vm.txns.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val budgets by vm.budgets.collectAsState(initial = emptyList())
    val goals by vm.goals.collectAsState(initial = emptyList())
    val cheques by vm.cheques.collectAsState(initial = emptyList())
    val loans by vm.loans.collectAsState(initial = emptyList())
    val people by vm.people.collectAsState(initial = emptyList())
    val todosFlow = remember(vm) { vm.db.todoDao().all() }
    val todos by todosFlow.collectAsState(initial = emptyList())
    val foods by vm.todayFoods.collectAsState(initial = emptyList())

    var prov by remember { mutableStateOf(AiHub.provider(ctx)) }
    var q by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun sendQuestion(question: String) {
        val cleanQ = question.trim()
        if (cleanQ.isBlank() || busy) return
        if (AiHub.key(ctx, prov).isBlank()) {
            errorMsg = "اول کلید API ${AiHub.label(prov)} را در تب «بیشتر» وارد و ذخیره کن."
            return
        }
        busy = true
        errorMsg = ""
        scope.launch {
            try {
                val ym = currentYM()
                val curMonthTxns = monthTxns(txns, ym)
                val monthInc = curMonthTxns.filter { it.isIncome }.sumOf { it.amount }
                val monthExp = curMonthTxns.filter { !it.isIncome }.sumOf { it.amount }
                val wealth = computeWealth(accounts)

                val data = buildString {
                    append("تاریخ امروز (شمسی): ${todayJalali()} (${ym.label()})\n")
                    append("ثروت خالص کل: ${fmt(wealth.net)} تومان (دارایی‌ها: ${fmt(wealth.assets)}، بدهی‌ها: ${fmt(wealth.debts)})\n")
                    append("خلاصه ماه جاری (${ym.label()}): درآمد ${fmt(monthInc)} تومان، هزینه ${fmt(monthExp)} تومان\n\n")

                    if (accounts.isNotEmpty()) {
                        append("حساب‌ها و دارایی‌ها:\n")
                        accounts.forEach { a ->
                            append("- ${a.name} (${a.type}): ${fmt(a.value())} تومان\n")
                        }
                        append("\n")
                    }

                    if (budgets.isNotEmpty()) {
                        val spentMap = categoryTotals(curMonthTxns).toMap()
                        append("بودجه‌های این ماه:\n")
                        budgets.forEach { b ->
                            append("- ${b.category}: خرج‌شده ${fmt(spentMap[b.category] ?: 0L)} از سقف ${fmt(b.limitAmount)} تومان\n")
                        }
                        append("\n")
                    }

                    if (goals.isNotEmpty()) {
                        append("اهداف پس‌انداز:\n")
                        goals.forEach { g ->
                            append("- ${g.name}: ${fmt(g.saved)} از ${fmt(g.target)} تومان\n")
                        }
                        append("\n")
                    }

                    val pendingCheques = cheques.filter { it.status == 0 }
                    if (pendingCheques.isNotEmpty()) {
                        append("چک‌های در انتظار:\n")
                        pendingCheques.forEach { c ->
                            append("- ${if (c.issued) "صادره به" else "دریافتی از"} ${c.party}: ${fmt(c.amount)} تومان، سررسید ${jalaliText(c.dueDate)} (${dueText(daysUntil(c.dueDate))})\n")
                        }
                        append("\n")
                    }

                    val activeLoans = loans.filter { it.paid < it.totalCount }
                    if (activeLoans.isNotEmpty()) {
                        append("وام‌ها و اقساط فعال:\n")
                        activeLoans.forEach { l ->
                            val due = loanDue(l)
                            append("- ${l.title}: هر قسط ${fmt(l.installment)} تومان (${l.paid} از ${l.totalCount} پرداخت شده)" +
                                (if (due != null) "، قسط بعدی: ${jalaliText(due)}" else "") + "\n")
                        }
                        append("\n")
                    }

                    val openPeople = people.filter { it.paid < it.amount }
                    if (openPeople.isNotEmpty()) {
                        append("طلب و بدهی اشخاص:\n")
                        openPeople.forEach { p ->
                            append("- ${p.person}: ${fmt(p.amount - p.paid)} تومان (${if (p.owedToMe) "طلب من" else "بدهی من"})\n")
                        }
                        append("\n")
                    }

                    append("تراکنش‌های اخیر (حداکثر ۸۰ مورد):\n")
                    txns.take(80).forEach {
                        append("- ${jalaliText(it.date)} | ${if (it.isIncome) "درآمد" else "هزینه"} | ${fmt(it.amount)} تومان | دسته: ${it.category.ifBlank { "بدون دسته" }} | توضیح: ${it.note}\n")
                    }

                    append("\nکارها (Todos):\n")
                    todos.take(50).forEach { t ->
                        val dueStr = if (t.dueDate > 0) " (موعد: ${jalaliText(t.dueDate)})" else ""
                        append("- ${if (t.done) "[انجام شد]" else "[مانده]"} ${t.title}$dueStr ${if (t.category.isNotBlank()) "[${t.category}]" else ""}\n")
                    }

                    append("\nتغذیه امروز (مجموع کالری: ${foods.sumOf { it.kcal }} kcal):\n")
                    foods.forEach { f ->
                        append("- ${f.name} (${f.grams} گرم): ${f.kcal} کالری\n")
                    }
                }

                val system = "تو دستیار هوشمند و مشاور شخصی مالی، برنامه‌ریزی و تغذیه‌ی فارسی‌زبان در اپلیکیشن «مدیریت زندگی» هستی. " +
                    "بر اساس داده‌های واقعی کاربر که در ادامه آمده، دقیق، صمیمی، کاربردی و مرتب به فارسی پاسخ بده. مبالغ به تومان و تاریخ‌ها به شمسی هستند.\n\n$data"

                val ans = AiHub.ask(ctx, system, cleanQ)
                history = listOf(cleanQ to ans) + history
                q = ""
            } catch (e: Exception) {
                errorMsg = "خطا: ${e.message}"
            }
            busy = false
        }
    }

    val quickPrompts = listOf(
        "وضعیت مالی و خرج‌های این ماهم رو تحلیل کن",
        "چه کارهایی برای امروز یا عقب‌افتاده دارم؟",
        "چک‌ها، اقساط و بدهی‌های پیش‌رو چیا هستن؟",
        "کالری و تغذیه امروز من چطور بوده؟"
    )

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("دستیار هوشمند", style = MaterialTheme.typography.titleLarge)
            if (history.isNotEmpty()) {
                TextButton(onClick = { history = emptyList() }) { Text("پاک‌کردن صفحه") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiHub.PROVIDERS.forEach { p ->
                FilterChip(
                    selected = prov == p,
                    onClick = { prov = p; AiHub.setProvider(ctx, p); errorMsg = "" },
                    label = { Text(AiHub.label(p)) }
                )
            }
        }

        Text("پرسش‌های سریع:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { prompt ->
                AssistChip(
                    enabled = !busy,
                    onClick = { q = prompt; sendQuestion(prompt) },
                    label = { Text(prompt) }
                )
            }
        }

        OutlinedTextField(
            value = q, onValueChange = { q = it },
            label = { Text("سؤالت را در مورد مالی، کارها یا تغذیه بنویس...") },
            minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !busy && q.isNotBlank(),
            onClick = { sendQuestion(q) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "در حال تحلیل و نوشتن پاسخ..." else "بپرس")
        }

        if (errorMsg.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Text(errorMsg, Modifier.padding(12.dp), color = ExpenseColor)
            }
        }

        history.forEach { (question, answer) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "پرسش: $question",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    Text(answer, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
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
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }

    var prov by remember { mutableStateOf(AiHub.provider(ctx)) }
    var keyText by remember { mutableStateOf(AiHub.key(ctx, AiHub.provider(ctx))) }
    var modelText by remember { mutableStateOf(AiHub.model(ctx, AiHub.provider(ctx))) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<ParsedSms>>(emptyList()) }
    var rial by remember { mutableStateOf(true) }
    var smsAcc by remember { mutableStateOf(0L) }

    val chosenSmsAcc = if (usable.any { it.id == smsAcc }) smsAcc else (usable.firstOrNull()?.id ?: 0L)

    fun saveAi() {
        AiHub.setKey(ctx, prov, keyText)
        AiHub.setModel(ctx, prov, modelText)
    }

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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiHub.PROVIDERS.forEach { p ->
                FilterChip(
                    selected = prov == p,
                    onClick = {
                        prov = p
                        AiHub.setProvider(ctx, p)
                        keyText = AiHub.key(ctx, p)
                        modelText = AiHub.model(ctx, p)
                    },
                    label = { Text(AiHub.label(p)) }
                )
            }
        }
        Text(AiHub.keyHint(prov), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = keyText, onValueChange = { keyText = it },
            label = { Text("کلید API ${AiHub.label(prov)}") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = modelText, onValueChange = { modelText = it },
            label = { Text("نام مدل (مثلاً ${AiHub.defaultModel(prov)})") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { saveAi(); msg = "ذخیره شد" }) { Text("ذخیره") }
            OutlinedButton(enabled = !aiBusy, onClick = {
                saveAi()
                aiBusy = true
                scope.launch {
                    try {
                        models = AiHub.listModels(ctx)
                        if (models.isEmpty()) msg = "مدلی پیدا نشد" else showModels = true
                    } catch (e: Exception) {
                        msg = "خطا: ${e.message}"
                    }
                    aiBusy = false
                }
            }) { Text("فهرست مدل‌ها") }
            OutlinedButton(enabled = !aiBusy, onClick = {
                saveAi()
                aiBusy = true
                scope.launch {
                    try {
                        val r = AiHub.ask(ctx, "در یک جمله‌ی کوتاه فارسی جواب بده.", "سلام، کار می‌کنی؟")
                        msg = "✓ ${AiHub.label(prov)}: ${r.take(120)}"
                    } catch (e: Exception) {
                        msg = "خطا: ${e.message}"
                    }
                    aiBusy = false
                }
            }) { Text(if (aiBusy) "صبر کن..." else "تست اتصال") }
        }

        HorizontalDivider()
        Text("پیامک بانکی", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rial, onCheckedChange = { rial = it })
            Text("مبلغ پیامک‌ها ریال است (تقسیم بر ۱۰ و تبدیل به تومان)")
        }
        if (usable.isNotEmpty()) {
            Text("انتخاب حساب برای اعمال موجودی پیامک‌ها:", style = MaterialTheme.typography.bodySmall)
            AccountChips(usable, chosenSmsAcc) { smsAcc = it }
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
                vm.addSmsTxns(
                    found.map {
                        Txn(
                            amount = it.amount, isIncome = it.income, category = "پیامک",
                            note = it.body.take(60), date = it.date, accountId = chosenSmsAcc
                        )
                    },
                    chosenSmsAcc
                )
                Prefs.addSmsIds(ctx, found.map { it.id }.toSet())
                msg = "${found.size} تراکنش اضافه شد و روی حساب اعمال گشت"
                found = emptyList()
            }) { Text("افزودن همه (${found.size}) و اعمال روی حساب") }
        }

        HorizontalDivider()
        Text("بکاپ", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { exportLauncher.launch("lifemanager-backup.json") }) { Text("گرفتن بکاپ") }
        Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("بازیابی (جایگزین همه‌ی داده‌ها)") }
    }

    if (showModels) {
        AlertDialog(
            onDismissRequest = { showModels = false },
            title = { Text("مدل‌های موجود ${AiHub.label(prov)}") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(models) { m ->
                        TextButton(onClick = {
                            modelText = m
                            AiHub.setModel(ctx, prov, m)
                            showModels = false
                            msg = "مدل انتخاب شد: $m"
                        }) { Text(m) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModels = false }) { Text("بستن") } }
        )
    }
}
