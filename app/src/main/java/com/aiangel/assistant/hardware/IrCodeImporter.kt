package com.aiangel.assistant.hardware

import com.aiangel.assistant.data.dao.IrCommandDao
import com.aiangel.assistant.data.entity.IrCommandEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reason: this phone's ConsumerIrManager only TRANSMITS infrared — it has
 * no IR receiver, so "point the old remote at the phone and capture it"
 * learning mode is not physically possible on stock hardware (would need
 * a USB-OTG IR receiver dongle or an ESP8266-style bridge device).
 *
 * This importer is the practical substitute: it bulk-loads a JSON code
 * set — either hand-entered from a manufacturer's published codes, or
 * captured externally (bridge device, spare-remote decoder app, an
 * oscilloscope reading) and exported to this format — through the same
 * validation discipline as the rest of the Universal Data Ingestion
 * Engine (ProjectDataRouter / SecurityValidator) rather than a raw insert.
 *
 * Expected JSON shape:
 * {
 *   "deviceName": "Kennede Fan",
 *   "brand": "Kennede",
 *   "commands": [
 *     { "label": "POWER", "frequencyHz": 38000, "pattern": "9000,4500,560,560,..." },
 *     { "label": "SPEED_UP", "frequencyHz": 38000, "pattern": "..." }
 *   ]
 * }
 */
sealed class ImportResult {
    data class Success(val deviceName: String, val importedCount: Int, val skippedCount: Int) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

class IrCodeImporter(
    private val irCommandDao: IrCommandDao
) {
    /**
     * Validates and imports a code set. Existing (deviceName, label) pairs
     * are skipped rather than silently overwritten, so a re-import never
     * clobbers a command the user has already verified against the real
     * appliance — overwriting verified data would be worse than a partial import.
     */
    suspend fun importCodeSet(json: String): ImportResult = withContext(Dispatchers.IO) {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return@withContext ImportResult.Failure("JSON format မှားနေပါတယ်: ${e.message}")
        }

        val deviceName = root.optString("deviceName").trim()
        if (deviceName.isEmpty()) {
            return@withContext ImportResult.Failure("\"deviceName\" field ပါရှိရပါမယ်")
        }

        val brand = root.optString("brand").takeIf { it.isNotBlank() }
        val commands = root.optJSONArray("commands")
        if (commands == null || commands.length() == 0) {
            return@withContext ImportResult.Failure("\"commands\" list ထဲမှာ command အနည်းဆုံး တစ်ခု ပါရပါမယ်")
        }

        var imported = 0
        var skipped = 0

        for (i in 0 until commands.length()) {
            val entry = commands.optJSONObject(i)
                ?: return@withContext ImportResult.Failure("commands[$i] က JSON object မဟုတ်ပါ")

            val validationOutcome = validateCommandEntry(entry, i)
            if (validationOutcome is ImportResult.Failure) {
                return@withContext validationOutcome
            }
            val validated = validationOutcome as ValidatedCommand

            val alreadyExists = irCommandDao.commandExists(deviceName, validated.label) > 0
            if (alreadyExists) {
                skipped++
                continue
            }

            irCommandDao.insert(
                IrCommandEntity(
                    deviceName = deviceName,
                    commandLabel = validated.label,
                    frequencyHz = validated.frequencyHz,
                    pattern = validated.pattern,
                    brand = brand,
                    isVerified = false
                )
            )
            imported++
        }

        ImportResult.Success(deviceName, imported, skipped)
    }

    private data class ValidatedCommand(val label: String, val frequencyHz: Int, val pattern: String)

    /** Returns ValidatedCommand on success, ImportResult.Failure on error — checked by the caller. */
    private fun validateCommandEntry(entry: JSONObject, index: Int): Any {
        val label = entry.optString("label").trim()
        if (label.isEmpty()) {
            return ImportResult.Failure("commands[$index]: \"label\" field ပါရှိရပါမယ်")
        }

        val frequencyHz = entry.optInt("frequencyHz", -1)
        if (frequencyHz !in 30000..60000) {
            return ImportResult.Failure(
                "commands[$index] (\"$label\"): frequencyHz က 30000-60000 Hz range ထဲမှာ ရှိရပါမယ်"
            )
        }

        val pattern = entry.optString("pattern").trim()
        val parsedPattern = pattern.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parsedPattern.isEmpty() || parsedPattern.size % 2 != 0) {
            return ImportResult.Failure(
                "commands[$index] (\"$label\"): \"pattern\" က comma-separated on/off pair (even count) ဖြစ်ရပါမယ်"
            )
        }
        if (parsedPattern.any { it <= 0 }) {
            return ImportResult.Failure(
                "commands[$index] (\"$label\"): pulse duration တွေဟာ positive integer ဖြစ်ရပါမယ်"
            )
        }

        return ValidatedCommand(label, frequencyHz, pattern)
    }

    /** Exports a device's stored commands back to the same JSON shape, for backup or sharing. */
    suspend fun exportCodeSet(deviceName: String, commands: List<IrCommandEntity>, brand: String? = null): String {
        val root = JSONObject()
        root.put("deviceName", deviceName)
        brand?.let { root.put("brand", it) }

        val array = JSONArray()
        for (command in commands) {
            val entry = JSONObject()
            entry.put("label", command.commandLabel)
            entry.put("frequencyHz", command.frequencyHz)
            entry.put("pattern", command.pattern)
            array.put(entry)
        }
        root.put("commands", array)
        return root.toString(2)
    }
}
