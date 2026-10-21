package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GeminiApiService
import com.example.security.api.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApiKeyManagementCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val quotaManager = remember { ApiKeyQuotaManager.getRequiredInstance(context) }
    val poolState by quotaManager.poolState.collectAsState()

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var showRotationHistory by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "API Keys",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "API Key & Quota စီမံခန့်ခွဲမှုစနစ်",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-Failover • Cost Optimization • Rate Limiter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Add Key Button
                IconButton(
                    onClick = { showAddKeyDialog = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Backup Key",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Active Key Status Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "လက်ရှိ သုံးစွဲနေသော Key",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = poolState.activeKeyLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Status Badge
                        HealthStatusBadge(poolState.activeKeyStatus)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = poolState.activeKeyMasked,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Tier Chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (poolState.activeKeyTier == ApiKeyTier.FREE_TIER) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (poolState.activeKeyTier == ApiKeyTier.FREE_TIER) "Free Tier (၁၅ RPM)" else "Paid / Custom Tier",
                                color = if (poolState.activeKeyTier == ApiKeyTier.FREE_TIER) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quota Usage Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "နေ့စဉ် Quota သုံးစွဲမှု",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "${poolState.activeKeyUsagePercent}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                poolState.activeKeyUsagePercent >= 90 -> MaterialTheme.colorScheme.error
                                poolState.activeKeyUsagePercent >= 80 -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { (poolState.activeKeyUsagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when {
                            poolState.activeKeyUsagePercent >= 90 -> MaterialTheme.colorScheme.error
                            poolState.activeKeyUsagePercent >= 80 -> Color(0xFFE65100)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    if (poolState.activeKeyUsagePercent >= 80) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Limit Warning",
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "သတိပေးချက်: Quota ၈၀% ကျော်လွန်နေပြီဖြစ်၍ Backup Key သို့ အသင့် failover ပြုလုပ်ပါမည်",
                                fontSize = 10.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cost Optimization Strategy Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ကုန်ကျစရိတ် ချွေတာမှု ဗျူဟာ (Auto-Revert)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "အခမဲ့ Quota Key Cooldown ပြီးသည်နှင့် အလိုအလျောက် ပြန်လည်အသုံးပြုမည်",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Switch(
                    checked = poolState.isAutoRevertToFreeEnabled,
                    onCheckedChange = { coroutineScope.launch { quotaManager.setAutoRevertToFree(it) } },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row: Test Connection & Reset Quota
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isTestingConnection = true
                        testResultText = null
                        coroutineScope.launch {
                            val activeKey = quotaManager.getActiveApiKey()
                            if (activeKey.isNullOrBlank()) {
                                testResultText = "Key မရှိသေးပါ"
                                isTestingConnection = false
                                return@launch
                            }
                            val apiService = GeminiApiService(context)
                            val (success, msg) = apiService.testKeyConnection(activeKey)
                            testResultText = if (success) "အဆက်အသွယ် ကောင်းမွန်ပါသည် ✓" else "ချိတ်ဆက်မှု မအောင်မြင်ပါ: $msg"
                            isTestingConnection = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("စမ်းသပ်မည်", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            quotaManager.resetAllQuotasAndCooldowns()
                            Toast.makeText(context, "Quota နှင့် Cooldown အားလုံး Reset ပြုလုပ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Quota", fontSize = 11.sp)
                }
            }

            testResultText?.let { result ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result,
                    fontSize = 11.sp,
                    color = if (result.contains("✓")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Configured Keys List Accordion
            Text(
                text = "Key စာရင်း (${poolState.keysList.size} ခု) • အဆင်သင့်ရှိ (${poolState.availableKeysCount})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                poolState.keysList.forEach { key ->
                    KeyItemRow(
                        key = key,
                        isActive = key.id == (poolState.keysList.firstOrNull { it.label == poolState.activeKeyLabel }?.id ?: ""),
                        onSelectActive = {
                            coroutineScope.launch { quotaManager.manuallySetActiveKey(key.id) }
                        },
                        onDelete = {
                            coroutineScope.launch { quotaManager.removeKey(key.id) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recent Rotation & Alerts Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRotationHistory = !showRotationHistory }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Key ပြောင်းလဲမှု မှတ်တမ်းနှင့် သတိပေးချက်များ (${poolState.alerts.size})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (showRotationHistory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = showRotationHistory) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (poolState.alerts.isEmpty()) {
                        Text(
                            text = "လက်ရှိ သတိပေးချက် သို့မဟုတ် Failover မှတ်တမ်း မရှိသေးပါ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        poolState.alerts.take(8).forEach { alert ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val iconTint = when (alert.severity) {
                                    AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                                    AlertSeverity.WARNING -> Color(0xFFE65100)
                                    AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
                                }
                                Icon(
                                    imageVector = when (alert.severity) {
                                        AlertSeverity.CRITICAL -> Icons.Default.ErrorOutline
                                        AlertSeverity.WARNING -> Icons.Default.WarningAmber
                                        AlertSeverity.INFO -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = alert.keyLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = iconTint
                                        )
                                        Text(
                                            text = timeFormat.format(Date(alert.timestamp)),
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Text(
                                        text = alert.message,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }

    // Add New Key Dialog
    if (showAddKeyDialog) {
        AddApiKeyDialog(
            onDismiss = { showAddKeyDialog = false },
            onKeyAdded = { key, label, tier ->
                coroutineScope.launch {
                    quotaManager.addOrUpdateKey(rawKey = key, label = label, tier = tier)
                    showAddKeyDialog = false
                    Toast.makeText(context, "Key အသစ်အား Secure Vault တွင် ထည့်သွင်းပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun HealthStatusBadge(status: ApiKeyHealthStatus) {
    val (bg, fg, label) = when (status) {
        ApiKeyHealthStatus.HEALTHY -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "ကောင်းမွန် (Healthy)")
        ApiKeyHealthStatus.NEAR_LIMIT_80 -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "၈၀% ရောက်ရှိ (Warning)")
        ApiKeyHealthStatus.RATE_LIMITED_RPM -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Rate Limit (Cooldown 60s)")
        ApiKeyHealthStatus.EXHAUSTED_429 -> Triple(Color(0xFFFFEBEE), Color(0xFFB71C1C), "Quota ကုန်ဆုံး (429)")
        ApiKeyHealthStatus.IN_COOLDOWN -> Triple(Color(0xFFEDE7F6), Color(0xFF4527A0), "Cooldown စောင့်ဆိုင်းနေ")
        ApiKeyHealthStatus.ERROR -> Triple(Color(0xFFFFEBEE), Color(0xFFB71C1C), "ချို့ယွင်းချက် (Error)")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = fg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun KeyItemRow(
    key: ManagedApiKey,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isActive,
                    onClick = onSelectActive,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = key.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (key.tier == ApiKeyTier.FREE_TIER) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "[Free]",
                                fontSize = 9.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${key.maskedKey} • သုံးစွဲမှု: ${key.dailyUsagePercent}% (${key.requestsToday} calls)",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Quick Status Indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !key.isAvailable -> Color(0xFFD32F2F)
                            key.dailyUsagePercent >= 80 -> Color(0xFFE65100)
                            else -> Color(0xFF388E3C)
                        }
                    )
            )

            if (key.id != ApiKeyQuotaManager.DEFAULT_PRIMARY_ID) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddApiKeyDialog(
    onDismiss: () -> Unit,
    onKeyAdded: (rawKey: String, label: String, tier: ApiKeyTier) -> Unit
) {
    var rawKey by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf(ApiKeyTier.FREE_TIER) }
    var keyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("API Key အသစ်ထည့်သွင်းခြင်း", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Key အား Android Keystore (AES-256 GCM) ဖြင့် လုံခြုံစွာ Encrypt ပြုလုပ် သိမ်းဆည်းပါမည်။",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("အမည် / မှတ်စု (ဥပမာ: Backup Key 1)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rawKey,
                    onValueChange = { rawKey = it },
                    label = { Text("Gemini API Key (AIzaSy...)", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Key အမျိုးအစား (Tier):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTier == ApiKeyTier.FREE_TIER,
                        onClick = { selectedTier = ApiKeyTier.FREE_TIER },
                        label = { Text("Free Quota (15 RPM)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = selectedTier == ApiKeyTier.PAID_TIER,
                        onClick = { selectedTier = ApiKeyTier.PAID_TIER },
                        label = { Text("Paid / Higher Tier", fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawKey.isNotBlank()) {
                        val finalLabel = label.ifBlank { "Backup Key" }
                        onKeyAdded(rawKey.trim(), finalLabel, selectedTier)
                    }
                },
                enabled = rawKey.isNotBlank()
            ) {
                Text("လုံခြုံစွာ သိမ်းဆည်းမည်")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ပယ်ဖျက်မည်")
            }
        }
    )
}
