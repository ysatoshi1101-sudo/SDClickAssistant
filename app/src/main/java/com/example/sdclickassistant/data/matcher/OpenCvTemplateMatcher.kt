package com.example.sdclickassistant.data.matcher

import android.graphics.Bitmap
import android.util.Log
import com.example.sdclickassistant.data.matcher.MatchResult
import com.example.sdclickassistant.data.matcher.TemplateMatcher
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import javax.inject.Inject

/**
 * OpenCVのmatchTemplateを使った、TemplateMatcherの実装。
 *
 * OpenCVを使うコードはこのクラスの中だけに閉じ込める。
 * Hiltから1つだけ(Singleton)作られ、OpenCVの初期化も一度だけ行われる。
 */
class OpenCvTemplateMatcher @Inject constructor() : TemplateMatcher {

    companion object {
        private const val TAG = "OpenCvTemplateMatcher"
    }

    // OpenCVのネイティブライブラリの読み込み。初回の呼び出し時に一度だけ行う。
    private val isOpenCvReady: Boolean by lazy {
        val loaded = OpenCVLoader.initLocal()
        if (!loaded) {
            Log.e(TAG, "OpenCV initialization failed")
        }
        loaded
    }

    override fun findBestMatch(screen: Bitmap, template: Bitmap): MatchResult? {

        if (!isOpenCvReady) {
            return null
        }

        if (
            template.width > screen.width ||
            template.height > screen.height
        ) {
            Log.w(
                TAG,
                "Template is larger than screen: " +
                        "template=${template.width}x${template.height}, " +
                        "screen=${screen.width}x${screen.height}"
            )
            return null
        }

        // OpenCVの内部データ。使い終わったら必ずrelease()する。
        val screenMat = Mat()
        val templateMat = Mat()
        val result = Mat()

        return try {

            // BitmapをMatへ変換し、アルファ(透明度)チャンネルを除いたRGBにする。
            Utils.bitmapToMat(screen, screenMat)
            Utils.bitmapToMat(template, templateMat)

            Imgproc.cvtColor(screenMat, screenMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_RGBA2RGB)

            // 明るさの違いに比較的強い、正規化相関係数による照合。
            Imgproc.matchTemplate(
                screenMat,
                templateMat,
                result,
                Imgproc.TM_CCOEFF_NORMED
            )

            val minMax = Core.minMaxLoc(result)
            val score = minMax.maxVal.toFloat()

            // 一色だけのテンプレートなどでは一致度が計算できずNaNになる。
            if (score.isNaN()) {
                Log.w(TAG, "Match score is NaN (template may be a single color)")
                return null
            }

            // maxLocは「見つかった領域の左上」。中心座標に直して返す。
            MatchResult(
                score = score.coerceIn(0f, 1f),
                centerX = (minMax.maxLoc.x + template.width / 2.0).toInt(),
                centerY = (minMax.maxLoc.y + template.height / 2.0).toInt()
            )

        } catch (e: Exception) {

            Log.e(TAG, "Template matching failed", e)
            null

        } finally {

            screenMat.release()
            templateMat.release()
            result.release()
        }
    }
}