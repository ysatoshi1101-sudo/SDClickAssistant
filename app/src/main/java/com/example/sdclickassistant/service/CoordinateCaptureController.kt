package com.example.sdclickassistant.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

enum class CaptureMode { SINGLE_POINT, SWIPE }

class CoordinateCaptureController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var captureView: View? = null

    fun show(mode: CaptureMode, onCaptured: (CapturedTouch) -> Unit) {
        if (captureView != null) return

        var startX = 0f
        var startY = 0f

        val instruction = when (mode) {
            CaptureMode.SINGLE_POINT -> "取得したい位置をタップしてください"
            CaptureMode.SWIPE -> "開始位置から終了位置までスワイプしてください"
        }

        val label = TextView(appContext).apply {
            text = instruction
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(24, 16, 24, 16)
        }

        val frame = FrameLayout(appContext).apply {
            setBackgroundColor(Color.parseColor("#332979FF"))
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = 120
                }
            )
        }

        frame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val result = when (mode) {
                        CaptureMode.SINGLE_POINT -> CapturedTouch(startX.toInt(), startY.toInt())
                        CaptureMode.SWIPE -> CapturedTouch(
                            startX.toInt(), startY.toInt(),
                            event.rawX.toInt(), event.rawY.toInt()
                        )
                    }
                    hide()
                    onCaptured(result)
                    true
                }
                else -> false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(frame, params)
        captureView = frame
    }

    fun hide() {
        captureView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
            captureView = null
        }
    }
}