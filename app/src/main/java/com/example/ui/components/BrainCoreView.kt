package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ResponseMode
import com.example.ui.theme.*

data class AgentTaskTemplate(
    val title: String,
    val description: String,
    val prompt: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun BrainCoreView(
    onRunTask: (String, ResponseMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val templates = listOf(
        AgentTaskTemplate(
            title = "Thought Chain Logic Solver",
            description = "အဆင့်ဆင့် စဉ်းစားတွေးခေါ်မှုဖြင့် ပြဿနာများကို ဖြေရှင်းခြင်း",
            prompt = "မေးခွန်း: '၁ မှ ၁၀၀ အထိ ကိန်းပြည့်ပေါင်းလဒ်ကို အလွယ်ဆုံးနည်းဖြင့် တွက်ချက်ပါ'။ Thought Chain နည်းလမ်းဖြင့် ရှင်းပြပေးပါ။",
            icon = Icons.Filled.Psychology
        ),
        AgentTaskTemplate(
            title = "Code Review & Optimization Agent",
            description = "ဆော့ဖ်ဝဲလ် ကုဒ်များ ဆန်းစစ်ခြင်းနှင့် ကောင်းမွန်အောင် ပြုပြင်ခြင်း",
            prompt = "Android Kotlin Jetpack Compose performance optimization ပြုလုပ်ရန် အဓိက သတိပြုရမည့် အချက် (၅) ချက်ကို ရှင်းပြပေးပါ။",
            icon = Icons.Filled.Code
        ),
        AgentTaskTemplate(
            title = "Strategic Decision Analysis",
            description = "နည်းဗျူဟာမြောက် ဆုံးဖြတ်ချက်များ ဆန်းစစ် သုံးသပ်ခြင်း",
            prompt = "AI Technology ကို စီးပွားရေး လုပ်ငန်းများတွင် ထိရောက်စွာ အသုံးပြုနိုင်မည့် SWOT Analysis ကို ပြုစုပေးပါ။",
            icon = Icons.Filled.Speed
        ),
        AgentTaskTemplate(
            title = "Mathematical Proof & STEM Assistant",
            description = "သင်္ချာနှင့် သိပ္ပံ မေးခွန်းများကို အဆင့်လိုက် ရှင်းလင်းခြင်း",
            prompt = "Pythagorean Theorem (ပိုင်သာဂိုရပ်စ် သီအိုရမ်) ကို လက်တွေ့ ဥပမာများဖြင့် ရှင်းပြပေးပါ။",
            icon = Icons.Filled.Functions
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("brain_core_view")
    ) {
        // Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ModeExpertColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            color = AngelSurfaceDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = "Brain Core",
                        tint = ModeExpertColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Brain Core - Agent Reasoning",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "လေ့လာဆန်းစစ်ချက်၊ အဆင့်ဆင့် စဉ်းစားတွေးခေါ်မှု Thought Chain နှင့် Agent တာဝန်များကို စနစ်တကျ ဆောင်ရွက်ပေးပါသည်။",
                    fontSize = 12.sp,
                    color = AngelTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select Agent Reasoning Task:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AngelNeonGreen
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(templates) { template ->
                Surface(
                    onClick = { onRunTask(template.prompt, ResponseMode.EXPERT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agent_task_${template.title}"),
                    shape = RoundedCornerShape(14.dp),
                    color = AngelCardDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ModeExpertColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = template.icon,
                                contentDescription = template.title,
                                tint = ModeExpertColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = template.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = template.description,
                                fontSize = 12.sp,
                                color = AngelTextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ModeExpertColor
                        ) {
                            Text(
                                text = "Run",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
