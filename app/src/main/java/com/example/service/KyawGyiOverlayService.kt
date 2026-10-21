package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.example.engine.EngineState
import com.example.utils.KyawGyiServiceController
import com.example.utils.OverlayPermissionHelper

class KyawGyiOverlayService : Service() {

    private val TAG = "KYAW_GYI_OVERLAY"
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ImageView
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Service စာသားများမှ Broadcast အချက်အလက် ဖမ်းယူရန် Receiver
    private val engineStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == KyawGyiForegroundService.BROADCAST_ENGINE_EVENT) {
                val stateName = intent.getStringExtra(KyawGyiForegroundService.EXTRA_STATE)
                val message = intent.getStringExtra(KyawGyiForegroundService.EXTRA_MESSAGE)
                val speechResult = intent.getStringExtra(KyawGyiForegroundService.EXTRA_SPEECH_RESULT)

                stateName?.let { updateOverlayUI(it) }
                speechResult?.let {
                    Log.i(TAG, "Overlay received Speech Result: $it")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Stopping overlay service.")
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingWidget()
        registerEngineReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing on startCommand. Stopping service.")
            cleanupOverlayView()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun cleanupOverlayView() {
        if (isViewAttached && ::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
                isViewAttached = false
                Log.i(TAG, "Floating Mic Overlay removed cleanly.")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view.", e)
            }
        }
    }

    private var isViewAttached = false

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWidget() {
        // 1. Overlay Layout Parameters သတ်မှတ်ခြင်း
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // 2. Simple Dynamic ImageView ဖန်တီးခြင်း
        overlayView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black background
            setPadding(30, 30, 30, 30)
        }

        // 3. Touch & Drag Event Logic
        overlayView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        if (isViewAttached) {
                            try {
                                windowManager.updateViewLayout(overlayView, layoutParams)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update view layout", e)
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            // Mic Button ကို နှိပ်လိုက်ပါက Voice Recognition စတင်ပါမည်
                            Log.i(TAG, "Floating Mic Clicked -> Starting Listening")
                            KyawGyiServiceController.startListening(this@KyawGyiOverlayService)
                        }
                        return true
                    }
                }
                return false
            }
        })

        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Skipping windowManager.addView.")
            isViewAttached = false
            return
        }

        try {
            windowManager.addView(overlayView, layoutParams)
            isViewAttached = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view (Permission missing or BadTokenException)", e)
            isViewAttached = false
        }
    }

    // 4. Core Engine ၏ အခြေအနေအလိုက် Floating Widget ပုံရိပ်ပြောင်းလဲခြင်း
    private fun updateOverlayUI(stateName: String) {
        when (stateName) {
            EngineState.LISTENING.name -> {
                overlayView.setBackgroundColor(Color.parseColor("#D32F2F")) // Red when Listening
            }
            EngineState.PROCESSING.name -> {
                overlayView.setBackgroundColor(Color.parseColor("#FBC02D")) // Yellow when Processing
            }
            EngineState.SPEAKING.name -> {
                overlayView.setBackgroundColor(Color.parseColor("#388E3C")) // Green when Speaking
            }
            else -> {
                overlayView.setBackgroundColor(Color.parseColor("#80000000")) // Default semi-transparent
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerEngineReceiver() {
        val filter = IntentFilter(KyawGyiForegroundService.BROADCAST_ENGINE_EVENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(engineStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(engineStateReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(engineStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        cleanupOverlayView()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
