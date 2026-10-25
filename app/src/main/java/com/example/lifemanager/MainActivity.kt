package com.example.lifemanager

import android.app.Application
import android.content.Context
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

// ---------- دیتابیس ----------

@Entity(tableName = "transactions")
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val isIncome: Boolean,
    val category: String,
    val note: String,
    val date: Long
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false
)

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun all(): Flow<List<Txn>>
    @Insert suspend fun insert(t: Txn)
    @Delete suspend fun delete(t: Txn)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done, id DESC")
    fun all(): Flow<List<TaskItem>>
    @Insert suspend fun insert(t: TaskItem)
    @Update suspend fun update(t: TaskItem)
    @Delete suspend fun delete(t: TaskItem)
}

@Database(entities = [Txn::class, TaskItem::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "life.db")
                .build().also { inst = it }
        }
    }
}

// ---------- ViewModel ----------

class MainVm(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    val txns = db.txnDao().all()
    val tasks = db.taskDao().all()

    fun addTxn(amount: Long, income: Boolean, category: String, note: String) =
        viewModelScope.launch {
            db.txnDao().insert(
                Txn(amount = amount, isIncome = income, category = category,
                    note = note, date = System.currentTimeMillis())
            )
        }
    fun delTxn(t: Txn) = viewModelScope.launch { db.txnDao().delete(t) }

    fun addTask(title: String) = viewModelScope.launch { db.taskDao().insert(TaskItem(title = title)) }
    fun toggle(t: TaskItem) = viewModelScope.launch { db.taskDao().update(t.copy(done = !t.done)) }
    fun delTask(t: TaskItem) = viewModelScope.launch { db.taskDao().delete(t) }
}

// ---------- ابزار ----------

fun fmt(n: Long): String = String.format(Locale.US, "%,d", n)

fun String.faToLong(): Long? {
    val sb = StringBuilder()
    for (c in this) {
        sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
    }
    return sb.toString().replace(",", "").trim().toLongOrNull()
}

// ---------- رابط کاربری ----------

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
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("💰") }, label = { Text("مالی") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("✅") }, label = { Text("کارها") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (tab == 0) TxnScreen(vm) else TaskScreen(vm)
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
