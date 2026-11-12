package com.example.sdclickassistant.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingButtonController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var containerView: View? = null
    var lastPosition: Pair<Int, Int> = 100 to 300
        private set

    // 再生ボタン・戻るボタン・「−」「＋」ボタン(円形)の共通サイズ
    private val primaryButtonSizePx = 95

    // 「∞」ボタン(円形)のサイズ。こちらは拡大/縮小要望の対象外のため据え置き。
    private val infiniteButtonSizePx = 62

    // 繰り返し回数の現在値。show()の引数で初期化し、
    // ここでは表示更新のためだけに保持する(保存はコールバック経由で呼び出し側が行う)。
    private var currentRepeatCount = 1
    private var currentIsInfiniteLoop = false

    // 表示更新のために保持する参照
    private var countTextView: TextView? = null
    private var minusButtonView: TextView? = null
    private var plusButtonView: TextView? = null
    private var infiniteToggleView: TextView? = null

    // マーカー(ステップ位置の表示)を、表示中か。毎回、非表示から始まる。
    private var markersVisible = false
    private var markerToggleView: TextView? = null

    fun isShowing(): Boolean = containerView != null

    /** マーカーの表示ボタンが、表示の状態か(配置待機の画面が出ている間のみ true になる)。 */
    fun areMarkersVisible(): Boolean = containerView != null && markersVisible

    /**
     * 再生ボタン＋戻るボタン＋繰り返し回数パネルをセットで表示する。
     *
     * @param initialRepeatCount 表示する繰り返し回数の初期値
     * @param initialIsInfiniteLoop 無限ループが有効かどうかの初期値
     * @param onTap 再生ボタンをドラッグと区別された「単純タップ」した時に呼ばれる
     * @param onBack 戻るボタンをタップした時に呼ばれる
     * @param onRepeatSettingsChanged 繰り返し回数・無限ループの設定が
     *        変更された時に呼ばれる(呼び出し側でDB保存する想定)
     * @param onMarkersToggled マーカー(ステップ位置の表示)のボタンが押された時に、
     *        新しい表示状態(true=表示)と共に呼ばれる(最初は非表示)
     */
    fun show(
        initialRepeatCount: Int,
        initialIsInfiniteLoop: Boolean,
        onTap: () -> Unit,
        onBack: () -> Unit,
        onRepeatSettingsChanged: (repeatCount: Int, isInfiniteLoop: Boolean) -> Unit,
        onMarkersToggled: (visible: Boolean) -> Unit
    ) {
        if (containerView != null) return

        // マーカーは、毎回、非表示から始める
        markersVisible = false
        currentRepeatCount = initialRepeatCount
        currentIsInfiniteLoop = initialIsInfiniteLoop

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastPosition.first
            y = lastPosition.second
        }

        // 全体を縦に並べる外側コンテナ。
        // ドラッグはこのコンテナ単位で行われるため、
        // 内部に何を追加しても再生ボタンのドラッグで一緒に移動する。
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ------------------------------------------------------------
        // 1段目: 戻るボタン + 再生ボタン
        // ------------------------------------------------------------

        val buttonRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val backButton = TextView(appContext).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#757575"))
            }
            layoutParams = LinearLayout.LayoutParams(
                primaryButtonSizePx,
                primaryButtonSizePx
            ).apply {
                marginEnd = 10
            }
            setOnClickListener { onBack() }
        }

        val playButton = TextView(appContext).apply {
            text = "▶"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 17f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50"))
            }
            layoutParams = LinearLayout.LayoutParams(
                primaryButtonSizePx,
                primaryButtonSizePx
            )
        }

        // マーカー(ステップ位置の表示)の、表示/非表示を切り替えるボタン。
        // 毎回、非表示から始まる。表示中は青、非表示はグレー。
        val markerToggleButton = TextView(appContext).apply {
            text = "◎"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 17f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#616161"))
            }
            layoutParams = LinearLayout.LayoutParams(
                primaryButtonSizePx,
                primaryButtonSizePx
            ).apply {
                marginStart = 10
            }
            setOnClickListener {
                markersVisible = !markersVisible
                refreshMarkerToggle()
                onMarkersToggled(markersVisible)
            }
        }
        markerToggleView = markerToggleButton

        buttonRow.addView(backButton)
        buttonRow.addView(playButton)
        buttonRow.addView(markerToggleButton)

        // ------------------------------------------------------------
        // 2段目: 繰り返し回数パネル(− / 回数 / ＋ / ∞切替)
        // ------------------------------------------------------------

        val repeatRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6
            }
        }

        fun notifyRepeatSettingsChanged() {
            onRepeatSettingsChanged(currentRepeatCount, currentIsInfiniteLoop)
        }

        val minusButton = TextView(appContext).apply {
            text = "−"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#616161"))
            }
            layoutParams = LinearLayout.LayoutParams(
                primaryButtonSizePx,
                primaryButtonSizePx
            ).apply {
                marginEnd = 8
            }
            setOnClickListener {
                if (!currentIsInfiniteLoop && currentRepeatCount > 1) {
                    currentRepeatCount--
                    refreshRepeatDisplay()
                    notifyRepeatSettingsChanged()
                }
            }
        }

        val countText = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 8)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f
                setColor(Color.parseColor("#CC000000"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8
            }
        }

        val plusButton = TextView(appContext).apply {
            text = "＋"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#616161"))
            }
            layoutParams = LinearLayout.LayoutParams(
                primaryButtonSizePx,
                primaryButtonSizePx
            ).apply {
                marginEnd = 8
            }
            setOnClickListener {
                if (!currentIsInfiniteLoop) {
                    currentRepeatCount++
                    refreshRepeatDisplay()
                    notifyRepeatSettingsChanged()
                }
            }
        }

        val infiniteToggle = TextView(appContext).apply {
            text = "∞"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 10f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#616161"))
            }
            layoutParams = LinearLayout.LayoutParams(
                infiniteButtonSizePx,
                infiniteButtonSizePx
            )
            setOnClickListener {
                currentIsInfiniteLoop = !currentIsInfiniteLoop
                refreshRepeatDisplay()
                notifyRepeatSettingsChanged()
            }
        }

        repeatRow.addView(minusButton)
        repeatRow.addView(countText)
        repeatRow.addView(plusButton)
        repeatRow.addView(infiniteToggle)

        container.addView(buttonRow)
        container.addView(repeatRow)

        countTextView = countText
        minusButtonView = minusButton
        plusButtonView = plusButton
        infiniteToggleView = infiniteToggle

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var downTime = 0L
        var isDragging = false

        val tapMoveThreshold = 15f
        val tapTimeThreshold = 200L

        playButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    downTime = System.currentTimeMillis()
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
                    windowManager.updateViewLayout(container, params)
                    lastPosition = params.x to params.y
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isDragging && elapsed < tapTimeThreshold) {
                        onTap()
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(container, params)
        containerView = container

        refreshRepeatDisplay()
    }

    /**
     * 表示中の繰り返し回数パネルの表示内容を、外部(DB取得完了時など)から
     * 更新するためのメソッド。show()呼び出し時点ではまだシナリオが
     * 読み込めていない場合に、後から正しい値へ反映するために使う。
     */
    fun updateRepeatDisplay(repeatCount: Int, isInfiniteLoop: Boolean) {
        currentRepeatCount = repeatCount
        currentIsInfiniteLoop = isInfiniteLoop
        refreshRepeatDisplay()
    }

    /**
     * currentRepeatCount / currentIsInfiniteLoopの現在値を
     * 実際のView表示に反映する。
     */
    private fun refreshRepeatDisplay() {
        countTextView?.text = if (currentIsInfiniteLoop) {
            "∞"
        } else {
            "${currentRepeatCount}回"
        }

        // 無限ループ中は回数の増減が意味を持たないため、
        // −/＋ボタンを無効化し薄く表示する。
        val repeatControlsEnabled = !currentIsInfiniteLoop
        minusButtonView?.isEnabled = repeatControlsEnabled
        plusButtonView?.isEnabled = repeatControlsEnabled
        minusButtonView?.alpha = if (repeatControlsEnabled) 1f else 0.4f
        plusButtonView?.alpha = if (repeatControlsEnabled) 1f else 0.4f

        // ∞切替ボタンは、ONの時は目立つ色にして状態が分かるようにする。
        val infiniteColor = if (currentIsInfiniteLoop) "#2979FF" else "#616161"
        (infiniteToggleView?.background as? GradientDrawable)?.setColor(
            Color.parseColor(infiniteColor)
        )
    }

    /**
     * マーカーの表示ボタンの見た目を、現在の状態に合わせる。
     * 表示中は青、非表示はグレー(「∞」ボタンのオン/オフと同じ見せ方)。
     */
    private fun refreshMarkerToggle() {
        val color = if (markersVisible) "#2979FF" else "#616161"
        (markerToggleView?.background as? GradientDrawable)?.setColor(
            Color.parseColor(color)
        )
    }

    fun hide() {
        containerView?.let {
            windowManager.removeView(it)
            containerView = null
        }
        countTextView = null
        minusButtonView = null
        plusButtonView = null
        infiniteToggleView = null
        markerToggleView = null
    }
}