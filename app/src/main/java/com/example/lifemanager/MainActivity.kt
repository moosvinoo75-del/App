package com.example.lifemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            MaterialTheme {
                if (crash != null) {
                    Text(
                        crash.take(1800),
                        Modifier.statusBarsPadding().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
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
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon) }, label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
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

@Composable
fun TxnScreen(vm: MainVm) {
    val list by vm.txns.collectAsState(initial = emptyList())
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val income = list.filter { it.isIncome }.sumOf { it.amount }
    val expense = list.filter { !it.isIncome }.sumOf { it.amount }

    fun add(isIncome: Boolean) {
        val a = amount.faToLong() ?: return
        if (a <= 0) return
        vm.addTxn(a, isIncome, category.trim(), note.trim())
        amount = ""; category = ""; note = ""
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("موجودی: ${fmt(income - expense)}", style = MaterialTheme.typography.titleLarge)
                    Text("درآمد: ${fmt(income)}")
                    Text("هزینه: ${fmt(expense)}")
                }
            }
        }
        item {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("مبلغ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = category, onValueChange = { category = it },
                label = { Text("دسته‌بندی (مثلاً خوراک)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("توضیح") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { add(true) }) { Text("＋ درآمد") }
                Button(
                    onClick = { add(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("－ هزینه") }
            }
        }
        items(list, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.category.ifBlank { "بدون دسته" })
                        if (t.note.isNotBlank()) Text(t.note, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        (if (t.isIncome) "+" else "-") + fmt(t.amount),
                        color = if (t.isIncome) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    TextButton(onClick = { vm.delTxn(t) }) { Text("حذف") }
                }
            }
        }
    }
}

@Composable
fun TaskScreen(vm: MainVm) {
    val list by vm.tasks.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
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
        LazyColumn {
            items(list, key = { it.id }) { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = t.done, onCheckedChange = { vm.toggle(t) })
                    Text(
                        t.title, Modifier.weight(1f),
                        textDecoration = if (t.done) TextDecoration.LineThrough else null
                    )
                    TextButton(onClick = { vm.delTask(t) }) { Text("حذف") }
                }
            }
        }
    }
}
