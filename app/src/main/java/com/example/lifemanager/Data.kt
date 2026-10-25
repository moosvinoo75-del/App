package com.example.lifemanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale

// ---------- جدول‌ها ----------

@Entity(tableName = "transactions")
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val isIncome: Boolean,
    val category: String,
    val note: String,
    val date: Long,
    @ColumnInfo(defaultValue = "0") val accountId: Long = 0
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false
)

@Entity(tableName = "foods")
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val grams: Int,
    val kcal: Int,
    val date: Long
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val balance: Long,
    @ColumnInfo(defaultValue = "0") val quantity: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val unitPrice: Long = 0
)

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun all(): Flow<List<Txn>>
    @Query("SELECT * FROM transactions")
    suspend fun list(): List<Txn>
    @Insert suspend fun insert(t: Txn)
    @Insert suspend fun insertAll(l: List<Txn>)
    @Delete suspend fun delete(t: Txn)
    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done, id DESC")
    fun all(): Flow<List<TaskItem>>
    @Query("SELECT * FROM tasks")
    suspend fun list(): List<TaskItem>
    @Insert suspend fun insert(t: TaskItem)
    @Insert suspend fun insertAll(l: List<TaskItem>)
    @Update suspend fun update(t: TaskItem)
    @Delete suspend fun delete(t: TaskItem)
    @Query("DELETE FROM tasks")
    suspend fun clear()
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE date >= :start ORDER BY date DESC")
    fun since(start: Long): Flow<List<Food>>
    @Query("SELECT * FROM foods")
    suspend fun list(): List<Food>
    @Insert suspend fun insertAll(l: List<Food>)
    @Delete suspend fun delete(f: Food)
    @Query("DELETE FROM foods")
    suspend fun clear()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun all(): Flow<List<Account>>
    @Query("SELECT * FROM accounts")
    suspend fun list(): List<Account>
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?
    @Insert suspend fun insert(a: Account)
    @Insert suspend fun insertGetId(a: Account): Long
    @Insert suspend fun insertAll(l: List<Account>)
    @Update suspend fun update(a: Account)
    @Delete suspend fun delete(a: Account)
    @Query("DELETE FROM accounts")
    suspend fun clear()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `foods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `grams` INTEGER NOT NULL, `kcal` INTEGER NOT NULL, " +
                "`date` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `balance` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `quantity` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `unitPrice` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [Txn::class, TaskItem::class, Food::class, Account::class],
    version = 5,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun taskDao(): TaskDao
    abstract fun foodDao(): FoodDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "life.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { inst = it }
        }
    }
}

// ---------- ابزارها ----------

fun fmt(n: Long): String = String.format(Locale.US, "%,d", n)

fun digits(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
    }
    return sb.toString()
}

fun String.faToLong(): Long? = digits(this).replace(",", "").trim().toLongOrNull()

fun startOfDay(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun apiKey(c: Context): String = sp(c).getString("key", "") ?: ""
    fun setApiKey(c: Context, v: String) {
        sp(c).edit().putString("key", v.trim()).apply()
    }
    fun smsIds(c: Context): Set<String> = sp(c).getStringSet("sms", emptySet()) ?: emptySet()
    fun addSmsIds(c: Context, ids: Set<String>) {
        sp(c).edit().putStringSet("sms", smsIds(c) + ids).apply()
    }
}

// ---------- هوش مصنوعی ----------

object Ai {
    const val MODEL_FAST = "claude-haiku-5-5"
    const val MODEL_SMART = "claude-sonnet-5-5"

    val FOOD_PROMPT = """
کاربر لیست غذاهایی که خورده را می‌نویسد. کالری هر مورد را تخمین بزن.
فقط و فقط یک آرایه JSON برگردان، بدون هیچ متن دیگر، با این قالب:
[{"name":"نام غذا","grams":عدد,"kcal":عدد}]
""".trimIndent()

    suspend fun ask(key: String, model: String, system: String, user: String): String =
        withContext(Dispatchers.IO) {
            val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", key)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 1000)
                    .put("system", system)
                    .put(
                        "messages",
                        JSONArray().put(JSONObject().put("role", "user").put("content", user))
                    )
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw Exception("کد $code: ${text.take(300)}")
                val arr = JSONObject(text).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("type") == "text") sb.append(o.getString("text"))
                }
                sb.toString()
            } finally {
                conn.disconnect()
            }
        }
}

// ---------- جدول کالری داخلی (وقتی کلید API نیست) ----------

object LocalFood {
    private val per100 = mapOf(
        "برنج" to 130, "پلو" to 130, "چلو" to 130, "نان" to 270, "سنگک" to 250,
        "لواش" to 275, "بربری" to 280, "مرغ" to 165, "جوجه" to 165, "گوشت" to 250,
        "کوبیده" to 250, "ماهی" to 140, "تخم مرغ" to 155, "پنیر" to 300, "ماست" to 60,
        "شیر" to 60, "سیب زمینی" to 90, "سیب" to 52, "موز" to 89, "پرتقال" to 47,
        "خیار" to 15, "گوجه" to 18, "عدس" to 116, "لوبیا" to 110, "نخود" to 120,
        "قورمه سبزی" to 150, "قیمه" to 160, "آبگوشت" to 100, "ماکارونی" to 160,
        "حلوا" to 400, "خرما" to 280, "گردو" to 650, "بادام" to 580, "کره" to 717,
        "روغن" to 884, "عسل" to 304, "شکر" to 387, "پیتزا" to 270, "ساندویچ" to 250,
        "همبرگر" to 250, "املت" to 150, "کتلت" to 220
    )
    private val piece = mapOf("تخم مرغ" to 50, "سیب" to 180, "موز" to 120, "پرتقال" to 150)

    fun estimate(text: String): Pair<List<Food>, List<String>> {
        val now = System.currentTimeMillis()
        val found = mutableListOf<Food>()
        val missing = mutableListOf<String>()
        val t = digits(text).replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک')
        val parts = t.split(Regex("[،,\\n+]|\\sو\\s")).map { it.trim() }.filter { it.isNotEmpty() }
        val keys = per100.keys.sortedByDescending { it.length }
        for (p in parts) {
            val key = keys.firstOrNull { p.contains(it) }
            if (key == null) {
                missing.add(p)
                continue
            }
            val num = Regex("[0-9]+(?:\\.[0-9]+)?").find(p)?.value?.toDoubleOrNull()
            val pw = piece[key]
            val grams: Double = when {
                num == null -> 100.0
                pw != null && (p.contains("عدد") || (num <= 10 && !p.contains("گرم"))) -> num * pw
                else -> num
            }
            val kcal = (grams * (per100[key] ?: 0) / 100.0).toInt()
            found.add(Food(name = p, grams = grams.toInt(), kcal = kcal, date = now))
        }
        return Pair(found, missing)
    }
}

// ---------- ViewModel ----------

class MainVm(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    val txns = db.txnDao().all()
    val tasks = db.taskDao().all()
    val todayFoods = db.foodDao().since(startOfDay())
    val accounts = db.accountDao().all()

    // ثبت تراکنش + تغییر خودکار موجودی حساب (همه با هم، در یک تراکنش دیتابیس)
    // فقط حساب‌های نقد/بانک/بدهی قابل انتخاب‌اند (نه طلا، ارز، سهام)
    fun addTxn(
        amount: Long, income: Boolean, category: String, note: String,
        date: Long, accountId: Long
    ) = viewModelScope.launch {
        db.withTransaction {
            val usable = db.accountDao().list().filter { it.type !in PRICED_TYPES }
            val accId: Long =
                if (accountId > 0 && usable.any { it.id == accountId }) accountId
                else usable.firstOrNull()?.id
                    ?: db.accountDao().insertGetId(Account(name = "کیف پول", type = "نقد", balance = 0))
            val acc = db.accountDao().getById(accId)
            if (acc != null) {
                val delta = if (income) amount else -amount
                val signed = if (acc.type == "بدهی") -delta else delta
                db.accountDao().update(acc.copy(balance = acc.balance + signed))
            }
            db.txnDao().insert(
                Txn(
                    amount = amount, isIncome = income, category = category,
                    note = note, date = date, accountId = accId
                )
            )
        }
    }

    // واردکردن گروهی (مثلاً پیامک): روی حساب‌ها اثر نمی‌گذارد
    fun addTxns(list: List<Txn>) = viewModelScope.launch { db.txnDao().insertAll(list) }

    // حذف تراکنش + برگرداندن اثرش روی حساب
    fun delTxn(t: Txn) = viewModelScope.launch {
        db.withTransaction {
            if (t.accountId > 0) {
                val acc = db.accountDao().getById(t.accountId)
                if (acc != null) {
                    val delta = if (t.isIncome) -t.amount else t.amount
                    val signed = if (acc.type == "بدهی") -delta else delta
                    db.accountDao().update(acc.copy(balance = acc.balance + signed))
                }
            }
            db.txnDao().delete(t)
        }
    }

    fun addTask(title: String) = viewModelScope.launch { db.taskDao().insert(TaskItem(title = title)) }
    fun toggle(t: TaskItem) = viewModelScope.launch { db.taskDao().update(t.copy(done = !t.done)) }
    fun delTask(t: TaskItem) = viewModelScope.launch { db.taskDao().delete(t) }

    fun addFoods(list: List<Food>) = viewModelScope.launch { db.foodDao().insertAll(list) }
    fun delFood(f: Food) = viewModelScope.launch { db.foodDao().delete(f) }

    fun addAccount(name: String, type: String, balance: Long, quantity: Double, unitPrice: Long) =
        viewModelScope.launch {
            db.accountDao().insert(
                Account(
                    name = name, type = type, balance = balance,
                    quantity = quantity, unitPrice = unitPrice
                )
            )
        }
    fun updateAccount(a: Account) = viewModelScope.launch { db.accountDao().update(a) }
    fun delAccount(a: Account) = viewModelScope.launch { db.accountDao().delete(a) }

    suspend fun exportJson(): String {
        val o = JSONObject()
        val a = JSONArray()
        db.txnDao().list().forEach {
            a.put(
                JSONObject().put("amount", it.amount).put("income", it.isIncome)
                    .put("category", it.category).put("note", it.note).put("date", it.date)
                    .put("accountId", it.accountId)
            )
        }
        o.put("txns", a)
        val b = JSONArray()
        db.taskDao().list().forEach {
            b.put(JSONObject().put("title", it.title).put("done", it.done))
        }
        o.put("tasks", b)
        val c = JSONArray()
        db.foodDao().list().forEach {
            c.put(
                JSONObject().put("name", it.name).put("grams", it.grams)
                    .put("kcal", it.kcal).put("date", it.date)
            )
        }
        o.put("foods", c)
        val d = JSONArray()
        db.accountDao().list().forEach {
            d.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("type", it.type).put("balance", it.balance)
                    .put("quantity", it.quantity).put("unitPrice", it.unitPrice)
            )
        }
        o.put("accounts", d)
        return o.toString(2)
    }

    suspend fun importJson(s: String) {
        val o = JSONObject(s)
        val a = o.optJSONArray("txns") ?: JSONArray()
        val txns = (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            Txn(
                amount = j.getLong("amount"), isIncome = j.getBoolean("income"),
                category = j.optString("category"), note = j.optString("note"),
                date = j.getLong("date"), accountId = j.optLong("accountId", 0)
            )
        }
        val b = o.optJSONArray("tasks") ?: JSONArray()
        val tasks = (0 until b.length()).map { i ->
            val j = b.getJSONObject(i)
            TaskItem(title = j.getString("title"), done = j.optBoolean("done"))
        }
        val c = o.optJSONArray("foods") ?: JSONArray()
        val foods = (0 until c.length()).map { i ->
            val j = c.getJSONObject(i)
            Food(
                name = j.getString("name"), grams = j.optInt("grams"),
                kcal = j.getInt("kcal"), date = j.getLong("date")
            )
        }
        val d = o.optJSONArray("accounts") ?: JSONArray()
        val accounts = (0 until d.length()).map { i ->
            val j = d.getJSONObject(i)
            Account(
                id = j.optLong("id", 0), name = j.getString("name"),
                type = j.getString("type"), balance = j.getLong("balance"),
                quantity = j.optDouble("quantity", 0.0), unitPrice = j.optLong("unitPrice", 0)
            )
        }
        db.withTransaction {
            db.txnDao().clear()
            db.taskDao().clear()
            db.foodDao().clear()
            db.accountDao().clear()
            db.accountDao().insertAll(accounts)
            db.txnDao().insertAll(txns)
            db.taskDao().insertAll(tasks)
            db.foodDao().insertAll(foods)
        }
    }
}
