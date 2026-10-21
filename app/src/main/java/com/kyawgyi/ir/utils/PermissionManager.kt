package com.kyawgyi.ir.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kyawgyi.ir.service.VoiceForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PermissionManager in com.kyawgyi.ir.utils
 *
 * Manages runtime permissions for Microphone (RECORD_AUDIO) using ActivityResultContracts.
 * Includes logic to check for denied status and show a user-friendly rationale dialog
 * before voice engine initialization.
 */
class PermissionManager(
    private val onAllPermissionsGranted: () -> Unit = {},
    private val onPermissionsDenied: (List<String>) -> Unit = {}
) {

    companion object {
        private const val TAG = "PermissionManager"

        // Core required permissions
        const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
        const val PERMISSION_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
        const val PERMISSION_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
        const val PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS
        const val PERMISSION_CALL_PHONE = Manifest.permission.CALL_PHONE
        val PERMISSION_READ_AUDIO = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val PERMISSION_POST_NOTIFICATIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            ""
        }

        val REQUIRED_VOICE_PERMISSIONS: Array<String> = buildList {
            add(PERMISSION_RECORD_AUDIO)
            add(PERMISSION_FINE_LOCATION)
            add(PERMISSION_COARSE_LOCATION)
            add(PERMISSION_READ_CONTACTS)
            add(PERMISSION_CALL_PHONE)
            add(PERMISSION_READ_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        /**
         * Checks if contacts permission (READ_CONTACTS) is granted.
         */
        fun hasContactsPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Checks if call phone permission (CALL_PHONE) is granted.
         */
        fun hasCallPhonePermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Checks if media audio permission (READ_MEDIA_AUDIO or READ_EXTERNAL_STORAGE) is granted.
         */
        fun hasAudioMediaPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                PERMISSION_READ_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Checks if microphone permission (RECORD_AUDIO) is granted.
         */
        fun hasMicrophonePermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                PERMISSION_RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Checks if microphone permission is currently denied.
         */
        fun isMicrophoneDenied(context: Context): Boolean {
            return !hasMicrophonePermission(context)
        }

        /**
         * Checks if UI should display an educational rationale before asking for microphone permission.
         */
        fun shouldShowMicrophoneRationale(activity: ComponentActivity): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                PERMISSION_RECORD_AUDIO
            )
        }

        /**
         * Checks if location permission (either FINE or COARSE) is granted.
         */
        fun hasLocationPermission(context: Context): Boolean {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return hasFine || hasCoarse
        }

        /**
         * Checks if notification permission is granted (Android 13+).
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
         * Returns true if all voice service permissions are granted.
         */
        fun hasVoiceServicePermissions(context: Context): Boolean {
            return hasMicrophonePermission(context) && hasLocationPermission(context) && hasNotificationPermission(context)
        }

        /**
         * Returns list of missing permissions.
         */
        fun getMissingPermissions(context: Context): List<String> {
            return REQUIRED_VOICE_PERMISSIONS.filter { permission ->
                permission.isNotEmpty() && ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
        }

        /**
         * Checks permissions and activates VoiceForegroundService if permitted.
         */
        fun activateVoiceServiceIfPermitted(context: Context): Boolean {
            if (hasVoiceServicePermissions(context)) {
                Log.i(TAG, "All voice permissions verified. Starting VoiceForegroundService.")
                VoiceForegroundService.startService(context)
                return true
            } else {
                val missing = getMissingPermissions(context)
                Log.w(TAG, "Cannot activate voice service. Missing permissions: $missing")
                return false
            }
        }
    }

    private var multiplePermissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var microphonePermissionLauncher: ActivityResultLauncher<String>? = null

    private val _missingPermissionsFlow = MutableStateFlow<List<String>>(emptyList())
    val missingPermissionsFlow: StateFlow<List<String>> = _missingPermissionsFlow.asStateFlow()

    private val _isMicrophoneGrantedFlow = MutableStateFlow(false)
    val isMicrophoneGrantedFlow: StateFlow<Boolean> = _isMicrophoneGrantedFlow.asStateFlow()

    private val _showRationaleDialogFlow = MutableStateFlow(false)
    val showRationaleDialogFlow: StateFlow<Boolean> = _showRationaleDialogFlow.asStateFlow()

    /**
     * Registers ActivityResultLauncher for Microphone single permission.
     */
    fun registerMicrophoneLauncher(
        activity: ComponentActivity,
        onGranted: () -> Unit = {},
        onDenied: () -> Unit = {}
    ) {
        microphonePermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            _isMicrophoneGrantedFlow.value = isGranted
            if (isGranted) {
                Log.i(TAG, "Microphone permission granted by user via RequestPermission contract.")
                onGranted()
                onAllPermissionsGranted()
            } else {
                Log.w(TAG, "Microphone permission denied by user.")
                onDenied()
                onPermissionsDenied(listOf(PERMISSION_RECORD_AUDIO))
            }
        }
    }

    /**
     * Registers ActivityResultLauncher for multiple permissions.
     */
    fun registerLauncher(activity: ComponentActivity) {
        multiplePermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            handlePermissionResults(activity, permissionsMap)
        }
    }

    /**
     * Request microphone runtime permission using ActivityResultContracts.RequestPermission.
     * Evaluates denied status and rationale necessity before triggering the launcher.
     */
    fun requestMicrophoneWithRationale(
        activity: ComponentActivity,
        onReadyToInitializeEngine: () -> Unit
    ) {
        if (hasMicrophonePermission(activity)) {
            _isMicrophoneGrantedFlow.value = true
            onReadyToInitializeEngine()
            return
        }

        if (shouldShowMicrophoneRationale(activity)) {
            Log.i(TAG, "Showing user-friendly rationale dialog before voice engine initialization.")
            _showRationaleDialogFlow.value = true
        } else {
            Log.i(TAG, "Directly launching microphone permission contract.")
            microphonePermissionLauncher?.launch(PERMISSION_RECORD_AUDIO)
        }
    }

    fun dismissRationaleDialog() {
        _showRationaleDialogFlow.value = false
    }

    fun confirmRationaleAndRequest() {
        _showRationaleDialogFlow.value = false
        microphonePermissionLauncher?.launch(PERMISSION_RECORD_AUDIO)
    }

    /**
     * Checks permissions and requests any missing permissions via the multiple permissions launcher.
     */
    fun checkAndRequestPermissions(activity: ComponentActivity): Boolean {
        val missing = getMissingPermissions(activity)
        _missingPermissionsFlow.value = missing

        if (missing.isEmpty()) {
            Log.i(TAG, "All permissions already granted. Activating voice service...")
            onAllPermissionsGranted()
            VoiceForegroundService.startService(activity)
            return true
        }

        Log.i(TAG, "Requesting missing permissions: $missing")
        multiplePermissionsLauncher?.launch(missing.toTypedArray())
        return false
    }

    private fun handlePermissionResults(
        context: Context,
        results: Map<String, Boolean>
    ) {
        val denied = results.filterValues { !it }.keys.toList()
        _missingPermissionsFlow.value = denied

        if (denied.isEmpty()) {
            Log.i(TAG, "All requested permissions granted by user.")
            _isMicrophoneGrantedFlow.value = true
            onAllPermissionsGranted()
            VoiceForegroundService.startService(context)
        } else {
            Log.w(TAG, "User denied permissions: $denied")
            onPermissionsDenied(denied)
        }
    }
}

/**
 * State container for Composable-based Microphone Permission flow.
 */
@Composable
fun rememberMicrophonePermissionState(
    onPermissionGranted: () -> Unit = {},
    onPermissionDenied: () -> Unit = {}
): MicrophonePermissionState {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var isPermissionDenied by remember { mutableStateOf(!PermissionManager.hasMicrophonePermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionDenied = !granted
        if (granted) {
            showRationale = false
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }

    return remember(launcher, showRationale, isPermissionDenied) {
        MicrophonePermissionState(
            context = context,
            launcher = launcher,
            showRationale = showRationale,
            isDenied = isPermissionDenied,
            onShowRationaleChange = { showRationale = it },
            onPermissionGranted = onPermissionGranted
        )
    }
}

class MicrophonePermissionState(
    private val context: Context,
    val launcher: ManagedActivityResultLauncher<String, Boolean>,
    val showRationale: Boolean,
    val isDenied: Boolean,
    val onShowRationaleChange: (Boolean) -> Unit,
    val onPermissionGranted: () -> Unit
) {
    fun requestMicrophonePermission(activity: ComponentActivity? = null) {
        if (PermissionManager.hasMicrophonePermission(context)) {
            onPermissionGranted()
            return
        }

        val shouldShow = activity?.let { PermissionManager.shouldShowMicrophoneRationale(it) } ?: true
        if (shouldShow) {
            onShowRationaleChange(true)
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun confirmRationale() {
        onShowRationaleChange(false)
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun dismissRationale() {
        onShowRationaleChange(false)
    }
}

/**
 * User-Friendly Rationale Dialog for Microphone Permission
 *
 * Clearly explains why Kyaw Gyi voice assistant needs microphone access,
 * highlighting privacy safeguards and enabling the user to grant permission
 * before the voice engine initializes.
 */
@Composable
fun MicrophoneRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Microphone Permission",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "မိုက်ခရိုဖုန်း အသုံးပြုခွင့် လိုအပ်ပါသည်",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Microphone Access Required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "ကျော်ကြီး အသံလက်ထောက်ဖြင့် အသံအမိန့်များ (Voice Commands) ပေးပို့နိုင်ရန်နှင့် နိုးကြားစကား 'ကျော်ကြီး' ကို သိရှိနားဆင်နိုင်ရန် မိုက်ခရိုဖုန်း အသုံးပြုခွင့် ပေးအပ်ရန် လိုအပ်ပါသည်။ သင်၏ အသံဒေတာများကို စက်ထဲတွင်သာ လုံခြုံစွာ စီမံထားရှိပါသည်။",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("မလုပ်တော့ပါ")
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ခွင့်ပြုမည်")
                    }
                }
            }
        }
    }
}

/**
 * Jetpack Compose helper to register and launch runtime permission requests
 * for Microphone and Location using ActivityResultContracts.
 */
@Composable
fun rememberVoicePermissionLauncher(
    onAllGranted: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {}
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys.toList()
        if (denied.isEmpty()) {
            onAllGranted()
        } else {
            onDenied(denied)
        }
    }
}
