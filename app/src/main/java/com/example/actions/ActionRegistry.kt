package com.example.actions

import com.example.pipeline.contract.RiskLevel

/**
 * Action Definition Descriptor in ActionRegistry
 */
data class ActionDefinition(
    val actionType: String,
    val description: String,
    val requiredParameters: List<String>,
    val optionalParameters: List<String>,
    val riskLevel: RiskLevel,
    val requiredPermissions: List<String>,
    val defaultRequiresConfirmation: Boolean,
    val confirmationPromptTemplateMM: String? = null,
    val category: ActionCategory
)

enum class ActionCategory {
    APP_NAVIGATION,
    COMMUNICATION,
    DEVICE_CONTROL,
    ACCESSIBILITY_INTERACTION,
    MEDIA_CONTROL,
    ORGANIZATION,
    UTILITY
}

/**
 * Centralized Action Registry
 * Only actions registered here can be executed by the intelligent pipeline.
 */
object ActionRegistry {

    private val registry: Map<String, ActionDefinition> = listOf(
        ActionDefinition(
            actionType = "OPEN_APP",
            description = "Launch or switch to an installed application",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("package"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.APP_NAVIGATION
        ),
        ActionDefinition(
            actionType = "CALL",
            description = "Initiate phone call to a contact or phone number",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("phoneNumber"),
            riskLevel = RiskLevel.HIGH,
            requiredPermissions = listOf("android.permission.CALL_PHONE"),
            defaultRequiresConfirmation = true,
            confirmationPromptTemplateMM = "{target} ထံ ဖုန်းခေါ်ဆိုပေးရမလားခင်ဗျာ။",
            category = ActionCategory.COMMUNICATION
        ),
        ActionDefinition(
            actionType = "SEND_MESSAGE",
            description = "Compose or send SMS or chat message",
            requiredParameters = listOf("target", "message"),
            optionalParameters = listOf("app"),
            riskLevel = RiskLevel.HIGH,
            requiredPermissions = listOf("android.permission.SEND_SMS"),
            defaultRequiresConfirmation = true,
            confirmationPromptTemplateMM = "{target} ဆီသို့ \"{message}\" ဟု မက်ဆေ့ခ်ျ ပို့ပေးရမလားခင်ဗျာ။",
            category = ActionCategory.COMMUNICATION
        ),
        ActionDefinition(
            actionType = "TAP",
            description = "Click or tap an on-screen element",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("x", "y"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.ACCESSIBILITY_INTERACTION
        ),
        ActionDefinition(
            actionType = "LONG_PRESS",
            description = "Long click an on-screen element",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("durationMs"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.ACCESSIBILITY_INTERACTION
        ),
        ActionDefinition(
            actionType = "SCROLL",
            description = "Scroll screen content in a given direction",
            requiredParameters = listOf("direction"), // UP, DOWN, LEFT, RIGHT
            optionalParameters = listOf("amount"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.ACCESSIBILITY_INTERACTION
        ),
        ActionDefinition(
            actionType = "BACK",
            description = "Navigate back in the UI stack",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.APP_NAVIGATION
        ),
        ActionDefinition(
            actionType = "HOME",
            description = "Navigate to Android Home Screen",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.APP_NAVIGATION
        ),
        ActionDefinition(
            actionType = "RECENTS",
            description = "Open Recent Applications screen",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.APP_NAVIGATION
        ),
        ActionDefinition(
            actionType = "NOTIFICATIONS",
            description = "Open Notification Drawer",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.APP_NAVIGATION
        ),
        ActionDefinition(
            actionType = "QUICK_SETTINGS",
            description = "Open Quick Settings Panel",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.APP_NAVIGATION
        ),
        ActionDefinition(
            actionType = "OPEN_SETTINGS",
            description = "Navigate directly to a specific Android System Setting or Permission page via DirectSettingsRouter",
            requiredParameters = emptyList(),
            optionalParameters = listOf("settingsDestination", "target"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.APP_NAVIGATION
        ),
        ActionDefinition(
            actionType = "SELECT_TEXT",
            description = "Select text on active screen",
            requiredParameters = listOf("targetText"),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.ACCESSIBILITY_INTERACTION
        ),
        ActionDefinition(
            actionType = "COPY",
            description = "Copy selected content to system clipboard",
            requiredParameters = listOf("content"),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "PASTE",
            description = "Paste clipboard text into focused field",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.ACCESSIBILITY_INTERACTION
        ),
        ActionDefinition(
            actionType = "DELETE",
            description = "Delete contact, file, note or message item",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("itemType"),
            riskLevel = RiskLevel.CRITICAL,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = true,
            confirmationPromptTemplateMM = "{target} ကို အမှန်တကယ် ဖျက်ပစ်မှာ သေချာပါသလားခင်ဗျာ။",
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "VOLUME_UP",
            description = "Increase media/ringtone sound volume",
            requiredParameters = emptyList(),
            optionalParameters = listOf("step", "percent"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "VOLUME_DOWN",
            description = "Decrease media/ringtone sound volume",
            requiredParameters = emptyList(),
            optionalParameters = listOf("step", "percent"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "TAKE_SCREENSHOT",
            description = "Capture current screen image",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "FLASHLIGHT",
            description = "Turn device torch on or off",
            requiredParameters = listOf("enable"),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.CAMERA"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "WIFI_TOGGLE",
            description = "Enable or disable Wi-Fi adapter / open Wi-Fi settings",
            requiredParameters = listOf("enable"),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.MEDIUM,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "BLUETOOTH_TOGGLE",
            description = "Enable or disable Bluetooth / open Bluetooth settings",
            requiredParameters = listOf("enable"),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.MEDIUM,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "BRIGHTNESS_UP",
            description = "Increase screen brightness",
            requiredParameters = emptyList(),
            optionalParameters = listOf("step", "percent"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.WRITE_SETTINGS"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "BRIGHTNESS_DOWN",
            description = "Decrease screen brightness",
            requiredParameters = emptyList(),
            optionalParameters = listOf("step", "percent"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.WRITE_SETTINGS"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "SET_ALARM",
            description = "Schedule clock alarm for a specified time",
            requiredParameters = listOf("time"),
            optionalParameters = listOf("label"),
            riskLevel = RiskLevel.MEDIUM,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.ORGANIZATION
        ),
        ActionDefinition(
            actionType = "SET_TIMER",
            description = "Start countdown timer for specified duration in minutes/seconds",
            requiredParameters = listOf("durationMinutes"),
            optionalParameters = listOf("label"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.ORGANIZATION
        ),
        ActionDefinition(
            actionType = "DICTATE",
            description = "Transcribe speech directly into focused input field",
            requiredParameters = listOf("text"),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "SEARCH",
            description = "Perform search query in Google or specified app",
            requiredParameters = listOf("query"),
            optionalParameters = listOf("targetApp"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "CUSTOM",
            description = "Custom macro or URL voice control shortcut",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("url", "actionPayload"),
            riskLevel = RiskLevel.MEDIUM,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "INGEST_FILE",
            description = "Ingest, hash, and route media or project document into sandboxed vault",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("fileUri", "mimeType", "category"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "SEARCH_FILES",
            description = "Search locally indexed ingested files, documents, and media",
            requiredParameters = listOf("query"),
            optionalParameters = listOf("category"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "ROUTE_ASSET",
            description = "Intelligently route and categorize project file without code overwrites",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("destinationCategory"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.UTILITY
        ),
        ActionDefinition(
            actionType = "BATTERY_STATUS",
            description = "Query current device battery percentage and charging state",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "IR_COMMAND",
            description = "Transmit infrared remote control code via hardware IR blaster",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("frequency", "pattern"),
            riskLevel = RiskLevel.LOW,
            requiredPermissions = listOf("android.permission.TRANSMIT_IR"),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        ),
        ActionDefinition(
            actionType = "SMART_HOME",
            description = "Control configured local or webhook Smart Home device",
            requiredParameters = listOf("target"),
            optionalParameters = listOf("state", "room"),
            riskLevel = RiskLevel.MEDIUM,
            requiredPermissions = emptyList(),
            defaultRequiresConfirmation = false,
            category = ActionCategory.DEVICE_CONTROL
        )
    ).associateBy { it.actionType }

    /**
     * Look up action definition by string identifier
     */
    fun getAction(actionType: String): ActionDefinition? {
        return registry[actionType.uppercase()]
    }

    /**
     * Check if an action is officially registered and approved
     */
    fun isApprovedAction(actionType: String): Boolean {
        return registry.containsKey(actionType.uppercase())
    }

    fun isRegistered(actionType: String): Boolean = isApprovedAction(actionType)

    /**
     * Get all registered actions
     */
    fun getAllActions(): List<ActionDefinition> {
        return registry.values.toList()
    }

    /**
     * Retrieve risk level for an action type. Defaults to LOW if unknown.
     */
    fun getRiskLevel(actionType: String): RiskLevel {
        return getAction(actionType)?.riskLevel ?: RiskLevel.LOW
    }

    /**
     * Retrieve required Android permissions for an action type.
     */
    fun getRequiredPermissions(actionType: String): List<String> {
        return getAction(actionType)?.requiredPermissions ?: emptyList()
    }

    /**
     * Check if an action requires user confirmation by default.
     */
    fun requiresConfirmation(actionType: String): Boolean {
        return getAction(actionType)?.defaultRequiresConfirmation ?: false
    }

    /**
     * Get missing required parameters for an action.
     */
    fun getMissingParameters(actionType: String, parameters: Map<String, Any?>): List<String> {
        val def = getAction(actionType) ?: return emptyList()
        return def.requiredParameters.filter { req ->
            val value = parameters[req]
            value == null || (value is String && value.isBlank())
        }
    }

    /**
     * Validate whether all required parameters are provided.
     */
    fun validateParameters(actionType: String, parameters: Map<String, Any?>): Boolean {
        return getMissingParameters(actionType, parameters).isEmpty()
    }

    /**
     * Generate localized confirmation prompt string if defined.
     */
    fun getConfirmationPrompt(actionType: String, target: String? = null, message: String? = null): String? {
        val def = getAction(actionType) ?: return null
        val template = def.confirmationPromptTemplateMM ?: return null
        var prompt = template
        if (target != null) {
            prompt = prompt.replace("{target}", target)
        }
        if (message != null) {
            prompt = prompt.replace("{message}", message)
        }
        return prompt
    }
}
