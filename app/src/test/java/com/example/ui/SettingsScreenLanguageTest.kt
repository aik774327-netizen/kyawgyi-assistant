package com.example.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.LanguagePreferencesRepository
import com.example.ui.components.SettingsScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenLanguageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun defaultPreferredLanguage_isMyanmarBurmese() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LanguagePreferencesRepository.getInstance(context)

        assertEquals("my-MM", LanguagePreferencesRepository.DEFAULT_LANGUAGE_CODE)
        val defaultLocale = repo.createLocale("my-MM")
        assertEquals("my", defaultLocale.language)
        assertEquals("MM", defaultLocale.country)
    }

    @Test
    fun savePreferredLanguage_persistsAndNotifiesVoicePipeline() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LanguagePreferencesRepository.getInstance(context)

        var notifiedCode = ""
        var notifiedLocale: Locale? = null

        repo.addOnLanguageChangeListener { code, locale ->
            notifiedCode = code
            notifiedLocale = locale
        }

        // Test persisting my-MM
        repo.savePreferredLanguage("my-MM")
        assertEquals("my-MM", repo.getSelectedLanguageSync())
        assertEquals("my", repo.getSelectedLocaleSync().language)
        assertEquals("my-MM", notifiedCode)
        assertNotNull(notifiedLocale)

        // Test sample greeting for my-MM
        val greeting = repo.getSampleGreeting("my-MM")
        assertTrue(greeting.contains("မင်္ဂလာပါ"))

        // Test changing to en-US
        repo.savePreferredLanguage("en-US")
        assertEquals("en-US", repo.getSelectedLanguageSync())
        assertEquals("en", repo.getSelectedLocaleSync().language)
        assertEquals("en-US", notifiedCode)
        val enGreeting = repo.getSampleGreeting("en-US")
        assertTrue(enGreeting.contains("Kyaw Gyi") || enGreeting.contains("Hello"))
    }

    @Test
    fun settingsScreen_rendersWithLanguageSectionAndTestTags() {
        var selectedLangCallback: String? = null

        composeTestRule.setContent {
            SettingsScreen(
                selectedLanguage = "my-MM",
                onLanguageSelected = { selectedLangCallback = it }
            )
        }

        // Check language section exists
        composeTestRule.onNodeWithTag("settings_screen_language_section").assertExists()

        // Check language options exist
        composeTestRule.onNodeWithTag("settings_screen_language_option_my_mm").assertExists()
        composeTestRule.onNodeWithTag("settings_screen_language_option_en_us").assertExists()
        composeTestRule.onNodeWithTag("test_voice_locale_button").assertExists()

        // Click English option
        composeTestRule.onNodeWithTag("settings_screen_language_option_en_us")
            .performScrollTo()
            .performClick()
        assertEquals("en-US", selectedLangCallback)
    }
}
