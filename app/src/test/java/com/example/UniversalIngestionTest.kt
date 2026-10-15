package com.example

import com.example.database.MusicAssetEntity
import com.example.ingestion.AssetIndexer
import com.example.ingestion.ProjectDataRouter
import com.example.ingestion.SecurityValidator
import com.example.nlu.MyanmarVoiceParser
import com.example.nlu.ParsedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalIngestionTest {

  @Test
  fun testSecurityValidatorBlocksPathTraversalAndExecutables() {
    val maliciousPathRes = SecurityValidator.validate("../../etc/passwd", 1024)
    assertTrue("Path traversal must be rejected", maliciousPathRes is SecurityValidator.ValidationResult.Invalid)

    val exeRes = SecurityValidator.validate("malware.exe", 1024)
    assertTrue("Executable must be rejected", exeRes is SecurityValidator.ValidationResult.Invalid)

    val shRes = SecurityValidator.validate("script.sh", 1024)
    assertTrue("Shell scripts must be rejected", shRes is SecurityValidator.ValidationResult.Invalid)

    val validRes = SecurityValidator.validate("Lay Phyu - Song.mp3", 1024)
    assertTrue("Valid MP3 must pass", validRes is SecurityValidator.ValidationResult.Valid)
  }

  @Test
  fun testDeterministicSha256() {
    val data = "KYAW_GYI_AI_INGESTION_TEST_STREAM".toByteArray()
    val hash1 = SecurityValidator.computeSha256(data)
    val hash2 = SecurityValidator.computeSha256(data)
    assertEquals("SHA-256 must be strictly deterministic", hash1, hash2)
    assertEquals(64, hash1.length)
  }

  @Test
  fun testFileClassificationRouting() {
    assertEquals(
      ProjectDataRouter.AssetCategory.MUSIC,
      ProjectDataRouter.classify("lay_phyu.mp3", "audio/mpeg", "mp3")
    )

    assertEquals(
      ProjectDataRouter.AssetCategory.IMAGE,
      ProjectDataRouter.classify("profile.png", "image/png", "png")
    )

    assertEquals(
      ProjectDataRouter.AssetCategory.PROJECT_CONFIG,
      ProjectDataRouter.classify("fan_config.json", "application/json", "json")
    )

    assertEquals(
      ProjectDataRouter.AssetCategory.PROJECT_SOURCE,
      ProjectDataRouter.classify("MainPatch.kt", "text/x-kotlin", "kt")
    )
  }

  @Test
  fun testMyanmarVoiceParserMusicCommands() {
    val intent1 = MyanmarVoiceParser.parse("လေးဖြူသီချင်းဖွင့်ပါ")
    assertTrue(intent1 is ParsedIntent.PlayMusic)
    assertEquals("လေးဖြူ", (intent1 as ParsedIntent.PlayMusic).query)

    val intent2 = MyanmarVoiceParser.parse("သီချင်းရပ်ပါ")
    assertTrue(intent2 is ParsedIntent.PauseMusic)

    val intent3 = MyanmarVoiceParser.parse("နောက်သီချင်း")
    assertTrue(intent3 is ParsedIntent.PlayNextMusic)

    val intent4 = MyanmarVoiceParser.parse("ဒုတိယတစ်ပုဒ်")
    assertTrue(intent4 is ParsedIntent.PlayCandidateIndex)
    assertEquals(1, (intent4 as ParsedIntent.PlayCandidateIndex).index)

    val intent5 = MyanmarVoiceParser.parse("angel ဖွင့်ပါ")
    assertTrue(intent5 is ParsedIntent.OpenAngelChatbox)
  }

  @Test
  fun testAssetIndexerRankingWithBurmeseAliases() {
    val sampleSongs = listOf(
      MusicAssetEntity(
        id = "1",
        fileAssetId = "f1",
        title = "အလွမ်းများ",
        artist = "လေးဖြူ",
        album = "Power 54",
        genre = "Rock",
        durationMs = 240000L,
        year = 2002,
        trackNumber = 1,
        fileUri = "file:///song1.mp3",
        absolutePath = "/storage/song1.mp3",
        mimeType = "audio/mpeg",
        sha256 = "hash1",
        source = "TEST",
        searchableText = AssetIndexer.buildSearchableText("အလွမ်းများ", "လေးဖြူ", "Power 54", "song1.mp3")
      ),
      MusicAssetEntity(
        id = "2",
        fileAssetId = "f2",
        title = "အလင်းရောင်",
        artist = "မျိုးကြီး",
        album = "Single",
        genre = "Rock",
        durationMs = 210000L,
        year = 2005,
        trackNumber = 2,
        fileUri = "file:///song2.mp3",
        absolutePath = "/storage/song2.mp3",
        mimeType = "audio/mpeg",
        sha256 = "hash2",
        source = "TEST",
        searchableText = AssetIndexer.buildSearchableText("အလင်းရောင်", "မျိုးကြီး", "Single", "song2.mp3")
      )
    )

    // Search query in English transliteration "lay phyu" should rank Lay Phyu song #1
    val ranked = AssetIndexer.rankMatches("lay phyu", sampleSongs)
    assertFalse("Ranked results should not be empty", ranked.isEmpty())
    assertEquals("လေးဖြူ", ranked.first().asset.artist)
    assertEquals("အလွမ်းများ", ranked.first().asset.title)
  }

  @Test
  fun testMyanmarDialectNormalizationAndContextDisambiguation() {
    // 1. Dialect & Slang variation: "ဘန်ဂါ ဖွင့်ပီး" -> Normalizes to Fan ON
    val intent1 = MyanmarVoiceParser.parse("ဘန်ဂါ ဖွင့်ပီး")
    assertTrue("Dialect 'ဘန်ဂါ ဖွင့်ပီး' should parse to Fan ON", intent1 is ParsedIntent.KennedeFanCommand)
    assertEquals("FAN_POWER_ON", (intent1 as ParsedIntent.KennedeFanCommand).action)

    // 2. Ambiguous command "ဖွင့်ပါ" with active FAN context
    val intent2 = MyanmarVoiceParser.parse("ဖွင့်ပါ", lastActiveContext = "FAN")
    assertTrue("Context 'FAN' should resolve 'ဖွင့်ပါ' to Fan ON", intent2 is ParsedIntent.KennedeFanCommand)

    // 3. Ambiguous command "ရပ်ပါ" with active MUSIC context
    val intent3 = MyanmarVoiceParser.parse("ရပ်ပါ", lastActiveContext = "MUSIC")
    assertTrue("Context 'MUSIC' should resolve 'ရပ်ပါ' to PauseMusic", intent3 is ParsedIntent.PauseMusic)
  }
}
