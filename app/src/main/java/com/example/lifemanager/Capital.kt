package com.example.lifemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

val ACCOUNT_TYPES = listOf("نقد", "بانک", "طلا", "ارز", "سهام", "بدهی")

fun compact(v: Long): String = when {
    v >= 1_000_000 -> "${v / 1_000_000}م"
    v >= 1_000 -> "${v / 1_000}ه"
    else -> "$v"
}

@Composable
fun FinanceHost(vm: MainVm) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = sub == 0, onClick = { sub = 0 }, label = { Text("تراکنش‌ها") })
            FilterChip(selected = sub == 1, onClick = { sub = 1 }, label = { Text("سرمایه و نمودار") })
        }
        if (sub == 0) TxnScreen(vm) else CapitalScreen(vm)
    }
}

@Composable
fun BarRow(label: String, value: Long, max: Long, color: Color) {
    val frac = if (max > 0) (value.toFloat() / max).coerceIn(0.02f, 1f) else 0.02f
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(fmt(value), style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .background(Color(0x1F000000), RoundedCornerShape(5.dp))
        ) {
            Box(
                Modifier.fillMaxWidth(frac).fillMaxHeight()
                    .background(color, RoundedCornerShape(5.dp))
            )
        }
    }
}

@Composable
fun CapitalScreen(vm: MainVm) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val txns by vm.txns.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ACCOUNT_TYPES[0]) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var editText by remember { mutableStateOf("") }

    val assets = accounts.filter { it.type != "بدهی" }.sumOf { it.balance }
    val debts = accounts.filter { it.type == "بدهی" }.sumOf { it.balance }
    val net = assets - debts

    val byType = accounts.filter { it.type != "بدهی" }
        .groupBy { it.type }
        .map { (k, v) -> k to v.sumOf { it.balance } }
        .sortedByDescending { it.second }

    val monthAgo = System.currentTimeMillis() - 30L * 86_400_000L
    val byCat = txns.filter { !it.isIncome && it.date >= monthAgo }
        .groupBy { it.category.ifBlank { "بدون دسته" } }
        .map { (k, v) -> k to v.sumOf { it.amount } }
        .sortedByDescending { it.second }
        .take(8)

    val weekNames = listOf("", "ی", "د", "س", "چ", "پ", "ج", "ش")
    val week = (6 downTo 0).map { back ->
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -back)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        val end = start + 86_400_000L
        val sum = txns.filter { !it.isIncome && it.date >= start && it.date < end }.sumOf { it.amount }
        weekNames[c.get(Calendar.DAY_OF_WEEK)] to sum
    }
    val weekMax = week.maxOfOrNull { it.second } ?: 0L

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ثروت خالص: ${fmt(net)}", style = MaterialTheme.typography.titleLarge)
                Text("دارایی‌ها: ${fmt(assets)}")
                Text("بدهی‌ها: ${fmt(debts)}")
            }
        }

        if (accounts.isEmpty()) {
            Text("هنوز حسابی اضافه نکرده‌ای.", style = MaterialTheme.typography.bodySmall)
        }
        accounts.forEach { a ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name)
                        Text(a.type, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        fmt(a.balance),
                        color = if (a.type == "بدهی") Color(0xFFC62828) else Color.Unspecified
                    )
                    TextButton(onClick = { editing = a; editText = a.balance.toString() }) { Text("ویرایش") }
                    TextButton(onClick = { vm.delAccount(a) }) { Text("حذف") }
                }
            }
        }

        Text("حساب جدید", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("نام (مثلاً کیف پول)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = balance, onValueChange = { balance = it },
            label = { Text("موجودی / ارزش") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ACCOUNT_TYPES.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
            }
        }
        Button(onClick = {
            val b = balance.faToLong()
            if (name.isNotBlank() && b != null) {
                vm.addAccount(name.trim(), type, b)
                name = ""
                balance = ""
            }
        }) { Text("افزودن حساب") }

        HorizontalDivider()
        Text("دارایی‌ها بر اساس نوع", style = MaterialTheme.typography.titleMedium)
        if (byType.isEmpty()) {
            Text("—")
        } else {
            val maxType = byType.first().second
            byType.forEach { BarRow(it.first, it.second, maxType, MaterialTheme.colorScheme.primary) }
        }

        HorizontalDivider()
        Text("هزینه‌ها بر اساس دسته (۳۰ روز اخیر)", style = MaterialTheme.typography.titleMedium)
        if (byCat.isEmpty()) {
            Text("—")
        } else {
            val maxCat = byCat.first().second
            byCat.forEach { BarRow(it.first, it.second, maxCat, MaterialTheme.colorScheme.error) }
        }

        HorizontalDivider()
        Text("هزینه‌ی ۷ روز اخیر", style = MaterialTheme.typography.titleMedium)
        Text("ه = هزار، م = میلیون", style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.fillMaxWidth().height(160.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            week.forEach { (label, v) ->
                val frac = if (weekMax > 0) v.toFloat() / weekMax else 0f
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(compact(v), style = MaterialTheme.typography.bodySmall)
                    Box(
                        Modifier.width(24.dp).height(maxOf(2f, 100f * frac).dp)
                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                    )
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    editing?.let { acc ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(acc.name) },
            text = {
                OutlinedTextField(
                    value = editText, onValueChange = { editText = it },
                    label = { Text("موجودی جدید") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editText.faToLong()?.let { vm.updateAccount(acc.copy(balance = it)) }
                    editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("انصراف") } }
        )
    }
}
