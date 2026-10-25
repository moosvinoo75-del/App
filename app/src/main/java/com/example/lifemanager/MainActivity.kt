package com.example.lifemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ذخیره‌ی خطا برای نمایش در اجرای بعدی
        val f = java.io.File(filesDir, "crash.txt")
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                f.writeText(android.util.Log.getStackTraceString(e))
            } catch (_: Throwable) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        val crash = if (f.exists()) f.readText().also { f.delete() } else null

        setContent {
            AppTheme {
                if (crash != null) {
                    Surface(Modifier.fillMaxSize()) {
                        Text(
                            crash.take(1800),
                            Modifier.statusBarsPadding().padding(16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        App()
                    }
                }
            }
        }
    }
}

@Composable
fun App(vm: MainVm = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "💰" to "مالی", "✅" to "کارها", "🍽" to "تغذیه", "🤖" to "دستیار", "⚙️" to "بیشتر"
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(icon, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
            when (tab) {
                0 -> FinanceHost(vm)
                1 -> TaskScreen(vm)
                2 -> FoodScreen(vm)
                3 -> AssistantScreen(vm)
                else -> MoreScreen(vm)
            }
        }
    }
}

// ---------- کارت بزرگ بالای صفحه ----------

@Composable
fun HeroCard(
    title: String, value: Long,
    leftLabel: String, leftValue: String,
    rightLabel: String, rightValue: String
) {
    val brush = Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6)))
    val soft = Color.White.copy(alpha = 0.8f)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(brush).padding(20.dp)
    ) {
        Column {
            Text(title, color = soft, style = MaterialTheme.typography.bodyMedium)
            Text(fmt(value), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(leftLabel, color = soft, style = MaterialTheme.typography.bodySmall)
                    Text(leftValue, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(rightLabel, color = soft, style = MaterialTheme.typography.bodySmall)
                    Text(rightValue, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------- تراکنش‌ها ----------

val CATEGORIES = listOf("خوراک", "حمل‌ونقل", "قبض", "خرید", "درمان", "تفریح", "حقوق", "سایر")

@Composable
fun TxnScreen(vm: MainVm) {
    val list by vm.txns.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayJalali()) }
    var dateErr by remember { mutableStateOf(false) }
    var pickedAcc by remember { mutableStateOf(0L) }

    // فقط حساب‌های نقد/بانک/بدهی؛ طلا و ارز و سهام ارزششان مقدار × قیمت است
    val usable = accounts.filter { it.type !in PRICED_TYPES }
    val accId = if (usable.any { it.id == pickedAcc }) pickedAcc else (usable.firstOrNull()?.id ?: 0L)
    val accName = accounts.associate { it.id to it.name }

    val income = list.filter { it.isIncome }.sumOf { it.amount }
    val expense = list.filter { !it.isIncome }.sumOf { it.amount }

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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard(
                "تراز درآمد و هزینه", income - expense,
                "درآمد", "+" + fmt(income),
                "هزینه", "-" + fmt(expense)
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ثبت تراکنش", style = MaterialTheme.typography.titleMedium)
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
        item { Text("تراکنش‌های اخیر", style = MaterialTheme.typography.titleMedium) }
        if (list.isEmpty()) {
            item {
                Text(
                    "هنوز تراکنشی ثبت نشده 🌱",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(list, key = { it.id }) { t ->
            val c = if (t.isIncome) IncomeColor else ExpenseColor
            Card(Modifier.fillMaxWidth()) {
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
                        Text(
                            jalaliLabel(t.date) + (accName[t.accountId]?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        (if (t.isIncome) "+" else "-") + fmt(t.amount),
                        color = c, fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { vm.delTxn(t) }) { Text("✕") }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------- کارها ----------

@Composable
fun TaskScreen(vm: MainVm) {
    val list by vm.tasks.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    val doneCount = list.count { it.done }
    val frac = if (list.isEmpty()) 0f else doneCount.toFloat() / list.size

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("کارها", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$doneCount از ${list.size} انجام شد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("کار جدید") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (title.isNotBlank()) { vm.addTask(title.trim()); title = "" }
            }) { Text("افزودن") }
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            Text("فعلاً کاری نداری 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = t.done, onCheckedChange = { vm.toggle(t) })
                        Text(
                            t.title, Modifier.weight(1f),
                            textDecoration = if (t.done) TextDecoration.LineThrough else null,
                            color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = { vm.delTask(t) }) { Text("✕") }
                    }
                }
            }
        }
    }
}
