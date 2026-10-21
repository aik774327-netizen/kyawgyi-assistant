package com.example.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoicePermissions
 *
 * Centralized definition and management of runtime permissions required for Kyaw Gyi
 * voice-activated intelligence engine:
 * 1. Contacts (READ_CONTACTS, CALL_PHONE) - For voice-based dialing & contact lookup
 * 2. Media (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE) - For voice-based local MP3 music playback
 * 3. QueryAllPackages (QUERY_ALL_PACKAGES) - For voice-based discovery & launching of installed apps
 * 4. Core Audio (RECORD_AUDIO) & Notifications (POST_NOTIFICATIONS) - For voice listening & foreground service
 *
 * Provides a central permission request flow to be triggered before enabling the voice-activated engine.
 */
object VoicePermissions {

    private const val TAG = "VoicePermissions"

    // 1. Contacts Permissions
    const val PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS
    const val PERMISSION_CALL_PHONE = Manifest.permission.CALL_PHONE

    // 2. Media Audio Permissions (Storage)
    val PERMISSION_READ_MEDIA_AUDIO: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // 3. Query All Packages Permission
    const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

    // 4. Voice Audio Recording & Service Permissions
    const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    val PERMISSION_POST_NOTIFICATIONS: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    /**
     * Categories of permissions required by Kyaw Gyi Voice Assistant.
     */
    enum class PermissionGroup(
        val titleMM: String,
        val titleEN: String,
        val descriptionMM: String,
        val icon: ImageVector
    ) {
        MICROPHONE(
            titleMM = "အသံဖမ်းယူခွင့် (Microphone)",
            titleEN = "Microphone Access",
            descriptionMM = "ကျော်ကြီးအား အသံဖြင့်ခေါ်ယူရန်နှင့် စကားသံညွှန်ကြားချက်များကို နားထောင်ရန် လိုအပ်ပါသည်",
            icon = Icons.Default.Mic
        ),
        CONTACTS(
            titleMM = "ဖုန်းအဆက်အသွယ် (Contacts)",
            titleEN = "Contacts & Calling",
            descriptionMM = "စကားသံဖြင့် အမည်ပြော၍ တိုက်ရိုက်ဖုန်းခေါ်ဆိုရန် အဆက်အသွယ်များကို ဖတ်ရှုခွင့် လိုအပ်ပါသည်",
            icon = Icons.Default.Contacts
        ),
        MEDIA(
            titleMM = "တေးဂီတနှင့် မီဒီယာ (Media Audio)",
            titleEN = "Media & Music",
            descriptionMM = "စက်အတွင်းရှိ MP3 သီချင်းဖိုင်များကို အသံဖြင့် ညွှန်ကြားဖွင့်လှစ်နိုင်ရန် လိုအပ်ပါသည်",
            icon = Icons.Default.MusicNote
        ),
        QUERY_PACKAGES(
            titleMM = "အက်ပ်များ ရှာဖွေခွင့် (Query Packages)",
            titleEN = "App Discovery",
            descriptionMM = "စက်အတွင်း ထည့်သွင်းထားသော အက်ပ်များကို စကားသံဖြင့် တိုက်ရိုက်ဖွင့်လှစ်/ပိတ်နိုင်ရန် လိုအပ်ပါသည်",
            icon = Icons.Default.Apps
        )
    }

    data class PermissionStatusItem(
        val permission: String,
        val group: PermissionGroup,
        val isGranted: Boolean,
        val isCritical: Boolean = true
    )

    /**
     * Returns the array of required runtime permissions based on the device's Android API version.
     */
    fun getRequiredRuntimePermissions(): Array<String> {
        val permissions = mutableListOf(
            PERMISSION_RECORD_AUDIO,
            PERMISSION_READ_CONTACTS,
            PERMISSION_CALL_PHONE,
            PERMISSION_READ_MEDIA_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    /**
     * Check whether Contacts permission is granted.
     */
    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check whether Call Phone permission is granted.
     */
    fun hasCallPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check whether Media Audio permission is granted.
     */
    fun hasMediaPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check whether Microphone permission is granted.
     */
    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check whether Query All Packages is permitted / queryable.
     * On Android 11+ (API 30+), verifies package manager ability to resolve installed applications.
     */
    fun hasQueryAllPackagesPermission(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            // Check whether QUERY_ALL_PACKAGES is declared and active, or package queries resolve apps
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedApps = pm.queryIntentActivities(mainIntent, 0)
            resolvedApps.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying packages capability", e)
            false
        }
    }

    /**
     * Check whether notification permission is granted (Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if ALL required runtime permissions for the voice engine are currently granted.
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty()
    }

    /**
     * Returns a list of all ungranted runtime permissions.
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()

        if (!hasMicrophonePermission(context)) {
            missing.add(PERMISSION_RECORD_AUDIO)
        }
        if (!hasContactsPermission(context)) {
            missing.add(PERMISSION_READ_CONTACTS)
        }
        if (!hasCallPhonePermission(context)) {
            missing.add(PERMISSION_CALL_PHONE)
        }
        if (!hasMediaPermission(context)) {
            missing.add(PERMISSION_READ_MEDIA_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return missing
    }

    /**
     * Comprehensive status report for all voice permission groups.
     */
    fun getPermissionGroupStatuses(context: Context): List<PermissionStatusItem> {
        val list = mutableListOf<PermissionStatusItem>()
        list.add(
            PermissionStatusItem(
                permission = PERMISSION_RECORD_AUDIO,
                group = PermissionGroup.MICROPHONE,
                isGranted = hasMicrophonePermission(context),
                isCritical = true
            )
        )
        list.add(
            PermissionStatusItem(
                permission = PERMISSION_READ_CONTACTS,
                group = PermissionGroup.CONTACTS,
                isGranted = hasContactsPermission(context) && hasCallPhonePermission(context),
                isCritical = false
            )
        )
        list.add(
            PermissionStatusItem(
                permission = PERMISSION_READ_MEDIA_AUDIO,
                group = PermissionGroup.MEDIA,
                isGranted = hasMediaPermission(context),
                isCritical = false
            )
        )
        list.add(
            PermissionStatusItem(
                permission = PERMISSION_QUERY_ALL_PACKAGES,
                group = PermissionGroup.QUERY_PACKAGES,
                isGranted = hasQueryAllPackagesPermission(context),
                isCritical = false
            )
        )
        return list
    }

    /**
     * Open system application settings for manual permission grant.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening application settings", e)
        }
    }
}

/**
 * VoicePermissionCoordinator
 *
 * Central permission request flow coordinator to be registered in a ComponentActivity.
 * Enforces pre-flight permission validation before triggering the voice-activated engine.
 */
class VoicePermissionCoordinator(
    private val activity: ComponentActivity,
    private val onAllGranted: () -> Unit = {},
    private val onPermissionDenied: (deniedList: List<String>) -> Unit = {}
) {
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val _showRationaleState = MutableStateFlow(false)
    val showRationaleState: StateFlow<Boolean> = _showRationaleState.asStateFlow()

    private val _missingPermissionsState = MutableStateFlow<List<String>>(emptyList())
    val missingPermissionsState: StateFlow<List<String>> = _missingPermissionsState.asStateFlow()

    init {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val denied = results.filter { !it.value }.keys.toList()
            if (denied.isEmpty()) {
                Log.i("VoicePermCoordinator", "All required voice permissions granted successfully.")
                _showRationaleState.value = false
                onAllGranted()
            } else {
                Log.w("VoicePermCoordinator", "Some voice permissions denied: $denied")
                _missingPermissionsState.value = denied
                _showRationaleState.value = true
                onPermissionDenied(denied)
            }
        }
    }

    /**
     * Central execution flow to be triggered BEFORE enabling the voice-activated engine.
     * If all required permissions are already granted, immediately executes [onReadyToEnable].
     * Otherwise, initiates the central permission request flow.
     */
    fun executeBeforeEnablingVoiceEngine(onReadyToEnable: () -> Unit = onAllGranted) {
        val missing = VoicePermissions.getMissingPermissions(activity)
        if (missing.isEmpty()) {
            Log.i("VoicePermCoordinator", "All permissions present. Voice engine can be enabled immediately.")
            _showRationaleState.value = false
            onReadyToEnable()
        } else {
            Log.i("VoicePermCoordinator", "Permissions missing before enabling voice engine: $missing. Triggering request flow.")
            _missingPermissionsState.value = missing
            permissionLauncher?.launch(missing.toTypedArray())
        }
    }

    fun dismissRationale() {
        _showRationaleState.value = false
    }
}

/**
 * Composable state holder for the central voice permission request flow.
 */
@Composable
fun rememberCentralVoicePermissionFlow(
    onEngineReady: () -> Unit
): CentralVoicePermissionFlowState {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var missingPermissions by remember { mutableStateOf(VoicePermissions.getMissingPermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys.toList()
        missingPermissions = VoicePermissions.getMissingPermissions(context)
        if (denied.isEmpty() && VoicePermissions.hasMicrophonePermission(context)) {
            showDialog = false
            onEngineReady()
        } else {
            showDialog = true
        }
    }

    return remember(launcher) {
        CentralVoicePermissionFlowState(
            checkAndRequest = {
                val missing = VoicePermissions.getMissingPermissions(context)
                missingPermissions = missing
                if (missing.isEmpty()) {
                    onEngineReady()
                } else {
                    launcher.launch(missing.toTypedArray())
                }
            },
            showDialog = showDialog,
            missingPermissions = missingPermissions,
            dismissDialog = { showDialog = false },
            openSettings = {
                showDialog = false
                VoicePermissions.openAppSettings(context)
            }
        )
    }
}

class CentralVoicePermissionFlowState(
    val checkAndRequest: () -> Unit,
    val showDialog: Boolean,
    val missingPermissions: List<String>,
    val dismissDialog: () -> Unit,
    val openSettings: () -> Unit
)

/**
 * Central Voice Permissions Rationale Dialog (Material 3).
 * Explains clearly in Myanmar why Contacts, Media, Query Packages, and Mic are needed.
 */
@Composable
fun CentralVoicePermissionsDialog(
    missingPermissions: List<String>,
    onGrantClicked: () -> Unit,
    onOpenSettingsClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val statuses = remember(missingPermissions) {
        VoicePermissions.getPermissionGroupStatuses(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ကျော်ကြီး အသံစနစ် ခွင့်ပြုချက်များ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ကျော်ကြီး Voice Assistant အပြည့်အဝ အလုပ်လုပ်နိုင်ရန် အောက်ပါ ခွင့်ပြုချက်များ လိုအပ်ပါသည်ခင်ဗျာ-",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                statuses.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.isGranted)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = item.group.icon,
                                contentDescription = null,
                                tint = if (item.isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.group.titleMM,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = item.group.descriptionMM,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            if (item.isGranted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Granted",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = "လိုအပ်",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGrantClicked,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("ခွင့်ပြုချက်များ ပေးမည်", fontSize = 13.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onOpenSettingsClicked) {
                    Text("Settings သို့", fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("ပိတ်မည်", fontSize = 12.sp)
                }
            }
        }
    )
}
