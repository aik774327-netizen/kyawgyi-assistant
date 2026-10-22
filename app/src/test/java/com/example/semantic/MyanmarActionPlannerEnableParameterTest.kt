package com.example.semantic

import com.example.semantic.model.ActionPlan
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * ISSUE-031 regression tests (Phase 1.5) — T15-031-01 (planner unit level).
 *
 * Root cause (CONFIRMED, Phase 1 audit + Phase 1.5 plan §2.5):
 * [MyanmarActionPlanner] wrote the toggle state only as the String
 * `params["state"]` ("ON"/"OFF", produced by MyanmarEntityExtractor),
 * while `UnifiedIntelligencePipeline.executeAction` reads
 * `parameters["enable"] as? Boolean ?: true` in its FLASHLIGHT (:695),
 * WIFI_TOGGLE (:708) and BLUETOOTH_TOGGLE (:721) branches. The "enable"
 * key was never written, so every chat-path toggle defaulted to ON and
 * the OFF state was unreachable.
 *
 * Required producer-side contract: when a SETTING_STATE entity is
 * present, the planner must additionally write
 * `params["enable"] = (state == "ON")`, keeping `params["state"]`
 * unchanged for existing consumers.
 */
class MyanmarActionPlannerEnableParameterTest {

    private val planner = MyanmarActionPlanner()

    private val safeResult = ActionSafetyValidator.SafetyResult(
        isSafeToExecuteDirectly = true,
        requiresConfirmation = false
    )

    private fun planWithState(intent: SemanticIntent, state: String?): ActionPlan {
        val entities = if (state == null) {
            emptyList()
        } else {
            listOf(SemanticEntity(type = EntityType.SETTING_STATE, value = state))
        }
        return planner.planAction(
            intent = intent,
            target = null,
            confidence = 0.95f,
            confidenceLevel = ConfidenceLevel.EXECUTE,
            entities = entities,
            safetyResult = safeResult,
            reasoning = "T15-031-01"
        )
    }

    @Test
    fun t15_031_01_settingStateOff_writesEnableFalse() {
        val plan = planWithState(SemanticIntent.TOGGLE_WIFI, "OFF")
        // Existing behavior preserved: the String state stays under "state".
        assertEquals("OFF", plan.parameters["state"])
        // ISSUE-031: the Boolean the pipeline actually reads must be written.
        assertEquals(false, plan.parameters["enable"])
    }

    @Test
    fun t15_031_01_settingStateOn_writesEnableTrue() {
        val plan = planWithState(SemanticIntent.TOGGLE_WIFI, "ON")
        assertEquals("ON", plan.parameters["state"])
        assertEquals(true, plan.parameters["enable"])
    }

    @Test
    fun t15_031_01_noSettingState_writesNoEnableKey() {
        // No state entity -> no invented "enable" value (the verb-less
        // mention default is a separate, still-open product decision;
        // this fix must not silently change it).
        val plan = planWithState(SemanticIntent.TOGGLE_WIFI, null)
        assertFalse(plan.parameters.containsKey("enable"))
    }
}
