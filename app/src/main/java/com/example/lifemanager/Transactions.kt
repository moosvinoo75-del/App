package com.example.lifemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val CATEGORIES = listOf("خوراک", "حمل‌ونقل", "قبض", "خرید", "درمان", "تفریح", "حقوق", "سایر")

@Composable
fun TxnScreen(vm: MainVm, ym: YM, onYm: (YM) -> Unit) {
    val all by vm.txns.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())

    var showForm by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayJalali()) }
    var dateErr by remember { mutableStateOf(false) }
    var pickedAcc by remember { mutableStateOf(0L) }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0: همه، 1: درآمد، 2: هزینه
    var selectedAccFilter by remember { mutableStateOf(0L) } // 0: همه‌ی حساب‌ها

    var editing by remember { mutableStateOf<Txn?>(null) }
    var eAmount by remember { mutableStateOf("") }
    var eIncome by remember { mutableStateOf(false) }
    var eCat by remember { mutableStateOf("") }
    var eNote by remember { mutableStateOf("") }
    var eDate by remember { mutableStateOf("") }
    var eDateErr by remember { mutableStateOf(false) }
    var eAccId by remember { mutableStateOf(0L) }

    // فقط حساب‌های نقد/بانک/بدهی؛ طلا و ارز و سهام ارزششان مقدار × قیمت است
    val usable = accounts.filter { it.type !in PRICED_TYPES }
    val accId = if (usable.any { it.id == pickedAcc }) pickedAcc else (usable.firstOrNull()?.id ?: 0L)
    val accName = accounts.associate { it.id to it.name }

    val month = monthTxns(all, ym)
    val income = month.filter { it.isIncome }.sumOf { it.amount }
    val expense = month.filter { !it.isIncome }.sumOf { it.amount }
    val q = query.trim()

    val shown = month.filter { t ->
        val matchType = filter == 0 || (filter == 1 && t.isIncome) || (filter == 2 && !t.isIncome)
        val matchAcc = selectedAccFilter == 0L || t.accountId == selectedAccFilter
        val matchQuery = q.isEmpty() || t.category.contains(q) || t.note.contains(q)
        matchType && matchAcc && matchQuery
    }
    val groups = shown.groupBy { jalaliText(it.date) }

    fun add(isIncome: Boolean) {
        val a = amount.faToLong() ?: return
        if (a <= 0) return
        val d = parseJalali(dateText)
        if (d == null) {
            dateErr = true
            return
        }
        vm.addTxn(a, isIncome, category.trim(), note.trim(), d, accId)
        amount = ""; category = ""; note = ""
        dateText = todayJalali()
    }

    fun open(t: Txn) {
        editing = t
        eAmount = t.amount.toString()
        eIncome = t.isIncome
        eCat = t.category
        eNote = t.note
        eDate = jalaliText(t.date)
        eDateErr = false
        eAccId = if (usable.any { it.id == t.accountId }) t.accountId else (usable.firstOrNull()?.id ?: 0L)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { MonthNav(ym, onYm) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("درآمد", shortAmount(income), IncomeColor, Modifier.weight(1f))
                StatCard("هزینه", shortAmount(expense), ExpenseColor, Modifier.weight(1f))
                StatCard(
                    "تراز", shortAmount(income - expense),
                    if (income - expense >= 0) IncomeColor else ExpenseColor, Modifier.weight(1f)
                )
            }
        }
        item {
            Button(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showForm) "بستن فرم" else "＋ ثبت تراکنش جدید")
            }
        }
        if (showForm) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = amount, onValueChange = { amount = it },
                            label = { Text("مبلغ (تومان)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AmountHint(amount)

                        if (usable.isEmpty()) {
                            Text(
                                "حساب نقد یا بانکی نداری؛ با ثبت اولین تراکنش، حساب «کیف پول» ساخته می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("حساب", style = MaterialTheme.typography.bodySmall)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                usable.forEach { a ->
                                    FilterChip(
                                        selected = accId == a.id,
                                        onClick = { pickedAcc = a.id },
                                        label = { Text(a.name) }
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CATEGORIES.forEach { c ->
                                FilterChip(
                                    selected = category == c,
                                    onClick = { category = c },
                                    label = { Text(c) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = category, onValueChange = { category = it },
                            label = { Text("دسته‌بندی (یا از بالا انتخاب کن)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = note, onValueChange = { note = it },
                            label = { Text("توضیح") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = dateText,
                                onValueChange = { dateText = it; dateErr = false },
                                label = { Text(if (dateErr) "تاریخ نامعتبر است" else "تاریخ شمسی") },
                                isError = dateErr, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = false,
                                onClick = { dateText = todayJalali(); dateErr = false },
                                label = { Text("امروز") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    dateText = jalaliText(System.currentTimeMillis() - 86_400_000L)
                                    dateErr = false
                                },
                                label = { Text("دیروز") }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { add(true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = IncomeColor)
                            ) { Text("＋ درآمد") }
                            Button(
                                onClick = { add(false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ExpenseColor)
                            ) { Text("－ هزینه") }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("جستجو در دسته و توضیح") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("همه") })
                FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("درآمد") })
                FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("هزینه") })
            }
        }
        if (usable.isNotEmpty()) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedAccFilter == 0L,
                        onClick = { selectedAccFilter = 0L },
                        label = { Text("همه‌ی حساب‌ها") }
                    )
                    usable.forEach { a ->
                        FilterChip(
                            selected = selectedAccFilter == a.id,
                            onClick = { selectedAccFilter = a.id },
                            label = { Text(a.name) }
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    "تراکنشی پیدا نشد 🌱",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        groups.forEach { (day, rows) ->
            item(key = "h$day") {
                val dayNet = rows.sumOf { if (it.isIncome) it.amount else -it.amount }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        jalaliLabel(rows.first().date),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        shortAmount(dayNet),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (dayNet >= 0) IncomeColor else ExpenseColor
                    )
                }
            }
            items(rows, key = { it.id }) { t ->
                val c = if (t.isIncome) IncomeColor else ExpenseColor
                Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { open(t) }) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (t.isIncome) "↓" else "↑", color = c, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.category.ifBlank { "بدون دسته" }, fontWeight = FontWeight.Medium)
                            if (t.note.isNotBlank()) {
                                Text(
                                    t.note, maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val acc = accName[t.accountId]
                            if (acc != null) {
                                Text(
                                    acc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            (if (t.isIncome) "+" else "-") + fmt(t.amount),
                            color = c, fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { vm.delTxn(t) }) { Text("✕") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    editing?.let { t ->
        val chosenAcc = if (usable.any { it.id == eAccId }) eAccId else (usable.firstOrNull()?.id ?: 0L)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("ویرایش تراکنش") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !eIncome, onClick = { eIncome = false },
                            label = { Text("هزینه") }, modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = eIncome, onClick = { eIncome = true },
                            label = { Text("درآمد") }, modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = eAmount, onValueChange = { eAmount = it },
                        label = { Text("مبلغ (تومان)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AmountHint(eAmount)

                    if (usable.isNotEmpty()) {
                        Text("حساب", style = MaterialTheme.typography.bodySmall)
                        AccountChips(usable, chosenAcc) { eAccId = it }
                    }

                    OutlinedTextField(
                        value = eCat, onValueChange = { eCat = it },
                        label = { Text("دسته") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = eNote, onValueChange = { eNote = it },
                        label = { Text("توضیح") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = eDate, onValueChange = { eDate = it; eDateErr = false },
                        label = { Text(if (eDateErr) "تاریخ نامعتبر است" else "تاریخ شمسی") },
                        isError = eDateErr, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val a = eAmount.faToLong()
                    val d = parseJalali(eDate)
                    if (d == null) {
                        eDateErr = true
                    } else if (a != null && a > 0) {
                        vm.updateTxn(t, a, eIncome, eCat.trim(), eNote.trim(), d, chosenAcc)
                        editing = null
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("انصراف") } }
        )
    }
}
