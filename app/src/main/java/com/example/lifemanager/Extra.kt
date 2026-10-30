package com.example.lifemanager

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
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// ---------- جدول‌های جدید ----------

// status: 0 در انتظار، 1 انجام شد (پاس/وصول)، 2 برگشتی
@Entity(tableName = "cheques")
data class Cheque(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val party: String,
    val amount: Long,
    val dueDate: Long,
    val issued: Boolean,
    val status: Int = 0,
    val note: String = ""
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val installment: Long,
    val totalCount: Int,
    val paid: Int,
    val firstDue: Long
)

@Entity(tableName = "person_debts")
data class PersonDebt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amount: Long,
    val paid: Long,
    val owedToMe: Boolean,
    val note: String,
    val date: Long
)

// lastIdx = شماره‌ی آخرین ماهی که ثبت شده (سال × ۱۲ + ماه − ۱)
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val isIncome: Boolean,
    val category: String,
    val accountId: Long,
    val day: Int,
    val lastIdx: Int,
    val autoPost: Boolean,
    val active: Boolean
)

@Entity(tableName = "transfers")
data class Transfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val amount: Long,
    val note: String,
    val date: Long
)

@Dao
interface ChequeDao {
    @Query("SELECT * FROM cheques ORDER BY dueDate")
    fun all(): Flow<List<Cheque>>
    @Query("SELECT * FROM cheques")
    suspend fun list(): List<Cheque>
    @Insert suspend fun insert(c: Cheque)
    @Insert suspend fun insertAll(l: List<Cheque>)
    @Update suspend fun update(c: Cheque)
    @Delete suspend fun delete(c: Cheque)
    @Query("DELETE FROM cheques")
    suspend fun clear()
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY id")
    fun all(): Flow<List<Loan>>
    @Query("SELECT * FROM loans")
    suspend fun list(): List<Loan>
    @Insert suspend fun insert(l: Loan)
    @Insert suspend fun insertAll(l: List<Loan>)
    @Update suspend fun update(l: Loan)
    @Delete suspend fun delete(l: Loan)
    @Query("DELETE FROM loans")
    suspend fun clear()
}

@Dao
interface PersonDebtDao {
    @Query("SELECT * FROM person_debts ORDER BY date DESC")
    fun all(): Flow<List<PersonDebt>>
    @Query("SELECT * FROM person_debts")
    suspend fun list(): List<PersonDebt>
    @Insert suspend fun insert(p: PersonDebt)
    @Insert suspend fun insertAll(l: List<PersonDebt>)
    @Update suspend fun update(p: PersonDebt)
    @Delete suspend fun delete(p: PersonDebt)
    @Query("DELETE FROM person_debts")
    suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY id")
    fun all(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring")
    suspend fun list(): List<Recurring>
    @Insert suspend fun insert(r: Recurring)
    @Insert suspend fun insertAll(l: List<Recurring>)
    @Update suspend fun update(r: Recurring)
    @Delete suspend fun delete(r: Recurring)
    @Query("DELETE FROM recurring")
    suspend fun clear()
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY date DESC")
    fun all(): Flow<List<Transfer>>
    @Query("SELECT * FROM transfers")
    suspend fun list(): List<Transfer>
    @Insert suspend fun insert(t: Transfer)
    @Insert suspend fun insertAll(l: List<Transfer>)
    @Delete suspend fun delete(t: Transfer)
    @Query("DELETE FROM transfers")
    suspend fun clear()
}

// ---------- تاریخ ----------

fun daysInJalaliMonth(m: Int): Int = if (m <= 6) 31 else if (m <= 11) 30 else 29

fun jalaliNoon(y: Int, m: Int, d: Int): Long {
    val g = jalaliToGregorian(y, m, d)
    val c = Calendar.getInstance()
    c.set(g[0], g[1] - 1, g[2], 12, 0, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun dayStartOf(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

// چند روز تا این تاریخ (منفی = گذشته)
fun daysUntil(ms: Long): Int =
    Math.round((dayStartOf(ms) - startOfDay()) / 86_400_000.0).toInt()

fun dueText(diff: Int): String = when {
    diff < 0 -> "${-diff} روز گذشته"
    diff == 0 -> "امروز"
    diff == 1 -> "فردا"
    else -> "$diff روز مانده"
}

// تاریخ سررسید قسط بعدی؛ اگر وام تسویه شده باشد null
fun loanDue(l: Loan): Long? {
    if (l.paid >= l.totalCount) return null
    val j = jalaliOf(l.firstDue)
    val idx = j[0] * 12 + (j[1] - 1) + l.paid
    val y = idx / 12
    val m = idx % 12 + 1
    return jalaliNoon(y, m, minOf(j[2], daysInJalaliMonth(m)))
}

// ---------- اثر تراکنش روی حساب‌ها ----------

suspend fun AppDb.applyToAccount(accId: Long, income: Boolean, amount: Long, reverse: Boolean) {
    if (accId <= 0) return
    val acc = accountDao().getById(accId) ?: return
    var delta = if (income) amount else -amount
    if (reverse) delta = -delta
    val signed = if (acc.type == "بدهی") -delta else delta
    accountDao().update(acc.copy(balance = acc.balance + signed))
}

// ثبت تراکنش و اعمال اثرش؛ حساب نامعتبر → اولین حساب نقد/بانک/بدهی (یا ساخت «کیف پول»)
suspend fun AppDb.postTxn(
    amount: Long, income: Boolean, category: String, note: String,
    date: Long, accountId: Long
) {
    withTransaction {
        val usable = accountDao().list().filter { it.type !in PRICED_TYPES }
        val accId: Long =
            if (accountId > 0 && usable.any { it.id == accountId }) accountId
            else usable.firstOrNull()?.id
                ?: accountDao().insertGetId(Account(name = "کیف پول", type = "نقد", balance = 0))
        applyToAccount(accId, income, amount, false)
        txnDao().insert(
            Txn(
                amount = amount, isIncome = income, category = category,
                note = note, date = date, accountId = accId
            )
        )
    }
}

// ---------- تراکنش‌های تکراری ----------

private fun recurringStartIdx(day: Int): Int {
    val j = jalaliOf(System.currentTimeMillis())
    val curIdx = j[0] * 12 + (j[1] - 1)
    val dayEff = minOf(day, daysInJalaliMonth(j[1]))
    return if (j[2] < dayEff) curIdx - 1 else curIdx
}

// تراکنش‌های تکراریِ سررسیدشده را ثبت می‌کند (هم هنگام باز شدن برنامه، هم در پس‌زمینه)
suspend fun applyRecurringDue(db: AppDb) {
    db.withTransaction {
        val j = jalaliOf(System.currentTimeMillis())
        val curIdx = j[0] * 12 + (j[1] - 1)
        val curDay = j[2]
        for (r in db.recurringDao().list()) {
            if (!r.active || !r.autoPost) continue
            var last = r.lastIdx
            var next = last + 1
            var guard = 0
            while (next <= curIdx && guard < 36) {
                val y = next / 12
                val m = next % 12 + 1
                val day = minOf(r.day, daysInJalaliMonth(m))
                if (next == curIdx && curDay < day) break
                db.postTxn(
                    r.amount, r.isIncome, r.category.ifBlank { r.title }, r.title,
                    jalaliNoon(y, m, day), r.accountId
                )
                last = next
                next++
                guard++
            }
            if (last != r.lastIdx) db.recurringDao().update(r.copy(lastIdx = last))
        }
    }
}

// ---------- عملیات روی ViewModel ----------

fun MainVm.addCheque(party: String, amount: Long, dueDate: Long, issued: Boolean, note: String) =
    viewModelScope.launch {
        db.chequeDao().insert(
            Cheque(party = party, amount = amount, dueDate = dueDate, issued = issued, note = note)
        )
    }

fun MainVm.delCheque(c: Cheque) = viewModelScope.launch { db.chequeDao().delete(c) }

// passed = true: چک پاس/وصول شد و تراکنش ثبت می‌شود؛ false: برگشت خورد
fun MainVm.settleCheque(c: Cheque, passed: Boolean, accountId: Long) = viewModelScope.launch {
    db.withTransaction {
        if (passed) {
            db.postTxn(
                c.amount, !c.issued, "چک",
                (if (c.issued) "چک صادره به " else "چک دریافتی از ") + c.party,
                System.currentTimeMillis(), accountId
            )
        }
        db.chequeDao().update(c.copy(status = if (passed) 1 else 2))
    }
}

fun MainVm.addLoan(title: String, installment: Long, totalCount: Int, paid: Int, firstDue: Long) =
    viewModelScope.launch {
        db.loanDao().insert(
            Loan(
                title = title, installment = installment, totalCount = totalCount,
                paid = paid, firstDue = firstDue
            )
        )
    }

fun MainVm.delLoan(l: Loan) = viewModelScope.launch { db.loanDao().delete(l) }

fun MainVm.payInstallment(l: Loan, accountId: Long) = viewModelScope.launch {
    db.withTransaction {
        if (l.paid < l.totalCount) {
            db.postTxn(
                l.installment, false, "قسط",
                "قسط ${l.paid + 1} از ${l.totalCount} - ${l.title}",
                System.currentTimeMillis(), accountId
            )
            db.loanDao().update(l.copy(paid = l.paid + 1))
        }
    }
}

fun MainVm.addPerson(person: String, amount: Long, owedToMe: Boolean, note: String, date: Long) =
    viewModelScope.launch {
        db.personDebtDao().insert(
            PersonDebt(
                person = person, amount = amount, paid = 0, owedToMe = owedToMe,
                note = note, date = date
            )
        )
    }

fun MainVm.payPerson(p: PersonDebt, delta: Long, accountId: Long) = viewModelScope.launch {
    db.withTransaction {
        if (delta > 0L) {
            val noteText = if (p.owedToMe) "وصول طلب از ${p.person}" else "پرداخت بدهی به ${p.person}"
            db.postTxn(
                amount = delta,
                income = p.owedToMe,
                category = "اشخاص",
                note = noteText + if (p.note.isNotBlank()) " (${p.note})" else "",
                date = System.currentTimeMillis(),
                accountId = accountId
            )
            val newPaid = (p.paid + delta).coerceIn(0L, p.amount)
            db.personDebtDao().update(p.copy(paid = newPaid))
        }
    }
}

fun MainVm.delPerson(p: PersonDebt) = viewModelScope.launch { db.personDebtDao().delete(p) }

fun MainVm.addRecurring(
    title: String, amount: Long, income: Boolean, category: String,
    accountId: Long, day: Int, autoPost: Boolean
) = viewModelScope.launch {
    db.recurringDao().insert(
        Recurring(
            title = title, amount = amount, isIncome = income, category = category,
            accountId = accountId, day = day, lastIdx = recurringStartIdx(day),
            autoPost = autoPost, active = true
        )
    )
}

fun MainVm.payRecurringManual(r: Recurring, accountId: Long) = viewModelScope.launch {
    db.withTransaction {
        db.postTxn(
            r.amount, r.isIncome, r.category.ifBlank { r.title }, r.title,
            System.currentTimeMillis(), if (accountId > 0) accountId else r.accountId
        )
    }
}

fun MainVm.setRecurringActive(r: Recurring, active: Boolean) = viewModelScope.launch {
    if (active) {
        db.recurringDao().update(r.copy(active = true, lastIdx = recurringStartIdx(r.day)))
    } else {
        db.recurringDao().update(r.copy(active = false))
    }
}

fun MainVm.delRecurring(r: Recurring) = viewModelScope.launch { db.recurringDao().delete(r) }

fun MainVm.transfer(fromId: Long, toId: Long, amount: Long, note: String, date: Long) =
    viewModelScope.launch {
        if (fromId != toId && amount > 0) {
            db.withTransaction {
                db.applyToAccount(fromId, false, amount, false)
                db.applyToAccount(toId, true, amount, false)
                db.transferDao().insert(
                    Transfer(fromId = fromId, toId = toId, amount = amount, note = note, date = date)
                )
            }
        }
    }

fun MainVm.delTransfer(t: Transfer) = viewModelScope.launch {
    db.withTransaction {
        db.applyToAccount(t.fromId, false, t.amount, true)
        db.applyToAccount(t.toId, true, t.amount, true)
        db.transferDao().delete(t)
    }
}

// ---------- یادآور سررسیدها ----------

suspend fun dueReminders(db: AppDb): List<String> {
    val lines = mutableListOf<String>()
    for (c in db.chequeDao().list()) {
        if (c.status != 0) continue
        val diff = daysUntil(c.dueDate)
        if (diff <= 1) {
            lines.add(
                "چک ${if (c.issued) "صادره به" else "دریافتی از"} ${c.party} (${fmt(c.amount)} تومان) — ${dueText(diff)}"
            )
        }
    }
    for (l in db.loanDao().list()) {
        val due = loanDue(l) ?: continue
        val diff = daysUntil(due)
        if (diff <= 1) {
            lines.add("قسط ${l.title} (${fmt(l.installment)} تومان) — ${dueText(diff)}")
        }
    }
    return lines
}

fun showDueNotification(ctx: Context, lines: List<String>) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(
        NotificationChannel("due", "سررسیدها", NotificationManager.IMPORTANCE_DEFAULT)
    )
    val pi = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )
    val n = Notification.Builder(ctx, "due")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("یادآور سررسیدها")
        .setContentText(lines.first())
        .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()
    nm.notify(1001, n)
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            val db = AppDb.get(applicationContext)
            applyRecurringDue(db)
            val lines = dueReminders(db)
            val today = startOfDay()
            if (lines.isNotEmpty() && Prefs.lastNotifyDay(applicationContext) != today) {
                showDueNotification(applicationContext, lines)
                Prefs.setLastNotifyDay(applicationContext, today)
            }
        } catch (_: Exception) {
        }
        return Result.success()
    }
}

// ---------- خروجی CSV ----------

private fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

fun buildCsv(txns: List<Txn>, accName: Map<Long, String>): String {
    val sb = StringBuilder("\uFEFF")
    sb.append("تاریخ شمسی,نوع,مبلغ (تومان),دسته,توضیح,حساب\n")
    for (t in txns.sortedBy { it.date }) {
        sb.append(csvCell(jalaliText(t.date))).append(',')
            .append(if (t.isIncome) "درآمد" else "هزینه").append(',')
            .append(t.amount).append(',')
            .append(csvCell(t.category)).append(',')
            .append(csvCell(t.note)).append(',')
            .append(csvCell(accName[t.accountId] ?: "")).append('\n')
    }
    return sb.toString()
}

// ---------- بکاپ و بازیابی بخش‌های جدید (متدهای هماهنگ با AppDb) ----------

suspend fun AppDb.putAllExtra(o: JSONObject) {
    val a = JSONArray()
    chequeDao().list().forEach {
        a.put(
            JSONObject().put("party", it.party).put("amount", it.amount).put("dueDate", it.dueDate)
                .put("issued", it.issued).put("status", it.status).put("note", it.note)
        )
    }
    o.put("cheques", a)
    val b = JSONArray()
    loanDao().list().forEach {
        b.put(
            JSONObject().put("title", it.title).put("installment", it.installment)
                .put("totalCount", it.totalCount).put("paid", it.paid).put("firstDue", it.firstDue)
        )
    }
    o.put("loans", b)
    val c = JSONArray()
    personDebtDao().list().forEach {
        c.put(
            JSONObject().put("person", it.person).put("amount", it.amount).put("paid", it.paid)
                .put("owedToMe", it.owedToMe).put("note", it.note).put("date", it.date)
        )
    }
    o.put("people", c)
    val d = JSONArray()
    recurringDao().list().forEach {
        d.put(
            JSONObject().put("title", it.title).put("amount", it.amount)
                .put("isIncome", it.isIncome).put("category", it.category)
                .put("accountId", it.accountId).put("day", it.day).put("lastIdx", it.lastIdx)
                .put("autoPost", it.autoPost).put("active", it.active)
        )
    }
    o.put("recurring", d)
    val e = JSONArray()
    transferDao().list().forEach {
        e.put(
            JSONObject().put("fromId", it.fromId).put("toId", it.toId).put("amount", it.amount)
                .put("note", it.note).put("date", it.date)
        )
    }
    o.put("transfers", e)
}

suspend fun AppDb.restoreAllExtra(o: JSONObject) {
    fun arr(name: String): JSONArray = o.optJSONArray(name) ?: JSONArray()

    val a = arr("cheques")
    val cheques = (0 until a.length()).map { i ->
        val j = a.getJSONObject(i)
        Cheque(
            party = j.getString("party"), amount = j.getLong("amount"),
            dueDate = j.getLong("dueDate"), issued = j.getBoolean("issued"),
            status = j.optInt("status", 0), note = j.optString("note")
        )
    }
    val b = arr("loans")
    val loans = (0 until b.length()).map { i ->
        val j = b.getJSONObject(i)
        Loan(
            title = j.getString("title"), installment = j.getLong("installment"),
            totalCount = j.getInt("totalCount"), paid = j.getInt("paid"),
            firstDue = j.getLong("firstDue")
        )
    }
    val c = arr("people")
    val people = (0 until c.length()).map { i ->
        val j = c.getJSONObject(i)
        PersonDebt(
            person = j.getString("person"), amount = j.getLong("amount"),
            paid = j.getLong("paid"), owedToMe = j.getBoolean("owedToMe"),
            note = j.optString("note"), date = j.getLong("date")
        )
    }
    val d = arr("recurring")
    val recs = (0 until d.length()).map { i ->
        val j = d.getJSONObject(i)
        Recurring(
            title = j.getString("title"), amount = j.getLong("amount"),
            isIncome = j.getBoolean("isIncome"), category = j.optString("category"),
            accountId = j.optLong("accountId", 0), day = j.getInt("day"),
            lastIdx = j.getInt("lastIdx"), autoPost = j.getBoolean("autoPost"),
            active = j.getBoolean("active")
        )
    }
    val e = arr("transfers")
    val trs = (0 until e.length()).map { i ->
        val j = e.getJSONObject(i)
        Transfer(
            fromId = j.getLong("fromId"), toId = j.getLong("toId"),
            amount = j.getLong("amount"), note = j.optString("note"), date = j.getLong("date")
        )
    }

    chequeDao().clear()
    loanDao().clear()
    personDebtDao().clear()
    recurringDao().clear()
    transferDao().clear()
    chequeDao().insertAll(cheques)
    loanDao().insertAll(loans)
    personDebtDao().insertAll(people)
    recurringDao().insertAll(recs)
    transferDao().insertAll(trs)
}
