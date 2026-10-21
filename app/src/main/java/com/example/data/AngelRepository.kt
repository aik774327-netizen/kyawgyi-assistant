package com.example.data

import kotlinx.coroutines.flow.Flow

class AngelRepository(
    private val chatDao: ChatDao,
    private val memoryDao: MemoryDao,
    private val dailyTaskDao: DailyTaskDao,
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val focusSessionDao: FocusSessionDao,
    private val commandLogDao: CommandLogDao,
    private val customShortcutDao: CustomShortcutDao,
    private val apiService: GeminiApiService,
    private val dailyPlannerDao: DailyPlannerDao? = null
) {
    val allMessages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()
    val last50Messages: Flow<List<ChatMessageEntity>> = chatDao.getLast50Messages()
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    val allDailyTasks: Flow<List<DailyTaskEntity>> = dailyTaskDao.getAllTasks()
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val allHabits: Flow<List<HabitEntity>> = habitDao.getAllHabits()
    val allFocusSessions: Flow<List<FocusSessionEntity>> = focusSessionDao.getAllFocusSessions()
    val allCommandLogs: Flow<List<CommandLogEntity>> = commandLogDao.getAllCommandLogs()
    val last50CommandLogs: Flow<List<CommandLogEntity>> = commandLogDao.getLast50CommandLogs()
    val allCustomShortcuts: Flow<List<CustomShortcutEntity>> = customShortcutDao.getAllCustomShortcuts()
    val allPlannerItems: Flow<List<DailyPlannerItemEntity>> = dailyPlannerDao?.getAllPlannerItems() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val plannerTasks: Flow<List<DailyPlannerItemEntity>> = dailyPlannerDao?.getDailyTasks() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val plannerHabits: Flow<List<DailyPlannerItemEntity>> = dailyPlannerDao?.getHabits() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun insertMessage(message: ChatMessageEntity): Long = chatDao.insertMessage(message)

    suspend fun deleteMessageById(id: Long) = chatDao.deleteMessageById(id)

    suspend fun clearChatHistory() = chatDao.clearHistory()

    // Command Logs
    suspend fun insertCommandLog(log: CommandLogEntity): Long = commandLogDao.insertCommandLog(log)

    suspend fun deleteCommandLogById(id: Long) = commandLogDao.deleteCommandLogById(id)

    suspend fun clearCommandLogs() = commandLogDao.clearCommandLogs()

    val failedCommandLogs: Flow<List<CommandLogEntity>> = commandLogDao.getFailedCommandLogs()

    suspend fun findRecentFailure(queryText: String): CommandLogEntity? = commandLogDao.findRecentFailure(queryText)

    // Custom Shortcuts
    suspend fun insertCustomShortcut(shortcut: CustomShortcutEntity): Long = customShortcutDao.insertCustomShortcut(shortcut)

    suspend fun updateCustomShortcut(shortcut: CustomShortcutEntity) = customShortcutDao.updateCustomShortcut(shortcut)

    suspend fun deleteCustomShortcut(shortcut: CustomShortcutEntity) = customShortcutDao.deleteCustomShortcut(shortcut)

    suspend fun insertMemory(memory: MemoryEntity): Long = memoryDao.insertMemory(memory)

    suspend fun deleteMemory(memory: MemoryEntity) = memoryDao.deleteMemory(memory)

    // Daily Tasks
    suspend fun insertDailyTask(task: DailyTaskEntity): Long = dailyTaskDao.insertTask(task)
    suspend fun updateDailyTask(task: DailyTaskEntity) = dailyTaskDao.updateTask(task)
    suspend fun deleteDailyTask(task: DailyTaskEntity) = dailyTaskDao.deleteTask(task)

    // Task Entity (Room)
    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)
    suspend fun updateTask(task: Task) = taskDao.updateTask(task)
    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
    suspend fun deleteTaskById(id: Long) = taskDao.deleteTaskById(id)

    // Habits
    suspend fun insertHabit(habit: HabitEntity): Long = habitDao.insertHabit(habit)
    suspend fun updateHabit(habit: HabitEntity) = habitDao.updateHabit(habit)
    suspend fun deleteHabit(habit: HabitEntity) = habitDao.deleteHabit(habit)

    // Focus Sessions
    suspend fun insertFocusSession(session: FocusSessionEntity): Long = focusSessionDao.insertFocusSession(session)
    suspend fun updateFocusSession(session: FocusSessionEntity) = focusSessionDao.updateFocusSession(session)
    suspend fun deleteFocusSession(session: FocusSessionEntity) = focusSessionDao.deleteFocusSession(session)

    // Daily Planner Items & Habits
    suspend fun insertPlannerItem(item: DailyPlannerItemEntity): Long = dailyPlannerDao?.insertItem(item) ?: -1L
    suspend fun updatePlannerItem(item: DailyPlannerItemEntity) = dailyPlannerDao?.updateItem(item)
    suspend fun deletePlannerItem(item: DailyPlannerItemEntity) = dailyPlannerDao?.deleteItem(item)
    suspend fun deletePlannerItemById(id: Long) = dailyPlannerDao?.deleteItemById(id)
    suspend fun clearCompletedPlannerTasks() = dailyPlannerDao?.clearCompletedTasks()

    suspend fun generateAiResponse(
        prompt: String,
        mode: String,
        engine: String,
        systemPrompt: String,
        history: List<ChatMessageEntity>
    ): Pair<String, String?> {
        return apiService.generateResponse(prompt, mode, engine, systemPrompt, history)
    }

    suspend fun seedInitialDataIfEmpty() {
        if (chatDao.getMessageCount() == 0) {
            val welcomeText = "ဟေး... မင်္ဂလာပါဗျာ! ကျွန်တော်က သင့်ရဲ့ နေ့စဉ်ဘဝနဲ့ နည်းပညာကိစ္စတွေမှာ အတူတိုင်ပင်ဖော်၊ ကူညီဖော်ဖြစ်တဲ့ **Personal AI Companion (ကျော်ကြီး / Nova / Aura)** ပါ။\n\n" +
                    "• **Warm & Empathetic Friend**: စိတ်ဖိစီးနေချိန် နှစ်သိမ့်ပေးဖို့ဖြစ်ဖြစ်၊ ပျော်စရာတွေ မျှဝေဖို့ဖြစ်ဖြစ် သူငယ်ချင်းကောင်းတစ်ယောက်လို အမြဲနားထောင်ပေးပါမယ်။\n" +
                    "• **Sharp Life & Tech Partner**: နည်းပညာပြဿနာဖြေရှင်းတာ၊ အစီအစဉ်ဆွဲတာနဲ့ ဖုန်းထိန်းချုပ်မှုတွေကို မြန်မာလိုရော English လိုပါ သဘာဝကျကျ ကူညီပေးနိုင်ပါတယ်။\n\n" +
                    "ဒီနေ့ရော ဘယ်လိုနေလဲ၊ စိတ်ထဲရှိတာ ပေါ့ပေါ့ပါးပါး ပြောပြပါဦးနော်!"

            chatDao.insertMessage(
                ChatMessageEntity(
                    role = "angel",
                    content = welcomeText,
                    responseMode = "QUICK",
                    engineType = "Response Engine"
                )
            )
        }

        if (memoryDao.getMemoryCount() == 0) {
            memoryDao.insertMemory(
                MemoryEntity(
                    key = "Primary Language",
                    value = "Fluent Conversational Burmese (မြန်မာဘာသာ) & Seamless English",
                    category = "Preference",
                    isPinned = true
                )
            )
            memoryDao.insertMemory(
                MemoryEntity(
                    key = "Companion Persona",
                    value = "Warm, Grounded, Witty, Empathetic with Candor & Active Listener",
                    category = "System Memory",
                    isPinned = true
                )
            )
        }

        if (dailyTaskDao.getTaskCount() == 0) {
            dailyTaskDao.insertTask(
                DailyTaskEntity(
                    title = "Review Myanmar Speech Recognition model output",
                    category = "AI Research",
                    isCompleted = false,
                    dueDate = "Today"
                )
            )
            dailyTaskDao.insertTask(
                DailyTaskEntity(
                    title = "Organize daily habits and focus routines",
                    category = "Personal",
                    isCompleted = true,
                    dueDate = "Today"
                )
            )
        }

        if (taskDao.getTaskCount() == 0) {
            taskDao.insertTask(
                Task(
                    title = "Review Myanmar Speech Recognition model output",
                    category = "Work",
                    isCompleted = false
                )
            )
            taskDao.insertTask(
                Task(
                    title = "Organize daily habits and focus routines",
                    category = "Personal",
                    isCompleted = true
                )
            )
            taskDao.insertTask(
                Task(
                    title = "Buy fresh vegetables and organic groceries",
                    category = "Shopping",
                    isCompleted = false
                )
            )
        }

        if (habitDao.getHabitCount() == 0) {
            habitDao.insertHabit(
                HabitEntity(
                    title = "Morning Mindful Meditation",
                    streakCount = 5,
                    frequency = "Daily",
                    isCompletedToday = true
                )
            )
            habitDao.insertHabit(
                HabitEntity(
                    title = "Daily Speech Recognition Practice (20 mins)",
                    streakCount = 12,
                    frequency = "Daily",
                    isCompletedToday = false
                )
            )
        }

        if (focusSessionDao.getFocusSessionCount() == 0) {
            focusSessionDao.insertFocusSession(
                FocusSessionEntity(
                    sessionName = "Deep Work - System Architecture Analysis",
                    durationMinutes = 45,
                    notes = "Evaluated local Room DB schema and DAO reactive flows"
                )
            )
        }

        if (customShortcutDao.getCustomShortcutCount() == 0) {
            customShortcutDao.insertCustomShortcut(
                CustomShortcutEntity(
                    phrase = "ဖေ့ဘုတ် ဖွင့်ပါ",
                    targetPackageOrAction = "com.facebook.katana",
                    description = "Open Facebook Mobile Application"
                )
            )
            customShortcutDao.insertCustomShortcut(
                CustomShortcutEntity(
                    phrase = "ယူကျု ဖွင့်ပါ",
                    targetPackageOrAction = "com.google.android.youtube",
                    description = "Open YouTube Player App"
                )
            )
            customShortcutDao.insertCustomShortcut(
                CustomShortcutEntity(
                    phrase = "ဆက်တင် ဖွင့်ပါ",
                    targetPackageOrAction = "com.android.settings",
                    description = "Open Android System Settings"
                )
            )
        }

        if (commandLogDao.getCommandLogCount() == 0) {
            commandLogDao.insertCommandLog(
                CommandLogEntity(
                    commandText = "ကျော်ကြီး ပင်မမျက်နှာပြင်",
                    commandType = "HOME",
                    status = "SUCCESS",
                    actionDetail = "Executed Accessibility ACTION_HOME"
                )
            )
            commandLogDao.insertCommandLog(
                CommandLogEntity(
                    commandText = "ကျော်ကြီး နောက်သို့",
                    commandType = "BACK",
                    status = "SUCCESS",
                    actionDetail = "Executed Accessibility ACTION_BACK"
                )
            )
        }

        if (dailyPlannerDao != null && dailyPlannerDao.getTotalItemCount() == 0) {
            dailyPlannerDao.insertItem(
                DailyPlannerItemEntity(
                    title = "Review Myanmar Speech Recognition model output",
                    category = "Work",
                    isHabit = false,
                    isCompleted = false,
                    priority = "High",
                    scheduledDate = "Today"
                )
            )
            dailyPlannerDao.insertItem(
                DailyPlannerItemEntity(
                    title = "Organize daily habits and focus routines",
                    category = "Personal",
                    isHabit = false,
                    isCompleted = true,
                    priority = "Medium",
                    scheduledDate = "Today"
                )
            )
            dailyPlannerDao.insertItem(
                DailyPlannerItemEntity(
                    title = "30-Min Deep Focus Session",
                    category = "Work",
                    isHabit = true,
                    isCompleted = true,
                    streakCount = 7,
                    targetDaysPerWeek = 5,
                    weeklyCompletionHistory = "1,1,1,1,1,0,0"
                )
            )
            dailyPlannerDao.insertItem(
                DailyPlannerItemEntity(
                    title = "Daily Meditation & Breathing Exercise",
                    category = "Health",
                    isHabit = true,
                    isCompleted = true,
                    streakCount = 14,
                    targetDaysPerWeek = 7,
                    weeklyCompletionHistory = "1,1,1,1,1,1,1"
                )
            )
            dailyPlannerDao.insertItem(
                DailyPlannerItemEntity(
                    title = "Read 15 Pages of Technical Docs",
                    category = "Study",
                    isHabit = true,
                    isCompleted = false,
                    streakCount = 4,
                    targetDaysPerWeek = 6,
                    weeklyCompletionHistory = "1,1,1,1,0,0,0"
                )
            )
        }
    }
}
