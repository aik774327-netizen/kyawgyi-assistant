package com.example.audio

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MusicController tests after the FAKE-09 demo-track removal (2026-10-22).
 *
 * The previous version of this test asserted that the built-in DEFAULT_DEMO_TRACKS
 * ("မာယာ", "မိုး", ...) were always present in the library. Those tracks pointed
 * at non-existent /demo/music/*.mp3 files and were fabricated library data, so
 * they were deleted. These tests now assert the honest behavior: the library
 * contains only real MediaStore scan results (empty under Robolectric), voice
 * intents against an empty library report NotFound, and the playback state
 * machine works with an explicit in-test fixture item.
 */
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
    fun testLibraryStartsEmptyWithoutDemoTracks() = runBlocking {
        val tracks = musicController.scanExternalStorageAudioFiles(forceRefresh = true)
        assertNotNull(tracks)
        assertTrue("Library must contain only real MediaStore tracks (none under Robolectric)", tracks.isEmpty())
        assertTrue(musicController.fileNameMap.isEmpty())
        assertTrue(musicController.audioFiles.value.isEmpty())
    }

    @Test
    fun testPlaybackControlsWithFixtureTrack() {
        // Explicit fixture (not library data): a non-demo item whose file does
        // not exist on disk, exercising the controller's playback state flow.
        val song = AudioFileItem(
            id = 42L,
            fileName = "fixture.mp3",
            title = "Fixture Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180000L,
            filePath = "/nonexistent/fixture.mp3",
            contentUri = Uri.EMPTY,
            normalizedFileName = "fixture",
            normalizedTitle = "fixture song",
            isDemo = false
        )

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
    fun testPlaySongByVoiceIntentNotFoundWhenLibraryEmpty() = runBlocking {
        val byFileName = musicController.playSongByVoiceIntent("maya.mp3 သီချင်းဖွင့်ပါ")
        assertTrue(byFileName is VoicePlayOutcome.NotFound)

        val byTitle = musicController.playSongByVoiceIntent("မာယာ သီချင်းဖွင့်")
        assertTrue(byTitle is VoicePlayOutcome.NotFound)
    }

    @Test
    fun testPlaySongByVoiceIntentGenericRequestNotFoundWhenLibraryEmpty() = runBlocking {
        // Previously this returned Success by falling back to a demo track.
        val outcome = musicController.playSongByVoiceIntent("သီချင်းဖွင့်")
        assertTrue(outcome is VoicePlayOutcome.NotFound)
    }

    @Test
    fun testPlaySongByVoiceIntentNotFound() = runBlocking {
        val outcome = musicController.playSongByVoiceIntent("xyz_non_existent_random_song_123456")
        assertTrue(outcome is VoicePlayOutcome.NotFound)
    }
}
