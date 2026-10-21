package com.example

import com.example.data.Task
import com.example.ui.components.TaskCategory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskManagementUnitTest {

    @Test
    fun task_creationWithDueDatesAndTags() {
        val now = System.currentTimeMillis()
        val task = Task(
            id = 1,
            title = "Prepare quarterly report",
            isCompleted = false,
            priority = "HIGH",
            category = "Work",
            dueDateMillis = now,
            tags = "Work,Urgent"
        )

        assertEquals("Prepare quarterly report", task.title)
        assertEquals("Work", task.category)
        assertEquals("HIGH", task.priority)
        assertEquals(now, task.dueDateMillis)
        assertEquals("Work,Urgent", task.tags)
        assertFalse(task.isCompleted)
    }

    @Test
    fun taskCategory_fromStringResolution() {
        assertEquals(TaskCategory.WORK, TaskCategory.fromString("Work"))
        assertEquals(TaskCategory.PERSONAL, TaskCategory.fromString("Personal"))
        assertEquals(TaskCategory.HEALTH, TaskCategory.fromString("Health"))
        assertEquals(TaskCategory.STUDY, TaskCategory.fromString("Study"))
        assertEquals(TaskCategory.SHOPPING, TaskCategory.fromString("Shopping"))
        // Fallback for unknown
        assertEquals(TaskCategory.WORK, TaskCategory.fromString("UnknownCategory"))
    }

    @Test
    fun taskBackup_serializationStructure() {
        val tasks = listOf(
            Task(id = 10, title = "Task Alpha", isCompleted = true, priority = "LOW", category = "Personal", dueDateMillis = 1725840000000L, tags = "Personal"),
            Task(id = 20, title = "Task Beta", isCompleted = false, priority = "HIGH", category = "Work", dueDateMillis = null, tags = "Work")
        )

        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("isCompleted", task.isCompleted)
                put("priority", task.priority)
                put("category", task.category)
                put("dueDateMillis", task.dueDateMillis ?: JSONObject.NULL)
                put("tags", task.tags)
            }
            jsonArray.put(obj)
        }

        assertEquals(2, jsonArray.length())
        val firstObj = jsonArray.getJSONObject(0)
        assertEquals("Task Alpha", firstObj.getString("title"))
        assertTrue(firstObj.getBoolean("isCompleted"))
        assertEquals(1725840000000L, firstObj.getLong("dueDateMillis"))

        val secondObj = jsonArray.getJSONObject(1)
        assertEquals("Task Beta", secondObj.getString("title"))
        assertFalse(secondObj.getBoolean("isCompleted"))
        assertTrue(secondObj.isNull("dueDateMillis"))
    }
}
