package com.example.ceviri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager

    private lateinit var floatingView: View
    private lateinit var resultBoxView: View
    private lateinit var targetFrameView: View

    private lateinit var floatingParams: WindowManager.LayoutParams
    private lateinit var targetFrameParams: WindowManager.LayoutParams
    private lateinit var resultBoxParams: WindowManager.LayoutParams

    private lateinit var translatedTextTv: TextView
    private lateinit var englishTurkishTranslator: Translator

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isTranslationModelDownloaded = false

    private var isCollapsed = false
    private var delayTimeMs = 3000L
    private val collapseHandler = Handler(Looper.getMainLooper())
    private lateinit var bubbleView: View
    private lateinit var lineView: View

    companion object {
        var screenCaptureIntentData: Intent? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()


        val prefs = getSharedPreferences("CeviriAyarlar", Context.MODE_PRIVATE)
        delayTimeMs = prefs.getInt("gizlenme_suresi", 3) * 1000L
        val savedOpacity = prefs.getInt("cubuk_opaklik", 50)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
        englishTurkishTranslator = Translation.getClient(options)
        downloadTranslationModel()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)
        bubbleView = floatingView.findViewById(R.id.bubble_view)
        lineView = floatingView.findViewById(R.id.line_view)


        lineView.alpha = savedOpacity / 100f

        setupFloatingView()

        targetFrameView = LayoutInflater.from(this).inflate(R.layout.layout_target_frame, null)
        setupTargetFrameView()

        resultBoxView = LayoutInflater.from(this).inflate(R.layout.layout_result_box, null)
        translatedTextTv = resultBoxView.findViewById(R.id.translated_text_tv)
        setupResultBoxView()

        startCollapseTimer()
    }

    private fun setupFloatingView() {
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        floatingParams.gravity = Gravity.TOP or Gravity.START
        floatingParams.x = 0
        floatingParams.y = 100
        windowManager.addView(floatingView, floatingParams)

        floatingView.findViewById<View>(R.id.floating_root_container).setOnTouchListener(createTouchListener(floatingParams, 0))
    }

    private fun setupTargetFrameView() {
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        targetFrameParams = WindowManager.LayoutParams(
            500, 250, layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        targetFrameParams.gravity = Gravity.TOP or Gravity.START
        targetFrameParams.x = 200
        targetFrameParams.y = 500
        windowManager.addView(targetFrameView, targetFrameParams)

        targetFrameView.findViewById<View>(R.id.target_frame_parent).setOnTouchListener(createTouchListener(targetFrameParams, 2))

        val resizeHandle = targetFrameView.findViewById<View>(R.id.resize_handle)
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0
            private var initialHeight = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = targetFrameParams.width
                        initialHeight = targetFrameParams.height
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diffX = (event.rawX - initialTouchX).toInt()
                        val diffY = (event.rawY - initialTouchY).toInt()
                        targetFrameParams.width = initialWidth + diffX
                        targetFrameParams.height = initialHeight + diffY
                        if (targetFrameParams.width < 150) targetFrameParams.width = 150
                        if (targetFrameParams.height < 150) targetFrameParams.height = 150
                        windowManager.updateViewLayout(targetFrameView, targetFrameParams)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupResultBoxView() {
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        resultBoxParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        resultBoxParams.gravity = Gravity.CENTER
        windowManager.addView(resultBoxView, resultBoxParams)
        resultBoxView.visibility = View.GONE
        resultBoxView.findViewById<View>(R.id.result_box_parent).setOnTouchListener(createTouchListener(resultBoxParams, 1))
    }

    private val collapseRunnable = Runnable {
        if (!isCollapsed) {
            isCollapsed = true
            bubbleView.visibility = View.GONE
            lineView.visibility = View.VISIBLE

            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val isLeft = floatingParams.x < screenWidth / 2

            floatingParams.x = if (isLeft) 0 else screenWidth
            windowManager.updateViewLayout(floatingView, floatingParams)
        }
    }

    private fun startCollapseTimer() {
        collapseHandler.removeCallbacks(collapseRunnable)
        collapseHandler.postDelayed(collapseRunnable, delayTimeMs)
    }

    private fun createTouchListener(params: WindowManager.LayoutParams, viewType: Int): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            private val longPressHandler = Handler(Looper.getMainLooper())
            private var isLongPressed = false
            private val longPressRunnable = Runnable {
                if (viewType == 0 && !isCollapsed) {
                    isLongPressed = true
                    targetFrameView.visibility = if (targetFrameView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    Toast.makeText(this@FloatingWidgetService, "Yeşil hedef alanı gizlendi/açıldı", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isLongPressed = false

                        if (viewType == 0) {
                            if (isCollapsed) {
                                isCollapsed = false
                                lineView.visibility = View.GONE
                                bubbleView.visibility = View.VISIBLE

                                val metrics = resources.displayMetrics
                                val isLeft = params.x < metrics.widthPixels / 2
                                params.x = if (isLeft) 50 else metrics.widthPixels - 150
                                windowManager.updateViewLayout(floatingView, params)

                                startCollapseTimer()
                                return true
                            }

                            collapseHandler.removeCallbacks(collapseRunnable)
                            longPressHandler.postDelayed(longPressRunnable, 800)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (viewType == 0) {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            if (!isLongPressed) {
                                val diffX = abs(event.rawX - initialTouchX)
                                val diffY = abs(event.rawY - initialTouchY)
                                if (diffX < 10 && diffY < 10) {
                                    if (targetFrameView.visibility == View.GONE) {
                                        Toast.makeText(this@FloatingWidgetService, "Lütfen önce balona basılı tutarak yeşil alanı açın!", Toast.LENGTH_LONG).show()
                                    } else {
                                        resultBoxView.visibility = View.VISIBLE
                                        translatedTextTv.text = "Okunuyor..."
                                        captureScreenAndRecognizeText()
                                    }
                                }
                            }
                            if (!isCollapsed) startCollapseTimer()
                        } else if (viewType == 1) {
                            val diffX = abs(event.rawX - initialTouchX)
                            val diffY = abs(event.rawY - initialTouchY)
                            if (diffX < 10 && diffY < 10) {
                                resultBoxView.visibility = View.GONE
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diffX_movement = abs(event.rawX - initialTouchX)
                        val diffY_movement = abs(event.rawY - initialTouchY)

                        if (diffX_movement > 10 || diffY_movement > 10) {
                            if (viewType == 0) longPressHandler.removeCallbacks(longPressRunnable)
                        }

                        val canMove = when(viewType) {
                            0 -> !isLongPressed && !isCollapsed
                            1 -> true
                            2 -> true
                            else -> true
                        }

                        if (canMove) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()

                            val viewToUpdate = when(viewType) {
                                0 -> floatingView
                                1 -> resultBoxView
                                else -> targetFrameView
                            }
                            windowManager.updateViewLayout(viewToUpdate, params)
                        }
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun downloadTranslationModel() {
        val conditions = DownloadConditions.Builder().requireWifi().build()
        englishTurkishTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener { isTranslationModelDownloaded = true }
            .addOnFailureListener {
                Toast.makeText(this, "Çeviri modeli indirilemedi! Wifi'yi kontrol edin.", Toast.LENGTH_LONG).show()
            }
    }

    private fun captureScreenAndRecognizeText() {
        val intentData = screenCaptureIntentData
        if (intentData == null) return

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(-1, intentData)

        val width: Int; val height: Int; val density: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width(); height = bounds.height()
            density = resources.configuration.densityDpi
        } else {
            val metrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            width = metrics.widthPixels; height = metrics.heightPixels
            density = metrics.densityDpi
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Capture", width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, Handler(Looper.getMainLooper())
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmapWidth = width + rowPadding / pixelStride

                val fullBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                fullBitmap.copyPixelsFromBuffer(buffer)
                image.close()
                stopScreenCapture()

                val loc = IntArray(2)
                targetFrameView.getLocationOnScreen(loc)
                var startX = loc[0]; var startY = loc[1]
                var cropW = targetFrameView.width; var cropH = targetFrameView.height

                if (startX < 0) startX = 0
                if (startY < 0) startY = 0
                if (startX + cropW > bitmapWidth) cropW = bitmapWidth - startX
                if (startY + cropH > height) cropH = height - startY

                val croppedBitmap = Bitmap.createBitmap(fullBitmap, startX, startY, cropW, cropH)
                recognizeTextAndTranslate(croppedBitmap)
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun recognizeTextAndTranslate(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { visionText ->
                if (visionText.text.isNotBlank()) translateTextToTurkish(visionText.text)
                else translatedTextTv.text = "Yazı bulunamadı."
            }
    }

    private fun translateTextToTurkish(text: String) {
        if (!isTranslationModelDownloaded) { translatedTextTv.text = "Çeviri modeli iniyor..."; return }
        englishTurkishTranslator.translate(text).addOnSuccessListener { translatedTextTv.text = it }
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        mediaProjection?.stop(); mediaProjection = null
    }

    private fun createNotificationChannel() {
        val channelId = "floating_widget_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Çeviri Servisi", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
            .setContentTitle("Among Us Çevirici")
            .setContentText("Baloncuk aktif.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        collapseHandler.removeCallbacks(collapseRunnable)
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        if (::resultBoxView.isInitialized) windowManager.removeView(resultBoxView)
        if (::targetFrameView.isInitialized) windowManager.removeView(targetFrameView)
    }
}