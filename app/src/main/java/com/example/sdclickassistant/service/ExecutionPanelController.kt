package com.example.sdclickassistant.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class ExecutionPanelController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var panelView: View? = null
    private var loopTextView: TextView? = null
    private var stepTextView: TextView? = null

    // STOPアイコン(赤い正方形)の一辺のサイズ
    private val stopIconSizePx = 40

    fun isShowing(): Boolean = panelView != null

    fun show(x: Int, y: Int, onStop: () -> Unit) {
        if (panelView != null) return

        // 動画プレイヤーでよく見る「停止」アイコンを模した、赤い正方形のView。
        // 実際のタップ判定はパネル全体(container)で受けるため、
        // ここでは個別のクリックリスナーは付けず、目印としてのみ機能させる。
        val stopButton = View(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#F44336"))
            }
            layoutParams = LinearLayout.LayoutParams(
                stopIconSizePx,
                stopIconSizePx
            )
        }

        val infoColumn = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 0, 0, 0)
        }

        val loopView = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }

        val stepView = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }

        infoColumn.addView(loopView)
        infoColumn.addView(stepView)

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 8, 10, 8)
            setBackgroundColor(Color.parseColor("#CC000000"))
            // 赤いアイコン部分だけでなく、パネル全体をタップしても
            // 停止できるようにする(赤い正方形は押しやすさのための
            // 目印として残しつつ、実際のタップ判定エリアは広げる)。
            isClickable = true
            setOnClickListener { onStop() }
        }
        container.addView(stopButton)
        container.addView(infoColumn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        windowManager.addView(container, params)
        panelView = container
        loopTextView = loopView
        stepTextView = stepView
    }

    fun updateStatus(loopText: String, stepText: String) {
        loopTextView?.text = loopText
        stepTextView?.text = stepText
    }

    fun hide() {
        panelView?.let {
            windowManager.removeView(it)
            panelView = null
            loopTextView = null
            stepTextView = null
        }
    }
}