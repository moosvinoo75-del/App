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
    // حساب‌های طلا/ارز/سهام ارزششان مقدار × قیمت است؛ موجودی نقدی ندارند
    if (acc.type in PRICED_TYPES) return
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

fun MainVm.addCheque(party: String, amount: Long, dueDate: Long, issued
