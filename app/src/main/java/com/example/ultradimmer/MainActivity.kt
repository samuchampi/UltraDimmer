package com.example.ultradimmer

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var isDimming = false
    private var currentAlpha = 120

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        val title = TextView(this).apply {
            text = "Ultra Dimmer"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }

        val label = TextView(this).apply {
            text = "Oscurecimiento extra: 55%"
            textSize = 18f
            setPadding(0, 0, 0, 32)
        }

        val seekBar = SeekBar(this).apply {
            max = 220
            progress = currentAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentAlpha = progress
                    val percent = (progress * 100) / 220
                    label.text = "Oscurecimiento extra: $percent%"
                    if (isDimming) updateOverlay()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val toggleButton = Button(this).apply {
            text = "Activar filtro oscuro"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    requestOverlayPermission()
                    return@setOnClickListener
                }
                isDimming = !isDimming
                if (isDimming) {
                    updateOverlay()
                    text = "Desactivar filtro oscuro"
                } else {
                    stopService(Intent(this@MainActivity, DimmerService::class.java))
                    text = "Activar filtro oscuro"
                }
            }
        }

        layout.addView(title)
        layout.addView(label)
        layout.addView(seekBar)
        layout.addView(toggleButton)
        setContentView(layout)
    }

    private fun updateOverlay() {
        val intent = Intent(this, DimmerService::class.java).apply {
            putExtra("ALPHA", currentAlpha)
        }
        startForegroundService(intent)
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "Activa 'Mostrar sobre otras aplicaciones'", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}

class DimmerService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = View(this).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(overlayView, params)
    }

    private fun startForegroundNotification() {
        val channelId = "dimmer_channel"
        val channel = NotificationChannel(
            channelId,
            "Filtro de brillo",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Ultra Dimmer activo")
            .setContentText("Toca para ajustar o apagar el filtro")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alpha = intent?.getIntExtra("ALPHA", 120)?.coerceIn(0, 220) ?: 120
        overlayView?.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }
}
