package com.example.sdclickassistant.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.MacroStep
import java.util.UUID
import kotlin.math.hypot

class SequenceRecordingController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // 全画面の記録用オーバーレイ(タッチを受け取って記録する)
    private var rootView: FrameLayout? = null

    // 再送中・待機中・一時停止中にタッチ透過へ切り替えるため、パラメータを保持する。
    private var rootParams: WindowManager.LayoutParams? = null

    // 状態表示とボタンをまとめた小さい操作パネル(別ウィンドウ)。
    // 記録用オーバーレイをタッチ透過にしても、パネルのボタンは押せるようにするため分けている。
    private var panelView: View? = null

    /** 記録機能の状態 */
    private enum class RecordingState {
        IDLE,       // 待機中(開始前)。記録しない
        RECORDING,  // 記録中
        PAUSED      // 一時停止中。記録しない
    }

    companion object {
        private const val MOVE_THRESHOLD_PX = 24f
        private const val LONG_PRESS_THRESHOLD_MS = 400L
        private const val INDICATOR_SIZE_PX = 60

        // 待機時間を自動計測できない場合(最後のステップ・一時停止の直前のステップ)の既定値
        private const val DEFAULT_WAIT_TIME_MS = 300L

        // 自動計測した待機時間の下限。操作間隔が極端に短くても、最低これだけは待つ。
        private const val MIN_WAIT_TIME_MS = 100L

        // タッチ透過(FLAG_NOT_TOUCHABLE)にする間の不透明度。
        // Android 12以降は、タッチを通すウィンドウの不透明度が0.8を超えると
        // 下のアプリへのタッチがブロックされるため、それ以下にしておく。
        private const val PASS_THROUGH_ALPHA = 0.7f

        // 開始/再開ボタン(緑)と一時停止ボタン(赤)の色
        private const val COLOR_START = "#4CAF50"
        private const val COLOR_PAUSE = "#F44336"
    }

    /**
     * 操作記録用のオーバーレイを表示する。最初は待機中(記録しない)で始まる。
     *
     * @param onReplay 1回分の操作(タップ/長押し/スワイプ)を記録した直後に呼ばれる。
     *        呼び出し側は、同じ操作を対象アプリへ実際に送信し、
     *        完了したら onDone を呼ぶこと(それまでオーバーレイはタッチ透過状態になる)。
     * @param onFinished 「記録終了」が押された時に、記録したステップ一覧と共に呼ばれる。
     */
    fun show(
        onReplay: (MacroStep, onDone: () -> Unit) -> Unit,
        onFinished: (List<MacroStep>) -> Unit
    ) {
        if (rootView != null) return

        val recorded = mutableListOf<MacroStep>()
        var state = RecordingState.IDLE
        var isReplaying = false

        // 直前に記録したステップの「指を離した時刻」(event.eventTime基準)。
        // 次のステップの指を置いた時刻との差を、直前のステップの待機時間にする。
        // 一時停止した時はnullに戻し、停止中の時間を待機時間に含めない。
        var lastStepUpTime: Long? = null

        // ------------------------------------------------------------
        // 操作パネル(状態表示 + 開始/一時停止/再開 + 記録終了)
        // ------------------------------------------------------------

        val statusText = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(24, 12, 24, 12)
            textSize = 13f
        }

        val toggleButton = Button(appContext)

        val finishButton = Button(appContext).apply {
            text = "記録終了"
        }

        val panel = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(statusText)
            addView(toggleButton)
            addView(finishButton)
        }

        // ------------------------------------------------------------
        // 記録用オーバーレイ(全画面)
        // ------------------------------------------------------------

        val root = FrameLayout(appContext)

        /**
         * 現在の状態に合わせて、表示とタッチ透過を更新する。
         *
         * 記録中(かつ再送中でない)の時だけ、オーバーレイがタッチを受け取る。
         * それ以外は下のアプリにタッチを通す。
         */
        fun refreshUi() {
            if (rootView == null) return

            val capturing = state == RecordingState.RECORDING && !isReplaying
            setTouchable(capturing)

            // 記録中だけ薄く色を付けて、記録状態であることが分かるようにする
            root.setBackgroundColor(
                if (state == RecordingState.RECORDING) {
                    Color.parseColor("#22000000")
                } else {
                    Color.TRANSPARENT
                }
            )

            statusText.text = when {
                isReplaying -> "記録: ${recorded.size}件（反映中…）"
                state == RecordingState.IDLE -> "待機中（開始で記録）"
                state == RecordingState.RECORDING -> "記録中: ${recorded.size}件"
                else -> "一時停止中: ${recorded.size}件"
            }

            styleToggleButton(toggleButton, state)
        }

        toggleButton.setOnClickListener {
            state = when (state) {
                RecordingState.IDLE,
                RecordingState.PAUSED -> RecordingState.RECORDING

                RecordingState.RECORDING -> {
                    // 一時停止した時点で、直前ステップとの間隔計測をリセットする
                    lastStepUpTime = null
                    RecordingState.PAUSED
                }
            }
            refreshUi()
        }

        finishButton.setOnClickListener {
            hide()
            onFinished(recorded.toList())
        }

        // 座標は2種類を使い分ける。
        //  - raw座標(画面全体の絶対座標): ステップとして保存・再送する座標
        //  - local座標(オーバーレイ内の座標): 丸い目印(インジケーター)の描画位置
        // オーバーレイの左上が画面の(0,0)と一致しない端末があるため、
        // 描画にrawを使うと目印がズレる。
        var downX = 0f
        var downY = 0f
        var downLocalX = 0f
        var downLocalY = 0f
        var downTime = 0L

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downLocalX = event.x
                    downLocalY = event.y
                    downTime = System.currentTimeMillis()

                    // 待機時間の自動計測:
                    // 直前のステップの指を離した時刻 → 今回の指を置いた時刻 の間隔を、
                    // 直前のステップの待機時間として上書きする。
                    val previousUpTime = lastStepUpTime
                    if (previousUpTime != null && recorded.isNotEmpty()) {
                        val measuredWait =
                            (event.eventTime - previousUpTime)
                                .coerceAtLeast(MIN_WAIT_TIME_MS)

                        recorded[recorded.lastIndex] =
                            recorded.last().copy(waitTime = measuredWait)
                    }

                    true
                }
                MotionEvent.ACTION_UP -> {
                    val upX = event.rawX
                    val upY = event.rawY
                    val upLocalX = event.x
                    val upLocalY = event.y
                    val elapsed = System.currentTimeMillis() - downTime
                    val distance = hypot((upX - downX).toDouble(), (upY - downY).toDouble())

                    val step = if (distance < MOVE_THRESHOLD_PX) {
                        if (elapsed >= LONG_PRESS_THRESHOLD_MS) {
                            showIndicator(root, downLocalX, downLocalY, Color.parseColor("#CCFF6D00"))
                            MacroStep(
                                id = UUID.randomUUID().toString(),
                                order = recorded.size,
                                actionType = ActionType.LONG_PRESS,
                                x = downX.toInt(),
                                y = downY.toInt(),
                                duration = elapsed,
                                waitTime = DEFAULT_WAIT_TIME_MS
                            )
                        } else {
                            showIndicator(root, downLocalX, downLocalY, Color.parseColor("#CC2979FF"))
                            MacroStep(
                                id = UUID.randomUUID().toString(),
                                order = recorded.size,
                                actionType = ActionType.TAP,
                                x = downX.toInt(),
                                y = downY.toInt(),
                                waitTime = DEFAULT_WAIT_TIME_MS
                            )
                        }
                    } else {
                        showIndicator(root, downLocalX, downLocalY, Color.parseColor("#CC2979FF"))
                        showIndicator(root, upLocalX, upLocalY, Color.parseColor("#CCFF6D00"))
                        MacroStep(
                            id = UUID.randomUUID().toString(),
                            order = recorded.size,
                            actionType = ActionType.SWIPE,
                            x = downX.toInt(),
                            y = downY.toInt(),
                            endX = upX.toInt(),
                            endY = upY.toInt(),
                            duration = elapsed,
                            waitTime = DEFAULT_WAIT_TIME_MS
                        )
                    }

                    recorded.add(step)

                    // 次のステップとの間隔を測るため、指を離した時刻を覚えておく
                    lastStepUpTime = event.eventTime

                    // 記録した操作を、対象アプリへ実際に送信する。
                    // 送信中はオーバーレイをタッチ透過にして、下のアプリに届くようにする。
                    // 完了したら状態に合わせて元に戻す(送信中に一時停止された場合も考慮)。
                    isReplaying = true
                    refreshUi()
                    onReplay(step) {
                        isReplaying = false
                        refreshUi()
                    }

                    true
                }
                else -> false
            }
        }

        // ------------------------------------------------------------
        // ウィンドウの追加(先に記録用、後から操作パネル = パネルが手前に来る)
        // ------------------------------------------------------------

        val rootLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val panelLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(root, rootLayoutParams)
        rootView = root
        rootParams = rootLayoutParams

        try {
            windowManager.addView(panel, panelLayoutParams)
            panelView = panel
        } catch (e: Exception) {
            hide()
            return
        }

        // 最初は待機中(タッチ透過・記録しない)にする
        refreshUi()
    }

    /**
     * 開始/一時停止/再開ボタンの見た目を、状態に合わせて設定する。
     *
     * 待機中・一時停止中 → 緑 + ▶ + 「開始」/「再開」
     * 記録中            → 赤 + ❚❚ + 「一時停止」
     */
    private fun styleToggleButton(button: Button, state: RecordingState) {
        val isRecording = state == RecordingState.RECORDING

        button.text = when (state) {
            RecordingState.IDLE -> "開始"
            RecordingState.RECORDING -> "一時停止"
            RecordingState.PAUSED -> "再開"
        }
        button.setTextColor(Color.WHITE)

        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.parseColor(if (isRecording) COLOR_PAUSE else COLOR_START))
        }
        button.setPadding(24, 12, 24, 12)

        button.compoundDrawablePadding = 10
        button.setCompoundDrawablesWithIntrinsicBounds(
            RecordingIconDrawable(
                if (isRecording) RecordingIconDrawable.Kind.PAUSE
                else RecordingIconDrawable.Kind.PLAY
            ),
            null,
            null,
            null
        )
    }

    /**
     * 記録用オーバーレイのタッチ透過(下のアプリへタッチを通す状態)を切り替える。
     *
     * touchable = false: 下のアプリにタッチが届く(待機中・一時停止中・再送中)
     * touchable = true : タッチをオーバーレイが受け取って記録する
     */
    private fun setTouchable(touchable: Boolean) {
        val view = rootView ?: return
        val params = rootParams ?: return

        if (touchable) {
            params.flags = params.flags and
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            params.alpha = 1f
        } else {
            params.flags = params.flags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            params.alpha = PASS_THROUGH_ALPHA
        }

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            // 既に消えている場合は無視
        }
    }

    private fun showIndicator(root: FrameLayout, x: Float, y: Float, color: Int) {
        val circle = View(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        val params = FrameLayout.LayoutParams(INDICATOR_SIZE_PX, INDICATOR_SIZE_PX).apply {
            leftMargin = (x - INDICATOR_SIZE_PX / 2).toInt()
            topMargin = (y - INDICATOR_SIZE_PX / 2).toInt()
        }
        root.addView(circle, params)
        circle.postDelayed({ root.removeView(circle) }, 300)
    }

    fun hide() {
        rootView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
        }
        rootView = null
        rootParams = null

        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
        }
        panelView = null
    }
}

/**
 * ボタンに表示する白い図形(▶ または ❚❚)。
 * 文字記号だと端末のフォントによって表示されない場合があるため、コードで描画する。
 */
private class RecordingIconDrawable(private val kind: Kind) : Drawable() {

    enum class Kind { PLAY, PAUSE }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val path = Path()

    override fun draw(canvas: Canvas) {
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()

        when (kind) {
            Kind.PLAY -> {
                // 右向きの三角形
                path.reset()
                path.moveTo(left + w * 0.2f, top + h * 0.1f)
                path.lineTo(left + w * 0.2f, top + h * 0.9f)
                path.lineTo(left + w * 0.9f, top + h * 0.5f)
                path.close()
                canvas.drawPath(path, paint)
            }

            Kind.PAUSE -> {
                // 縦の二本線
                canvas.drawRect(
                    left + w * 0.15f, top + h * 0.1f,
                    left + w * 0.4f, top + h * 0.9f,
                    paint
                )
                canvas.drawRect(
                    left + w * 0.6f, top + h * 0.1f,
                    left + w * 0.85f, top + h * 0.9f,
                    paint
                )
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = ICON_SIZE_PX

    override fun getIntrinsicHeight(): Int = ICON_SIZE_PX

    companion object {
        private const val ICON_SIZE_PX = 36
    }
}