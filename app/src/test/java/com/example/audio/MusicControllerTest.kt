package com.example.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicControllerTest {

    private lateinit var context: Context
    private lateinit var musicController: MusicController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        musicController = MusicController.getInstance(context)
    }

    @Test
    fun testLookupMapPopulatedWithFileNames() {
        val fileNameMap = musicController.fileNameMap
        assertNotNull(fileNameMap)
        assertTrue(fileNameMap.isNotEmpty())

        // Check lookup by exact filename
        assertTrue("Lookup map must contain 'maya.mp3'", fileNameMap.containsKey("maya.mp3"))

        // Check lookup without extension
        assertTrue("Lookup map must contain 'maya'", fileNameMap.containsKey("maya"))

        val track = fileNameMap["maya.mp3"]
        assertNotNull(track)
        assertEquals("မာယာ", track?.title)
    }

    @Test
    fun testScanAudioFilesReturnsTracks() = runBlocking {
        val tracks = musicController.scanExternalStorageAudioFiles(forceRefresh = true)
        assertNotNull(tracks)
        assertTrue(tracks.isNotEmpty())

        val trackTitles = tracks.map { it.title }
        assertTrue(trackTitles.contains("မာယာ"))
    }

    @Test
    fun testPlaybackControls() {
        val song = musicController.audioFiles.value.first()

        val playSuccess = musicController.playSong(song)
        assertTrue(playSuccess)

        val state = musicController.playbackState.value
        assertTrue(state.isPlaying)
        assertEquals(song.id, state.currentSong?.id)

        // Pause
        musicController.pause()
        assertFalse(musicController.playbackState.value.isPlaying)

        // Resume
        musicController.resume()
        assertTrue(musicController.playbackState.value.isPlaying)

        // Stop
        musicController.stop()
        assertFalse(musicController.playbackState.value.isPlaying)
    }

    @Test
    fun testPlaySongByVoiceIntentExactFileName() = runBlocking {
        val outcome = musicController.playSongByVoiceIntent("maya.mp3 သီချင်းဖွင့်ပါ")
        assertTrue(outcome is VoicePlayOutcome.Success)
        val success = outcome as VoicePlayOutcome.Success
        assertEquals("မာယာ", success.song.title)
        assertTrue(success.userMessageMM.contains("မာယာ") || success.userMessageMM.contains("ဖွင့်"))
    }

    @Test
    fun testPlaySongByVoiceIntentExactTitle() = runBlocking {
        val outcome = musicController.playSongByVoiceIntent("မာယာ သီချင်းဖွင့်")
        assertTrue(outcome is VoicePlayOutcome.Success)
        val success = outcome as VoicePlayOutcome.Success
        assertEquals("မာယာ", success.song.title)
    }

    @Test
    fun testPlaySongByVoiceIntentGenericFallback() = runBlocking {
        val outcome = musicController.playSongByVoiceIntent("သီချင်းဖွင့်")
        assertTrue(outcome is VoicePlayOutcome.Success)
    }

    @Test
    fun testPlaySongByVoiceIntentNotFound() = runBlocking {
        val outcome = musicController.playSongByVoiceIntent("xyz_non_existent_random_song_123456")
        assertTrue(outcome is VoicePlayOutcome.NotFound)
    }
}
