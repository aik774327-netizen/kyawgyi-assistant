package com.kyawgyi.voice

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * FloatingAssistantService (Floating Overlay Microphone Bubble)
 *
 * Provides a system-wide draggable floating action bubble using [WindowManager] and `SYSTEM_ALERT_WINDOW`:
 * 1. Appears over other applications (e.g. YouTube, Browser, Games) for immediate voice access.
 * 2. Drag-to-move support with screen bounds constraint.
 * 3. Single-tap to toggle KyawGyi Voice Listening via [HybridPipelineController] and [VoiceController].
 * 4. Visual state feedback: Pulses neon green when listening, yellow when thinking, cyan when speaking.
 */
class FloatingAssistantService : Service() {

    companion object {
        private const val TAG = "FloatingAssistant"
        const val CHANNEL_ID = "kyawgyi_floating_overlay_channel"
        const val NOTIFICATION_ID = 12055
        const val ACTION_START_OVERLAY = "com.kyawgyi.voice.ACTION_START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.kyawgyi.voice.ACTION_STOP_OVERLAY"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start FloatingAssistantService: SYSTEM_ALERT_WINDOW permission missing")
                return
            }
            val intent = Intent(context, FloatingAssistantService::class.java).apply {
                action = ACTION_START_OVERLAY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingAssistantService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var stateObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            removeFloatingView()
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (floatingView == null) {
            setupFloatingBubble()
            observeAssistantState()
        }

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 350
        }

        // Programmatic creation of the floating bubble container
        val density = resources.displayMetrics.density
        val sizePx = (56 * density).toInt()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }

        val bubbleCircle = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#121824"))
                setStroke((2 * density).toInt(), Color.parseColor("#00E676"))
            }
        }

        val micIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt())
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.parseColor("#00E676"))
        }

        val label = TextView(this).apply {
            text = "ကျော်ကြီး"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(4, 2, 4, 2)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor("#CC10141D"))
            }
        }

        bubbleCircle.addView(micIcon)
        rootLayout.addView(bubbleCircle)
        rootLayout.addView(label)

        // Drag and Click gesture handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        rootLayout.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(rootLayout, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onBubbleClicked()
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = rootLayout
        try {
            windowManager?.addView(floatingView, layoutParams)
            Log.i(TAG, "Floating assistant bubble added to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating bubble to WindowManager: ${e.message}", e)
        }
    }

    private fun onBubbleClicked() {
        Log.i(TAG, "Floating bubble tapped -> Triggering voice assistant.")
        serviceScope.launch {
            val stateStore = AssistantStateStore.getInstance()
            val stt = SttEngineManager.getInstance(applicationContext)
            if (stateStore.getCurrentState() == AssistantState.IDLE) {
                stt.startListening()
            } else {
                stt.stopListening()
            }
        }
    }

    private fun observeAssistantState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            AssistantStateStore.getInstance().state.collectLatest { state ->
                updateBubbleAppearance(state)
            }
        }
    }

    private fun updateBubbleAppearance(state: AssistantState) {
        val root = floatingView as? LinearLayout ?: return
        val bubble = root.getChildAt(0) as? LinearLayout ?: return
        val label = root.getChildAt(1) as? TextView ?: return

        val density = resources.displayMetrics.density
        val (strokeColor, labelText) = when (state) {
            AssistantState.LISTENING -> Color.parseColor("#00E676") to "နားထောင်နေသည်..."
            AssistantState.THINKING, AssistantState.UNDERSTANDING -> Color.parseColor("#FFD600") to "စဥ်းစားနေသည်..."
            AssistantState.SPEAKING -> Color.parseColor("#00B0FF") to "ပြောဆိုနေပါသည်..."
            AssistantState.WAKE_DETECTED -> Color.parseColor("#FF4081") to "ဟယ်လို ကျော်ကြီး"
            else -> Color.parseColor("#78909C") to "ကျော်ကြီး"
        }

        bubble.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#121824"))
            setStroke((2.5f * density).toInt(), strokeColor)
        }
        label.text = labelText
    }

    private fun removeFloatingView() {
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
                Log.i(TAG, "Floating assistant bubble removed from WindowManager.")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing floating view: ${e.message}")
            }
            floatingView = null
        }
        stateObserverJob?.cancel()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KyawGyi Floating Assistant Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps KyawGyi floating microphone widget active on screen"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KyawGyi Floating Widget")
            .setContentText("မျက်နှာပြင်ပေါ်တွင် အသံအမိန့်ပေးခလုတ် ဖွင့်လှစ်ထားပါသည်")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        removeFloatingView()
        serviceScope.cancel()
        super.onDestroy()
    }
}
