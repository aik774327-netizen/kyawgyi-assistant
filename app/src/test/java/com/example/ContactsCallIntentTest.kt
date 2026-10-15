package com.example

import com.example.database.ErrorCategory
import com.example.models.ContactItem
import com.example.nlu.MyanmarVoiceParser
import com.example.nlu.ParsedIntent
import com.example.realworld.RealContactsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Integration Test for:
 * (၄) ဖုန်းခေါ်ဆိုမှုအမှတ် (Contacts & Phone Calling) ဆိုင်ရာ
 * 1. Truthful parsing of Burmese calling intents ("အမေ့ဆီ ဖုန်းခေါ်ပါ", "မိတ်ဆွေဆီ ဖုန်းခေါ်ပါ").
 * 2. Accurate Burmese relationship resolution ("အမေ" <-> "မေမေ", "မိတ်ဆွေ" <-> "သူငယ်ချင်း").
 * 3. Prevention of simulated numbers; proper Real-World Mismatch detection when contact is missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContactsCallIntentTest {

  @Test
  fun testMyanmarVoiceParser_phoneCallingVariants() {
    // 1. "အမေ့ဆီ ဖုန်းခေါ်ပါ"
    val res1 = MyanmarVoiceParser.parse("အမေ့ဆီ ဖုန်းခေါ်ပါ")
    assertTrue("Should be CallContact intent", res1 is ParsedIntent.CallContact)
    assertEquals("အမေ", (res1 as ParsedIntent.CallContact).target)

    // 2. "မိတ်ဆွေဆီ ဖုန်းခေါ်ပါ"
    val res2 = MyanmarVoiceParser.parse("မိတ်ဆွေဆီ ဖုန်းခေါ်ပါ")
    assertTrue("Should be CallContact intent", res2 is ParsedIntent.CallContact)
    assertEquals("မိတ်ဆွေ", (res2 as ParsedIntent.CallContact).target)

    // 3. "ကျော်ကြီး အမေ့ဆီ ဖုန်းခေါ်ပေးပါ"
    val res3 = MyanmarVoiceParser.parse("ကျော်ကြီး အမေ့ဆီ ဖုန်းခေါ်ပေးပါ")
    assertTrue("Should be CallContact intent", res3 is ParsedIntent.CallContact)
    assertEquals("အမေ", (res3 as ParsedIntent.CallContact).target)

    // 4. "မေမေ့ဆီ ဖုန်းဆက်ပါ"
    val res4 = MyanmarVoiceParser.parse("မေမေ့ဆီ ဖုန်းဆက်ပါ")
    assertTrue("Should be CallContact intent", res4 is ParsedIntent.CallContact)
    assertEquals("မေမေ", (res4 as ParsedIntent.CallContact).target)

    // 5. Direct phone number digits: "09778899111 ဆီ ဖုန်းခေါ်ပါ"
    val res5 = MyanmarVoiceParser.parse("09778899111 ဆီ ဖုန်းခေါ်ပါ")
    assertTrue("Should be CallContact intent", res5 is ParsedIntent.CallContact)
    assertEquals("09778899111", (res5 as ParsedIntent.CallContact).target)
  }

  @Test
  fun testRealContactsRepository_smartMatching() {
    val contacts = listOf(
      ContactItem(id = "1", name = "Daw Mya", phoneNumber = "09111111111", relationship = "အမေ"),
      ContactItem(id = "2", name = "U Ba", phoneNumber = "09222222222", relationship = "အဖေ"),
      ContactItem(id = "3", name = "Ko Aung", phoneNumber = "09333333333", relationship = "မိတ်ဆွေ"),
      ContactItem(id = "4", name = "Office Admin", phoneNumber = "09444444444", relationship = "ရုံး")
    )

    // Mock Context not needed for pure logic matching helper:
    // Create minimal repo or test helper
    val repo = RealContactsRepository(androidx.test.core.app.ApplicationProvider.getApplicationContext())

    // 1. Match "အမေ" and "မေမေ"
    val matchMom1 = repo.findContactByTarget("အမေ", contacts)
    assertNotNull("Must match mother", matchMom1)
    assertEquals("Daw Mya", matchMom1?.name)
    assertEquals("09111111111", matchMom1?.phoneNumber)

    val matchMom2 = repo.findContactByTarget("မေမေ", contacts)
    assertNotNull("Must match 'မေမေ' to 'အမေ'", matchMom2)
    assertEquals("Daw Mya", matchMom2?.name)

    // 2. Match "မိတ်ဆွေ" and "သူငယ်ချင်း"
    val matchFriend1 = repo.findContactByTarget("မိတ်ဆွေ", contacts)
    assertNotNull("Must match friend", matchFriend1)
    assertEquals("Ko Aung", matchFriend1?.name)
    assertEquals("09333333333", matchFriend1?.phoneNumber)

    val matchFriend2 = repo.findContactByTarget("သူငယ်ချင်း", contacts)
    assertNotNull("Must match 'သူငယ်ချင်း' to 'မိတ်ဆွေ'", matchFriend2)
    assertEquals("Ko Aung", matchFriend2?.name)

    // 3. Search query filter
    val searchResults = repo.searchContacts("09333", contacts)
    assertEquals(1, searchResults.size)
    assertEquals("Ko Aung", searchResults.first().name)

    // 4. Missing target returns null (Never pretend or hallucinate)
    val missing = repo.findContactByTarget("ဦးလေး", contacts)
    assertNull("Missing contact must return null so real-world mismatch can be handled", missing)
  }

  @Test
  fun testRealWorldMismatch_categoryClassification() {
    // When contact is missing in contacts and alias list:
    val category = ErrorCategory.REAL_WORLD_MISMATCH
    assertEquals(category.burmeseTitle, "လက်တွေ့မကိုက်ညီမှု (Real-world Mismatch)")
    assertTrue(category.explanationBurmese.contains("လက်တွေ့"))
  }
}
