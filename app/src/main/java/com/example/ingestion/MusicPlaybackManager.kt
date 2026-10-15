package com.example.ingestion

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.example.database.MusicAssetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class PlaybackStatus {
  IDLE,
  PREPARING,
  PLAYING,
  PAUSED,
  STOPPED,
  ERROR
}

class MusicPlaybackManager(private val context: Context) {

  private var mediaPlayer: MediaPlayer? = null
  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private var progressTrackerJob: Job? = null

  private val _currentSong = MutableStateFlow<MusicAssetEntity?>(null)
  val currentSong: StateFlow<MusicAssetEntity?> = _currentSong.asStateFlow()

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

  private val _playbackStatus = MutableStateFlow(PlaybackStatus.IDLE)
  val playbackStatus: StateFlow<PlaybackStatus> = _playbackStatus.asStateFlow()

  private val _playlist = MutableStateFlow<List<MusicAssetEntity>>(emptyList())
  val playlist: StateFlow<List<MusicAssetEntity>> = _playlist.asStateFlow()

  fun setPlaylist(songs: List<MusicAssetEntity>) {
    _playlist.value = songs
  }

  fun play(song: MusicAssetEntity): Boolean {
    stop()

    val file = File(song.absolutePath)
    if (!file.exists()) {
      _playbackStatus.value = PlaybackStatus.ERROR
      return false
    }

    return try {
      _playbackStatus.value = PlaybackStatus.PREPARING
      _currentSong.value = song

      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        )
        setDataSource(context, Uri.fromFile(file))
        prepare()
        start()

        setOnCompletionListener {
          _isPlaying.value = false
          _playbackStatus.value = PlaybackStatus.STOPPED
          playNext()
        }

        setOnErrorListener { _, _, _ ->
          _isPlaying.value = false
          _playbackStatus.value = PlaybackStatus.ERROR
          true
        }
      }

      val dur = mediaPlayer?.duration?.toLong() ?: song.durationMs
      _durationMs.value = if (dur > 0) dur else song.durationMs
      _isPlaying.value = true
      _playbackStatus.value = PlaybackStatus.PLAYING

      startProgressTracker()
      true
    } catch (_: Exception) {
      _isPlaying.value = false
      _playbackStatus.value = PlaybackStatus.ERROR
      false
    }
  }

  fun pause() {
    try {
      mediaPlayer?.let {
        if (it.isPlaying) {
          it.pause()
          _isPlaying.value = false
          _playbackStatus.value = PlaybackStatus.PAUSED
        }
      }
    } catch (_: Exception) {}
  }

  fun resume() {
    try {
      mediaPlayer?.let {
        it.start()
        _isPlaying.value = true
        _playbackStatus.value = PlaybackStatus.PLAYING
        startProgressTracker()
      }
    } catch (_: Exception) {}
  }

  fun stop() {
    progressTrackerJob?.cancel()
    try {
      mediaPlayer?.let {
        if (it.isPlaying) {
          it.stop()
        }
        it.release()
      }
    } catch (_: Exception) {}
    mediaPlayer = null
    _isPlaying.value = false
    _playbackStatus.value = PlaybackStatus.STOPPED
    _currentPositionMs.value = 0L
  }

  fun seekTo(positionMs: Long) {
    try {
      mediaPlayer?.seekTo(positionMs.toInt())
      _currentPositionMs.value = positionMs
    } catch (_: Exception) {}
  }

  fun playNext(): Boolean {
    val list = _playlist.value
    if (list.isEmpty()) return false
    val current = _currentSong.value ?: return play(list.first())
    val currentIndex = list.indexOfFirst { it.id == current.id }
    val nextIndex = if (currentIndex >= 0 && currentIndex + 1 < list.size) currentIndex + 1 else 0
    return play(list[nextIndex])
  }

  fun playPrevious(): Boolean {
    val list = _playlist.value
    if (list.isEmpty()) return false
    val current = _currentSong.value ?: return play(list.last())
    val currentIndex = list.indexOfFirst { it.id == current.id }
    val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
    return play(list[prevIndex])
  }

  private fun startProgressTracker() {
    progressTrackerJob?.cancel()
    progressTrackerJob = scope.launch {
      while (isActive && _isPlaying.value) {
        mediaPlayer?.let {
          if (it.isPlaying) {
            _currentPositionMs.value = it.currentPosition.toLong()
          }
        }
        delay(500)
      }
    }
  }
}
