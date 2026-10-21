package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

private val Context.taskBackupDataStore: DataStore<Preferences> by preferencesDataStore(name = "tasks_backup_preferences")

data class TaskBackupMetadata(
    val count: Int,
    val timestampMillis: Long,
    val formattedDate: String
)

/**
 * TaskBackupDataStore exports and imports tasks using Jetpack DataStore Preferences,
 * preserving user data locally.
 */
class TaskBackupDataStore(private val context: Context) {

    private object PreferencesKeys {
        val BACKUP_TASKS_JSON = stringPreferencesKey("backup_tasks_json")
        val BACKUP_TIMESTAMP = longPreferencesKey("backup_timestamp")
        val BACKUP_TASK_COUNT = intPreferencesKey("backup_task_count")
    }

    val lastBackupMetadata: Flow<TaskBackupMetadata?> = context.taskBackupDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val timestamp = preferences[PreferencesKeys.BACKUP_TIMESTAMP]
            val count = preferences[PreferencesKeys.BACKUP_TASK_COUNT]
            if (timestamp != null && count != null) {
                val formatter = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                TaskBackupMetadata(
                    count = count,
                    timestampMillis = timestamp,
                    formattedDate = formatter.format(Date(timestamp))
                )
            } else {
                null
            }
        }

    /**
     * Exports current list of tasks to DataStore as JSON.
     */
    suspend fun backupTasks(tasks: List<Task>): TaskBackupMetadata {
        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("isCompleted", task.isCompleted)
                put("category", task.category)
                put("priority", task.priority)
                put("dueDateMillis", task.dueDateMillis ?: JSONObject.NULL)
                put("tags", task.tags)
                put("createdAt", task.createdAt)
            }
            jsonArray.put(obj)
        }

        val jsonString = jsonArray.toString()
        val now = System.currentTimeMillis()

        context.taskBackupDataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_TASKS_JSON] = jsonString
            preferences[PreferencesKeys.BACKUP_TIMESTAMP] = now
            preferences[PreferencesKeys.BACKUP_TASK_COUNT] = tasks.size
        }

        val formatter = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return TaskBackupMetadata(
            count = tasks.size,
            timestampMillis = now,
            formattedDate = formatter.format(Date(now))
        )
    }

    /**
     * Reads saved tasks from DataStore and converts back to List<Task>.
     */
    suspend fun restoreTasks(): List<Task> {
        val preferences = context.taskBackupDataStore.data.first()
        val jsonString = preferences[PreferencesKeys.BACKUP_TASKS_JSON] ?: return emptyList()

        val restored = mutableListOf<Task>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dueDate = if (obj.isNull("dueDateMillis")) null else obj.optLong("dueDateMillis")
                val task = Task(
                    id = obj.optLong("id", 0L),
                    title = obj.optString("title", ""),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    category = obj.optString("category", "General"),
                    priority = obj.optString("priority", "MEDIUM"),
                    dueDateMillis = dueDate,
                    tags = obj.optString("tags", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                if (task.title.isNotBlank()) {
                    restored.add(task)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return restored
    }

    /**
     * Clears existing backup in DataStore.
     */
    suspend fun clearBackup() {
        context.taskBackupDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.BACKUP_TASKS_JSON)
            preferences.remove(PreferencesKeys.BACKUP_TIMESTAMP)
            preferences.remove(PreferencesKeys.BACKUP_TASK_COUNT)
        }
    }
}
