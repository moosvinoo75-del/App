package com.example.lifemanager

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

        // اجازه‌ی اعلان (اندروید ۱۳ به بالا)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // بررسی سررسیدها و ثبت تراکنش‌های تکراری، هر ۱۲ ساعت در پس‌زمینه
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "due-reminders", ExistingPeriodicWorkPolicy.KEEP, req
        )

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
                0 -> FinanceRoot(vm)
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
