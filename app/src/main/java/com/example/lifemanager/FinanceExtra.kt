package com.example.lifemanager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ---------- اجزای مشترک ----------

@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun LineField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun NumField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DateField(value: String, err: Boolean, label: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange,
            label = { Text(if (err) "تاریخ نامعتبر است" else label) },
            isError = err, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = { onChange(todayJalali()) },
                label = { Text("امروز") }
            )
            FilterChip(
                selected = false,
                onClick = { onChange(jalaliText(System.currentTimeMillis() + 7 * 86_400_000L)) },
                label = { Text("۷ روز بعد") }
            )
            FilterChip(
                selected = false,
                onClick = { onChange(jalaliText(System.currentTimeMillis() + 30 * 86_400_000L)) },
                label = { Text("۳۰ روز بعد") }
            )
        }
    }
}

@Composable
fun AccountChips(accounts: List<Account>, selected: Long, onSelect: (Long) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        accounts.forEach { a ->
            FilterChip(
                selected = selected == a.id,
                onClick = { onSelect(a.id) },
                label = { Text(a.name) }
            )
        }
    }
}

fun dueColor(diff: Int): Color = when {
    diff < 0 -> ExpenseColor
    diff <= 1 -> Color(0xFFF59E0B)
    else -> Color.Unspecified
}

// ---------- چک و قسط ----------

@Composable
fun ChequeLoanScreen(vm: MainVm) {
    var mode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("چک‌ها") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("اقساط وام") })
        }
        if (mode == 0) ChequeSection(vm) else LoanSection(vm)
    }
}

@Composable
fun ChequeCard(c: Cheque, onPass: () -> Unit, onBounce: () -> Unit, onDelete: () -> Unit) {
    val diff = daysUntil(c.dueDate)
    val color = if (c.issued) ExpenseColor else IncomeColor
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.party, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (c.issued) "چک صادره" else "چک دریافتی",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(fmt(c.amount), color = color, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDelete) { Text("✕") }
            }
            AmountWords(c.amount)
            Text(
                "سررسید: ${jalaliLabel(c.dueDate)}" + (if (c.status == 0) " · ${dueText(diff)}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (c.status == 0) dueColor(diff) else Color.Unspecified
            )
            if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall)
            if (c.status == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPass) { Text(if (c.issued) "پرداخت شد" else "وصول شد") }
                    OutlinedButton(onClick = onBounce) { Text("برگشت خورد") }
                }
            } else {
                Text(
                    if (c.status == 1) "✓ انجام شد" else "✗ برگشت خورد",
                    fontWeight = FontWeight.SemiBold,
                    color = if (c.status == 1) IncomeColor else ExpenseColor
                )
            }
        }
    }
}

@Composable
fun ChequeSection(vm: MainVm) {
    val cheques by vm.cheques.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }
    var issued by remember { mutableStateOf(true) }
    var party by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayJalali()) }
    var dateErr by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var passing by remember { mutableStateOf<Cheque?>(null) }
    var pickAcc by remember { mutableStateOf(0L) }

    val pending = cheques.filter { it.status == 0 }.sortedBy { it.dueDate }
    val done = cheques.filter { it.status != 0 }.sortedByDescending { it.dueDate }
    val issuedSum = pending.filter { it.issued }.sumOf { it.amount }
    val receivedSum = pending.filter { !it.issued }.sumOf { it.amount }

    PageColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("صادره در انتظار", shortAmount(issuedSum), ExpenseColor, Modifier.weight(1f))
            StatCard("دریافتی در انتظار", shortAmount(receivedSum), IncomeColor, Modifier.weight(1f))
        }
        if (pending.isEmpty()) {
            Text(
                "چک در انتظاری نداری.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        pending.forEach { c ->
            ChequeCard(
                c,
                onPass = { passing = c; pickAcc = 0L },
                onBounce = { vm.settleCheque(c, false, 0L) },
                onDelete = { vm.delCheque(c) }
            )
        }

        SectionCard("ثبت چک جدید") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = issued, onClick = { issued = true },
                    label = { Text("صادره (من داده‌ام)") }
                )
                FilterChip(
                    selected = !issued, onClick = { issued = false },
                    label = { Text("دریافتی") }
                )
            }
            LineField(party, { party = it }, if (issued) "در وجه (به نام چه کسی)" else "از طرف چه کسی")
            NumField(amount, { amount = it }, "مبلغ (تومان)")
            AmountHint(amount)
            DateField(dateText, dateErr, "تاریخ سررسید (شمسی)") { dateText = it; dateErr = false }
            LineField(note, { note = it }, "توضیح (اختیاری)")
            Button(onClick = {
                val a = amount.faToLong()
                val d = parseJalali(dateText)
                if (d == null) {
                    dateErr = true
                } else if (party.isNotBlank() && a != null && a > 0) {
                    vm.addCheque(party.trim(), a, d, issued, note.trim())
                    party = ""; amount = ""; note = ""; dateText = todayJalali()
                }
            }) { Text("ثبت چک") }
        }

        if (done.isNotEmpty()) {
            Text("سابقه", style = MaterialTheme.typography.titleMedium)
            done.forEach { c -> ChequeCard(c, {}, {}, { vm.delCheque(c) }) }
        }
    }

    passing?.let { c ->
        val chosen = if (usable.any { it.id == pickAcc }) pickAcc else (usable.firstOrNull()?.id ?: 0L)
        AlertDialog(
            onDismissRequest = { passing = null },
            title = { Text(if (c.issued) "پرداخت چک صادره" else "وصول چک دریافتی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${fmt(c.amount)} تومان ${if (c.issued) "از" else "به"} کدام حساب؟")
                    if (usable.isEmpty()) {
                        Text("حسابی نداری؛ حساب «کیف پول» ساخته می‌شود.")
                    } else {
                        AccountChips(usable, chosen) { pickAcc = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.settleCheque(c, true, chosen)
                    passing = null
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { passing = null }) { Text("انصراف") } }
        )
    }
}

@Composable
fun LoanCard(l: Loan, onPay: () -> Unit, onDelete: () -> Unit) {
    val due = loanDue(l)
    val diff = if (due != null) daysUntil(due) else 0
    val frac = if (l.totalCount > 0) l.paid.toFloat() / l.totalCount else 0f
    val remaining = (l.totalCount - l.paid).coerceAtLeast(0) * l.installment
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(fmt(l.installment), fontWeight = FontWeight.Bold)
                TextButton(onClick = onDelete) { Text("✕") }
            }
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Text(
                "${l.paid} قسط از ${l.totalCount} پرداخت شده · مانده کل: ${fmt(remaining)} تومان",
                style = MaterialTheme.typography.bodySmall
            )
            if (due != null) {
                Text(
                    "قسط بعدی: ${jalaliLabel(due)} · ${dueText(diff)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = dueColor(diff)
                )
                Button(onClick = onPay) { Text("پرداخت قسط") }
            } else {
                Text("✓ وام تسویه شد", color = IncomeColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun LoanSection(vm: MainVm) {
    val loans by vm.loans.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }
    var title by remember { mutableStateOf("") }
    var instText by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("") }
    var paidCount by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayJalali()) }
    var dateErr by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Loan?>(null) }
    var pickAcc by remember { mutableStateOf(0L) }

    val active = loans.filter { it.paid < it.totalCount }
    val finished = loans.filter { it.paid >= it.totalCount }
    val remainingTotal = active.sumOf { (it.totalCount - it.paid) * it.installment }
    val monthly = active.sumOf { it.installment }

    PageColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("مانده‌ی کل اقساط", shortAmount(remainingTotal), ExpenseColor, Modifier.weight(1f))
            StatCard("جمع اقساط ماهانه", shortAmount(monthly), ExpenseColor, Modifier.weight(1f))
        }
        AmountWords(remainingTotal)
        if (active.isEmpty()) {
            Text(
                "وام یا قسط فعالی نداری.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        active.forEach { l ->
            LoanCard(l, onPay = { paying = l; pickAcc = 0L }, onDelete = { vm.delLoan(l) })
        }

        SectionCard("ثبت وام یا اقساط جدید") {
            LineField(title, { title = it }, "عنوان (مثلاً وام مسکن، قسط گوشی)")
            NumField(instText, { instText = it }, "مبلغ هر قسط (تومان)")
            AmountHint(instText)
            NumField(count, { count = it }, "تعداد کل اقساط")
            NumField(paidCount, { paidCount = it }, "چند قسط تا الان پرداخت شده؟ (اختیاری)")
            DateField(dateText, dateErr, "سررسید قسط اول (شمسی)") { dateText = it; dateErr = false }
            Text(
                "قسط‌ها هر ماه در همین روز سررسید می‌شوند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                val a = instText.faToLong()
                val n = digits(count).trim().toIntOrNull()
                val p = digits(paidCount).trim().toIntOrNull() ?: 0
                val d = parseJalali(dateText)
                if (d == null) {
                    dateErr = true
                } else if (title.isNotBlank() && a != null && a > 0 && n != null && n > 0 && p in 0..n) {
                    vm.addLoan(title.trim(), a, n, p, d)
                    title = ""; instText = ""; count = ""; paidCount = ""; dateText = todayJalali()
                }
            }) { Text("ثبت وام") }
        }

        if (finished.isNotEmpty()) {
            Text("تسویه‌شده‌ها", style = MaterialTheme.typography.titleMedium)
            finished.forEach { l -> LoanCard(l, {}, { vm.delLoan(l) }) }
        }
    }

    paying?.let { l ->
        val chosen = if (usable.any { it.id == pickAcc }) pickAcc else (usable.firstOrNull()?.id ?: 0L)
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("پرداخت قسط ${l.paid + 1} از ${l.totalCount}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${fmt(l.installment)} تومان از کدام حساب پرداخت شود؟")
                    if (usable.isEmpty()) {
                        Text("حسابی نداری؛ حساب «کیف پول» ساخته می‌شود.")
                    } else {
                        AccountChips(usable, chosen) { pickAcc = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.payInstallment(l, chosen)
                    paying = null
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("انصراف") } }
        )
    }
}

// ---------- طلب و بدهی افراد ----------

@Composable
fun PersonCard(p: PersonDebt, onPay: () -> Unit, onDelete: () -> Unit) {
    val remaining = p.amount - p.paid
    val c = if (p.owedToMe) IncomeColor else ExpenseColor
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.person, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (p.owedToMe) "از او طلب دارم" else "به او بدهکارم",
                        style = MaterialTheme.typography.bodySmall,
                        color = c
                    )
                }
                Text(fmt(remaining), color = c, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDelete) { Text("✕") }
            }
            if (p.paid > 0) {
                Text(
                    "از ${fmt(p.amount)} تومان، ${fmt(p.paid)} تسویه شده",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall)
            Text(
                jalaliLabel(p.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (remaining > 0) {
                OutlinedButton(onClick = onPay) { Text("ثبت پرداخت / تسویه") }
            } else {
                Text("✓ تسویه شد", color = IncomeColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PeopleScreen(vm: MainVm) {
    val list by vm.people.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }

    var owedToMe by remember { mutableStateOf(true) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayJalali()) }
    var dateErr by remember { mutableStateOf(false) }

    var paying by remember { mutableStateOf<PersonDebt?>(null) }
    var payText by remember { mutableStateOf("") }
    var payAcc by remember { mutableStateOf(0L) }

    val open = list.filter { it.paid < it.amount }
    val settled = list.filter { it.paid >= it.amount }
    val receivable = open.filter { it.owedToMe }.sumOf { it.amount - it.paid }
    val payable = open.filter { !it.owedToMe }.sumOf { it.amount - it.paid }
    val perPerson = open.groupBy { it.person }
        .map { (k, v) -> k to v.sumOf { if (it.owedToMe) it.amount - it.paid else -(it.amount - it.paid) } }
        .sortedByDescending { abs(it.second) }

    PageColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("طلب من", shortAmount(receivable), IncomeColor, Modifier.weight(1f))
            StatCard("بدهی من", shortAmount(payable), ExpenseColor, Modifier.weight(1f))
            StatCard(
                "تراز", shortAmount(receivable - payable),
                if (receivable - payable >= 0) IncomeColor else ExpenseColor, Modifier.weight(1f)
            )
        }
        Text(
            "این مبالغ در ثروت خالص حساب نمی‌شوند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (perPerson.size > 1) {
            SectionCard("خلاصه به تفکیک افراد") {
                perPerson.forEach { (name, net) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name)
                        Text(
                            if (net >= 0) "طلب ${shortAmount(net)}" else "بدهی ${shortAmount(-net)}",
                            color = if (net >= 0) IncomeColor else ExpenseColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (open.isEmpty()) {
            Text(
                "طلب یا بدهی بازی نداری.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        open.forEach { p ->
            PersonCard(p, onPay = { paying = p; payText = ""; payAcc = 0L }, onDelete = { vm.delPerson(p) })
        }

        SectionCard("ثبت طلب یا بدهی") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = owedToMe, onClick = { owedToMe = true },
                    label = { Text("از او طلب دارم") }
                )
                FilterChip(
                    selected = !owedToMe, onClick = { owedToMe = false },
                    label = { Text("به او بدهکارم") }
                )
            }
            LineField(person, { person = it }, "نام شخص")
            NumField(amount, { amount = it }, "مبلغ (تومان)")
            AmountHint(amount)
            LineField(note, { note = it }, "توضیح (اختیاری)")
            DateField(dateText, dateErr, "تاریخ (شمسی)") { dateText = it; dateErr = false }
            Button(onClick = {
                val a = amount.faToLong()
                val d = parseJalali(dateText)
                if (d == null) {
                    dateErr = true
                } else if (person.isNotBlank() && a != null && a > 0) {
                    vm.addPerson(person.trim(), a, owedToMe, note.trim(), d)
                    person = ""; amount = ""; note = ""; dateText = todayJalali()
                }
            }) { Text("ثبت") }
        }

        if (settled.isNotEmpty()) {
            Text("تسویه‌شده‌ها", style = MaterialTheme.typography.titleMedium)
            settled.forEach { p -> PersonCard(p, {}, { vm.delPerson(p) }) }
        }
    }

    paying?.let { p ->
        val remaining = p.amount - p.paid
        val chosenAcc = if (usable.any { it.id == payAcc }) payAcc else (usable.firstOrNull()?.id ?: 0L)
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text(p.person) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مانده: ${fmt(remaining)} تومان")
                    NumField(payText, { payText = it }, "مبلغ پرداخت‌/وصول‌شده (تومان)")
                    AmountHint(payText)
                    if (usable.isNotEmpty()) {
                        Text("حساب بانکی / نقدی (برای اعمال در موجودی)", style = MaterialTheme.typography.bodySmall)
                        AccountChips(usable, chosenAcc) { payAcc = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = payText.faToLong()
                    if (v != null && v > 0) vm.payPerson(p, v, chosenAcc)
                    paying = null
                }) { Text("ثبت جزئی") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.payPerson(p, remaining, chosenAcc)
                    paying = null
                }) { Text("تسویه کامل") }
            }
        )
    }
}

// ---------- تراکنش تکراری و اشتراک ----------

@Composable
fun RecurringScreen(vm: MainVm) {
    val list by vm.recurring.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }
    val accName = accounts.associate { it.id to it.name }

    var isIncome by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var autoPost by remember { mutableStateOf(true) }
    var pickAcc by remember { mutableStateOf(0L) }
    val accId = if (usable.any { it.id == pickAcc }) pickAcc else (usable.firstOrNull()?.id ?: 0L)

    var postingItem by remember { mutableStateOf<Recurring?>(null) }
    var postAcc by remember { mutableStateOf(0L) }

    val monthlyExp = list.filter { it.active && !it.isIncome }.sumOf { it.amount }
    val monthlyInc = list.filter { it.active && it.isIncome }.sumOf { it.amount }

    PageColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("هزینه‌ی ثابت ماهانه", shortAmount(monthlyExp), ExpenseColor, Modifier.weight(1f))
            StatCard("جمع سالانه", shortAmount(monthlyExp * 12), ExpenseColor, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("درآمد ثابت ماهانه", shortAmount(monthlyInc), IncomeColor, Modifier.weight(1f))
            StatCard(
                "خالص ماهانه", shortAmount(monthlyInc - monthlyExp),
                if (monthlyInc - monthlyExp >= 0) IncomeColor else ExpenseColor, Modifier.weight(1f)
            )
        }
        Text(
            "جمع سالانه‌ی هزینه‌های ثابت:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AmountWords(monthlyExp * 12)

        if (list.isEmpty()) {
            Text(
                "هنوز موردی اضافه نکرده‌ای. مثلاً اجاره، اینترنت یا حقوق ماهانه.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        list.forEach { r ->
            val c = if (r.isIncome) IncomeColor else ExpenseColor
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${if (r.isIncome) "درآمد" else "هزینه"} · هر ماه روز ${r.day}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(fmt(r.amount), color = c, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { vm.delRecurring(r) }) { Text("✕") }
                    }
                    AmountWords(r.amount)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = r.active,
                            onCheckedChange = { vm.setRecurringActive(r, it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (r.autoPost) "ثبت خودکار تراکنش" + (accName[r.accountId]?.let { " (حساب $it)" } ?: "")
                            else "فقط در فهرست (بدون ثبت تراکنش)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = { postingItem = r; postAcc = r.accountId }) {
                        Text("ثبت در تراکنش‌ها (هم‌اکنون)")
                    }
                }
            }
        }

        SectionCard("مورد جدید") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isIncome, onClick = { isIncome = false },
                    label = { Text("هزینه / اشتراک") }
                )
                FilterChip(
                    selected = isIncome, onClick = { isIncome = true },
                    label = { Text("درآمد ثابت") }
                )
            }
            LineField(title, { title = it }, "عنوان (مثلاً اجاره، اینترنت، نتفلیکس)")
            NumField(amount, { amount = it }, "مبلغ ماهانه (تومان)")
            AmountHint(amount)
            NumField(day, { day = it }, "روز ماه (۱ تا ۳۱)")
            LineField(category, { category = it }, "دسته (اختیاری)")
            if (usable.isEmpty()) {
                Text(
                    "حسابی نداری؛ هنگام اولین ثبت، حساب «کیف پول» ساخته می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("حساب", style = MaterialTheme.typography.bodySmall)
                AccountChips(usable, accId) { pickAcc = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoPost, onCheckedChange = { autoPost = it })
                Spacer(Modifier.width(8.dp))
                Text("ثبت خودکار تراکنش هر ماه")
            }
            Text(
                "اگر خاموش باشد، فقط در فهرست و جمع سالانه می‌آید (مناسب اشتراک‌هایی که خودت پرداخت می‌کنی).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                val a = amount.faToLong()
                val d = digits(day).trim().toIntOrNull()
                if (title.isNotBlank() && a != null && a > 0 && d != null && d in 1..31) {
                    vm.addRecurring(title.trim(), a, isIncome, category.trim(), accId, d, autoPost)
                    title = ""; amount = ""; day = ""; category = ""
                }
            }) { Text("افزودن") }
        }
    }

    postingItem?.let { r ->
        val chosenAcc = if (usable.any { it.id == postAcc }) postAcc else (usable.firstOrNull()?.id ?: 0L)
        AlertDialog(
            onDismissRequest = { postingItem = null },
            title = { Text("ثبت تراکنش: ${r.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${fmt(r.amount)} تومان از کدام حساب کسر/اضافه شود؟")
                    if (usable.isNotEmpty()) {
                        AccountChips(usable, chosenAcc) { postAcc = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.postRecurringNow(r, chosenAcc)
                    postingItem = null
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { postingItem = null }) { Text("انصراف") } }
        )
    }
}

// ---------- انتقال بین حساب‌ها و خروجی اکسل ----------

@Composable
fun ToolsScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val transfers by vm.transfers.collectAsState(initial = emptyList())
    val txns by vm.txns.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }
    val accName = accounts.associate { it.id to it.name }
    var fromId by remember { mutableStateOf(0L) }
    var toId by remember { mutableStateOf(0L) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayJalali()) }
    var dateErr by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val from = if (usable.any { it.id == fromId }) fromId else (usable.firstOrNull()?.id ?: 0L)
    val to = if (toId != from && usable.any { it.id == toId }) toId
    else (usable.firstOrNull { it.id != from }?.id ?: 0L)

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val csv = buildCsv(txns, accName)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(csv.toByteArray()) }
                }
                msg = "فایل ذخیره شد. آن را با اکسل یا Google Sheets باز کن."
            } catch (e: Exception) {
                msg = "خطا: ${e.message}"
            }
        }
    }

    PageColumn {
        if (msg.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) { Text(msg, Modifier.padding(12.dp)) }
        }

        SectionCard("انتقال بین حساب‌ها") {
            if (usable.size < 2) {
                Text(
                    "برای انتقال، حداقل دو حساب نقد، بانک یا بدهی لازم است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("از حساب", style = MaterialTheme.typography.bodySmall)
                AccountChips(usable, from) { fromId = it }
                Text("به حساب", style = MaterialTheme.typography.bodySmall)
                AccountChips(usable, to) { toId = it }
                NumField(amount, { amount = it }, "مبلغ (تومان)")
                AmountHint(amount)
                DateField(dateText, dateErr, "تاریخ (شمسی)") { dateText = it; dateErr = false }
                LineField(note, { note = it }, "توضیح (اختیاری)")
                Text(
                    "پرداخت بدهی کارت اعتباری از حساب بانکی هم همین‌جا ثبت می‌شود: مقصد را حساب بدهی انتخاب کن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    val a = amount.faToLong()
                    val d = parseJalali(dateText)
                    if (d == null) {
                        dateErr = true
                    } else if (a != null && a > 0 && from != 0L && to != 0L && from != to) {
                        vm.transfer(from, to, a, note.trim(), d)
                        amount = ""; note = ""; dateText = todayJalali()
                        msg = "انتقال ثبت شد"
                    }
                }) { Text("انتقال") }
            }
        }

        if (transfers.isNotEmpty()) {
            Text("انتقال‌های اخیر", style = MaterialTheme.typography.titleMedium)
            transfers.take(15).forEach { t ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("از ${accName[t.fromId] ?: "؟"} به ${accName[t.toId] ?: "؟"}")
                            Text(
                                jalaliLabel(t.date) + (if (t.note.isNotBlank()) " · ${t.note}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(fmt(t.amount), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { vm.delTransfer(t) }) { Text("✕") }
                    }
                }
            }
        }

        SectionCard("خروجی اکسل (CSV)") {
            Text(
                "همه‌ی ${txns.size} تراکنش با تاریخ شمسی، نوع، مبلغ، دسته، توضیح و حساب.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { export.launch("transactions.csv") }) { Text("ذخیره‌ی فایل CSV") }
        }
    }
}
