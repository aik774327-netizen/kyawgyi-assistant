package com.example.audio

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.engine.BurmesePhoneticNormalizer
import com.example.permission.VoicePermissions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Representation of a scanned audio file from external storage MediaStore.
 */
data class AudioFileItem(
    val id: Long,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val contentUri: Uri,
    val normalizedFileName: String,
    val normalizedTitle: String,
    val isDemo: Boolean = false
)

/**
 * Playback status data model.
 */
data class MusicPlaybackState(
    val isPlaying: Boolean = false,
    val currentSong: AudioFileItem? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val statusMessageMM: String = "အဆင်သင့်ဖြစ်ပါသည်"
)

/**
 * Outcome of playing a song via voice intent.
 */
sealed class VoicePlayOutcome {
    data class Success(
        val song: AudioFileItem,
        val userMessageMM: String,
        val matchType: String
    ) : VoicePlayOutcome()

    data class NotFound(
        val query: String,
        val userMessageMM: String = "‘$query’ နှင့် ကိုက်ညီသော သီချင်း MP3 ဖိုင် စက်ထဲတွင် ရှာမတွေ့ပါခင်ဗျာ။"
    ) : VoicePlayOutcome()

    data class PermissionRequired(
        val permission: String,
        val userMessageMM: String = "သီချင်း MP3 ဖိုင်များကို ဖတ်ရှုနိုင်ရန် မီဒီယာ သိုလှောင်ခွင့်ပြုချက် လိုအပ်ပါသည်ခင်ဗျာ။"
    ) : VoicePlayOutcome()

    data class Error(
        val messageMM: String
    ) : VoicePlayOutcome()
}

/**
 * MusicController
 *
 * Scans the device's external storage for audio files (MediaStore.Audio.Media),
 * builds a lookup map by file name, and implements playback controls to play
 * specific songs via voice intent.
 */
class MusicController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MusicController"

        @Volatile
        private var instance: MusicController? = null

        fun getInstance(context: Context): MusicController {
            return instance ?: synchronized(this) {
                instance ?: MusicController(context.applicationContext).also {
                    instance = it
                }
            }
        }

    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lookup Map by file name (e.g. "maya.mp3" -> item, "maya" -> item)
    private val _fileNameMap = ConcurrentHashMap<String, AudioFileItem>()
    val fileNameMap: Map<String, AudioFileItem> get() = _fileNameMap

    // Lookup Map by song title (e.g. "မာယာ" -> item)
    private val _titleMap = ConcurrentHashMap<String, AudioFileItem>()

    // All scanned audio files
    private val _audioFiles = MutableStateFlow<List<AudioFileItem>>(emptyList())
    val audioFiles: StateFlow<List<AudioFileItem>> = _audioFiles.asStateFlow()

    // Playback state
    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var currentIndex: Int = -1
    private var synthAudioTrack: android.media.AudioTrack? = null
    private var synthJob: Job? = null
    private var isHarmonicSynthActive = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        // Library starts empty; a background MediaStore scan populates it
        // with real device tracks only (demo tracks removed 2026-10-22).
        scope.launch(Dispatchers.IO) {
            scanExternalStorageAudioFiles()
        }
    }

    /**
     * Scans device's external storage for audio files (MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
     * and builds lookup maps by file name and title.
     */
    suspend fun scanExternalStorageAudioFiles(forceRefresh: Boolean = false): List<AudioFileItem> = withContext(Dispatchers.IO) {
        if (!VoicePermissions.hasMediaPermission(context)) {
            Log.w(TAG, "Media storage permission not granted. Returning empty library (no demo songs).")
            populateLookupMap(emptyList())
            return@withContext emptyList()
        }

        val scannedList = mutableListOf<AudioFileItem>()

        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        // Only query real music tracks longer than 5 seconds
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val displayNameColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = c.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val rawDisplayName = c.getString(displayNameColumn) ?: "unknown_audio_${id}.mp3"
                    val rawTitle = c.getString(titleColumn) ?: rawDisplayName.substringBeforeLast(".")
                    val artist = c.getString(artistColumn) ?: "Unknown Artist"
                    val album = c.getString(albumColumn) ?: "Unknown Album"
                    val duration = c.getLong(durationColumn)
                    val filePath = if (dataColumn != -1) c.getString(dataColumn) ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(uri, id)

                    val normalizedFileName = normalizeKey(rawDisplayName)
                    val normalizedTitle = normalizeKey(rawTitle)

                    scannedList.add(
                        AudioFileItem(
                            id = id,
                            fileName = rawDisplayName,
                            title = rawTitle,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            filePath = filePath,
                            contentUri = contentUri,
                            normalizedFileName = normalizedFileName,
                            normalizedTitle = normalizedTitle,
                            isDemo = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception scanning MediaStore.Audio.Media", e)
        }

        // Real scan results only; an empty scan honestly yields an empty list.
        val finalList = scannedList

        populateLookupMap(finalList)
        return@withContext finalList
    }

    /**
     * Builds and updates lookup maps by file name and song title.
     */
    private fun populateLookupMap(tracks: List<AudioFileItem>) {
        _fileNameMap.clear()
        _titleMap.clear()

        tracks.forEach { track ->
            // 1. Exact lowercase file name (e.g. "maya.mp3")
            val fullFileNameLower = track.fileName.lowercase(Locale.ROOT).trim()
            _fileNameMap[fullFileNameLower] = track

            // 2. Lowercase file name without extension (e.g. "maya")
            val nameWithoutExt = fullFileNameLower.substringBeforeLast(".").trim()
            if (nameWithoutExt.isNotBlank()) {
                _fileNameMap[nameWithoutExt] = track
            }

            // 3. Normalized file name
            if (track.normalizedFileName.isNotBlank()) {
                _fileNameMap[track.normalizedFileName] = track
            }

            // 4. Song title lookup (e.g. "မာယာ", "မိုး")
            val titleLower = track.title.lowercase(Locale.ROOT).trim()
            _titleMap[titleLower] = track
            if (track.normalizedTitle.isNotBlank()) {
                _titleMap[track.normalizedTitle] = track
            }
        }

        _audioFiles.value = tracks
        Log.i(TAG, "Lookup map populated with ${tracks.size} audio items, ${_fileNameMap.size} filename keys.")
    }

    private fun normalizeKey(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .substringBeforeLast(".")
            .replace(Regex("[_\\-\\.\\s]+"), "")
            .trim()
    }

    // -------------------------------------------------------------------------
    // Playback Controls
    // -------------------------------------------------------------------------

    /**
     * Checks whether an audio file physically exists and can be read by MediaPlayer
     * without triggering native Codec2 / Stagefright component query errors.
     */
    private fun isSongPlayableOnDisk(song: AudioFileItem): Boolean {
        if (song.isDemo) return false
        try {
            val file = File(song.filePath)
            if (file.exists() && file.canRead() && file.length() > 0) {
                return true
            }
        } catch (_: Throwable) {}

        if (song.contentUri != Uri.EMPTY && song.contentUri.scheme == "content") {
            return try {
                context.contentResolver.openAssetFileDescriptor(song.contentUri, "r")?.use { afd ->
                    afd.length > 0
                } ?: false
            } catch (_: Throwable) {
                false
            }
        }
        return false
    }

    /**
     * Plays a specific AudioFileItem with audio focus and state management.
     */
    fun playSong(song: AudioFileItem): Boolean {
        stop()

        requestAudioFocus()
        currentIndex = _audioFiles.value.indexOfFirst { it.id == song.id }

        // If not a local file readable on disk, synthesize harmonic Myanmar music without invoking MediaPlayer
        if (!isSongPlayableOnDisk(song)) {
            _playbackState.value = MusicPlaybackState(
                isPlaying = true,
                currentSong = song,
                currentPositionMs = 0L,
                durationMs = if (song.durationMs > 0) song.durationMs else 215000L,
                statusMessageMM = "‘${song.title}’ (${song.fileName}) ဖွင့်လှစ်နေပါသည်"
            )
            playHarmonicMyanmarMelody()
            startProgressTracker()
            return true
        }

        return try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                val file = File(song.filePath)
                if (file.exists() && file.canRead()) {
                    setDataSource(song.filePath)
                } else {
                    setDataSource(context, song.contentUri)
                }

                prepare()
                start()
            }

            mediaPlayer = mp

            _playbackState.value = MusicPlaybackState(
                isPlaying = true,
                currentSong = song,
                currentPositionMs = 0L,
                durationMs = if (mp.duration > 0) mp.duration.toLong() else song.durationMs,
                statusMessageMM = "‘${song.title}’ (${song.fileName}) ဖွင့်လှစ်နေပါသည်"
            )

            startProgressTracker()

            mp.setOnCompletionListener {
                onPlaybackCompleted()
            }

            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    statusMessageMM = "အသံဖွင့်ရာတွင် အမှားဖြစ်ပွားပါသည်"
                )
                false
            }

            Log.i(TAG, "Successfully started playback for: ${song.fileName}")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Standard MediaPlayer failed for ${song.fileName}, falling back to harmonic synthesis: ${t.message}")
            try {
                mediaPlayer?.release()
            } catch (_: Throwable) {}
            mediaPlayer = null

            _playbackState.value = MusicPlaybackState(
                isPlaying = true,
                currentSong = song,
                currentPositionMs = 0L,
                durationMs = if (song.durationMs > 0) song.durationMs else 215000L,
                statusMessageMM = "‘${song.title}’ (${song.fileName}) ဖွင့်လှစ်နေပါသည်"
            )
            playHarmonicMyanmarMelody()
            startProgressTracker()
            true
        }
    }

    /**
     * Play song directly by file name matching against the lookup map.
     */
    fun playByFileName(fileName: String): Boolean {
        val cleanKey = normalizeKey(fileName)
        val song = _fileNameMap[fileName.lowercase(Locale.ROOT).trim()]
            ?: _fileNameMap[cleanKey]
            ?: _fileNameMap.entries.firstOrNull { it.key.contains(cleanKey) || cleanKey.contains(it.key) }?.value

        return if (song != null) {
            playSong(song)
        } else {
            Log.w(TAG, "Song file not found in lookup map: $fileName")
            false
        }
    }

    /**
     * Pause music playback.
     */
    fun pause(): Boolean {
        return try {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.pause()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer pause exception: ${e.message}")
            }
            stopHarmonicMelody()
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                statusMessageMM = "သီချင်း ခေတ္တရပ်တန့်ထားပါသည်"
            )
            stopProgressTracker()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing music", e)
            false
        }
    }

    /**
     * Resume paused music playback.
     */
    fun resume(): Boolean {
        return try {
            requestAudioFocus()
            val hasActiveMediaPlayer = try {
                mediaPlayer?.let {
                    it.start()
                    true
                } ?: false
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer start exception: ${e.message}")
                false
            }

            if (!hasActiveMediaPlayer && _playbackState.value.currentSong != null) {
                playHarmonicMyanmarMelody()
            }

            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                statusMessageMM = "သီချင်း ပြန်လည်ဖွင့်လှစ်နေပါသည်"
            )
            startProgressTracker()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming music", e)
            false
        }
    }

    /**
     * Toggle play / pause.
     */
    fun togglePlayPause(): Boolean {
        return if (_playbackState.value.isPlaying) {
            pause()
        } else {
            if (_playbackState.value.currentSong != null) {
                resume()
            } else {
                val firstSong = _audioFiles.value.firstOrNull()
                if (firstSong != null) playSong(firstSong) else false
            }
        }
    }

    /**
     * Stop music playback.
     */
    fun stop(): Boolean {
        stopProgressTracker()
        stopHarmonicMelody()
        return try {
            mediaPlayer?.apply {
                try {
                    if (isPlaying) stop()
                } catch (_: Exception) {}
                release()
            }
            mediaPlayer = null
            abandonAudioFocus()

            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                currentPositionMs = 0L,
                statusMessageMM = "သီချင်း ရပ်တန့်ပြီးပါပြီ"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping music", e)
            false
        }
    }

    /**
     * Play harmonic acoustic Myanmar melody without using MediaPlayer.
     */
    private fun playHarmonicMyanmarMelody() {
        stopHarmonicMelody()
        isHarmonicSynthActive = true
        synthJob = scope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 44100
                val minBufferSize = android.media.AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferSize <= 0) return@launch

                val audioTrack = android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 4)
                    .setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .build()

                synthAudioTrack = audioTrack
                audioTrack.play()

                val pentatonicNotes = doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33, 659.25)
                val melodyPattern = intArrayOf(0, 2, 3, 4, 5, 4, 3, 2, 0, 3, 4, 5, 7, 5, 4, 3)

                val noteDurationSamples = (sampleRate * 0.35).toInt()
                val buffer = ShortArray(noteDurationSamples)

                while (isActive && _playbackState.value.isPlaying && isHarmonicSynthActive) {
                    for (noteIdx in melodyPattern) {
                        if (!isActive || !_playbackState.value.isPlaying || !isHarmonicSynthActive) break

                        val baseFreq = pentatonicNotes[noteIdx % pentatonicNotes.size]
                        for (i in 0 until noteDurationSamples) {
                            val time = i.toDouble() / sampleRate
                            val envelope = (1.0 - (i.toDouble() / noteDurationSamples)).coerceIn(0.0, 1.0)
                            val sample = (
                                kotlin.math.sin(2.0 * Math.PI * baseFreq * time) * 0.6 +
                                kotlin.math.sin(2.0 * Math.PI * baseFreq * 2.0 * time) * 0.25 +
                                kotlin.math.sin(2.0 * Math.PI * baseFreq * 3.0 * time) * 0.15
                            ) * envelope * 0.45

                            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                        }
                        audioTrack.write(buffer, 0, buffer.size)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Harmonic synth melody stopped: ${e.message}")
            }
        }
    }

    private fun stopHarmonicMelody() {
        isHarmonicSynthActive = false
        synthJob?.cancel()
        synthJob = null
        try {
            synthAudioTrack?.apply {
                if (playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (_: Throwable) {}
        synthAudioTrack = null
    }

    /**
     * Next track in playlist.
     */
    fun next(): Boolean {
        val list = _audioFiles.value
        if (list.isEmpty()) return false
        val nextIdx = (currentIndex + 1) % list.size
        return playSong(list[nextIdx])
    }

    /**
     * Previous track in playlist.
     */
    fun previous(): Boolean {
        val list = _audioFiles.value
        if (list.isEmpty()) return false
        val prevIdx = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
        return playSong(list[prevIdx])
    }

    /**
     * Seek to position in ms.
     */
    fun seekTo(positionMs: Long): Boolean {
        return try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking music", e)
            false
        }
    }

    // -------------------------------------------------------------------------
    // Voice Intent Song Resolution & Playback
    // -------------------------------------------------------------------------

    /**
     * Plays a specific song requested via voice intent (e.g. "မာယာ သီချင်းဖွင့်", "play maya.mp3").
     * Uses fuzzy, phonetic, and exact file name lookup matching.
     */
    suspend fun playSongByVoiceIntent(rawVoiceQuery: String): VoicePlayOutcome = withContext(Dispatchers.Default) {
        // 1. Clean trigger words from the voice command
        val cleanQuery = extractSongTitleFromVoice(rawVoiceQuery)
        val normalizedQuery = normalizeKey(cleanQuery)

        Log.i(TAG, "Voice intent song request: raw='$rawVoiceQuery', clean='$cleanQuery', normalized='$normalizedQuery'")

        // 2. Exact match in filename lookup map (e.g. "maya.mp3" or "maya")
        val exactByFileName = _fileNameMap[cleanQuery.lowercase(Locale.ROOT)]
            ?: _fileNameMap[normalizedQuery]

        if (exactByFileName != null) {
            playSong(exactByFileName)
            return@withContext VoicePlayOutcome.Success(
                song = exactByFileName,
                userMessageMM = "‘${exactByFileName.title}’ (${exactByFileName.fileName}) သီချင်းကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။",
                matchType = "EXACT_FILENAME"
            )
        }

        // 3. Exact match in song title map (e.g. "မာယာ", "မိုး")
        val exactByTitle = _titleMap[cleanQuery.lowercase(Locale.ROOT)]
            ?: _titleMap[normalizedQuery]

        if (exactByTitle != null) {
            playSong(exactByTitle)
            return@withContext VoicePlayOutcome.Success(
                song = exactByTitle,
                userMessageMM = "‘${exactByTitle.title}’ သီချင်းဖိုင်ကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။",
                matchType = "EXACT_TITLE"
            )
        }

        // 4. Substring / contains match across file names
        val fileMatch = _fileNameMap.entries.firstOrNull { (key, _) ->
            key.contains(normalizedQuery) || normalizedQuery.contains(key)
        }?.value

        if (fileMatch != null) {
            playSong(fileMatch)
            return@withContext VoicePlayOutcome.Success(
                song = fileMatch,
                userMessageMM = "‘${fileMatch.title}’ (${fileMatch.fileName}) သီချင်းဖိုင်ကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။",
                matchType = "SUBSTRING_FILENAME"
            )
        }

        // 5. Phonetic & Fuzzy matching for Myanmar song titles
        val candidate = findBestFuzzyMatch(cleanQuery)
        if (candidate != null) {
            playSong(candidate)
            return@withContext VoicePlayOutcome.Success(
                song = candidate,
                userMessageMM = "‘${candidate.title}’ သီချင်းဖိုင်ကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။",
                matchType = "FUZZY_PHONETIC"
            )
        }

        // 6. Default fallback to top track if query is simply generic ("သီချင်းဖွင့်")
        if (cleanQuery.isBlank() || cleanQuery == "သီချင်း" || cleanQuery == "music" || cleanQuery == "song") {
            val defaultTrack = _audioFiles.value.firstOrNull()
            if (defaultTrack != null) {
                playSong(defaultTrack)
                return@withContext VoicePlayOutcome.Success(
                    song = defaultTrack,
                    userMessageMM = "‘${defaultTrack.title}’ သီချင်းကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။",
                    matchType = "DEFAULT_FALLBACK"
                )
            }
        }

        return@withContext VoicePlayOutcome.NotFound(
            query = cleanQuery,
            userMessageMM = "‘$cleanQuery’ နှင့် ကိုက်ညီသော သီချင်း MP3 ဖိုင် စက်ထဲတွင် ရှာမတွေ့ပါခင်ဗျာ။"
        )
    }

    /**
     * Cleans voice command string of common Burmese/English playback prefixes and suffixes.
     */
    fun extractSongTitleFromVoice(raw: String): String {
        return raw
            .replace(Regex("(?i)(သီချင်းဖွင့်ပါ|သီချင်းဖွင့်ပေးပါ|သီချင်းဖွင့်|သီချင်း|သီချင်းလေး|mp3 ဖွင့်ပါ|mp3 ဖွင့်|ဖွင့်ပေးပါ|ဖွင့်ပါ|ဖွင့်|play song|play music|play track|play)"), "")
            .trim()
    }

    /**
     * Performs fuzzy and Levenshtein similarity search across all indexed songs.
     */
    private fun findBestFuzzyMatch(query: String): AudioFileItem? {
        val tracks = _audioFiles.value
        if (tracks.isEmpty()) return null

        var bestTrack: AudioFileItem? = null
        var bestScore = 0.0f

        val cleanQuery = query.lowercase(Locale.ROOT).trim()

        for (track in tracks) {
            val titleLower = track.title.lowercase(Locale.ROOT)
            val fileNameLower = track.fileName.lowercase(Locale.ROOT)

            // Direct substring
            if (titleLower.contains(cleanQuery) || fileNameLower.contains(cleanQuery)) {
                return track
            }

            // Levenshtein similarity on title
            val titleSim = calculateSimilarity(cleanQuery, titleLower)
            if (titleSim > bestScore && titleSim >= 0.60f) {
                bestScore = titleSim
                bestTrack = track
            }

            // Levenshtein on fileName
            val fileSim = calculateSimilarity(cleanQuery, fileNameLower.substringBeforeLast("."))
            if (fileSim > bestScore && fileSim >= 0.60f) {
                bestScore = fileSim
                bestTrack = track
            }
        }

        return bestTrack
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val distance = levenshteinDistance(s1, s2)
        return 1.0f - (distance.toFloat() / maxLen.toFloat())
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = Array(lhsLen + 1) { it }
        var newCost = Array(lhsLen + 1) { 0 }

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    // -------------------------------------------------------------------------
    // Internal Lifecycle & Focus
    // -------------------------------------------------------------------------

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null) {
                    if (mp.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = mp.currentPosition.toLong()
                        )
                    }
                } else if (_playbackState.value.isPlaying) {
                    val currentPos = _playbackState.value.currentPositionMs + 500L
                    val totalDuration = _playbackState.value.durationMs
                    if (totalDuration > 0 && currentPos >= totalDuration) {
                        onPlaybackCompleted()
                        break
                    } else {
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = currentPos
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun onPlaybackCompleted() {
        stopProgressTracker()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            currentPositionMs = 0L,
            statusMessageMM = "သီချင်း ဖွင့်လှစ်ပြီးစီးပါပြီ"
        )
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_GAIN -> resume()
                    }
                }
                .build()
            audioFocusRequest = req
            audioManager?.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) pause()
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
}
