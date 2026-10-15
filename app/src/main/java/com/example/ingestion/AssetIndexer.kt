package com.example.ingestion

import com.example.database.MusicAssetEntity

object AssetIndexer {

  // Burmese famous rock & music artists transliteration aliases map
  private val ARTIST_ALIASES = mapOf(
    "lay phyu" to listOf("လေးဖြူ", "layphyu", "lay phyu", "ဦးလေးဖြူ"),
    "လေးဖြူ" to listOf("lay phyu", "layphyu", "လေးဖြူ", "ဦးလေးဖြူ"),
    "myo gyi" to listOf("မျိုးကြီး", "myogyi", "myo gyi"),
    "မျိုးကြီး" to listOf("myo gyi", "myogyi", "မျိုးကြီး"),
    "zaw win htut" to listOf("ဇော်ဝင်းထွဋ်", "zawwinhtut", "zaw win htut"),
    "ဇော်ဝင်းထွဋ်" to listOf("zaw win htut", "zawwinhtut", "ဇော်ဝင်းထွဋ်"),
    "r zarni" to listOf("အာဇာနည်", "rzarni", "r zarni"),
    "အာဇာနည်" to listOf("r zarni", "rzarni", "အာဇာနည်"),
    "y wine" to listOf("ဝိုင်ဝိုင်း", "ywine", "y wine"),
    "ဝိုင်ဝိုင်း" to listOf("y wine", "ywine", "ဝိုင်ဝိုင်း"),
    "iron cross" to listOf("အိုင်းရင်းခရော့စ်", "ic", "iron cross")
  )

  /**
   * Normalizes Burmese & English text for robust comparison.
   * Strips zero-width characters (\u200B, \u200C, \u200D), punctuation, extra whitespaces.
   */
  fun normalize(input: String): String {
    return input.lowercase()
      .replace("\u200B", "")
      .replace("\u200C", "")
      .replace("\u200D", "")
      .replace("  ", " ")
      .trim()
  }

  /**
   * Builds combined searchable index text for asset.
   */
  fun buildSearchableText(title: String, artist: String, album: String, filename: String): String {
    val normTitle = normalize(title)
    val normArtist = normalize(artist)
    val normAlbum = normalize(album)
    val normFilename = normalize(filename)

    val aliases = mutableSetOf<String>()
    ARTIST_ALIASES[normArtist]?.let { aliases.addAll(it) }
    ARTIST_ALIASES.entries.find { normArtist.contains(it.key) }?.value?.let { aliases.addAll(it) }

    return buildString {
      append("$normTitle | $normArtist | $normAlbum | $normFilename")
      if (aliases.isNotEmpty()) {
        append(" | ${aliases.joinToString(" ")}")
      }
    }
  }

  /**
   * Multi-tiered ranking score for search matches.
   * Higher score = higher ranking.
   */
  data class RankedMatch(
    val asset: MusicAssetEntity,
    val score: Int,
    val matchReason: String
  )

  fun rankMatches(query: String, candidates: List<MusicAssetEntity>): List<RankedMatch> {
    val cleanQuery = normalize(query)
    if (cleanQuery.isEmpty()) return emptyList()

    val ranked = candidates.mapNotNull { asset ->
      val titleNorm = normalize(asset.title)
      val artistNorm = normalize(asset.artist)
      val albumNorm = normalize(asset.album)
      val searchNorm = normalize(asset.searchableText)

      var score = 0
      var reason = ""

      // 1. Exact title
      if (titleNorm == cleanQuery) {
        score = 1000
        reason = "Exact Title"
      }
      // 2. Exact artist
      else if (artistNorm == cleanQuery) {
        score = 900
        reason = "Exact Artist"
      }
      // 3. Artist alias match
      else if (checkArtistAliasMatch(cleanQuery, artistNorm)) {
        score = 850
        reason = "Artist Alias Match"
      }
      // 4. Normalized Burmese or English substring in title
      else if (titleNorm.contains(cleanQuery)) {
        score = 700
        reason = "Title Substring"
      }
      // 5. Normalized Burmese or English substring in artist
      else if (artistNorm.contains(cleanQuery)) {
        score = 600
        reason = "Artist Substring"
      }
      // 6. Filename match
      else if (normalize(asset.absolutePath.substringAfterLast('/')).contains(cleanQuery)) {
        score = 500
        reason = "Filename Match"
      }
      // 7. Album match
      else if (albumNorm.contains(cleanQuery)) {
        score = 400
        reason = "Album Match"
      }
      // 8. General Searchable index text
      else if (searchNorm.contains(cleanQuery)) {
        score = 300
        reason = "Keyword Match"
      }

      if (score > 0) {
        RankedMatch(asset, score, reason)
      } else {
        null
      }
    }

    return ranked.sortedByDescending { it.score }
  }

  private fun checkArtistAliasMatch(query: String, artist: String): Boolean {
    val aliasesForQuery = ARTIST_ALIASES[query]
    if (aliasesForQuery != null && aliasesForQuery.any { it.contains(artist) || artist.contains(it) }) {
      return true
    }
    val aliasesForArtist = ARTIST_ALIASES[artist]
    if (aliasesForArtist != null && aliasesForArtist.any { it.contains(query) || query.contains(it) }) {
      return true
    }
    return false
  }
}
