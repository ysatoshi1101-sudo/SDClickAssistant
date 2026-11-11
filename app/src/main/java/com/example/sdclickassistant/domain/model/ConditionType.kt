package com.example.sdclickassistant.domain.model

/** IMAGE判定の、一致度しきい値の既定値と設定できる範囲 */
const val DEFAULT_IMAGE_MATCH_THRESHOLD = 0.90f
const val MIN_IMAGE_MATCH_THRESHOLD = 0.50f
const val MAX_IMAGE_MATCH_THRESHOLD = 1.00f

/**
 * CONDITIONステップの判定方式。
 *
 * LOOP_COUNT: 指定回数までラベルへ戻る(既存のループ制御)
 * COLOR: 指定座標の色が指定色と一致するか判定する
 * IMAGE: 画面のどこかに、登録したテンプレート画像が見えているか判定する
 *
 * 将来的にOCRを追加予定。
 */
enum class ConditionType {
    LOOP_COUNT,
    COLOR,
    IMAGE;

    companion object {
        /**
         * DBに保存された文字列からConditionTypeを復元する。
         *
         * 該当なし・null(旧データ)の場合はLOOP_COUNTとして扱う
         * (既存のCONDITIONステップとの後方互換のため)。
         */
        fun fromName(name: String?): ConditionType {
            return entries.find { it.name == name } ?: LOOP_COUNT
        }
    }
}