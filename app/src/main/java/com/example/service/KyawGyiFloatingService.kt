package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlin.math.abs

class KyawGyiFloatingService : Service() {

    companion object {
        private const val TAG = "KyawGyiFloatingService"
        const val CHANNEL_ID = "KyawGyiVoiceChannel"
        const val NOTIFICATION_ID = 888

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var lastDetectedCommand = "Listening..."
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var micImageView: ImageView
    private lateinit var statusTextView: TextView

    private var speechHandler: SpeechHandler? = null
    private var ttsEngine: com.example.utils.MyanmarTTSEngine? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        if (!com.example.utils.OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Stopping KyawGyiFloatingService.")
            stopSelf()
            return
        }
        isServiceRunning = true
        Log.i(TAG, "Kyaw Gyi Floating Service Created")

        createNotificationChannel()
        
        val notification = createNotification("ကျော်ကြီး မြန်မာအသံ AI အသင့်ရှိပါသည်")
        
        // Android 14 (API 34+) specifically requires FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service with microphone type: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting foreground service: ${e.message}", e)
        }

        ttsEngine = com.example.utils.MyanmarTTSEngine(this)
        setupFloatingView()
        initSpeechHandler()
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val rootLayout = FrameLayout(this)

        val outerGlowContainer = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#1B2A4A"),
                    Color.parseColor("#0A1118")
                )
                setStroke(5, Color.parseColor("#00E5FF"))
            }
            setPadding(18, 18, 18, 18)
            elevation = 12f
        }

        micImageView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.parseColor("#00E5FF"))
        }

        statusTextView = TextView(this).apply {
            text = "ကျော်ကြီး"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 10f
            gravity = Gravity.CENTER
            visibility = View.VISIBLE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(12, 2, 12, 2)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#D90B132B"))
                setStroke(2, Color.parseColor("#00E5FF"))
            }
        }

        val frameParams = FrameLayout.LayoutParams(110, 110, Gravity.CENTER)
        outerGlowContainer.addView(micImageView, frameParams)

        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            bottomMargin = 2
        }
        outerGlowContainer.addView(statusTextView, textParams)

        rootLayout.addView(outerGlowContainer, FrameLayout.LayoutParams(145, 145))
        floatingView = rootLayout

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = @Suppress("DEPRECATION") WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 250
        }

        outerGlowContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                            isDragging = true
                        }
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        try {
                            windowManager.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating floating view layout: ${e.message}")
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = event.rawX - initialTouchX
                        val diffY = event.rawY - initialTouchY
                        val absX = abs(diffX)
                        val absY = abs(diffY)

                        if (!isDragging && absX < 15 && absY < 15) {
                            // Tap gesture -> Toggle Speech Listening
                            if (speechHandler?.isListening == false) {
                                speechHandler?.startContinuousListening()
                                setMicVisualState(stateColor = "#00FF66", text = "နားထောင်နေသည်...")
                            } else {
                                setMicVisualState(stateColor = "#00E5FF", text = "အသင့်ရှိပါသည်")
                            }
                        } else if (absY > 80 && absY > absX) {
                            if (diffY < 0) {
                                // Swipe Up Gesture -> Launch Main App
                                setMicVisualState(stateColor = "#00FF66", text = "App ဖွင့်ပါမည်")
                                val appIntent = Intent(this@KyawGyiFloatingService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                startActivity(appIntent)
                            } else {
                                // Swipe Down Gesture -> Restart Voice Engine
                                setMicVisualState(stateColor = "#FFB300", text = "မိုက် ပြန်စနေသည်")
                                speechHandler?.startContinuousListening()
                            }
                        } else {
                            // Magnetic Snap-to-Edge Animation
                            val midX = screenWidth / 2
                            params.x = if (params.x < midX) 20 else (screenWidth - 165)
                            try {
                                windowManager.updateViewLayout(floatingView, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error snapping to edge: ${e.message}")
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        if (!com.example.utils.OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Cannot add floating view.")
            return
        }

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating view to WindowManager: ${e.message}")
        }
    }

    private fun initSpeechHandler() {
        speechHandler = SpeechHandler(
            context = this,
            onWakeWordDetected = {
                handler.post {
                    setMicVisualState(stateColor = "#00FF66", text = "ကျော်ကြီး!")
                    lastDetectedCommand = "Wake word detected!"
                }
            },
            onCommandReceived = { spokenText ->
                handler.post {
                    lastDetectedCommand = spokenText
                    setMicVisualState(stateColor = "#FFB300", text = spokenText)

                    val accessibilityInstance = KyawGyiAccessibilityService.instance
                    val result = if (accessibilityInstance != null) {
                        accessibilityInstance.executeVoiceCommand(spokenText)
                    } else {
                        Log.w(TAG, "Accessibility service is currently not active or bound.")
                        "Accessibility service not active"
                    }

                    Log.i(TAG, "Voice Command Result: $result")

                    // Voice TTS Feedback in Myanmar
                    if (spokenText.contains("ဖတ်") && (spokenText.contains("မျက်နှာပြင်") || spokenText.contains("screen") || spokenText.contains("စာ"))) {
                        KyawGyiVoiceSession.readScreenContentAloud(this@KyawGyiFloatingService) { spokenMsg ->
                            setMicVisualState(stateColor = "#00FF66", text = spokenMsg.take(30))
                        }
                    } else if (spokenText.contains("ပိတ်") || spokenText.contains("ထွက်")) {
                        ttsEngine?.speak("အက်ပ်ကို ပိတ်လိုက်ပါပြီခင်ဗျာ")
                    } else if (spokenText.contains("ဖွင့်")) {
                        ttsEngine?.speak("အက်ပ် ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ")
                    }

                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({
                        setMicVisualState(stateColor = "#00E5FF", text = "ကျော်ကြီး")
                    }, 3000)
                }
            },
            onPartialReceived = { partialText ->
                handler.post {
                    setMicVisualState(stateColor = "#00FF66", text = partialText)
                }
            }
        )

        // Avoid immediate dual-SpeechRecognizer collision if VoiceForegroundService is already active;
        // Floating Bubble starts listening on user tap or if background service is not running.
        if (!com.kyawgyi.ir.service.VoiceForegroundService.isServiceRunning.value) {
            speechHandler?.startContinuousListening()
        }
    }

    private fun setMicVisualState(stateColor: String, text: String) {
        statusTextView.text = text
        val color = Color.parseColor(stateColor)
        micImageView.setColorFilter(color)
        statusTextView.setTextColor(color)

        val bg = (floatingView as? FrameLayout)?.getChildAt(0)?.background as? GradientDrawable
        bg?.setStroke(5, color)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Kyaw Gyi Assistive Touch Mic Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kyaw Gyi Assistive Voice Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        handler.removeCallbacksAndMessages(null)
        speechHandler?.stopListening()
        speechHandler = null

        if (::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view on Destroy: ${e.message}")
            }
        }
        Log.i(TAG, "Kyaw Gyi Floating Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
