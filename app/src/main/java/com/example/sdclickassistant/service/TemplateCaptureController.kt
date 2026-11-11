package com.example.sdclickassistant.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 切り出したテンプレート画像と、撮影した画面の大きさ。
 *
 * bitmapは、受け取った側が使い終わったらrecycleすること。
 */
data class CroppedTemplate(
    val bitmap: Bitmap,
    val screenWidth: Int,
    val screenHeight: Int
)

/**
 * 画面の一部を切り出して、画像認識用のテンプレートを作るController。
 *
 * 大きく2つのモードを持つ(ColorPickerControllerと同じ流れ)。
 *  - 操作モード: 対象アプリを自由に操作できる(小さい操作パネルのみ表示)。
 *    目的の画面まで進めてから「この画面をキャプチャ」を押す。
 *  - 範囲選択モード: キャプチャした1枚の画像に対して、ドラッグで四角を描いて
 *    範囲を選ぶ。「撮り直す」で操作モードに戻れる。
 *
 * 重要: 操作パネルは、キャプチャの直前にいったん隠す。
 * 隠さないと、パネルがキャプチャ画像に写り込み、範囲選択でテンプレートに混ざってしまう。
 *
 * 座標は2種類を使い分ける(オーバーレイの原点が画面の(0,0)と一致しない場合があるため)。
 *  - 絶対座標(event.rawX/rawY): キャプチャ画像からの切り出しに使う
 *  - ローカル座標(event.x/y)   : 枠の描画に使う
 */
class TemplateCaptureController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        // テンプレートの最小サイズ(幅・高さとも)。
        // 小さすぎると別の場所にも一致しやすく、誤検出の原因になる。
        const val MIN_TEMPLATE_SIZE_PX = 24

        // 操作パネルを隠してから、キャプチャを要求するまでの待ち時間。
        // 隠した状態が画面に反映されるのを待つ。
        private const val HIDE_SETTLE_DELAY_MS = 250L

        // 範囲選択ツールバーのサイズ表示の色
        private const val INFO_COLOR_NORMAL = "#FFFFFF"
        private const val INFO_COLOR_WARNING = "#FFAB40"
    }

    /** 呼び出し側から受け取ったコールバック一式 */
    private class Callbacks(
        val onCaptureRequested: (onCaptured: (Bitmap?) -> Unit) -> Unit,
        val onDecide: (CroppedTemplate) -> Unit,
        val onCancel: () -> Unit
    )

    private var callbacks: Callbacks? = null

    // 動作中かどうか。操作パネルを隠してキャプチャを待っている間も、trueのまま。
    private var isActive = false

    // hide()のたびに増やす。古い(中断された)セッションの結果を破棄するために使う。
    private var sessionId = 0

    private var operatePanelView: View? = null
    private var selectionView: SelectionView? = null
    private var toolbarView: View? = null

    private var referenceBitmap: Bitmap? = null

    fun isShowing(): Boolean = isActive

    /**
     * テンプレート作成を開始する(操作モードから始まる)。
     *
     * @param onCaptureRequested 「この画面をキャプチャ」が押され、パネルを隠した後に呼ばれる。
     *        呼び出し側は現在の画面をキャプチャし、結果を onCaptured へ渡すこと。
     *        失敗した場合は onCaptured(null) を呼ぶこと。
     * @param onDecide 「決定」が押された時に呼ばれる。渡されたbitmapはrecycleすること。
     * @param onCancel 「キャンセル」が押された時、
     *        またはオーバーレイの表示に失敗した時に呼ばれる
     */
    fun show(
        onCaptureRequested: (onCaptured: (Bitmap?) -> Unit) -> Unit,
        onDecide: (CroppedTemplate) -> Unit,
        onCancel: () -> Unit
    ) {
        if (isActive) return

        isActive = true
        sessionId++
        callbacks = Callbacks(onCaptureRequested, onDecide, onCancel)

        showOperateMode()
    }

    /**
     * 表示中のもの(操作パネル・範囲選択モードどちらでも)を全て閉じる。
     * MacroAccessibilityServiceのonDestroy()などから呼ばれる想定。
     */
    fun hide() {
        sessionId++
        handler.removeCallbacksAndMessages(null)
        hideOperatePanel()
        hideSelectMode()
        callbacks = null
        isActive = false
    }

    /** キャンセル。後片付けをしてから、呼び出し側へ通知する。 */
    private fun cancelAll() {
        val cb = callbacks
        hide()
        cb?.onCancel?.invoke()
    }

    // ------------------------------------------------------------
    // 操作モード: 対象アプリを操作できる、小さい操作パネルのみ表示
    // ------------------------------------------------------------

    private fun showOperateMode(message: String? = null) {
        if (operatePanelView != null) return

        val cancelButton = Button(appContext).apply {
            text = "✕ キャンセル"
            setOnClickListener { cancelAll() }
        }

        val captureButton = Button(appContext).apply {
            text = "この画面をキャプチャ"
            setOnClickListener { requestCapture() }
        }

        val panel = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        if (message != null) {
            panel.addView(
                TextView(appContext).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    setPadding(8, 0, 16, 0)
                }
            )
        }

        panel.addView(cancelButton)
        panel.addView(captureButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        try {
            windowManager.addView(panel, params)
            operatePanelView = panel
        } catch (e: Exception) {
            cancelAll()
        }
    }

    private fun hideOperatePanel() {
        operatePanelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
        }
        operatePanelView = null
    }

    /**
     * 操作パネルを隠してから、画面のキャプチャを呼び出し側へ要求する。
     */
    private fun requestCapture() {
        val cb = callbacks ?: return
        val session = sessionId

        // パネルがキャプチャ画像に写り込まないよう、先に隠す。
        hideOperatePanel()

        handler.postDelayed({
            if (session == sessionId) {
                cb.onCaptureRequested { bitmap ->
                    if (session != sessionId) {
                        // 待っている間に中断された。受け取った画像は使わず破棄する。
                        bitmap?.recycle()
                    } else if (bitmap != null) {
                        enterSelectMode(bitmap)
                    } else {
                        // 失敗時は操作モードへ戻り、もう一度押せるようにする。
                        // メッセージが長いとパネルが画面からはみ出すため、短くしている。
                        showOperateMode("撮影に失敗")
                    }
                }
            }
        }, HIDE_SETTLE_DELAY_MS)
    }

    // ------------------------------------------------------------
    // 範囲選択モード: 全画面の選択オーバーレイ + 下部ツールバー
    // ------------------------------------------------------------

    private fun enterSelectMode(bitmap: Bitmap) {
        referenceBitmap = bitmap

        val selection = SelectionView(appContext)

        // ---- ツールバーの部品 ----
        // 大きな範囲を選んでもツールバーが画面からはみ出さないよう、
        // ボタンは記号だけにし、サイズ表示は短く・幅を固定にしている。

        val infoView = TextView(appContext).apply {
            setTextColor(Color.parseColor(INFO_COLOR_NORMAL))
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            // 表示が変わってもツールバーの幅が動かないよう、最小の幅を確保する
            minEms = 9
            setPadding(16, 8, 16, 8)
        }

        val cancelButton = createSymbolButton("✕", "キャンセル") {
            cancelAll()
        }

        val retakeButton = createSymbolButton("↺", "撮り直す") {
            hideSelectMode()
            showOperateMode()
        }

        val decideButton = createSymbolButton("✓", "決定") {
            val ref = referenceBitmap
            val rect = if (ref != null) {
                selection.absoluteRect(ref.width, ref.height)
            } else {
                null
            }

            if (
                ref != null &&
                rect != null &&
                rect.width() >= MIN_TEMPLATE_SIZE_PX &&
                rect.height() >= MIN_TEMPLATE_SIZE_PX
            ) {
                val cropped = cropBitmap(ref, rect)
                val result = CroppedTemplate(
                    bitmap = cropped,
                    screenWidth = ref.width,
                    screenHeight = ref.height
                )

                // 後片付け(元のキャプチャ画像のrecycleを含む)をしてから、結果を渡す。
                val cb = callbacks
                hide()
                cb?.onDecide?.invoke(result)
            }
        }
        decideButton.isEnabled = false

        /** 選択範囲に合わせて、サイズ表示と「決定」の有効/無効を更新する。 */
        fun refreshInfo() {
            val ref = referenceBitmap
            val rect = if (ref != null) {
                selection.absoluteRect(ref.width, ref.height)
            } else {
                null
            }

            when {
                rect == null -> {
                    infoView.text = "ドラッグで選択"
                    infoView.setTextColor(Color.parseColor(INFO_COLOR_NORMAL))
                    decideButton.isEnabled = false
                }

                rect.width() < MIN_TEMPLATE_SIZE_PX ||
                        rect.height() < MIN_TEMPLATE_SIZE_PX -> {
                    infoView.text = "${rect.width()}×${rect.height()} 小さすぎ"
                    infoView.setTextColor(Color.parseColor(INFO_COLOR_WARNING))
                    decideButton.isEnabled = false
                }

                else -> {
                    infoView.text = "${rect.width()}×${rect.height()}"
                    infoView.setTextColor(Color.parseColor(INFO_COLOR_NORMAL))
                    decideButton.isEnabled = true
                }
            }
        }

        selection.onSelectionChanged = { refreshInfo() }

        // ---- 選択オーバーレイ(全画面)の追加 ----

        val selectionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(selection, selectionParams)
            selectionView = selection
        } catch (e: Exception) {
            cancelAll()
            return
        }

        // ---- ツールバーの追加 ----

        val toolbar = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#CC000000"))
        }
        toolbar.addView(cancelButton)
        toolbar.addView(retakeButton)
        toolbar.addView(infoView)
        toolbar.addView(decideButton)

        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        try {
            windowManager.addView(toolbar, toolbarParams)
            toolbarView = toolbar
        } catch (e: Exception) {
            cancelAll()
            return
        }

        refreshInfo()
    }

    /**
     * ツールバー用の、記号だけの小さなボタンを作る。
     *
     * 標準のボタンは最小幅や余白が大きく、並べるとツールバーが
     * 画面からはみ出しやすいため、最小幅をなくして余白を詰めている。
     * 記号だけでは意味が分からなくならないよう、
     * 音声読み上げ用の説明(contentDescription)を付ける。
     */
    private fun createSymbolButton(
        symbol: String,
        description: String,
        onClick: () -> Unit
    ): Button {
        return Button(appContext).apply {
            text = symbol
            textSize = 18f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(32, 16, 32, 16)
            contentDescription = description
            setOnClickListener { onClick() }
        }
    }

    /**
     * キャプチャ画像から、指定範囲を切り出して返す。
     *
     * Bitmap.createBitmap(source, ...)は、範囲が元の画像全体と同じ場合に
     * 元のBitmapそのものを返すことがある。その場合、元のBitmapをrecycleすると
     * 切り出した画像まで使えなくなるため、必ず新しいBitmapに描き写して独立させる。
     */
    private fun cropBitmap(source: Bitmap, rect: Rect): Bitmap {
        val output = Bitmap.createBitmap(
            rect.width(),
            rect.height(),
            Bitmap.Config.ARGB_8888
        )

        Canvas(output).drawBitmap(
            source,
            rect,
            Rect(0, 0, rect.width(), rect.height()),
            null
        )

        return output
    }

    private fun hideSelectMode() {
        selectionView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
        }
        selectionView = null

        toolbarView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
        }
        toolbarView = null

        referenceBitmap?.recycle()
        referenceBitmap = null
    }

    // ------------------------------------------------------------
    // 範囲選択のオーバーレイ
    // ------------------------------------------------------------

    /**
     * ドラッグで四角を描く、全画面のオーバーレイ。
     * 選択範囲の外側を薄く暗くして、枠を表示する。
     */
    private class SelectionView(context: Context) : View(context) {

        var onSelectionChanged: (() -> Unit)? = null

        private var hasSelection = false

        // 絶対座標(画面全体を基準): キャプチャ画像からの切り出し用
        private var startAbsX = 0f
        private var startAbsY = 0f
        private var endAbsX = 0f
        private var endAbsY = 0f

        // ローカル座標(このウィンドウ内を基準): 枠の描画用
        private var startLocalX = 0f
        private var startLocalY = 0f
        private var endLocalX = 0f
        private var endLocalY = 0f

        private val dimPaint = Paint().apply {
            color = Color.parseColor("#88000000")
            style = Paint.Style.FILL
        }

        private val hintPaint = Paint().apply {
            color = Color.parseColor("#33000000")
            style = Paint.Style.FILL
        }

        private val outlinePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        private val linePaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        /**
         * 選択範囲を、キャプチャ画像の絶対座標で返す。選択前はnull。
         * 画像の範囲(maxWidth x maxHeight)からはみ出さないように丸める。
         */
        fun absoluteRect(maxWidth: Int, maxHeight: Int): Rect? {
            if (!hasSelection) return null

            val left = minOf(startAbsX, endAbsX).toInt().coerceIn(0, maxWidth)
            val top = minOf(startAbsY, endAbsY).toInt().coerceIn(0, maxHeight)
            val right = maxOf(startAbsX, endAbsX).toInt().coerceIn(0, maxWidth)
            val bottom = maxOf(startAbsY, endAbsY).toInt().coerceIn(0, maxHeight)

            return Rect(left, top, right, bottom)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hasSelection = true
                    startAbsX = event.rawX
                    startAbsY = event.rawY
                    endAbsX = event.rawX
                    endAbsY = event.rawY
                    startLocalX = event.x
                    startLocalY = event.y
                    endLocalX = event.x
                    endLocalY = event.y
                }

                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    endAbsX = event.rawX
                    endAbsY = event.rawY
                    endLocalX = event.x
                    endLocalY = event.y
                }

                else -> return false
            }

            invalidate()
            onSelectionChanged?.invoke()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            if (!hasSelection) {
                // 選択モードであることが分かるよう、全体を薄く暗くする
                canvas.drawRect(0f, 0f, w, h, hintPaint)
                return
            }

            val selected = RectF(
                minOf(startLocalX, endLocalX),
                minOf(startLocalY, endLocalY),
                maxOf(startLocalX, endLocalX),
                maxOf(startLocalY, endLocalY)
            )

            // 選択範囲の外側(上・下・左・右)を暗くする
            canvas.drawRect(0f, 0f, w, selected.top, dimPaint)
            canvas.drawRect(0f, selected.bottom, w, h, dimPaint)
            canvas.drawRect(0f, selected.top, selected.left, selected.bottom, dimPaint)
            canvas.drawRect(selected.right, selected.top, w, selected.bottom, dimPaint)

            // 枠(黒の縁取りの上に白線)
            canvas.drawRect(selected, outlinePaint)
            canvas.drawRect(selected, linePaint)
        }
    }
}