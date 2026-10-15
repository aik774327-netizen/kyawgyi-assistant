package com.kyawgyi.ir.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyawgyi.ir.apikey.ApiKeyManager
import com.kyawgyi.ir.apikey.ApiKeyQuotaAlert
import com.kyawgyi.ir.apikey.ApiKeyStatus
import com.kyawgyi.ir.apikey.ApiKeyTier
import com.kyawgyi.ir.apikey.ApiKeyValidationResult
import com.kyawgyi.ir.apikey.ApiKeyValidator
import com.kyawgyi.ir.apikey.ManagedApiKey
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyManagerScreen(
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val apiKeyManager = remember { ApiKeyManager.getInstance(context) }
  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              "API သော့ချက်စီမံမှု စနစ်",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              "Quota Detection, Failover & Free Tier Reversion",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          if (onNavigateBack != null) {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
        actions = {
          IconButton(
            onClick = { apiKeyManager.checkAndRevertToFreeTierIfReset() },
            modifier = Modifier.testTag("btn_sync_free_tier")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Sync Free Tier")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    floatingActionButton = {
      Button(
        onClick = { showAddDialog = true },
        modifier = Modifier.testTag("btn_add_api_key"),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("သော့အသစ် ထည့်မည်")
      }
    }
  ) { padding ->
    ApiKeyManagerContent(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      showAddDialog = showAddDialog,
      onShowAddDialogChange = { showAddDialog = it }
    )
  }
}

@Composable
fun ApiKeyManagerContent(
  modifier: Modifier = Modifier,
  showAddDialog: Boolean = false,
  onShowAddDialogChange: ((Boolean) -> Unit)? = null
) {
  val context = LocalContext.current
  val apiKeyManager = remember { ApiKeyManager.getInstance(context) }

  val keys by apiKeyManager.keys.collectAsState()
  val activeKey by apiKeyManager.activeKey.collectAsState()
  val autoFailover by apiKeyManager.autoFailoverEnabled.collectAsState()
  val autoRevert by apiKeyManager.autoRevertToFreeTier.collectAsState()
  val rotationLogs by apiKeyManager.rotationLogs.collectAsState()

  var latestAlert by remember { mutableStateOf<ApiKeyQuotaAlert?>(null) }
  var internalShowAddDialog by remember { mutableStateOf(false) }
  val isAddDialogVisible = showAddDialog || internalShowAddDialog

  LaunchedEffect(apiKeyManager) {
    apiKeyManager.quotaAlerts.collect { alert ->
      latestAlert = alert
    }
  }

  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Quota Pre-Warning Alert Banner (Animated)
    item {
      AnimatedVisibility(visible = latestAlert != null) {
        latestAlert?.let { alert ->
          QuotaAlertBanner(
            alert = alert,
            onDismiss = { latestAlert = null }
          )
        }
      }
    }

    // D9: Security Warning Card (Compiled-in keys vs Runtime Entry & Server Proxy)
    item {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("card_security_warning")
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.Security,
            contentDescription = "Security Warning",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
          )
          Spacer(Modifier.width(12.dp))
          Column {
            Text(
              "လုံခြုံရေးသတိပေးချက် (Security Warning)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
              "အက်ပ်အတွင်း ကြိုတင်ထည့်သွင်းတည်ဆောက်ထားသော (Compiled-in) API Key များသည် APK မှတစ်ဆင့် ပြန်လည်ထုတ်ယူခံရနိုင်ပါသည်။ အမှန်တကယ်ထုတ်လုပ်ဖြန့်ချိရာတွင် သုံးစွဲသူကိုယ်တိုင် runtime မှ သော့ထည့်သွင်းခြင်း သို့မဟုတ် server proxy ကို အသုံးပြုသင့်ပါသည်။ (Full server proxy fix is NOT DONE — needs server infrastructure)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }

    // 2. Active Key Hero Card
    item {
      ActiveKeyOverviewCard(
        activeKey = activeKey,
        onRecordUsage = { apiKeyManager.recordUsage(1) },
        onSimulateWarning = {
          activeKey?.let { key ->
            val warningTarget = (key.maxQuota * 0.85f).toInt()
            val increment = (warningTarget - key.usedQuota).coerceAtLeast(1)
            apiKeyManager.recordUsage(increment)
          }
        },
        onSimulate429Failover = {
          apiKeyManager.handleHttpError(429, "Too Many Requests (Rate limit test)")
        }
      )
    }

    // 3. Failover & Revert Strategy Controls
    item {
      StrategySettingsCard(
        autoFailover = autoFailover,
        onAutoFailoverChange = { apiKeyManager.setAutoFailoverEnabled(it) },
        autoRevert = autoRevert,
        onAutoRevertChange = { apiKeyManager.setAutoRevertToFreeTier(it) },
        onCheckRevert = { apiKeyManager.checkAndRevertToFreeTierIfReset() }
      )
    }

    // 4. Managed Keys Pool Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(
            "စီမံခန့်ခွဲထားသော API သော့များ (${keys.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer
        ) {
          Text(
            "AES-256-GCM လုံခြုံမှု",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    }

    // 5. Key Pool Cards
    items(keys, key = { it.id }) { keyItem ->
      ManagedKeyItemCard(
        keyItem = keyItem,
        isActive = keyItem.id == activeKey?.id,
        onSetActive = { apiKeyManager.setActiveKey(keyItem.id) },
        onResetQuota = { apiKeyManager.resetKeyQuota(keyItem.id) },
        onDelete = { apiKeyManager.removeApiKey(keyItem.id) }
      )
    }

    // 6. Failover & Rotation Audit Log
    if (rotationLogs.isNotEmpty()) {
      item {
        Spacer(Modifier.height(8.dp))
        Text(
          "လတ်တလော သော့ပြောင်းလဲမှု မှတ်တမ်း (Rotation Logs)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }

      items(rotationLogs.take(5)) { log ->
        RotationLogItem(log = log, keys = keys)
      }
    }

    item {
      Spacer(Modifier.height(60.dp))
    }
  }

  if (isAddDialogVisible) {
    AddApiKeyDialog(
      onDismiss = {
        internalShowAddDialog = false
        onShowAddDialogChange?.invoke(false)
      },
      onConfirm = { label, rawKey, tier, maxQuota, isDefaultFree ->
        apiKeyManager.addApiKey(label, rawKey, tier, maxQuota, isDefaultFree)
        internalShowAddDialog = false
        onShowAddDialogChange?.invoke(false)
      }
    )
  }
}

/**
 * Pre-warning Quota Alert Banner.
 */
@Composable
private fun QuotaAlertBanner(
  alert: ApiKeyQuotaAlert,
  onDismiss: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer
    ),
    shape = RoundedCornerShape(16.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.Top
    ) {
      Icon(
        Icons.Default.NotificationsActive,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(28.dp)
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "ခွဲတမ်းကုန်ဆုံးမှု ကြိုတင်သတိပေးချက်",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
          alert.messageBurmese,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "လက်ရှိအသုံးပြုမှု: ${alert.currentUsage} / ${alert.maxQuota} (${(alert.usagePercentage * 100).toInt()}%)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
        )
      }
      IconButton(onClick = onDismiss) {
        Icon(
          Icons.Default.Delete,
          contentDescription = "Dismiss",
          tint = MaterialTheme.colorScheme.error
        )
      }
    }
  }
}

/**
 * Active Key Overview Card with live gauges and simulation actions.
 */
@Composable
private fun ActiveKeyOverviewCard(
  activeKey: ManagedApiKey?,
  onRecordUsage: () -> Unit,
  onSimulateWarning: () -> Unit,
  onSimulate429Failover: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ),
    shape = RoundedCornerShape(20.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(12.dp)
              .clip(CircleShape)
              .background(if (activeKey?.canBeUsed == true) Color(0xFF10B981) else Color(0xFFEF4444))
          )
          Spacer(Modifier.width(8.dp))
          Text(
            "လက်ရှိ အသုံးပြုနေသော သော့ (Active Key)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primary
        ) {
          Text(
            activeKey?.tier?.burmeseLabel ?: "None",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(Modifier.height(10.dp))

      Text(
        activeKey?.label ?: "သော့ ရွေးချယ်ထားခြင်း မရှိပါ",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )

      Text(
        "Key: ${activeKey?.maskedKey ?: "N/A"}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(Modifier.height(14.dp))

      // Usage Progress Indicator
      val usagePct = activeKey?.usagePercentage ?: 0f
      val progressColor = when {
        usagePct >= 0.95f -> MaterialTheme.colorScheme.error
        usagePct >= 0.80f -> Color(0xFFF59E0B) // Warning amber
        else -> MaterialTheme.colorScheme.primary
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          "ခွဲတမ်း အသုံးပြုမှု: ${activeKey?.usedQuota ?: 0} / ${activeKey?.maxQuota ?: 0}",
          style = MaterialTheme.typography.labelMedium
        )
        Text(
          "${(usagePct * 100).toInt()}%",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = progressColor
        )
      }

      Spacer(Modifier.height(6.dp))

      LinearProgressIndicator(
        progress = { usagePct },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = progressColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )

      Spacer(Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          "ကျန်ရှိခွဲတမ်း: ${activeKey?.remainingQuota ?: 0} calls",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (activeKey?.isApproachingQuota == true) {
          Text(
            "⚠️ 80% ကျော်လွန်နေပြီ",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFF59E0B),
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      // Simulation Test Controls
      Text(
        "စမ်းသပ်မှု လုပ်ဆောင်ချက်များ (Test & Failover Simulation)",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
      )
      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onRecordUsage,
          modifier = Modifier
            .weight(1f)
            .testTag("btn_sim_call"),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
          Text("Call (+1)", fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = onSimulateWarning,
          modifier = Modifier
            .weight(1.2f)
            .testTag("btn_sim_warning")
        ) {
          Text("80% Quota စမ်းမည်", fontSize = 12.sp)
        }

        Button(
          onClick = onSimulate429Failover,
          modifier = Modifier
            .weight(1.3f)
            .testTag("btn_sim_429"),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("429 Failover", fontSize = 12.sp)
        }
      }
    }
  }
}

/**
 * Strategy Settings Card for Failover and Cost-Optimized Free Tier Reversion.
 */
@Composable
private fun StrategySettingsCard(
  autoFailover: Boolean,
  onAutoFailoverChange: (Boolean) -> Unit,
  autoRevert: Boolean,
  onAutoRevertChange: (Boolean) -> Unit,
  onCheckRevert: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
          "အလိုအလျောက် ပြောင်းလဲမှု မဟာဗျူဟာများ",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(Modifier.height(12.dp))

      // 1. Auto Failover Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "အလိုအလျောက် သော့ပြောင်းလဲခြင်း (Auto-Failover)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            "HTTP 429 သို့မဟုတ် ခွဲတမ်းကုန်ပါက ဝန်ဆောင်မှုမပြတ်စေရန် အရန်သော့သို့ ချောမွေ့စွာ ချက်ချင်းပြောင်းမည်",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = autoFailover,
          onCheckedChange = onAutoFailoverChange,
          modifier = Modifier.testTag("switch_auto_failover")
        )
      }

      Spacer(Modifier.height(12.dp))

      // 2. Revert to Free Tier on Reset Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            "အခမဲ့ခွဲတမ်း ပြန်သတ်မှတ်ပါက ပြန်လည်ရယူခြင်း (Cost Strategy)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            "မူလအခမဲ့သော့ Cooldown ပြီးဆုံးခြင်း သို့မဟုတ် ခွဲတမ်းသစ်စတင်ချိန်တွင် ကုန်ကျစရိတ်သက်သာစေရန် မူလအခမဲ့သော့သို့ အလိုအလျောက် ပြန်သွားမည်",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = autoRevert,
          onCheckedChange = onAutoRevertChange,
          modifier = Modifier.testTag("switch_auto_revert")
        )
      }

      Spacer(Modifier.height(8.dp))

      OutlinedButton(
        onClick = onCheckRevert,
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("အခမဲ့ခွဲတမ်း ပြန်လည်သတ်မှတ်မှု ယခု စစ်ဆေးမည်")
      }
    }
  }
}

/**
 * Key Pool Item Card.
 */
@Composable
private fun ManagedKeyItemCard(
  keyItem: ManagedApiKey,
  isActive: Boolean,
  onSetActive: () -> Unit,
  onResetQuota: () -> Unit,
  onDelete: () -> Unit
) {
  val borderModifier = if (isActive) {
    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
  } else {
    Modifier
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .then(borderModifier),
    colors = CardDefaults.cardColors(
      containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
      else MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Key,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
          Spacer(Modifier.width(8.dp))
          Text(
            keyItem.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        if (isActive) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary
          ) {
            Text(
              "ACTIVE",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimary,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
          }
        }
      }

      Spacer(Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "Key: ${keyItem.maskedKey}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StatusBadge(status = keyItem.status)
      }

      Spacer(Modifier.height(10.dp))

      // Usage Progress
      val usagePct = keyItem.usagePercentage
      val progressColor = when {
        usagePct >= 0.95f -> MaterialTheme.colorScheme.error
        usagePct >= 0.80f -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
      }

      LinearProgressIndicator(
        progress = { usagePct },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = progressColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
      )

      Spacer(Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          "${keyItem.usedQuota} / ${keyItem.maxQuota} (${(usagePct * 100).toInt()}%)",
          style = MaterialTheme.typography.labelSmall
        )
        Text(
          "ကျန်ရှိ: ${keyItem.remainingQuota}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (keyItem.isCoolingDown) {
        val secondsLeft = ((keyItem.cooldownUntilTimestamp - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        Spacer(Modifier.height(4.dp))
        Text(
          "⏳ Cooldown စောင့်ဆိုင်းနေသည်: $secondsLeft စက္ကန့်",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(Modifier.height(12.dp))

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(onClick = onResetQuota) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("ခွဲတမ်း Reset")
        }

        if (!isActive) {
          Spacer(Modifier.width(8.dp))
          Button(onClick = onSetActive) {
            Text("Active သတ်မှတ်မည်")
          }
        }

        if (!keyItem.isDefaultFreeTier) {
          Spacer(Modifier.width(8.dp))
          IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
  }
}

@Composable
private fun StatusBadge(status: ApiKeyStatus) {
  val (color, text) = when (status) {
    ApiKeyStatus.HEALTHY_ACTIVE -> Color(0xFF10B981) to "ပုံမှန် (Healthy)"
    ApiKeyStatus.APPROACHING_QUOTA -> Color(0xFFF59E0B) to "ခွဲတမ်း ကုန်လုနီးပါး"
    ApiKeyStatus.RATE_LIMITED_429 -> MaterialTheme.colorScheme.error to "Rate Limited (429)"
    ApiKeyStatus.QUOTA_EXHAUSTED -> MaterialTheme.colorScheme.error to "Quota Exhausted"
    ApiKeyStatus.COOLING_DOWN -> Color(0xFF6366F1) to "Cooldown စောင့်ဆိုင်း"
    ApiKeyStatus.DISABLED -> Color.Gray to "Disabled"
  }

  Surface(
    shape = RoundedCornerShape(6.dp),
    color = color.copy(alpha = 0.15f)
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
  }
}

@Composable
private fun RotationLogItem(
  log: com.kyawgyi.ir.apikey.ApiKeyRotationLog,
  keys: List<ManagedApiKey>
) {
  val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
  val toKey = keys.firstOrNull { it.id == log.toKeyId }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    shape = RoundedCornerShape(12.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        Icons.AutoMirrored.Filled.CompareArrows,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp)
      )
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          "ပြောင်းလဲသော သော့: ${toKey?.label ?: log.toKeyId}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          log.reason,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Text(
        dateFormat.format(Date(log.timestamp)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * Dialog to add a new API Key securely.
 */
@Composable
private fun AddApiKeyDialog(
  onDismiss: () -> Unit,
  onConfirm: (label: String, rawKey: String, tier: ApiKeyTier, maxQuota: Int, isDefaultFree: Boolean) -> Unit
) {
  var label by remember { mutableStateOf("") }
  var rawKey by remember { mutableStateOf("") }
  var selectedTier by remember { mutableStateOf(ApiKeyTier.FREE_TIER) }
  var maxQuotaText by remember { mutableStateOf("1000") }

  var isVerifying by remember { mutableStateOf(false) }
  var validationResult by remember { mutableStateOf<ApiKeyValidationResult?>(null) }
  val coroutineScope = rememberCoroutineScope()
  val validator = remember { ApiKeyValidator() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("API သော့အသစ် ထည့်သွင်းခြင်း")
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          "သော့ချက်ကို Hardware-backed AES-256-GCM ဖြင့် လုံခြုံစွာ Encrypt ပြုလုပ်သိမ်းဆည်းပါမည်။",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("သော့အမည် (Label)") },
          placeholder = { Text("ဥပမာ: Backup Gemini Key") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = rawKey,
            onValueChange = {
              rawKey = it
              validationResult = null
            },
            label = { Text("Secret API Key") },
            placeholder = { Text("AIzaSy...") },
            modifier = Modifier.weight(1f),
            singleLine = true
          )

          Button(
            onClick = {
              coroutineScope.launch {
                isVerifying = true
                validationResult = validator.validateKey(rawKey)
                isVerifying = false
              }
            },
            enabled = !isVerifying,
            modifier = Modifier.testTag("btn_verify_api_key")
          ) {
            if (isVerifying) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
              )
            } else {
              Text("စစ်ဆေးရန်")
            }
          }
        }

        // Validation result feedback banner
        validationResult?.let { res ->
          val (color, maskedDisplay) = when (res) {
            is ApiKeyValidationResult.Success -> MaterialTheme.colorScheme.primary to " (${res.maskedKey})"
            is ApiKeyValidationResult.InvalidKey -> MaterialTheme.colorScheme.error to " (${res.maskedKey})"
            is ApiKeyValidationResult.QuotaExceeded -> MaterialTheme.colorScheme.error to " (${res.maskedKey})"
            is ApiKeyValidationResult.NetworkError -> MaterialTheme.colorScheme.error to " (${res.maskedKey})"
            is ApiKeyValidationResult.EmptyInput -> MaterialTheme.colorScheme.error to ""
          }
          Text(
            text = "${res.message}$maskedDisplay",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier
              .padding(horizontal = 4.dp)
              .testTag("text_validation_result")
          )
        }

        Text("Tier အမျိုးအစား:", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ApiKeyTier.entries.forEach { tier ->
            FilterChip(
              selected = selectedTier == tier,
              onClick = { selectedTier = tier },
              label = { Text(tier.name, fontSize = 11.sp) }
            )
          }
        }

        OutlinedTextField(
          value = maxQuotaText,
          onValueChange = { maxQuotaText = it },
          label = { Text("အများဆုံး ခွဲတမ်း (Max Quota)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val quota = maxQuotaText.toIntOrNull() ?: 1000
          if (label.isNotBlank() && rawKey.isNotBlank()) {
            onConfirm(label.trim(), rawKey.trim(), selectedTier, quota, false)
          }
        },
        enabled = label.isNotBlank() && rawKey.isNotBlank()
      ) {
        Text("လုံခြုံစွာ သိမ်းမည်")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("မလုပ်တော့ပါ")
      }
    }
  )
}
