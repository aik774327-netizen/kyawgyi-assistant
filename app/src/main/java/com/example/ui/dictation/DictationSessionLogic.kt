package com.example.ui.dictation

import java.text.Normalizer

/**
 * Pure-Kotlin logic for the native Voice Typing (dictation) screen.
 *
 * This replicates, natively, the feature behaviour of the standalone
 * Kyawgyi Myanmar Voice Typing v2.1 HTML page:
 *  - final transcripts are inserted at the caret (never the interim text),
 *  - the character count excludes whitespace and is labelled "စာလုံး",
 *  - auto-restart after an unexpected recognizer stop is bounded
 *    (at most 5 restarts inside a 15-second window),
 *  - the document (text + language + toggles) is persisted locally.
 *
 * Everything here is free of Android types so it can be unit-tested on the JVM.
 */

/** Languages offered by the dictation screen (HTML v2.1 parity). */
val DICTATION_SUPPORTED_LANGUAGES: List<String> = listOf("my-MM", "en-US", "th-TH")

const val DICTATION_DEFAULT_LANGUAGE: String = "my-MM"

fun validateDictationLanguage(code: String?): String =
    DICTATION_SUPPORTED_LANGUAGES.firstOrNull { it.equals(code, ignoreCase = true) }
        ?: DICTATION_DEFAULT_LANGUAGE

/**
 * Document-safe Myanmar/Unicode cleanup for dictated text.
 *
 * This is the Unicode-cleanup subset of MyanmarSTEngine.normalizeMyanmarText
 * (NFC normalization, zero-width character stripping, tone-dot / visarga
 * de-duplication). The command-matching parts of that function — deleting
 * ALL whitespace and rewriting sound-alike words — are deliberately NOT
 * applied here: they would corrupt a dictated document (word spacing and
 * the speaker's actual words must be preserved).
 */
object DictationTextNormalizer {

    private val ZERO_WIDTH_CHARS = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")
    private val WHITESPACE_RUN = Regex("\\s+")

    fun normalizeSegment(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
        normalized = normalized.replace(ZERO_WIDTH_CHARS, "")
        normalized = normalized.replace(Regex("့+"), "့")
        normalized = normalized.replace(Regex("း+"), "း")
        // Preserve the speaker's spacing, but never let a segment carry
        // leading/trailing whitespace or multi-space runs into the document.
        normalized = normalized.replace(WHITESPACE_RUN, " ")
        return normalized.trim()
    }
}

/** Result of inserting one final transcript segment into the document. */
data class DictationInsertion(
    val newText: String,
    val newCaret: Int
)

object DictationText {

    /**
     * Insert a final transcript at the current selection, replacing any
     * selected range. The segment is normalized and followed by one
     * trailing space (HTML v2.1 parity). Returns null when the segment is
     * empty after normalization (nothing to insert).
     */
    fun insertTranscript(
        document: String,
        selectionStart: Int,
        selectionEnd: Int,
        rawTranscript: String?
    ): DictationInsertion? {
        val clean = DictationTextNormalizer.normalizeSegment(rawTranscript)
        if (clean.isEmpty()) return null
        val start = selectionStart.coerceIn(0, document.length)
        val end = selectionEnd.coerceIn(start, document.length)
        val insertion = clean + " "
        val newText = document.substring(0, start) + insertion + document.substring(end)
        return DictationInsertion(newText = newText, newCaret = start + insertion.length)
    }

    /**
     * Character count excluding whitespace, counted by Unicode code point
     * (the HTML used [...text.replace(/\s/g, '')].length, i.e. code points).
     */
    fun countCharacters(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!Character.isWhitespace(cp)) count++
            i += Character.charCount(cp)
        }
        return count
    }
}

/**
 * Bounded auto-restart guard, mirroring the HTML v2.1 restartAllowed():
 * every restart attempt is timestamped; at most [maxRestarts] attempts are
 * allowed inside any [windowMs] window. Blocked attempts are still recorded,
 * exactly like the HTML, so hammering the recognizer cannot reset the budget.
 */
class RestartGuard(
    private val maxRestarts: Int = 5,
    private val windowMs: Long = 15_000L
) {
    private val restartTimesMs = mutableListOf<Long>()

    /** Record one restart attempt at [nowMs]; true when it may proceed. */
    @Synchronized
    fun recordRestart(nowMs: Long): Boolean {
        prune(nowMs)
        restartTimesMs.add(nowMs)
        return restartTimesMs.size <= maxRestarts
    }

    /** Number of restart attempts currently inside the window. */
    @Synchronized
    fun restartsInWindow(nowMs: Long): Int {
        prune(nowMs)
        return restartTimesMs.size
    }

    /** Clear the budget (called on an intentional user start). */
    @Synchronized
    fun reset() {
        restartTimesMs.clear()
    }

    private fun prune(nowMs: Long) {
        restartTimesMs.removeAll { nowMs - it >= windowMs }
    }
}

/** The persisted dictation document (text + the screen's own toggles). */
data class DictationDocument(
    val text: String = "",
    val language: String = DICTATION_DEFAULT_LANGUAGE,
    val autoRestart: Boolean = true,
    val autoSave: Boolean = true,
    val commandCheckEnabled: Boolean = true,
    val updatedAtMs: Long = 0L
) {
    /** Encode to a flat string map for DataStore Preferences storage. */
    fun encode(): Map<String, String> = mapOf(
        KEY_TEXT to text,
        KEY_LANGUAGE to language,
        KEY_AUTO_RESTART to autoRestart.toString(),
        KEY_AUTO_SAVE to autoSave.toString(),
        KEY_COMMAND_CHECK to commandCheckEnabled.toString(),
        KEY_UPDATED_AT to updatedAtMs.toString()
    )

    companion object {
        const val KEY_TEXT = "dictation_text"
        const val KEY_LANGUAGE = "dictation_language"
        const val KEY_AUTO_RESTART = "dictation_auto_restart"
        const val KEY_AUTO_SAVE = "dictation_auto_save"
        const val KEY_COMMAND_CHECK = "dictation_command_check"
        const val KEY_UPDATED_AT = "dictation_updated_at"

        /** Decode a stored map; every field falls back to an honest default. */
        fun decode(values: Map<String, String>): DictationDocument = DictationDocument(
            text = values[KEY_TEXT] ?: "",
            language = validateDictationLanguage(values[KEY_LANGUAGE]),
            autoRestart = values[KEY_AUTO_RESTART]?.toBooleanStrictOrNull() ?: true,
            autoSave = values[KEY_AUTO_SAVE]?.toBooleanStrictOrNull() ?: true,
            // Command-check defaults ON, including for documents stored
            // before the setting existed (user-approved default, 2026-10-22).
            commandCheckEnabled = values[KEY_COMMAND_CHECK]?.toBooleanStrictOrNull() ?: true,
            updatedAtMs = values[KEY_UPDATED_AT]?.toLongOrNull() ?: 0L
        )
    }
}

// ---------------------------------------------------------------
// Command routing decision (dictation finals → voice-command pipeline)
// ---------------------------------------------------------------

/** Result of asking the app's command pipeline about one final segment. */
enum class DictationCommandCheckResult { HANDLED, NOT_HANDLED, FAILED }

/** What the dictation screen should do with one final segment. */
enum class DictationSegmentDecision { INSERT_AS_TEXT, EXECUTED_AS_COMMAND, SURFACE_ERROR }

/**
 * Pure routing decision for one final dictation segment:
 *  - command-check off → the segment is always plain text;
 *  - pipeline handled it → it was executed as a command, never filed as text;
 *  - pipeline did not handle it → plain dictated text, inserted at the caret;
 *  - pipeline threw → surface an honest error and drop the segment (it was
 *    meant as a command; silently filing it as text would hide the failure).
 */
fun decideDictationSegmentRoute(
    commandCheckEnabled: Boolean,
    checkResult: DictationCommandCheckResult?
): DictationSegmentDecision = when {
    !commandCheckEnabled -> DictationSegmentDecision.INSERT_AS_TEXT
    checkResult == DictationCommandCheckResult.HANDLED -> DictationSegmentDecision.EXECUTED_AS_COMMAND
    checkResult == DictationCommandCheckResult.FAILED -> DictationSegmentDecision.SURFACE_ERROR
    else -> DictationSegmentDecision.INSERT_AS_TEXT
}

/** Auto-save lifecycle shown next to the character count. */
enum class DictationSaveState { IDLE, SAVING, SAVED, DISABLED, FAILED }

/** Honest save-state label (HTML v2.1 wording parity). */
fun dictationSaveStateLabel(state: DictationSaveState, autoSaveEnabled: Boolean): String = when {
    !autoSaveEnabled -> "Auto-save ပိတ်ထားသည်"
    state == DictationSaveState.SAVING -> "သိမ်းဆည်းနေသည်…"
    state == DictationSaveState.SAVED -> "သိမ်းပြီး ✓"
    state == DictationSaveState.FAILED -> "သိမ်းမရပါ"
    else -> "စက်ထဲတွင် သိမ်းဆည်းထားသည်"
}

/** Classification of SpeechRecognizer failures for the dictation screen. */
enum class DictationErrorKind {
    PERMISSION_DENIED,
    NETWORK,
    LANGUAGE_NOT_SUPPORTED,
    AUDIO,
    NO_MATCH,
    SPEECH_TIMEOUT,
    RECOGNIZER_BUSY,
    SERVER,
    CLIENT,
    UNKNOWN
}

/**
 * Errors after which auto-restart must NOT be attempted: the user (or the
 * device) has to change something first. All other kinds may restart,
 * bounded by [RestartGuard].
 */
fun isFatalDictationError(kind: DictationErrorKind): Boolean =
    kind == DictationErrorKind.PERMISSION_DENIED || kind == DictationErrorKind.LANGUAGE_NOT_SUPPORTED

/** Short honest Burmese status line for each error kind. */
fun dictationErrorStatus(kind: DictationErrorKind): String = when (kind) {
    DictationErrorKind.PERMISSION_DENIED -> "မိုက်ခွင့်ပြုချက် လိုအပ်သည်"
    DictationErrorKind.NETWORK -> "ကွန်ရက်/အသံဝန်ဆောင်မှု အမှား"
    DictationErrorKind.LANGUAGE_NOT_SUPPORTED -> "ဘာသာစကား မပံ့ပိုးပါ"
    DictationErrorKind.AUDIO -> "မိုက်ခရိုဖုန်း မတွေ့ပါ"
    DictationErrorKind.NO_MATCH -> "အသံ မကိုက်ညီပါ"
    DictationErrorKind.SPEECH_TIMEOUT -> "အသံမကြားရသေးပါ"
    DictationErrorKind.RECOGNIZER_BUSY -> "အသံဖမ်းစနစ် မအားသေးပါ"
    DictationErrorKind.SERVER -> "အသံဝန်ဆောင်မှု ဆာဗာ အမှား"
    DictationErrorKind.CLIENT -> "အသံဖမ်းစနစ် အမှား"
    DictationErrorKind.UNKNOWN -> "အသံဖမ်းစနစ် အမှား"
}

/** Longer Burmese guidance shown to the user for each error kind. */
fun dictationErrorGuidance(kind: DictationErrorKind): String = when (kind) {
    DictationErrorKind.PERMISSION_DENIED ->
        "မိုက်ခွင့်ပြုချက် မရပါ။ ဖုန်း Settings → Apps → Kyaw Gyi AI → Permissions မှာ Microphone ကို ခွင့်ပြုပြီး ပြန်စပါ။"
    DictationErrorKind.NETWORK ->
        "Speech Recognition အတွက် ကွန်ရက် လိုအပ်နိုင်သည်။ Internet ချိတ်ဆက်မှု စစ်ပြီး ပြန်စမ်းပါ။"
    DictationErrorKind.LANGUAGE_NOT_SUPPORTED ->
        "ရွေးချယ်ထားသော ဘာသာစကားကို ဤဖုန်း၏ Speech Recognition ဝန်ဆောင်မှုက မပံ့ပိုးပါ။ ဘာသာစကားပြောင်းပြီး စမ်းကြည့်ပါ။"
    DictationErrorKind.AUDIO ->
        "မိုက်ခရိုဖုန်းကို စစ်ဆေးပါ။ အခြား app တစ်ခု သုံးနေပါက ပိတ်ပြီး ပြန်စမ်းပါ။"
    DictationErrorKind.NO_MATCH ->
        "ပြောလိုက်သော အသံကို မသတ်မှတ်နိုင်ပါ။ ထပ်ပြောကြည့်ပါ။"
    DictationErrorKind.SPEECH_TIMEOUT ->
        "အသံမကြားရသေးပါ။ Mic ကို နှိပ်ပြီး ထပ်ပြောကြည့်ပါ။"
    DictationErrorKind.RECOGNIZER_BUSY ->
        "အသံဖမ်းစနစ် မအားသေးပါ။ ခဏစောင့်ပြီး ပြန်စမ်းပါ။"
    DictationErrorKind.SERVER ->
        "အသံဝန်ဆောင်မှု ဆာဗာဘက်က အမှား ဖြစ်ပွားသည်။ ခဏစောင့်ပြီး ပြန်စမ်းပါ။"
    DictationErrorKind.CLIENT ->
        "အသံဖမ်းစနစ် အမှား ဖြစ်ပွားသည်။ ပြန်စမ်းကြည့်ပါ။"
    DictationErrorKind.UNKNOWN ->
        "အသံဖမ်းစနစ် အမှား ဖြစ်ပွားသည်။ ပြန်စမ်းကြည့်ပါ။"
}
