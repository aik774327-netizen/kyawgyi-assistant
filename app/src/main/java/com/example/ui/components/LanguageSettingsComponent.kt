package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LanguagePreferencesRepository
import com.example.data.VoiceLanguage
import com.example.ui.theme.*

/**
 * UI Settings component that allows users to select their preferred language
 * (e.g., my-MM, en-US) and persists this choice using DataStore,
 * ensuring the voice pipeline initializes with the selected locale.
 */
@Composable
fun LanguageSettingsComponent(
    modifier: Modifier = Modifier,
    selectedLanguageCode: String,
    onLanguageSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val supportedLanguages = LanguagePreferencesRepository.SUPPORTED_LANGUAGES

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, AngelCyanAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .testTag("language_settings_component"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = AngelCyanAccent.copy(alpha = 0.15f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Language Settings",
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "စကားပြောနှင့် အသံဘာသာစကား (Voice Language)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "DataStore ဖြင့် အမြဲသိမ်းဆည်းပြီး STT/TTS စနစ်များကို ချိန်ညှိပေးပါသည်",
                        fontSize = 11.sp,
                        color = AngelTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Locale Banner
            val currentLang = supportedLanguages.firstOrNull { it.code.equals(selectedLanguageCode, ignoreCase = true) }
                ?: supportedLanguages.first()

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AngelNeonGreen.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = AngelNeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "လက်ရှိ အသံပိုင်းသုံးဘာသာ: ${currentLang.flagEmoji} ${currentLang.displayNameMM} (${currentLang.code})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AngelNeonGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Language Options List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                supportedLanguages.forEach { language ->
                    val isSelected = language.code.equals(selectedLanguageCode, ignoreCase = true)
                    val tagSuffix = language.code.lowercase().replace('-', '_')

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AngelNeonGreen else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (!isSelected) {
                                    onLanguageSelected(language.code)
                                    Toast.makeText(
                                        context,
                                        "စကားပြောဘာသာစကားကို '${language.displayNameMM}' သို့ ပြောင်းလဲသတ်မှတ်ပါပြီ",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .testTag("language_option_$tagSuffix"),
                        color = if (isSelected) AngelDarkBackground.copy(alpha = 0.8f) else AngelDarkBackground.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = language.flagEmoji,
                                    fontSize = 24.sp
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = language.displayNameMM,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f)
                                        )
                                        if (language.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AngelCyanAccent.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "DEFAULT",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = AngelCyanAccent,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "${language.displayNameEN} • ${language.nativeName}",
                                        fontSize = 11.sp,
                                        color = AngelTextSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) AngelNeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) AngelNeonGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Text(
                                        text = language.code,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AngelNeonGreen else AngelTextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            onLanguageSelected(language.code)
                                            Toast.makeText(
                                                context,
                                                "စကားပြောဘာသာစကားကို '${language.displayNameMM}' သို့ ပြောင်းလဲသတ်မှတ်ပါပြီ",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AngelNeonGreen,
                                        unselectedColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // DataStore Persistence Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = AngelCyanAccent.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Jetpack DataStore အသုံးပြုထားပါသည် (Auto-saved & Persistent)",
                    fontSize = 10.sp,
                    color = AngelCyanAccent.copy(alpha = 0.8f)
                )
            }
        }
    }
}
