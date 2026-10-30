package com.example.lifemanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

// جدول قدیمی کارها (فقط برای سازگاری؛ کارهای جدید در جدول todos ذخیره می‌شوند)
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

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val limitAmount: Long
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    val saved: Long
)

@Entity(tableName = "snapshots")
data class Snapshot(
    @PrimaryKey val day: Long,
    val net: Long
)

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun all(): Flow<List<Txn>>
    @Query("SELECT * FROM transactions")
    suspend fun list(): List<Txn>
    @Insert suspend fun insert(t: Txn)
    @Insert suspend fun insertAll(l: List<Txn>)
    @Update suspend fun update(t: Txn)
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

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id")
    fun all(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets")
    suspend fun list(): List<Budget>
    @Insert suspend fun insert(b: Budget)
    @Insert suspend fun insertAll(l: List<Budget>)
    @Update suspend fun update(b: Budget)
    @Delete suspend fun delete(b: Budget)
    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id")
    fun all(): Flow<List<Goal>>
    @Query("SELECT * FROM goals")
    suspend fun list(): List<Goal>
    @Insert suspend fun insert(g: Goal)
    @Insert suspend fun insertAll(l: List<Goal>)
    @Update suspend fun update(g: Goal)
    @Delete suspend fun delete(g: Goal)
    @Query("DELETE FROM goals")
    suspend fun clear()
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY day")
    fun all(): Flow<List<Snapshot>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Snapshot)
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

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`category` TEXT NOT NULL, `limitAmount` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `target` INTEGER NOT NULL, `saved` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `snapshots` (`day` INTEGER NOT NULL, " +
                "`net` INTEGER NOT NULL, PRIMARY KEY(`day`))"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cheques` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`party` TEXT NOT NULL, `amount` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, " +
                "`issued` INTEGER NOT NULL, `status` INTEGER NOT NULL, `note` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `installment` INTEGER NOT NULL, `totalCount` INTEGER NOT NULL, " +
                "`paid` INTEGER NOT NULL, `firstDue` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `person_debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`person` TEXT NOT NULL, `amount` INTEGER NOT NULL, `paid` INTEGER NOT NULL, " +
                "`owedToMe` INTEGER NOT NULL, `note` TEXT NOT NULL, `date` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `amount` INTEGER NOT NULL, `isIncome` INTEGER NOT NULL, " +
                "`category` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                "`lastIdx` INTEGER NOT NULL, `autoPost` INTEGER NOT NULL, `active` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fromId` INTEGER NOT NULL, `toId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `date` INTEGER NOT NULL)"
        )
    }
}

// جدول جدید کارها + انتقال کارهای قدیمی
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `note` TEXT NOT NULL, `done` INTEGER NOT NULL, " +
                "`dueDate` INTEGER NOT NULL, `hasTime` INTEGER NOT NULL, `priority` INTEGER NOT NULL, " +
                "`category` TEXT NOT NULL, `repeatMode` INTEGER NOT NULL, `remind` INTEGER NOT NULL, " +
                "`subs` TEXT NOT NULL, `doneAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `todos` (`title`, `note`, `done`, `dueDate`, `hasTime`, `priority`, " +
                "`category`, `repeatMode`, `remind`, `subs`, `doneAt`) " +
                "SELECT `title`, '', `done`, 0, 0, 0, '', 0, 0, '', 0 FROM `tasks`"
        )
    }
}

@Database(
    entities = [
        Txn::class, TaskItem::class, Food::class, Account::class,
        Budget::class, Goal::class, Snapshot::class,
        Cheque::class, Loan::class, PersonDebt::class, Recurring::class, Transfer::class,
        Todo::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun taskDao(): TaskDao
    abstract fun foodDao(): FoodDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun chequeDao(): ChequeDao
    abstract fun loanDao(): LoanDao
    abstract fun personDebtDao(): PersonDebtDao
    abstract fun recurringDao(): RecurringDao
    abstract fun transferDao(): TransferDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "life.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                )
                .build().also { inst = it }
        }
    }
}

// ---------- ViewModel ----------

class MainVm(app: Application) : AndroidViewModel(app) {
    val db = AppDb.get(app)
    val txns = db.txnDao().all()
    val tasks = db.taskDao().all()
    val todayFoods = db.foodDao().since(startOfDay())
    val accounts = db.accountDao().all()
    val budgets = db.budgetDao().all()
    val goals = db.goalDao().all()
    val snapshots = db.snapshotDao().all()
    val cheques = db.chequeDao().all()
    val loans = db.loanDao().all()
    val people = db.personDebtDao().all()
    val recurring = db.recurringDao().all()
    val transfers = db.transferDao().all()

    init {
        // هر بار حساب‌ها عوض شوند، ثروت خالص امروز ذخیره می‌شود (برای نمودار روند)
        viewModelScope.launch {
            db.accountDao().all().collect { list ->
                if (list.isNotEmpty()) {
                    db.snapshotDao().upsert(Snapshot(startOfDay(), computeWealth(list).net))
                }
            }
        }
        // ثبت خودکار تراکنش‌های تکراری که موعدشان رسیده
        viewModelScope.launch {
            try {
                applyRecurringDue(db)
            } catch (_: Exception) {
            }
        }
    }

    // ثبت تراکنش + تغییر خودکار موجودی حساب
    fun addTxn(
        amount: Long, income: Boolean, category: String, note: String,
        date: Long, accountId: Long
    ) = viewModelScope.launch {
        db.postTxn(amount, income, category, note, date, accountId)
    }

    // ویرایش کامل تراکنش (مبلغ، نوع درآمد/هزینه، دسته، توضیح، تاریخ و حساب)
    fun updateTxn(
        old: Txn, amount: Long, isIncome: Boolean,
        category: String, note: String, date: Long, accountId: Long
    ) = viewModelScope.launch {
        db.withTransaction {
            db.applyToAccount(old.accountId, old.isIncome, old.amount, true)
            db.applyToAccount(accountId, isIncome, amount, false)
            db.txnDao().update(
                old.copy(
                    amount = amount, isIncome = isIncome,
                    category = category, note = note,
                    date = date, accountId = accountId
                )
            )
        }
    }

    // واردکردن گروهی بدون حساب
    fun addTxns(list: List<Txn>) = viewModelScope.launch { db.txnDao().insertAll(list) }

    // واردکردن پیامک‌ها همراه با اعمال روی حساب انتخابی (اگر accountId > 0 باشد)
    fun addSmsTxns(list: List<Txn>, accountId: Long) = viewModelScope.launch {
        db.withTransaction {
            if (accountId > 0) {
                for (t in list) {
                    db.postTxn(t.amount, t.isIncome, t.category, t.note, t.date, accountId)
                }
            } else {
                db.txnDao().insertAll(list)
            }
        }
    }

    fun delTxn(t: Txn) = viewModelScope.launch {
        db.withTransaction {
            db.applyToAccount(t.accountId, t.isIncome, t.amount, true)
            db.txnDao().delete(t)
        }
    }

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

    // بودجه: اگر برای این دسته قبلاً بودجه بوده، مبلغش عوض می‌شود
    fun setBudget(category: String, limit: Long) = viewModelScope.launch {
        val existing = db.budgetDao().list().firstOrNull { it.category == category }
        if (existing != null) {
            db.budgetDao().update(existing.copy(limitAmount = limit))
        } else {
            db.budgetDao().insert(Budget(category = category, limitAmount = limit))
        }
    }
    fun delBudget(b: Budget) = viewModelScope.launch { db.budgetDao().delete(b) }

    fun addGoal(name: String, target: Long, saved: Long) =
        viewModelScope.launch { db.goalDao().insert(Goal(name = name, target = target, saved = saved)) }

    // افزودن یا برداشت از هدف پس‌انداز (با قابلیت کسر/واریز همزمان روی حساب بانکی)
    fun addToGoal(g: Goal, delta: Long, accountId: Long = 0L) = viewModelScope.launch {
        db.withTransaction {
            val newSaved = (g.saved + delta).coerceAtLeast(0L)
            val actualDelta = newSaved - g.saved
            db.goalDao().update(g.copy(saved = newSaved))
            if (accountId > 0L && actualDelta != 0L) {
                val isDepositToGoal = actualDelta > 0
                val amt = kotlin.math.abs(actualDelta)
                db.postTxn(
                    amount = amt,
                    income = !isDepositToGoal,
                    category = "پس‌انداز",
                    note = (if (isDepositToGoal) "واریز به هدف: " else "برداشت از هدف: ") + g.name,
                    date = System.currentTimeMillis(),
                    accountId = accountId
                )
            }
        }
    }

    fun delGoal(g: Goal) = viewModelScope.launch { db.goalDao().delete(g) }

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
        val e = JSONArray()
        db.budgetDao().list().forEach {
            e.put(JSONObject().put("category", it.category).put("limit", it.limitAmount))
        }
        o.put("budgets", e)
        val g = JSONArray()
        db.goalDao().list().forEach {
            g.put(
                JSONObject().put("name", it.name).put("target", it.target).put("saved", it.saved)
            )
        }
        o.put("goals", g)
        db.putAllExtra(o)
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
        val e = o.optJSONArray("budgets") ?: JSONArray()
        val budgets = (0 until e.length()).map { i ->
            val j = e.getJSONObject(i)
            Budget(category = j.getString("category"), limitAmount = j.getLong("limit"))
        }
        val g = o.optJSONArray("goals") ?: JSONArray()
        val goals = (0 until g.length()).map { i ->
            val j = g.getJSONObject(i)
            Goal(name = j.getString("name"), target = j.getLong("target"), saved = j.getLong("saved"))
        }
        db.withTransaction {
            db.txnDao().clear()
            db.taskDao().clear()
            db.foodDao().clear()
            db.accountDao().clear()
            db.budgetDao().clear()
            db.goalDao().clear()
            db.accountDao().insertAll(accounts)
            db.txnDao().insertAll(txns)
            db.taskDao().insertAll(tasks)
            db.foodDao().insertAll(foods)
            db.budgetDao().insertAll(budgets)
            db.goalDao().insertAll(goals)
            db.restoreAll(o)
        }
    }
}
