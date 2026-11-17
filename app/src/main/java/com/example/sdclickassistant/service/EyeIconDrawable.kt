package com.example.sdclickassistant.service

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * マーカーの表示/非表示ボタンに表示する、目のアイコン。
 * 文字記号だと端末のフォントによって見た目が変わるため、コードで描画する。
 *
 * crossed = true : 斜線入りの目(マーカーが非表示)
 * crossed = false: 開いた目(マーカーが表示中)
 */
internal class EyeIconDrawable(private val crossed: Boolean) : Drawable() {

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val eyePath = Path()

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        outlinePaint.strokeWidth = w * 0.06f

        val halfWidth = w * 0.26f
        val lidHeight = h * 0.17f

        // 目の輪郭(アーモンド型)
        eyePath.reset()
        eyePath.moveTo(cx - halfWidth, cy)
        eyePath.quadTo(cx, cy - lidHeight * 2, cx + halfWidth, cy)
        eyePath.quadTo(cx, cy + lidHeight * 2, cx - halfWidth, cy)
        eyePath.close()
        canvas.drawPath(eyePath, outlinePaint)

        // 黒目(ここでは白で塗る)
        canvas.drawCircle(cx, cy, w * 0.075f, fillPaint)

        // 非表示を表す斜線
        if (crossed) {
            canvas.drawLine(
                cx - halfWidth * 0.85f, cy - halfWidth * 0.75f,
                cx + halfWidth * 0.85f, cy + halfWidth * 0.75f,
                outlinePaint
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        outlinePaint.alpha = alpha
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        outlinePaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}