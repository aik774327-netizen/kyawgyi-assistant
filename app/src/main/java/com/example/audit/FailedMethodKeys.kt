package com.example.audit

import java.util.Locale

/**
 * ISSUE-036 — ONE canonical failed-method key from ONE factory.
 *
 * Before this change the Failed-Method Memory was fragmented across four key
 * namespaces (executor `component_operation`, chat-pipeline cleaned
 * transcript, hybrid preprocessed text, IR raw query), so a block minted in
 * one namespace was invisible to the others — ER-007's own key
 * (`app_launch_ယူကျု`) was consulted by NO execution path.
 *
 * Canonical form: `v2|<component>|<target>` where the component is the
 * normalized action family (pipeline intents and executor components are
 * aliased onto the same family) and the target is the normalized target of
 * the action. Because the key derives from intent + target — never from the
 * raw transcript — rephrasings of the SAME failed method map to the same key,
 * while a DIFFERENT target or action maps to a different key.
 *
 * Legacy migration: keys recorded by older builds are never rewritten in
 * storage. [canonicalOrSelf] parses a legacy `component_operation` key into
 * its canonical form at record/lookup time, so an on-device legacy entry
 * (e.g. `app_launch_ယူကျု`) is found by any canonical lookup.
 */
object FailedMethodKeys {

    const val PREFIX = "v2|"

    /** Pipeline intent / executor component -> canonical action family. */
    private val COMPONENT_ALIASES = mapOf(
        "open_app" to "app_launch",
        "launch_app" to "app_launch",
        "open_application" to "app_launch",
        "app_launch" to "app_launch",
        "close_app" to "app_close",
        "app_close" to "app_close",
        "call" to "phone_call",
        "phone_call" to "phone_call",
        "make_call" to "phone_call",
        "send_sms" to "send_sms",
        "send_message" to "send_sms",
        "wifi" to "wifi_control",
        "wifi_toggle" to "wifi_control",
        "wifi_control" to "wifi_control",
        "bluetooth" to "bluetooth_control",
        "bluetooth_toggle" to "bluetooth_control",
        "bluetooth_control" to "bluetooth_control",
        "flashlight" to "flashlight_action",
        "torch" to "flashlight_action",
        "flashlight_action" to "flashlight_action",
        "ocr" to "screen_ocr",
        "screen_ocr" to "screen_ocr",
        "tts_synthesis" to "tts_synthesis"
    )

    /**
     * Heads a LEGACY stored key may start with. Legacy keys in persistent
     * storage were minted ONLY by FailureMemoryEngine's sync
     * (`"${component}_${operation}"`), so only real executor components are
     * parseable heads — free-text keys that merely contain underscores
     * (e.g. a test key like `call_contact_test`) are left untouched.
     */
    private val LEGACY_COMPONENT_HEADS = setOf(
        "app_close",
        "app_launch",
        "bluetooth_control",
        "flashlight_action",
        "phone_call",
        "screen_ocr",
        "send_sms",
        "tts_synthesis",
        "wifi_control"
    )

    fun normalizeText(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun normalizeComponent(raw: String): String {
        val normalized = normalizeText(raw)
        return COMPONENT_ALIASES[normalized] ?: normalized
    }

    /** The canonical key for a structured action: intent/action + target. */
    fun canonical(component: String, target: String): String =
        PREFIX + normalizeComponent(component) + "|" + normalizeText(target)

    /**
     * Parses a legacy key into canonical form, or returns null when the key
     * is not a recognizable legacy `component_operation` key. A split point is
     * accepted only when the head names a known action family (longest head
     * wins, so `app_launch_ယူကျု` parses as app_launch + ယူကျု, and
     * `tts_synthesis_speak_burmese` as tts_synthesis + speak_burmese).
     */
    fun fromLegacyKey(rawKey: String): String? {
        val key = normalizeText(rawKey)
        if (key.startsWith(PREFIX)) return key
        val underscorePositions = key.indices.filter { key[it] == '_' }
        // Longest head first.
        for (pos in underscorePositions.asReversed()) {
            val head = key.substring(0, pos)
            val tail = key.substring(pos + 1)
            if (tail.isBlank()) continue
            if (head in LEGACY_COMPONENT_HEADS) {
                return canonical(head, tail)
            }
        }
        return null
    }

    /**
     * The key used for storage and lookup: canonical form when the raw key is
     * structured or a parseable legacy key; otherwise the normalized raw key
     * itself (free-text keys stay self-consistent).
     */
    fun canonicalOrSelf(rawKey: String): String =
        fromLegacyKey(rawKey) ?: normalizeText(rawKey)
}
