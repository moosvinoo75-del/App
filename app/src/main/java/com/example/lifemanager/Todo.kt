package com.example.lifemanager

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
    val remind: Int = 0,
    val subs: String = "",
    val doneAt: Long = 0L
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY done, priority DESC, dueDate")
    fun all(): Flow<List<Todo>>
    @Query("SELECT * FROM todos")
    suspend fun list(): List<Todo>
    @Insert suspend fun insert(t: Todo)
    @Insert suspend fun insertAll(l: List<Todo>)
    @Update suspend fun update(t: Todo)
    @Delete suspend fun delete(t: Todo)
    @Query("DELETE FROM todos")
    suspend fun clear()
}
