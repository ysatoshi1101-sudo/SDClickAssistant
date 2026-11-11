package com.example.sdclickassistant.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 画面上の色をスポイトで取得するためのController。
 *
 * 大きく2つのモードを持つ。
 *  - 操作モード: 対象アプリを自由に操作できる(小さい操作パネルのみ表示)。
 *    目的の画面まで進めてから「この画面をキャプチャ」を押す。
 *  - 色選択モード: キャプチャした1枚の画像の上に十字マーカーを表示し、
 *    タップした位置の色を読み取る。「撮り直す」で操作モードに戻れる。
 *
 * 重要: 十字マーカー自体はキャプチャ後にオーバーレイとして描画するため、
 * キャプチャした画像のピクセル値には影響しない。
 */
class ColorPickerController(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // 操作モード用の小さいパネル(常に全画面ではないので、対象アプリはそのまま操作できる)
    private var operatePanelView: View? = null

    // 色選択モード用のView(十字マーカー全画面 + 下部ツールバー)
    private var crosshairView: View? = null
    private var toolbarView: View? = null

    private var referenceBitmap: Bitmap? = null

    // 色のサンプリングに使う座標(画面全体を基準にした絶対座標)
    // referenceBitmapも画面全体で撮影しているため、この座標系と一致させる。
    private var pickedX = 0
    private var pickedY = 0

    // 十字マーカーの描画に使う座標(オーバーレイウィンドウ内のローカル座標)
    // ウィンドウの原点(左上)が画面absolute(0,0)と一致しない場合があるため、
    // 描画にはpickedX/pickedYではなくこちらを使う。
    private var drawX = 0f
    private var drawY = 0f

    private var hasPicked = false

    fun isShowing(): Boolean = operatePanelView != null || crosshairView != null

    /**
     * スポイト機能を開始する(操作モードから始まる)。
     *
     * @param onCaptureRequested 「この画面をキャプチャ」が押された時に呼ばれる。
     *        呼び出し側は現在の画面をキャプチャし、結果を onCaptured へ渡すこと。
     *        キャプチャに失敗した場合は onCaptured(null) を呼ぶこと。
     * @param onDecide 「決定」が押された時に呼ばれる
     * @param onCancel 「キャンセル」が押された時、
     *                 またはオーバーレイの表示に失敗した時に呼ばれる
     */
    fun show(
        onCaptureRequested: (onCaptured: (Bitmap?) -> Unit) -> Unit,
        onDecide: (ColorPickResult) -> Unit,
        onCancel: () -> Unit
    ) {
        if (isShowing()) return

        fun cancelAll() {
            hideOperatePanel()
            hidePickMode()
            referenceBitmap?.recycle()
            referenceBitmap = null
            onCancel()
        }

        fun showOperateMode() {
            showOperatePanel(
                onCapture = { setCaptureButtonBusy ->
                    setCaptureButtonBusy(true)
                    onCaptureRequested { bitmap ->
                        if (bitmap != null) {
                            hideOperatePanel()
                            enterPickMode(
                                bitmap = bitmap,
                                onRetake = {
                                    hidePickMode()
                                    showOperateMode()
                                },
                                onDecide = onDecide,
                                onCancel = { cancelAll() }
                            )
                        } else {
                            // キャプチャ失敗時は操作モードのまま、ボタンを再度押せるようにする
                            setCaptureButtonBusy(false)
                        }
                    }
                },
                onCancel = { cancelAll() }
            )
        }

        showOperateMode()
    }

    // ------------------------------------------------------------
    // 操作モード: 対象アプリを操作できる、小さい操作パネルのみ表示
    // ------------------------------------------------------------

    private fun showOperatePanel(
        onCapture: (setBusy: (Boolean) -> Unit) -> Unit,
        onCancel: () -> Unit
    ) {
        if (operatePanelView != null) return

        val captureButton = Button(appContext).apply {
            text = "この画面をキャプチャ"
        }

        val cancelButton = Button(appContext).apply {
            text = "✕ キャンセル"
            setOnClickListener { onCancel() }
        }

        fun setBusy(busy: Boolean) {
            captureButton.isEnabled = !busy
            captureButton.text = if (busy) "キャプチャ中…" else "この画面をキャプチャ"
        }

        captureButton.setOnClickListener {
            onCapture(::setBusy)
        }

        val panel = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#CC000000"))
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
            onCancel()
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

    // ------------------------------------------------------------
    // 色選択モード: 十字マーカー(全画面) + 下部ツールバー
    // ------------------------------------------------------------

    private fun enterPickMode(
        bitmap: Bitmap,
        onRetake: () -> Unit,
        onDecide: (ColorPickResult) -> Unit,
        onCancel: () -> Unit
    ) {
        referenceBitmap = bitmap
        hasPicked = false

        var colorSwatchView: TextView? = null

        val crosshair = object : View(appContext) {

            private val outlinePaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 8f
                isAntiAlias = true
            }

            private val linePaint = Paint().apply {
                color = Color.WHITE
                strokeWidth = 4f
                isAntiAlias = true
            }

            // 十字の長さと、中心をあける空白の大きさ
            private val armLength = 28f
            private val centerGap = 8f

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                if (!hasPicked) return

                val cx = drawX
                val cy = drawY

                // 黒縁取り→白線の順に重ねて描画し、どんな背景でも見やすくする。
                // 中心(判定対象のピクセル)そのものは描画しないよう空白をあける。
                listOf(outlinePaint, linePaint).forEach { paint ->

                    canvas.drawLine(cx, cy - armLength, cx, cy - centerGap, paint)
                    canvas.drawLine(cx, cy + centerGap, cx, cy + armLength, paint)
                    canvas.drawLine(cx - armLength, cy, cx - centerGap, cy, paint)
                    canvas.drawLine(cx + centerGap, cy, cx + armLength, cy, paint)
                }
            }
        }

        crosshair.setOnTouchListener { _, event ->

            if (event.action == MotionEvent.ACTION_DOWN) {

                // サンプリング用: 画面全体を基準にした絶対座標
                pickedX = event.rawX.toInt()
                pickedY = event.rawY.toInt()

                // 描画用: ウィンドウ内のローカル座標
                drawX = event.x
                drawY = event.y

                hasPicked = true
                crosshair.invalidate()

                updateColorSwatch(colorSwatchView)
            }

            true
        }

        val crosshairParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        try {

            windowManager.addView(crosshair, crosshairParams)
            crosshairView = crosshair

        } catch (e: Exception) {

            referenceBitmap?.recycle()
            referenceBitmap = null

            onCancel()

            return
        }

        // ------------------------------------------------------------
        // ツールバー(キャンセル・撮り直す・色プレビュー・決定)
        //
        // ツールバーが画面からはみ出さないよう、ボタンは記号だけにし、
        // 色の表示は短く・幅を固定にしている。
        // ------------------------------------------------------------

        val swatch = TextView(appContext).apply {
            text = "タップで選択"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            // 表示が変わってもツールバーの幅が動かないよう、最小の幅を確保する
            minEms = 11
            setPadding(16, 8, 16, 8)
        }
        colorSwatchView = swatch

        val cancelButton = createSymbolButton("✕", "キャンセル") {
            hidePickMode()
            onCancel()
        }

        val retakeButton = createSymbolButton("↺", "撮り直す") {
            onRetake()
        }

        val decideButton = createSymbolButton("✓", "決定") {

            val bmp = referenceBitmap

            if (
                hasPicked &&
                bmp != null &&
                pickedX in 0 until bmp.width &&
                pickedY in 0 until bmp.height
            ) {

                val pixel = bmp.getPixel(pickedX, pickedY)

                val result = ColorPickResult(
                    x = pickedX,
                    y = pickedY,
                    r = Color.red(pixel),
                    g = Color.green(pixel),
                    b = Color.blue(pixel)
                )

                hidePickMode()
                onDecide(result)
            }
        }

        val toolbar = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#CC000000"))
        }
        toolbar.addView(cancelButton)
        toolbar.addView(retakeButton)
        toolbar.addView(swatch)
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

            hidePickMode()
            onCancel()
        }
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

    private fun updateColorSwatch(swatch: TextView?) {

        val bmp = referenceBitmap ?: return

        if (
            pickedX !in 0 until bmp.width ||
            pickedY !in 0 until bmp.height
        ) {
            return
        }

        val pixel = bmp.getPixel(pickedX, pickedY)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // R,G,Bの順。ステップ編集ダイアログの「R」「G」「B」と同じ順番。
        swatch?.text = "$r,$g,$b"
        swatch?.setBackgroundColor(Color.rgb(r, g, b))

        // 背景色(サンプリングした色)の輝度に応じて文字色を切り替える。
        // 明るい背景色 → 黒文字、暗い背景色 → 白文字にすることで、
        // どんな色を拾っても文字が見えなくなる問題(白文字固定の弊害)を防ぐ。
        swatch?.setTextColor(readableTextColorFor(r, g, b))
    }

    /**
     * 背景色(r,g,b)に対して視認性の高い文字色(黒 or 白)を返す。
     * YIQ近似式による輝度計算。閾値128は一般的な目安値。
     */
    private fun readableTextColorFor(r: Int, g: Int, b: Int): Int {
        val luminance = (r * 299 + g * 587 + b * 114) / 1000
        return if (luminance >= 128) Color.BLACK else Color.WHITE
    }

    private fun hidePickMode() {

        crosshairView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // 既に消えている場合は無視
            }
        }
        crosshairView = null

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
        hasPicked = false
    }

    /**
     * 表示中のもの(操作パネル・色選択モードどちらでも)を全て閉じる。
     * MacroAccessibilityServiceのonDestroy()などから呼ばれる想定。
     */
    fun hide() {
        hideOperatePanel()
        hidePickMode()
    }
}