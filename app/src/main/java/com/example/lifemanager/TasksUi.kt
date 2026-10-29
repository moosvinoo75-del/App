package com.example.lifemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

val TASK_CATEGORIES = listOf("شخصی", "کار", "خانه", "خرید", "سلامت", "مطالعه")
val PRIORITY_LABELS = listOf("عادی", "مهم", "فوری")
val REPEAT_LABELS = listOf("تکرار ندارد", "هر روز", "هر هفته", "هر ماه")

fun priorityColor(p: Int): Color = when (p) {
    2 -> ExpenseColor
    1 -> Color(0xFFF59E0B)
    else -> Color.Unspecified
}

@Composable
fun CountTabs(labels: List<String>, counts: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val on = selected == i
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    if (counts[i] > 0) "$label (${counts[i]})" else label,
                    maxLines = 1,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(3.dp).background(
                        if (on) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                )
            }
        }
    }
}

// ---------- صفحه‌ی کارها ----------

@Composable
fun TasksScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val flow = remember(vm) { vm.db.todoDao().all() }
    val todos by flow.collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var cat by remember { mutableStateOf("") }
    var quick by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Todo?>(null) }
    var creating by remember { mutableStateOf(false) }

    // یادآورها را دوباره زمان‌بندی می‌کند (مثلاً بعد از بازیابی بکاپ)
    LaunchedEffect(Unit) {
        vm.db.todoDao().list().filter { it.remind && !it.done }
            .forEach { scheduleTodoReminder(ctx, it) }
    }

    val dayStart = startOfDay()
    val todayEnd = dayStart + 86_400_000L
    val open = todos.filter { !it.done }
    val order = compareBy<Todo>(
        { if (it.dueDate == 0L) Long.MAX_VALUE else dayStartOf(it.dueDate) },
        { -it.priority },
        { it.dueDate }
    )
    val base = when (tab) {
        0 -> open.filter { it.dueDate in 1L until todayEnd }.sortedWith(order)
        1 -> open.filter { it.dueDate >= todayEnd }.sortedWith(order)
        2 -> open.sortedWith(order)
        else -> todos.filter { it.done }.sortedByDescending { it.doneAt }
    }
    val shown = if (cat.isBlank()) base else base.filter { it.category == cat }
    val cats = todos.map { it.category }.filter { it.isNotBlank() }.distinct()

    val todayRemaining = open.count { it.dueDate in 1L until todayEnd }
    val doneToday = todos.count { it.doneAt >= dayStart }
    val frac = if (doneToday + todayRemaining == 0) 0f
    else doneToday.toFloat() / (doneToday + todayRemaining)
    val counts = listOf(
        todayRemaining,
        open.count { it.dueDate >= todayEnd },
        open.size,
        todos.count { it.done }
    )
    val emptyText = when (tab) {
        0 -> "برای امروز کاری نداری 🎉"
        1 -> "کار پیش‌رویی نداری"
        2 -> "کاری ثبت نشده"
        else -> "هنوز کاری انجام نشده"
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = top + 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("کارها", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "امروز: $doneToday انجام شد · $todayRemaining باقی‌مانده",
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
            }
            item {
                CountTabs(listOf("امروز", "پیش‌رو", "همه", "انجام‌شده"), counts, tab) { tab = it }
            }
            if (cats.isNotEmpty()) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = cat.isBlank(), onClick = { cat = "" },
                            label = { Text("همه‌ی دسته‌ها") }
                        )
                        cats.forEach { c ->
                            FilterChip(
                                selected = cat == c, onClick = { cat = c },
                                label = { Text(c) }
                            )
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = quick, onValueChange = { quick = it },
                        label = { Text("افزودن سریع") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (quick.isNotBlank()) {
                            vm.saveTodo(
                                Todo(
                                    title = quick.trim(),
                                    dueDate = if (tab == 0) System.currentTimeMillis() else 0L
                                )
                            )
                            quick = ""
                        }
                    }) { Text("افزودن") }
                }
            }
            if (tab == 3 && shown.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = { vm.clearDoneTodos() }) {
                        Text("پاک‌کردن انجام‌شده‌ها")
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(shown, key = { it.id }) { t ->
                TodoCard(t, onToggle = { vm.toggleTodo(t) }, onOpen = { editing = t })
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text("＋ کار جدید", fontWeight = FontWeight.Bold)
        }
    }

    if (creating) {
        TodoDialog(
            base = null,
            onDismiss = { creating = false },
            onSave = { vm.saveTodo(it); creating = false },
            onDelete = null
        )
    }
    editing?.let { t ->
        TodoDialog(
            base = t,
            onDismiss = { editing = null },
            onSave = { vm.saveTodo(it); editing = null },
            onDelete = { vm.deleteTodo(t); editing = null }
        )
    }
}

@Composable
fun TodoCard(t: Todo, onToggle: () -> Unit, onOpen: () -> Unit) {
    val subs = parseSubs(t.subs)
    val overdue = !t.done && t.dueDate > 0 &&
        (if (t.hasTime) t.dueDate < System.currentTimeMillis() else daysUntil(t.dueDate) < 0)
    val parts = listOfNotNull(
        todoDueLabel(t).ifEmpty { null },
        if (t.category.isNotBlank()) t.category else null,
        if (t.repeatMode > 0) "🔁 ${REPEAT_LABELS[t.repeatMode]}" else null,
        if (t.remind && t.dueDate > 0) "🔔" else null,
        if (subs.isNotEmpty()) "☑ ${subs.count { it.first }}/${subs.size}" else null
    )
    Card(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen)) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = t.done, onCheckedChange = { onToggle() })
            Column(
                Modifier.weight(1f).padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    t.title, fontWeight = FontWeight.Medium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        color = if (overdue) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (t.priority > 0) {
                Text(
                    PRIORITY_LABELS[t.priority],
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = priorityColor(t.priority)
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ---------- فرم ساخت و ویرایش کار ----------

@Composable
fun TodoDialog(
    base: Todo?,
    onDismiss: () -> Unit,
    onSave: (Todo) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by remember { mutableStateOf(base?.title ?: "") }
    var note by remember { mutableStateOf(base?.note ?: "") }
    var dateText by remember {
        mutableStateOf(if (base != null && base.dueDate > 0) jalaliText(base.dueDate) else "")
    }
    var timeText by remember {
        mutableStateOf(if (base != null && base.hasTime) clockText(base.dueDate) else "")
    }
    var priority by remember { mutableIntStateOf(base?.priority ?: 0) }
    var category by remember { mutableStateOf(base?.category ?: "") }
    var repeatMode by remember { mutableIntStateOf(base?.repeatMode ?: 0) }
    var remind by remember { mutableStateOf(base?.remind ?: false) }
    var subs by remember { mutableStateOf(parseSubs(base?.subs ?: "")) }
    var newSub by remember { mutableStateOf("") }
    var titleErr by remember { mutableStateOf(false) }
    var dateErr by remember { mutableStateOf(false) }
    var timeErr by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun setDate(ms: Long?) {
        dateErr = false
        if (ms == null) {
            dateText = ""
            timeText = ""
            repeatMode = 0
            remind = false
        } else {
            dateText = jalaliText(ms)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (base == null) "کار جدید" else "ویرایش کار") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it; titleErr = false },
                    label = { Text(if (titleErr) "عنوان را بنویس" else "عنوان") },
                    isError = titleErr, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("موعد", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = dateText.isBlank(), onClick = { setDate(null) },
                        label = { Text("بدون تاریخ") }
                    )
                    FilterChip(
                        selected = false, onClick = { setDate(System.currentTimeMillis()) },
                        label = { Text("امروز") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { setDate(System.currentTimeMillis() + 86_400_000L) },
                        label = { Text("فردا") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { setDate(System.currentTimeMillis() + 7 * 86_400_000L) },
                        label = { Text("۷ روز بعد") }
                    )
                }
                OutlinedTextField(
                    value = dateText, onValueChange = { dateText = it; dateErr = false },
                    label = {
                        Text(if (dateErr) "تاریخ نامعتبر است" else "تاریخ شمسی (خالی = بدون تاریخ)")
                    },
                    isError = dateErr, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (dateText.isNotBlank()) {
                    Text("ساعت", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = timeText.isBlank(),
                            onClick = { timeText = ""; timeErr = false },
                            label = { Text("بدون ساعت") }
                        )
                        listOf("08:00", "12:00", "18:00", "21:00").forEach { tm ->
                            FilterChip(
                                selected = timeText == tm,
                                onClick = { timeText = tm; timeErr = false },
                                label = { Text(tm) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = timeText, onValueChange = { timeText = it; timeErr = false },
                        label = { Text(if (timeErr) "ساعت نامعتبر است" else "ساعت (مثلاً 18:30)") },
                        isError = timeErr, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("تکرار", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        REPEAT_LABELS.forEachIndexed { i, l ->
                            FilterChip(
                                selected = repeatMode == i,
                                onClick = { repeatMode = i },
                                label = { Text(l) }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = remind, onCheckedChange = { remind = it })
                        Spacer(Modifier.width(8.dp))
                        Text("یادآوری با اعلان")
                    }
                    Text(
                        "اگر ساعت نگذاری، ساعت ۹ صبح همان روز یادآوری می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text("اولویت", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRIORITY_LABELS.forEachIndexed { i, l ->
                        FilterChip(
                            selected = priority == i,
                            onClick = { priority = i },
                            label = { Text(l) }
                        )
                    }
                }

                Text("دسته", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TASK_CATEGORIES.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text("دسته (یا دلخواه بنویس)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("چک‌لیست", style = MaterialTheme.typography.titleSmall)
                subs.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = s.first,
                            onCheckedChange = { c ->
                                subs = subs.mapIndexed { j, x -> if (j == i) (c to x.second) else x }
                            }
                        )
                        Text(
                            s.second, Modifier.weight(1f),
                            textDecoration = if (s.first) TextDecoration.LineThrough else null
                        )
                        TextButton(onClick = {
                            subs = subs.filterIndexed { j, _ -> j != i }
                        }) { Text("✕") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSub, onValueChange = { newSub = it },
                        label = { Text("مورد جدید") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        if (newSub.isNotBlank()) {
                            subs = subs + (false to newSub.trim())
                            newSub = ""
                        }
                    }) { Text("＋") }
                }

                if (onDelete != null) {
                    TextButton(onClick = {
                        if (confirmDelete) onDelete() else confirmDelete = true
                    }) {
                        Text(
                            if (confirmDelete) "برای تأیید حذف دوباره بزن" else "حذف این کار",
                            color = ExpenseColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val dm: Long = if (dateText.isBlank()) 0L else (parseJalali(dateText) ?: -1L)
                val hm = if (timeText.isBlank()) null else parseTime(timeText)
                if (title.isBlank()) {
                    titleErr = true
                } else if (dm == -1L) {
                    dateErr = true
                } else if (dm > 0L && timeText.isNotBlank() && hm == null) {
                    timeErr = true
                } else {
                    val due = if (dm > 0L) combineDateTime(dm, hm) else 0L
                    onSave(
                        Todo(
                            id = base?.id ?: 0L,
                            title = title.trim(),
                            note = note.trim(),
                            done = base?.done ?: false,
                            dueDate = due,
                            hasTime = due > 0L && hm != null,
                            priority = priority,
                            category = category.trim(),
                            repeatMode = if (due > 0L) repeatMode else 0,
                            remind = remind && due > 0L,
                            subs = encodeSubs(subs),
                            doneAt = base?.doneAt ?: 0L
                        )
                    )
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
