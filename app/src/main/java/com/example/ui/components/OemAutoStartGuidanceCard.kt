package com.example.ui.components

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
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
import com.example.ui.theme.*
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.OemBackgroundPersistenceHelper
import com.example.utils.OemType

/**
 * OEM Brand Representation for UI Tabs
 */
enum class GuidanceBrand(
    val title: String,
    val oemType: OemType,
    val osName: String
) {
    XIAOMI("Xiaomi / Redmi", OemType.XIAOMI_MIUI_HYPEROS, "MIUI / HyperOS"),
    SAMSUNG("Samsung", OemType.SAMSUNG_ONEUI, "One UI"),
    OPPO_REALME("OPPO / Realme", OemType.OPPO_COLOROS, "ColorOS / Realme UI"),
    VIVO("Vivo / iQOO", OemType.VIVO_FUNTOUCH, "Funtouch OS / OriginOS"),
    TRANSSION("Infinix / Tecno", OemType.TRANSSION_INFINIX_TECNO, "XOS / HiOS"),
    HUAWEI("Huawei / Honor", OemType.HUAWEI_EMUI, "EMUI / MagicOS"),
    STANDARD("Standard / အခြား", OemType.STANDARD_AOSP, "Stock Android / Pixel")
}

/**
 * KYAW GYI AI — OEM Auto-Start & Battery Optimization Guidance Card
 *
 * Provides phone-brand specific step-by-step instructions to prevent
 * aggressive OEM task-killers (Xiaomi, Samsung, OPPO, Vivo, Huawei) from killing
 * Kyaw Gyi's background voice recognition service.
 */
@Composable
fun OemAutoStartGuidanceCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val helper = remember { OemBackgroundPersistenceHelper(context) }
    val diagnostics = remember { helper.getDiagnostics() }

    // Initial selected brand based on device detection
    val initialBrand = remember {
        when (diagnostics.oemType) {
            OemType.XIAOMI_MIUI_HYPEROS -> GuidanceBrand.XIAOMI
            OemType.SAMSUNG_ONEUI -> GuidanceBrand.SAMSUNG
            OemType.OPPO_COLOROS -> GuidanceBrand.OPPO_REALME
            OemType.VIVO_FUNTOUCH -> GuidanceBrand.VIVO
            OemType.TRANSSION_INFINIX_TECNO -> GuidanceBrand.TRANSSION
            OemType.HUAWEI_EMUI -> GuidanceBrand.HUAWEI
            else -> GuidanceBrand.STANDARD
        }
    }

    var selectedBrand by remember { mutableStateOf(initialBrand) }
    var isExpanded by remember { mutableStateOf(true) }
    var isIgnoredState by remember {
        mutableStateOf(KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context))
    }

    val deviceManufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val deviceModel = Build.MODEL

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (isIgnoredState) AngelCyanAccent.copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.6f)
                ),
                RoundedCornerShape(16.dp)
            )
            .testTag("oem_autostart_guidance_card"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isIgnoredState) AngelNeonGreen.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isIgnoredState) Icons.Default.Shield else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (isIgnoredState) AngelNeonGreen else Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "OEM Auto-Start နှင့် ဘက်ထရီ လမ်းညွှန်",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "သင့်ဖုန်း: $deviceManufacturer $deviceModel (Android ${Build.VERSION.RELEASE})",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp).testTag("toggle_oem_guide_btn")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = AngelTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Optimization Status Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isIgnoredState) AngelNeonGreen.copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isIgnoredState) AngelNeonGreen.copy(alpha = 0.3f) else Color(0xFFF59E0B).copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isIgnoredState) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isIgnoredState) AngelNeonGreen else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isIgnoredState) {
                            "ဘက်ထရီချွင်းချက် ရရှိထားပါသည် (Battery Optimization Unrestricted)"
                        } else {
                            "နောက်ခံ အသံနားထောင်မှု မရပ်တန့်စေရန် Auto-Start နှင့် Battery ချွင်းချက် ပေးထားပါ"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isIgnoredState) AngelNeonGreen else Color(0xFFF59E0B)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "အချို့သော ဖုန်းစနစ်များသည် အက်ပ်နောက်ကွယ်မှ အလုပ်လုပ်ခြင်းကို အလိုအလျောက် ပိတ်ပစ်တတ်ပါသည်။ ကျော်ကြီး အမြဲအဆင်သင့် ရှိနေစေရန် အောက်ပါ အဆင့်များကို လုပ်ဆောင်ပေးပါ:",
                        fontSize = 11.sp,
                        color = AngelTextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Brand Selection Horizontal Scroll Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GuidanceBrand.values().forEach { brand ->
                            val isSelected = selectedBrand == brand
                            Surface(
                                onClick = { selectedBrand = brand },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) AngelNeonGreen else AngelSurfaceDark,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AngelNeonGreen else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = brand.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Step-by-Step Instructions Container
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AngelSurfaceDark,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${selectedBrand.title} (${selectedBrand.osName}) အတွက် လမ်းညွှန်ချက်:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AngelCyanAccent
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val steps = getStepsForBrand(selectedBrand)
                            steps.forEachIndexed { index, step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AngelCyanAccent.copy(alpha = 0.15f),
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AngelCyanAccent
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = step,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Launch Vendor Settings / Auto-Start Button
                        Button(
                            onClick = {
                                val success = helper.launchSettings()
                                if (!success) {
                                    KyawGyiPermissionManager.openOemAutostartSettings(context)
                                }
                                Toast.makeText(
                                    context,
                                    "Kyaw Gyi AI အား Auto-start နှင့် No restrictions သတ်မှတ်ပေးပါ",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AngelNeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("open_oem_autostart_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Launch,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-Start ဖွင့်ရန်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Ignore Battery Optimizations Direct Request
                        OutlinedButton(
                            onClick = {
                                KyawGyiPermissionManager.openBatteryOptimizationSettings(context)
                                isIgnoredState = KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AngelCyanAccent
                            ),
                            border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("request_battery_exemption_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ဘက်ထရီ ကင်းလွတ်ခွင့်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Open App Info Details Settings
                    OutlinedButton(
                        onClick = {
                            KyawGyiPermissionManager.openAppDetailsSettings(context)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AngelTextSecondary
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .testTag("open_app_info_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AngelTextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kyaw Gyi AI - App Info ဆက်တင်သို့ သွားမည်",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Returns customized, precise Burmese instructions for each OEM manufacturer.
 */
private fun getStepsForBrand(brand: GuidanceBrand): List<String> {
    return when (brand) {
        GuidanceBrand.XIAOMI -> listOf(
            "Security App > Manage Apps > Kyaw Gyi AI သို့ သွားပါ။",
            "‘Autostart’ (အလိုအလျောက် စတင်ခွင့်) ကို ဖွင့် (ON) ပေးပါ။",
            "‘Battery Saver’ တွင် ‘No restrictions’ (ကန့်သတ်ချက်မရှိ) ကို ရွေးချယ်ပါ။",
            "Recent Apps (နောက်ဆုံးသုံးထားသော အက်ပ်များ) စာရင်းတွင် Kyaw Gyi AI ကို ဖိထားပြီး သော့ခလောက် (Lock icon) ခတ်ထားပေးပါ။",
            "Other Permissions တွင် ‘Display pop-up windows while running in the background’ ကို ခွင့်ပြုပေးပါ။"
        )
        GuidanceBrand.SAMSUNG -> listOf(
            "Settings > Battery and device care > Battery သို့ သွားပါ။",
            "‘Background usage limits’ > ‘Never sleeping apps’ စာရင်းထဲသို့ Kyaw Gyi AI ကို (+) နှိပ်၍ ထည့်သွင်းပေးပါ။",
            "App Info > Battery တွင် ‘Unrestricted’ (အကန့်အသတ်မရှိ) ကို ရွေးချယ်ပါ။",
            "Device Care > Auto optimization ဆက်တင်တွင် Kyaw Gyi AI ကို ခြွင်းချက် ထားပေးပါ။"
        )
        GuidanceBrand.OPPO_REALME -> listOf(
            "Settings > Battery > App battery management > Kyaw Gyi AI သို့ သွားပါ။",
            "‘Allow background activity’ နှင့် ‘Allow auto-launch’ (၂) ခုလုံးကို ဖွင့်ပေးပါ။",
            "Phone Manager အက်ပ် > Privacy permissions > ‘Startup manager’ တွင် Kyaw Gyi AI ကို ဖွင့်ပါ။",
            "Recent Apps စာရင်းတွင် အက်ပ်ပြတင်းပေါက်ကို အောက်သို့ဆွဲချ၍ Lock ခတ်ထားပေးပါ။"
        )
        GuidanceBrand.VIVO -> listOf(
            "Settings > Battery > ‘High background power consumption’ ထဲတွင် Kyaw Gyi AI ကို ဖွင့်ပေးပါ။",
            "iManager အက်ပ် > App manager > ‘Autostart’ တွင် Kyaw Gyi AI ကို ခွင့်ပြုပါ။",
            "Recent Apps စာရင်းတွင် Kyaw Gyi AI ကို Lock ခတ်ထားပေးပါ။"
        )
        GuidanceBrand.TRANSSION -> listOf(
            "Phone Master အက်ပ် > ‘Auto-start management’ (အလိုအလျောက် စတင်ခွင့်) တွင် Kyaw Gyi AI ကို ဖွင့်ပေးပါ။",
            "Settings > ‘Power Marathon’ သို့မဟုတ် ‘Battery Lab’ ဆက်တင်သို့ သွားပါ။",
            "‘App power consumption management’ တွင် Kyaw Gyi AI အား ကန့်သတ်ချက်မရှိ ခွင့်ပြုပါ။",
            "Recent Apps စာရင်းတွင် Kyaw Gyi AI အက်ပ်ကို အောက်သို့ဆွဲချ၍ သော့ခလောက် (Lock) ခတ်ထားပေးပါ။"
        )
        GuidanceBrand.HUAWEI -> listOf(
            "Settings > Battery > ‘App launch’ ဆက်တင်သို့ သွားပါ။",
            "Kyaw Gyi AI အား ‘Manage automatically’ မှ ‘Manage manually’ သို့ ပြောင်းလဲပါ။",
            "Auto-launch, Secondary launch, Run in background (၃) မျိုးလုံးကို ဖွင့်ပေးပါ။"
        )
        GuidanceBrand.STANDARD -> listOf(
            "Settings > Apps > All Apps > Kyaw Gyi AI သို့ သွားပါ။",
            "‘Battery’ (ဘက်ထရီ) ဆက်တင်ကို နှိပ်ပါ။",
            "‘Unrestricted’ (သို့မဟုတ် Don't optimize - မကန့်သတ်ပါ) ကို ရွေးချယ်ပေးပါ။",
            "အသိပေးချက်များ (Notifications) နှင့် အသံဖမ်းခွင့် (Microphone) ကို အမြဲ ခွင့်ပြုထားပေးပါ။"
        )
    }
}
