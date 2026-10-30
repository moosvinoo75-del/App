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
    var editName by remember { mutableStateOf("") }
    var editA by remember { mutableStateOf("") }
    var editB by remember { mutableStateOf("") }

    // با هر تغییر حساب‌ها در دیتابیس، خودکار دوباره حساب می‌شود
    val w = remember(accounts) { computeWealth(accounts) }

    val priced = type in PRICED_TYPES
    val newQty = parseQty(qty)
    val newPrice = price.faToLong()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArr
