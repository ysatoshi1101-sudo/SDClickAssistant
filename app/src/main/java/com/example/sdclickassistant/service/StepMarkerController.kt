package com.example.sdclickassistant.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.MacroStep
import kotlin.math.abs
import android.graphics.Canvas
import android.graphics.Paint

/** どのステップの、始点か終点かを表す識別子 */
data class MarkerTarget(val stepId: String, val isEndPoint: Boolean)

class StepMarkerController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val markerViews = mutableListOf<View>()
    private var lineView: View? = null
    private val markerSizePx = 64

    /**
     * 全ステップの位置に番号マーカーを表示する。
     * @param onPositionChanged ドラッグで位置が確定した時(指を離した時)に呼ばれる
     */
    fun show(steps: List<MacroStep>, onPositionChanged: (MarkerTarget, x: Int, y: Int) -> Unit) {
        hide()

        val swipeLines = steps.mapNotNull { step ->
            if (step.actionType != ActionType.SWIPE) return@mapNotNull null
            val x = step.x ?: return@mapNotNull null
            val y = step.y ?: return@mapNotNull null
            val endX = step.endX ?: return@mapNotNull null
            val endY = step.endY ?: return@mapNotNull null
            listOf(x.toFloat(), y.toFloat(), endX.toFloat(), endY.toFloat())
        }

        if (swipeLines.isNotEmpty()) {
            addLineView(swipeLines)
        }

        steps.sortedBy { it.order }.forEachIndexed { index, step ->
            val stepNumber = index + 1
            when (step.actionType) {
                ActionType.TAP, ActionType.LONG_PRESS -> {
                    val x = step.x ?: return@forEachIndexed
                    val y = step.y ?: return@forEachIndexed
                    addMarker(
                        x, y, "$stepNumber",
                        target = MarkerTarget(step.id, isEndPoint = false),
                        color = Color.parseColor("#CC2979FF"),
                        onPositionChanged = onPositionChanged
                    )
                }
                ActionType.SWIPE -> {
                    val x = step.x ?: return@forEachIndexed
                    val y = step.y ?: return@forEachIndexed
                    val endX = step.endX ?: return@forEachIndexed
                    val endY = step.endY ?: return@forEachIndexed
                    addMarker(
                        x, y, "$stepNumber",
                        target = MarkerTarget(step.id, isEndPoint = false),
                        color = Color.parseColor("#CC2979FF"),
                        onPositionChanged = onPositionChanged
                    )
                    addMarker(
                        endX, endY, "$stepNumber→",
                        target = MarkerTarget(step.id, isEndPoint = true),
                        color = Color.parseColor("#CCFF6D00"),
                        onPositionChanged = onPositionChanged
                    )
                }
                else -> {}
            }
        }
    }

    private fun addLineView(lines: List<List<Float>>) {
        val view = object : View(appContext) {
            private val paint = Paint().apply {
                color = Color.parseColor("#66FFFFFF")
                strokeWidth = 4f
                isAntiAlias = true
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                lines.forEach { line ->
                    canvas.drawLine(line[0], line[1], line[2], line[3], paint)
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, params)
            lineView = view
        } catch (e: Exception) {
            // 追加できなかった場合は無視
        }
    }

    private fun addMarker(
        x: Int,
        y: Int,
        label: String,
        target: MarkerTarget,
        color: Int,
        onPositionChanged: (MarkerTarget, Int, Int) -> Unit
    ) {
        val marker = TextView(appContext).apply {
            text = label
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
                setStroke(3, Color.WHITE)
            }
        }

        val params = WindowManager.LayoutParams(
            markerSizePx,
            markerSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x - markerSizePx / 2
            this.y = y - markerSizePx / 2
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val tapMoveThreshold = 10f

        marker.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > tapMoveThreshold || abs(dy) > tapMoveThreshold) {
                        isDragging = true
                    }
                    params.x = (initialX + dx).toInt()
                    params.y = (initialY + dy).toInt()
                    windowManager.updateViewLayout(marker, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // マーカーの中心座標をタップ座標として算出
                        val centerX = params.x + markerSizePx / 2
                        val centerY = params.y + markerSizePx / 2
                        onPositionChanged(target, centerX, centerY)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(marker, params)
            markerViews.add(marker)
        } catch (e: Exception) {
            // 追加できなかった場合は無視
        }
    }

    fun hide() {
        markerViews.forEach {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
        }
        markerViews.clear()

        lineView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
            lineView = null
        }
    }
}