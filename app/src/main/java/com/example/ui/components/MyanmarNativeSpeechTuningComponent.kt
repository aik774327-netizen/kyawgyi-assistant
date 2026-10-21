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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.utils.MyanmarTTSEngine
import com.example.utils.MyanmarWarmNaturalSpeechSystem
import com.example.utils.MyanmarWarmNaturalSpeechSystem.WarmVoicePersona

/**
 * KYAW GYI AI — Myanmar Warm Natural Voice & Native Articulation Component
 * (မြန်မာဘာသာစကားကို မြန်မာလူမျိုးတစ်ယောက်လို ပီပီသသ စကားအသံထွက်ပြီး နွေးနွေးထွေးထွေး စကားပြောဆိုမှု နည်းစနစ်)
 */
@Composable
fun MyanmarNativeSpeechTuningComponent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val warmSystem = remember { MyanmarWarmNaturalSpeechSystem.getInstance(context) }
    val currentPersona by warmSystem.currentPersona.collectAsState()
    val isWarmModeEnabled by warmSystem.isWarmModeEnabled.collectAsState()
    val isClearArticulation by warmSystem.isClearArticulationEnabled.collectAsState()
    val politeParticle by warmSystem.politeParticle.collectAsState()

    var isAuditioning by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(
                            AngelOrangeAccent.copy(alpha = 0.7f),
                            AngelNeonGreen.copy(alpha = 0.6f)
                        )
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .testTag("myanmar_native_speech_tuning_card"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = AngelOrangeAccent.copy(alpha = 0.2f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Native Voice",
                                tint = AngelOrangeAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "မြန်မာလူမျိုး အသံထွက် နွေးထွေးသော စကားသံ",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AngelOrangeAccent.copy(alpha = 0.25f),
                                border = BorderStroke(0.8.dp, AngelOrangeAccent)
                            ) {
                                Text(
                                    text = "NATIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AngelOrangeAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "ပီသပြတ်သားသော အသံထွက် • နွေးထွေးသော လေသံ • မြန်မာ့စကားအသုံးအနှုန်း",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-Banner Description
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AngelDarkBackground.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, AngelOrangeAccent.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = AngelOrangeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "စက်ရုပ်ဆန်ဆန် အသံမာမာ မဟုတ်ဘဲ မြန်မာလူမျိုးတစ်ဦးကဲ့သို့ ကြင်နာနွေးထွေးစွာ စကားပြောဆိုပေးမည့် နည်းစနစ်ဖြစ်ပါသည်",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Master Warm Phonation Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "နွေးထွေးသော စကားပြောဆိုမှု (Warm Conversational Delivery)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "အဆုံးသတ်စကားလုံးများတွင် ယဉ်ကျေးနွေးထွေးသော အသုံးအနှုန်းများ ဖြည့်ဆည်းပေးမည်",
                        fontSize = 10.sp,
                        color = AngelTextSecondary
                    )
                }

                Switch(
                    checked = isWarmModeEnabled,
                    onCheckedChange = { warmSystem.setWarmModeEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AngelDarkBackground,
                        checkedTrackColor = AngelOrangeAccent
                    ),
                    modifier = Modifier.testTag("warm_mode_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Polite Particle Selector (ခင်ဗျာ / ရှင် / ပါနော်)
            Text(
                text = "ယဉ်ကျေးသော စကားအဆုံးသတ် ရွေးချယ်မှု:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("ပါခင်ဗျာ", "ခင်ဗျာ (Gentleman)"),
                    Pair("ပါရှင်", "ရှင် (Lady)"),
                    Pair("ပါနော်", "ပါနော် (Friendly)")
                ).forEach { (particle, label) ->
                    val isSelected = politeParticle == particle
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AngelOrangeAccent.copy(alpha = 0.25f) else AngelSurfaceDark,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AngelOrangeAccent else Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                warmSystem.setPoliteParticle(particle)
                                Toast.makeText(context, "$label သို့ သတ်မှတ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("polite_particle_$particle")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = particle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AngelOrangeAccent else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Voice Personas Selector
            Text(
                text = "အသံနေအထား ပုံစံ (Voice Personas):",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WarmVoicePersona.values().forEach { persona ->
                    val isSelected = currentPersona == persona
                    val personaTag = persona.name.lowercase()

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AngelDarkBackground.copy(alpha = 0.85f) else AngelSurfaceDark.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) AngelOrangeAccent else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                warmSystem.setPersona(persona)
                            }
                            .testTag("persona_option_$personaTag")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { warmSystem.setPersona(persona) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AngelOrangeAccent,
                                        unselectedColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = persona.displayNameMM,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AngelOrangeAccent else Color.White
                                    )
                                    Text(
                                        text = persona.descriptionMM,
                                        fontSize = 10.sp,
                                        color = AngelTextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Audition Preview Button
            Button(
                onClick = {
                    if (!isAuditioning) {
                        isAuditioning = true
                        warmSystem.previewPersonaSpeech(currentPersona) {
                            isAuditioning = false
                        }
                    } else {
                        MyanmarTTSEngine.getInstance(context).stop()
                        isAuditioning = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("preview_native_speech_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAuditioning) AngelOrangeAccent else AngelNeonGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isAuditioning) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAuditioning) "စကားပြောဆိုမှု ရပ်တန့်မည်" else "နွေးထွေးသော စကားသံကို စမ်းသပ်နားဆင်မည်",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
