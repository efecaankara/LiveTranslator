package com.example.ceviri

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQUEST_MEDIA_PROJECTION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val prefs = getSharedPreferences("CeviriAyarlar", Context.MODE_PRIVATE)
        val savedDelay = prefs.getInt("gizlenme_suresi", 3)
        val savedOpacity = prefs.getInt("cubuk_opaklik", 50) // Varsayılan %50 saydam

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }


        val infoText = TextView(this).apply {
            text = "Çubuğa Dönüşme Süresi (Saniye):"
            textSize = 14f
            setPadding(0, 0, 0, 10)
        }
        val delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(savedDelay.toString())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 30)
            }
        }


        val opacityText = TextView(this).apply {
            text = "Çubuk Opaklığı: %$savedOpacity"
            textSize = 14f
            setPadding(0, 0, 0, 10)
        }
        val opacitySeekBar = SeekBar(this).apply {
            max = 100
            progress = savedOpacity
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 40) }
        }


        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

                val actualProgress = if (progress < 10) 10 else progress
                opacityText.text = "Çubuk Opaklığı: %$actualProgress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        val startButton = Button(this).apply {
            text = "Çeviri Balonunu Başlat"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 20) }
        }

        val stopButton = Button(this).apply {
            text = "Uygulamayı Tamamen Kapat"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(infoText)
        layout.addView(delayInput)
        layout.addView(opacityText)
        layout.addView(opacitySeekBar)
        layout.addView(startButton)
        layout.addView(stopButton)
        setContentView(layout)

        startButton.setOnClickListener {

            val userInput = delayInput.text.toString().toIntOrNull() ?: 3
            val userOpacity = if (opacitySeekBar.progress < 10) 10 else opacitySeekBar.progress

            prefs.edit().apply {
                putInt("gizlenme_suresi", userInput)
                putInt("cubuk_opaklik", userOpacity)
                apply()
            }

            if (Settings.canDrawOverlays(this)) {
                requestScreenCapturePermission()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, 100)
            }
        }

        stopButton.setOnClickListener {
            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            stopService(serviceIntent)
            Toast.makeText(this, "Çeviri servisi kapatıldı.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            FloatingWidgetService.screenCaptureIntentData = data
            val serviceIntent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
        }
    }
}