package com.example.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.database.MusicAssetEntity
import com.example.ingestion.MusicPlaybackManager
import com.example.ingestion.MusicRepository
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MusicLibraryDialog(
  musicRepository: MusicRepository,
  playbackManager: MusicPlaybackManager,
  onDismiss: () -> Unit
) {
  val allSongs by musicRepository.getAllMusicFlow().collectAsState(initial = emptyList())
  val currentPlayingSong by playbackManager.currentSong.collectAsState()
  val isPlaying by playbackManager.isPlaying.collectAsState()
  val currentPos by playbackManager.currentPositionMs.collectAsState()
  val duration by playbackManager.durationMs.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var songToDelete by remember { mutableStateOf<MusicAssetEntity?>(null) }

  val filteredSongs = remember(allSongs, searchQuery) {
    if (searchQuery.isBlank()) {
      allSongs
    } else {
      val q = searchQuery.trim().lowercase()
      allSongs.filter {
        it.title.lowercase().contains(q) ||
          it.artist.lowercase().contains(q) ||
          it.searchableText.lowercase().contains(q)
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F172A))
        .testTag("music_library_screen"),
      color = Color(0xFF0F172A)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {

        // 1. Header Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EmeraldPrimary.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Music Library (တေးဂီတ စာကြည့်တိုက်)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
              )
              Text(
                // Audit B10: playback is android.media.MediaPlayer, not
                // Media3/ExoPlayer — the label now names the real engine.
                text = "စုစုပေါင်း ${allSongs.size} ပုဒ် • Offline MediaPlayer Engine",
                fontSize = 11.sp,
                color = TextMuted
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_music_library_button")
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        // 2. Search Field
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("သီချင်းအမည် သို့မဟုတ် အဆိုတော် (ဥပမာ: လေးဖြူ)...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = EmeraldLight) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("music_search_textfield"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = EmeraldPrimary,
              unfocusedBorderColor = SurfaceCardBorder,
              focusedContainerColor = Color(0xFF1E293B),
              unfocusedContainerColor = Color(0xFF1E293B),
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary
            ),
            singleLine = true
          )
        }

        // 3. Songs List
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (filteredSongs.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 40.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (allSongs.isEmpty()) "သီချင်းများ မရှိသေးပါ\nAI Angel Chatbox တွင် MP3 ဖိုင် ပေးပို့ပါ" else "သီချင်း ရှာမတွေ့ပါ",
                  color = TextMuted,
                  fontSize = 13.sp
                )
              }
            }
          } else {
            items(filteredSongs) { song ->
              val isSelected = currentPlayingSong?.id == song.id
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable {
                    playbackManager.setPlaylist(filteredSongs)
                    if (isSelected && isPlaying) {
                      playbackManager.pause()
                    } else {
                      playbackManager.play(song)
                    }
                  }
                  .testTag("song_item_${song.id}"),
                colors = CardDefaults.cardColors(
                  containerColor = if (isSelected) EmeraldPrimary.copy(alpha = 0.15f) else Color(0xFF1E293B)
                ),
                border = androidx.compose.foundation.BorderStroke(
                  1.dp,
                  if (isSelected) EmeraldPrimary else SurfaceCardBorder
                )
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (isSelected) EmeraldPrimary else Color(0xFF334155)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (isSelected && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(24.dp)
                    )
                  }

                  Spacer(modifier = Modifier.width(12.dp))

                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = song.title,
                      color = if (isSelected) EmeraldLight else TextPrimary,
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp
                    )
                    Text(
                      text = "${song.artist} • ${song.album}",
                      color = TextMuted,
                      fontSize = 12.sp
                    )
                  }

                  IconButton(
                    onClick = { songToDelete = song },
                    modifier = Modifier.size(32.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Delete,
                      contentDescription = "Delete",
                      tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        }

        // 4. Now Playing Bottom Bar & Controls
        AnimatedVisibility(visible = currentPlayingSong != null) {
          currentPlayingSong?.let { song ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = EmeraldLight,
                    modifier = Modifier.size(24.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = song.title,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = TextPrimary
                    )
                    Text(
                      text = song.artist,
                      fontSize = 11.sp,
                      color = EmeraldLight
                    )
                  }
                }

                // Slider
                val maxDur = if (duration > 0) duration.toFloat() else 1000f
                Slider(
                  value = currentPos.coerceAtMost(duration).toFloat(),
                  onValueChange = { playbackManager.seekTo(it.toLong()) },
                  valueRange = 0f..maxDur,
                  colors = SliderDefaults.colors(
                    thumbColor = EmeraldPrimary,
                    activeTrackColor = EmeraldPrimary,
                    inactiveTrackColor = Color(0xFF334155)
                  ),
                  modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                // Controls: Previous, Play/Pause, Next
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  IconButton(onClick = { playbackManager.playPrevious() }) {
                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = TextPrimary)
                  }
                  IconButton(
                    onClick = {
                      if (isPlaying) playbackManager.pause() else playbackManager.resume()
                    },
                    modifier = Modifier
                      .size(48.dp)
                      .background(EmeraldPrimary, CircleShape)
                  ) {
                    Icon(
                      imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                      contentDescription = "Play/Pause",
                      tint = Color.White
                    )
                  }
                  IconButton(onClick = { playbackManager.playNext() }) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary)
                  }
                }
              }
            }
          }
        }
      }

      // Confirmation Dialog for Deletion
      if (songToDelete != null) {
        val song = songToDelete!!
        AlertDialog(
          onDismissRequest = { songToDelete = null },
          title = { Text("သီချင်းဖျက်ရန် အတည်ပြုပါ") },
          text = { Text("\"${song.title}\" ကို Music Library ထဲမှ အပြီးတိုင် ဖျက်ထုတ်လိုပါသလား?") },
          confirmButton = {
            TextButton(
              onClick = {
                val s = song
                songToDelete = null
                CoroutineScope(Dispatchers.IO).launch {
                  musicRepository.deleteSong(s)
                }
              }
            ) {
              Text("ဖျက်မည်", color = Color(0xFFEF4444))
            }
          },
          dismissButton = {
            TextButton(onClick = { songToDelete = null }) {
              Text("မဖျက်ပါ", color = TextMuted)
            }
          }
        )
      }
    }
  }
}
