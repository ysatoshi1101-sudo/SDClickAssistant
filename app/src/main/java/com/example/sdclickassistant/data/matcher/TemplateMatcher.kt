package com.example.sdclickassistant.data.matcher

import android.graphics.Bitmap

/**
 * テンプレートマッチングの結果。
 *
 * @param score 一致度(0.0〜1.0)。1.0に近いほど、テンプレートとそっくり。
 * @param centerX 見つかった領域の中心X座標(画面全体を基準にした絶対座標)
 * @param centerY 見つかった領域の中心Y座標(画面全体を基準にした絶対座標)
 */
data class MatchResult(
    val score: Float,
    val centerX: Int,
    val centerY: Int
)

/**
 * 画面の画像(screen)の中から、小さな画像(template)に
 * 最もよく似た場所を探す。
 *
 * 実装(OpenCVなど)を差し替えられるように、呼び出し側はこのインターフェースだけに依存する。
 */
interface TemplateMatcher {

    /**
     * 最も一致度の高い場所を返す。
     *
     * 処理が重いため、メインスレッドではなく Dispatchers.Default などで呼ぶこと。
     * 引数のBitmapはrecycleしない(呼び出し側が管理する)。
     *
     * @return 照合に成功した場合は結果。次の場合はnull。
     *  - templateがscreenより大きい
     *  - 照合ライブラリの初期化に失敗した
     *  - 一色だけのテンプレートなど、一致度を計算できない
     *  - 処理中に例外が発生した
     */
    fun findBestMatch(screen: Bitmap, template: Bitmap): MatchResult?
}