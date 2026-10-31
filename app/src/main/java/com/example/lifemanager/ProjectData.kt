package com.example.lifemanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "project_expenses")
data class ProjectExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectName: String, // مثلاً: ساخت خانه، ماه‌های گذشته، بازسازی
    val category: String,    // مثلاً: مصالح، دستمزد، تأسیسات، مجوزها، عمومی
    val amount: Long,
    val date: Long,
    val note: String = ""
)

@Dao
interface ProjectExpenseDao {
    @Query("SELECT * FROM project_expenses ORDER BY date DESC, id DESC")
    fun all(): Flow<List<ProjectExpense>>

    @Query("SELECT * FROM project_expenses")
    suspend fun list(): List<ProjectExpense>

    @Insert suspend fun insert(item: ProjectExpense)
    @Insert suspend fun insertAll(list: List<ProjectExpense>)
    @Update suspend fun update(item: ProjectExpense)
    @Delete suspend fun delete(item: ProjectExpense)
    @Query("DELETE FROM project_expenses")
    suspend fun clear()
}

fun MainVm.saveProjectExpense(item: ProjectExpense) = viewModelScope.launch {
    if (item.id == 0L) db.projectExpenseDao().insert(item)
    else db.projectExpenseDao().update(item)
}

fun MainVm.delProjectExpense(item: ProjectExpense) = viewModelScope.launch {
    db.projectExpenseDao().delete(item)
}

private fun parseProjectAmount(s: String): Long? =
    digits(s).replace(",", "").replace("٬", "").replace(" ", "").trim().toLongOrNull()

val DEFAULT_PROJECTS = listOf("ساخت خانه", "ماه‌های گذشته", "تعمیرات و بازسازی")
val PROJECT_CATEGORIES = listOf(
    "مصالح ساختمانی", "دستمزد استادکار و کارگر", "تأسیسات و لوله‌کشی",
    "برق‌کشی", "آهن‌آلات و اسکلت", "سیمان و گچ", "کاشی و سرامیک",
    "درب و پنجره", "کابینت و چوب", "مجوز و شهرداری", "حمل و نقل", "سایر هزینه‌ها"
)

@Composable
fun ProjectExpensesScreen(vm: MainVm) {
    val allItems by vm.projectExpenses.collectAsState(initial = emptyList())
    var selectedProj by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProjectExpense?>(null) }
    var creating by remember { mutableStateOf(false) }

    val projects = remember(allItems) {
        (DEFAULT_PROJECTS + allItems.map { it.projectName }).filter { it.isNotBlank() }.distinct()
    }

    val filtered = remember(allItems, selectedProj, selectedCat, search) {
        val q = digits(search).trim()
        allItems.filter { item ->
            (selectedProj.isEmpty() || item.projectName == selectedProj) &&
                (selectedCat.isEmpty() || item.category == selectedCat) &&
                (q.isEmpty() || item.note.contains(q) || item.category.contains(q) || item.projectName.contains(q))
        }
    }

    val totalFiltered = filtered.sumOf { it.amount }
    val totalAll = allItems.sumOf { it.amount }
    val byCat = remember(filtered) {
        filtered.groupBy { it.category.ifBlank { "سایر" } }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard(
                    title = if (selectedProj.isEmpty()) "جمع کل پروژه‌ها و خرج‌های گذشته" else "جمع پروژه: $selectedProj",
                    value = totalFiltered,
                    leftLabel = "تعداد ردیف‌ها",
                    leftValue = "${filtered.size} مورد",
                    rightLabel = "کل همه پروژه‌ها",
                    rightValue = fmt(totalAll)
                )
            }

            item {
                Text("انتخاب پروژه / دفتر:", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedProj.isEmpty(),
                        onClick = { selectedProj = "" },
                        label = { Text("همه پروژه‌ها") }
                    )
                    projects.forEach { p ->
                        FilterChip(
                            selected = selectedProj == p,
                            onClick = { selectedProj = if (selectedProj == p) "" else p },
                            label = { Text(p) }
                        )
                    }
                }
            }

            if (byCat.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("تفکیک دسته‌بندی‌ها", fontWeight = FontWeight.Bold)
                            byCat.forEach { (c, sum) ->
                                val pct = if (totalFiltered > 0) (sum * 100f / totalFiltered).toInt() else 0
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selectedCat = if (selectedCat == c) "" else c
                                    }.padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("$c ($pct٪)", style = MaterialTheme.typography.bodyMedium)
                                    Text(fmt(sum), fontWeight = FontWeight.SemiBold, color = ExpenseColor)
                                }
                            }
                            if (selectedCat.isNotEmpty()) {
                                TextButton(onClick = { selectedCat = "" }) {
                                    Text("نمایش همه دسته‌ها (حذف فیلتر $selectedCat)")
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("جستجو در توضیحات یا دسته‌ها...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        "هنوز هزینه‌ای در این بخش ثبت نشده است. با دکمه پایین صفحه اضافه کن.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(filtered, key = { it.id }) { item ->
                Card(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .clickable { editing = item }
                ) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${item.projectName} · ${item.category}",
                                fontWeight = FontWeight.Bold
                            )
                            if (item.note.isNotBlank()) {
                                Text(item.note, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                jalaliText(item.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            fmt(item.amount),
                            fontWeight = FontWeight.Bold,
                            color = ExpenseColor
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text("＋ ثبت هزینه ساخت / گذشته", fontWeight = FontWeight.Bold)
        }
    }

    if (creating) {
        ProjectExpenseDialog(
            base = null,
            defaultProject = selectedProj.ifBlank { "ساخت خانه" },
            existingProjects = projects,
            onDismiss = { creating = false },
            onSave = { vm.saveProjectExpense(it); creating = false },
            onDelete = null
        )
    }

    editing?.let { item ->
        ProjectExpenseDialog(
            base = item,
            defaultProject = item.projectName,
            existingProjects = projects,
            onDismiss = { editing = null },
            onSave = { vm.saveProjectExpense(it); editing = null },
            onDelete = { vm.delProjectExpense(item); editing = null }
        )
    }
}

@Composable
fun ProjectExpenseDialog(
    base: ProjectExpense?,
    defaultProject: String,
    existingProjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (ProjectExpense) -> Unit,
    onDelete: (() -> Unit)?
) {
    var projectName by remember { mutableStateOf(base?.projectName ?: defaultProject) }
    var category by remember { mutableStateOf(base?.category ?: PROJECT_CATEGORIES.first()) }
    var amountText by remember { mutableStateOf(base?.amount?.toString() ?: "") }
    var dateText by remember {
        mutableStateOf(if (base != null) jalaliText(base.date) else todayJalali())
    }
    var note by remember { mutableStateOf(base?.note ?: "") }
    var err by remember { mutableStateOf("") }
    var confirmDel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (base == null) "ثبت هزینه ساخت خانه / گذشته" else "ویرایش هزینه") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("نام پروژه یا دفتر:", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    existingProjects.forEach { p ->
                        FilterChip(
                            selected = projectName == p,
                            onClick = { projectName = p },
                            label = { Text(p) }
                        )
                    }
                }
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("نام پروژه (مثلاً ساخت خانه، ماه قبل و...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("دسته‌بندی هزینه:", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PROJECT_CATEGORIES.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("دسته‌بندی (یا دلخواه بنویس)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = "" },
                    label = { Text("مبلغ (تومان)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; err = "" },
                    label = { Text("تاریخ شمسی (مثلاً 1404/11/15)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات (مثلاً ۵۰ کیسه سیمان، دستمزد گچ‌کار و...)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (err.isNotBlank()) {
                    Text(err, color = ExpenseColor, style = MaterialTheme.typography.bodySmall)
                }

                if (onDelete != null) {
                    TextButton(onClick = {
                        if (confirmDel) onDelete() else confirmDel = true
                    }) {
                        Text(
                            if (confirmDel) "برای تأیید حذف دوباره بزن" else "حذف این ردیف",
                            color = ExpenseColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = parseProjectAmount(amountText)
                val dt = parseJalali(dateText)
                when {
                    projectName.isBlank() -> err = "نام پروژه را وارد کن"
                    amt == null || amt <= 0L -> err = "مبلغ معتبر نیست"
                    dt == null -> err = "تاریخ شمسی معتبر نیست (نمونه: 1405/02/10)"
                    else -> onSave(
                        ProjectExpense(
                            id = base?.id ?: 0L,
                            projectName = projectName.trim(),
                            category = category.trim().ifBlank { "سایر" },
                            amount = amt,
                            date = dt,
                            note = note.trim()
                        )
                    )
                }
            }) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
