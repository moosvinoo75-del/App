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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val ACCOUNT_TYPES = listOf("نقد", "بانک", "طلا", "ارز", "سهام", "بدهی")

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
fun AccountsScreen(vm: MainVm) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ACCOUNT_TYPES[0]) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var editA by remember { mutableStateOf("") }
    var editB by remember { mutableStateOf("") }

    // با هر تغییر حساب‌ها در دیتابیس، خودکار دوباره حساب می‌شود
    val w = remember(accounts) { computeWealth(accounts) }

    val priced = type in PRICED_TYPES
    val newQty = parseQty(qty)
    val newPrice = price.faToLong()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HeroCard(
            "ثروت خالص", w.net,
            "دارایی‌ها", fmt(w.assets),
            "بدهی‌ها", fmt(w.debts)
        )
        Text(
            "ثروت خالص = ارزش همه‌ی دارایی‌ها − بدهی‌ها",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AmountWords(w.net)

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
                        Text(
                            if (a.isPriced())
                                "${a.type} · ${qtyText(a.quantity)} ${unitName(a.type)} × ${fmt(a.unitPrice)}"
                            else a.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        fmt(a.value()),
                        color = if (a.type == "بدهی") Color(0xFFC62828) else Color.Unspecified
                    )
                    TextButton(onClick = {
                        editing = a
                        if (a.type in PRICED_TYPES) {
                            editA = if (a.quantity > 0) qtyText(a.quantity) else ""
                            editB = if (a.unitPrice > 0) a.unitPrice.toString() else ""
                        } else {
                            editA = a.balance.toString()
                            editB = ""
                        }
                    }) { Text("ویرایش") }
                    TextButton(onClick = { vm.delAccount(a) }) { Text("✕") }
                }
            }
        }

        Text("حساب جدید", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ACCOUNT_TYPES.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("نام (مثلاً کیف پول، سکه‌های خانه)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (priced) {
            OutlinedTextField(
                value = qty, onValueChange = { qty = it },
                label = { Text(qtyLabel(type)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = price, onValueChange = { price = it },
                label = { Text(priceLabel(type)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            AmountHint(price)
            if (newQty != null && newPrice != null) {
                val v = Math.round(newQty * newPrice)
                Text("ارزش کل: ${fmt(v)} تومان", fontWeight = FontWeight.SemiBold)
                AmountWords(v)
            }
        } else {
            OutlinedTextField(
                value = balance, onValueChange = { balance = it },
                label = { Text(if (type == "بدهی") "مبلغ بدهی" else "موجودی") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            AmountHint(balance)
        }
        Button(onClick = {
            if (name.isNotBlank()) {
                if (priced) {
                    if (newQty != null && newPrice != null) {
                        vm.addAccount(name.trim(), type, 0L, newQty, newPrice)
                        name = ""; qty = ""; price = ""
                    }
                } else {
                    val b = balance.faToLong()
                    if (b != null) {
                        vm.addAccount(name.trim(), type, b, 0.0, 0L)
                        name = ""; balance = ""
                    }
                }
            }
        }) { Text("افزودن حساب") }

        HorizontalDivider()
        Text("دارایی‌ها بر اساس نوع", style = MaterialTheme.typography.titleMedium)
        if (w.byType.isEmpty()) {
            Text("—")
        } else {
            val maxType = w.byType.first().second
            w.byType.forEach { BarRow(it.first, it.second, maxType, MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(16.dp))
    }

    editing?.let { acc ->
        val isP = acc.type in PRICED_TYPES
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(acc.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isP) {
                        OutlinedTextField(
                            value = editA, onValueChange = { editA = it },
                            label = { Text(qtyLabel(acc.type)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = editB, onValueChange = { editB = it },
                            label = { Text(priceLabel(acc.type)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        AmountHint(editB)
                    } else {
                        OutlinedTextField(
                            value = editA, onValueChange = { editA = it },
                            label = { Text("موجودی جدید") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        AmountHint(editA)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isP) {
                        val q = parseQty(editA)
                        val p = editB.faToLong()
                        if (q != null && p != null) vm.updateAccount(acc.copy(quantity = q, unitPrice = p))
                    } else {
                        editA.faToLong()?.let { vm.updateAccount(acc.copy(balance = it)) }
                    }
                    editing = null
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("انصراف") } }
        )
    }
}
