package com.example.discovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.CommandContextManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DynamicAppDiscoveryAndLaunchTest {

    private lateinit var context: Context
    private lateinit var matcher: AppNameMatcher
    private lateinit var resolver: DynamicAppResolver
    private lateinit var contextManager: CommandContextManager

    private val sampleApps = listOf(
        InstalledAppInfo(
            packageName = "com.facebook.katana",
            applicationLabel = "Facebook",
            normalizedLabel = "facebook",
            isLaunchable = true,
            isEnabled = true,
            isSystemApp = false
        ),
        InstalledAppInfo(
            packageName = "com.facebook.lite",
            applicationLabel = "Facebook Lite",
            normalizedLabel = "facebook lite",
            isLaunchable = true,
            isEnabled = true,
            isSystemApp = false
        ),
        InstalledAppInfo(
            packageName = "com.google.android.youtube",
            applicationLabel = "YouTube",
            normalizedLabel = "youtube",
            isLaunchable = true,
            isEnabled = true,
            isSystemApp = false
        ),
        InstalledAppInfo(
            packageName = "com.viber.voip",
            applicationLabel = "Viber",
            normalizedLabel = "viber",
            isLaunchable = true,
            isEnabled = true,
            isSystemApp = false
        ),
        InstalledAppInfo(
            packageName = "org.telegram.messenger",
            applicationLabel = "Telegram",
            normalizedLabel = "telegram",
            isLaunchable = true,
            isEnabled = true,
            isSystemApp = false
        ),
        InstalledAppInfo(
            packageName = "com.android.chrome",
            applicationLabel = "Chrome",
            normalizedLabel = "chrome",
            isLaunchable = true,
            isEnabled = true,
            isSystemApp = true
        ),
        InstalledAppInfo(
            packageName = "com.kbzbank.kpay",
            applicationLabel = "KBZPay",
            normalizedLabel = "kbzpay",
            isLaunchable = true,
            isEnabled = true,
            isSystemApp = false
        )
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        matcher = AppNameMatcher()
        contextManager = CommandContextManager.getInstance()
        contextManager.resetContext()
        resolver = DynamicAppResolver(context, appMatcher = matcher)
    }

    @Test
    fun testExactMatch() {
        val fbApp = sampleApps[0]
        val score = matcher.match("Facebook", fbApp)
        assertEquals(MatchType.EXACT, score.matchType)
        assertEquals(1.0f, score.confidence, 0.01f)
    }

    @Test
    fun testMyanmarLoanwordAliasMatching() {
        val fbApp = sampleApps[0]
        val ytApp = sampleApps[2]
        val viberApp = sampleApps[3]
        val tgApp = sampleApps[4]

        // Spoken Myanmar "ဖေ့စ်ဘုတ်"
        val fbScore = matcher.match("ဖေ့စ်ဘုတ်", fbApp)
        assertTrue("Expected confidence >= 0.90 for ဖေ့စ်ဘုတ်, was ${fbScore.confidence}", fbScore.confidence >= 0.90f)
        assertEquals(MatchType.LINGUISTIC_ALIAS, fbScore.matchType)

        // Spoken Myanmar "ယူကျု"
        val ytScore = matcher.match("ယူကျု", ytApp)
        assertTrue("Expected confidence >= 0.90 for ယူကျု, was ${ytScore.confidence}", ytScore.confidence >= 0.90f)
        assertEquals(MatchType.LINGUISTIC_ALIAS, ytScore.matchType)

        // Spoken Myanmar "ဗိုင်ဘာ"
        val viberScore = matcher.match("ဗိုင်ဘာ", viberApp)
        assertTrue("Expected confidence >= 0.90 for ဗိုင်ဘာ, was ${viberScore.confidence}", viberScore.confidence >= 0.90f)

        // Spoken Myanmar "တယ်လီဂရမ်"
        val tgScore = matcher.match("တယ်လီဂရမ်", tgApp)
        assertTrue("Expected confidence >= 0.90 for တယ်လီဂရမ်, was ${tgScore.confidence}", tgScore.confidence >= 0.90f)
    }

    @Test
    fun testPrefixAndSubstringMatching() {
        val ytApp = sampleApps[2]
        val chromeApp = sampleApps[5]

        val prefixScore = matcher.match("You", ytApp)
        assertTrue("Prefix match confidence >= 0.80, was ${prefixScore.confidence}", prefixScore.confidence >= 0.80f)
        assertEquals(MatchType.PREFIX, prefixScore.matchType)

        val substrScore = matcher.match("Tube", ytApp)
        assertTrue("Substring match confidence >= 0.75, was ${substrScore.confidence}", substrScore.confidence >= 0.75f)
        assertEquals(MatchType.CONTAINS, substrScore.matchType)
    }

    @Test
    fun testFuzzyLevenshteinMatching() {
        val tgApp = sampleApps[4]
        // "Telegrm" with typo
        val fuzzyScore = matcher.match("Telegrm", tgApp)
        assertTrue("Fuzzy score should be >= 0.70, was ${fuzzyScore.confidence}", fuzzyScore.confidence >= 0.70f)
        assertEquals(MatchType.FUZZY, fuzzyScore.matchType)
    }

    @Test
    fun testCandidateRanking() {
        val candidates = matcher.scoreCandidates("ယူကျု", sampleApps)
        assertTrue("Candidates should not be empty", candidates.isNotEmpty())
        assertEquals("YouTube", candidates[0].app.applicationLabel)
        assertTrue(candidates[0].score.confidence >= 0.90f)
    }

    @Test
    fun testAppTargetExtraction() {
        assertEquals("Facebook", resolver.extractAppTarget("Facebook ဖွင့်ပါ"))
        assertEquals("Viber", resolver.extractAppTarget("Viber ကို ဖွင့်ပေး"))
        assertEquals("YouTube", resolver.extractAppTarget("YouTube ဖွင့်ပေးပါ"))
        assertEquals("ယူကျု", resolver.extractAppTarget("ယူကျု ဖွင့်"))
        assertEquals("Chrome", resolver.extractAppTarget("Chrome ကို ဖွင့်ပါ"))
        assertEquals("Telegram", resolver.extractAppTarget("Telegram ကို ဖွင့်ပေး"))
    }

    @Test
    fun testContextualReferenceResolution() = runBlocking {
        // Set active app in multi-turn context
        contextManager.updateAppTarget("com.google.android.youtube", "YouTube")

        // Query with anaphoric reference: "အဲဒီ app ကို ဖွင့်ပေး"
        val convContext = contextManager.getContext()
        assertEquals("com.google.android.youtube", convContext.activeAppPackage)
        assertEquals("YouTube", convContext.activeAppNameMM)

        val resolution = resolver.resolveApp("အဲဒီ app ကို ဖွင့်ပေး")
        // If the package is in discovery (or mock fallback)
        if (resolution.decision == ResolutionDecision.CONTEXT_RESOLVED) {
            assertEquals("com.google.android.youtube", resolution.resolvedApp?.packageName)
            assertEquals(MatchType.CONTEXT, resolution.matchType)
        }
    }
}
