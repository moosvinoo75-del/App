package com.example.lifemanager

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

// priority: 0 عادی، 1 مهم، 2 فوری
// repeatMode: 0 ندارد، 1 روزانه، 2 هفتگی، 3 ماهانه
// dueDate: 0 یعنی بدون تاریخ
// subs: چک‌لیست؛ هر خط «0|متن» یا «1|متن» (1 = انجام شده)
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val done: Boolean = false,
    val dueDate: Long = 0L,
    val hasTime: Boolean = false,
    val priority: Int = 0,
    val category: String = "",
    val repeatMode: Int = 0,
    val remind: Boolean = false,
    val subs: String = "",
    val doneAt: Long = 0L
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY id DESC")
    fun all(): Flow<List<Todo>>
    @Query("SELECT * FROM todos")
    suspend fun list(): List<Todo>
    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): Todo?
    @Insert suspend fun insert(t: Todo): Long
    @Insert suspend fun insertAll(l: List<Todo>)
    @Update suspend fun update(t: Todo)
    @Delete suspend fun delete(t: Todo)
    @Query("DELETE FROM todos WHERE done = 1")
    suspend fun clearDone()
    @Query("DELETE FROM todos")
    suspend fun clear()
}

// ---------- چک‌لیست ----------

fun parseSubs(s: String): List<Pair<Boolean, String>> =
    s.split("\n")
        .filter { it.length >= 2 && it[1] == '|' }
        .map { (it[0] == '1') to it.substring(2) }

fun encodeSubs(l: List<Pair<Boolean, String>>): String =
    l.joinToString("\n") { (if (it.first) "1|" else "0|") + it.second.replace("\n", " ") }

fun resetSubs(s: String): String = encodeSubs(parseSubs(s).map { false to it.second })

// ---------- ساعت و تاریخ ----------

fun clockText(ms: Long): String {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

fun parseTime(s: String): Pair<Int, Int>? {
    val parts = digits(s).trim().replace('.', ':').replace('٫', ':').split(":")
    if (parts.size != 2) return null
    val h = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return Pair(h, m)
}

fun combineDateTime(dateMs: Long, hm: Pair<Int, Int>?): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = dateMs
    if (hm != null) {
        c.set(Calendar.HOUR_OF_DAY, hm.first)
        c.set(Calendar.MINUTE, hm.second)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

fun addJalaliMonth(ms: Long): Long {
    val j = jalaliOf(ms)
    val idx = j[0] * 12 + (j[1] - 1) + 1
    val y = idx / 12
    val m = idx % 12 + 1
    val d = minOf(j[2], daysInJalaliMonth(m))
    val g = jalaliToGregorian(y, m, d)
    val src = Calendar.getInstance()
    src.timeInMillis = ms
    val c = Calendar.getInstance()
    c.set(g[0], g[1] - 1, g[2], src.get(Calendar.HOUR_OF_DAY), src.get(Calendar.MINUTE), 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun addPeriod(ms: Long, mode: Int): Long {
    if (mode == 3) return addJalaliMonth(ms)
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.add(Calendar.DAY_OF_YEAR, if (mode == 2) 7 else 1)
    return c.timeInMillis
}

// موعد بعدی یک کار تکراری (همیشه بعد از امروز)
fun nextDue(t: Todo): Long {
    val today = startOfDay()
    var next = addPeriod(if (t.dueDate > 0) t.dueDate else System.currentTimeMillis(), t.repeatMode)
    var guard = 0
    while (dayStartOf(next) <= today && guard < 400) {
        next = addPeriod(next, t.repeatMode)
        guard++
    }
    return next
}

fun todoDueLabel(t: Todo): String {
    if (t.dueDate <= 0) return ""
    val diff = daysUntil(t.dueDate)
    val day = when (diff) {
        0 -> "امروز"
        1 -> "فردا"
        -1 -> "دیروز"
        else -> jalaliLabel(t.dueDate)
    }
    return if (t.hasTime) "$day ${clockText(t.dueDate)}" else day
}

// ---------- یادآور ----------

fun reminderTime(t: Todo): Long {
    if (t.hasTime) return t.dueDate
    val c = Calendar.getInstance()
    c.timeInMillis = t.dueDate
    c.set(Calendar.HOUR_OF_DAY, 9)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun scheduleTodoReminder(ctx: Context, t: Todo) {
    val wm = WorkManager.getInstance(ctx)
    val name = "todo-${t.id}"
    if (!t.remind || t.done || t.dueDate <= 0) {
        wm.cancelUniqueWork(name)
        return
    }
    val at = reminderTime(t)
    val delay = at - System.currentTimeMillis()
    if (delay <= 0) {
        wm.cancelUniqueWork(name)
        return
    }
    val req = OneTimeWorkRequestBuilder<TodoReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf("id" to t.id, "at" to at))
        .build()
    wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
}

fun showTodoNotification(ctx: Context, t: Todo) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(
        NotificationChannel("todo", "یادآور کارها", NotificationManager.IMPORTANCE_HIGH)
    )
    val pi = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )
    val n = Notification.Builder(ctx, "todo")
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(t.title)
        .setContentText(if (t.note.isNotBlank()) t.note else "یادآور کار")
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()
    nm.notify(2000 + (t.id % 100000).toInt(), n)
}

class TodoReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            val id = inputData.getLong("id", 0L)
            val at = inputData.getLong("at", 0L)
            val t = AppDb.get(applicationContext).todoDao().getById(id)
            if (t != null && !t.done && t.remind && t.dueDate > 0 &&
                kotlin.math.abs(reminderTime(t) - at) < 1000
            ) {
                showTodoNotification(applicationContext, t)
            }
        } catch (_: Exception) {
        }
        return Result.success()
    }
}

// ---------- عملیات روی ViewModel ----------

fun MainVm.saveTodo(t: Todo) = viewModelScope.launch {
    val ctx = getApplication<Application>()
    val id: Long = if (t.id == 0L) {
        db.todoDao().insert(t)
    } else {
        db.todoDao().update(t)
        t.id
    }
    scheduleTodoReminder(ctx, t.copy(id = id))
}

fun MainVm.toggleTodo(t: Todo) = viewModelScope.launch {
    val ctx = getApplication<Application>()
    val u: Todo = when {
        t.done -> t.copy(done = false, doneAt = 0L)
        t.repeatMode != 0 -> t.copy(
            dueDate = nextDue(t), subs = resetSubs(t.subs), doneAt = System.currentTimeMillis()
        )
        else -> t.copy(done = true, doneAt = System.currentTimeMillis())
    }
    db.todoDao().update(u)
    scheduleTodoReminder(ctx, u)
}

fun MainVm.deleteTodo(t: Todo) = viewModelScope.launch {
    db.todoDao().delete(t)
    WorkManager.getInstance(getApplication<Application>()).cancelUniqueWork("todo-${t.id}")
}

fun MainVm.clearDoneTodos() = viewModelScope.launch { db.todoDao().clearDone() }

// ---------- بکاپ ----------

suspend fun AppDb.putAllExtra(o: JSONObject) {
    putExtraJson(o)
    putTodosJson(o)
}

suspend fun AppDb.restoreAll(o: JSONObject) {
    restoreExtra(o)
    restoreTodos(o)
}

suspend fun AppDb.putTodosJson(o: JSONObject) {
    val a = JSONArray()
    todoDao().list().forEach {
        a.put(
            JSONObject().put("title", it.title).put("note", it.note).put("done", it.done)
                .put("dueDate", it.dueDate).put("hasTime", it.hasTime).put("priority", it.priority)
                .put("category", it.category).put("repeatMode", it.repeatMode)
                .put("remind", it.remind).put("subs", it.subs).put("doneAt", it.doneAt)
        )
    }
    o.put("todos", a)
}

suspend fun AppDb.restoreTodos(o: JSONObject) {
    val a = o.optJSONArray("todos")
    val list: List<Todo> = if (a != null) {
        (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            Todo(
                title = j.getString("title"), note = j.optString("note"),
                done = j.optBoolean("done"), dueDate = j.optLong("dueDate", 0L),
                hasTime = j.optBoolean("hasTime"), priority = j.optInt("priority", 0),
                category = j.optString("category"), repeatMode = j.optInt("repeatMode", 0),
                remind = j.optBoolean("remind"), subs = j.optString("subs"),
                doneAt = j.optLong("doneAt", 0L)
            )
        }
    } else {
        // بکاپ قدیمی: فقط عنوان و وضعیت انجام
        val old = o.optJSONArray("tasks") ?: JSONArray()
        (0 until old.length()).map { i ->
            val j = old.getJSONObject(i)
            Todo(title = j.getString("title"), done = j.optBoolean("done"))
        }
    }
    todoDao().clear()
    todoDao().insertAll(list)
}
