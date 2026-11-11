package com.example.sdclickassistant.domain.model

/**
 * このステップの実行に、画面キャプチャ(MediaProjectionの許可)が必要かどうか。
 *
 * 必要なもの:
 *  - スクリーンショット
 *  - 条件分岐のうち、画面を読み取る判定(色判定・画像認識)
 *
 * 判定方式(ConditionType)の分岐は、意図的に「else」を使わず、全ての種類を明示している。
 * 新しい判定方式(OCRなど)を追加すると、ここがコンパイルエラーになるため、
 * 「画面キャプチャが必要か」の更新漏れを防げる。
 */
fun MacroStep.requiresScreenCapture(): Boolean {
    return when (actionType) {
        ActionType.SCREENSHOT -> true

        ActionType.CONDITION -> when (ConditionType.fromName(conditionType)) {
            ConditionType.LOOP_COUNT -> false
            ConditionType.COLOR -> true
            ConditionType.IMAGE -> true
        }

        else -> false
    }
}

/**
 * ステップ一覧のうち、1つでも画面キャプチャが必要なステップがあるか。
 */
fun List<MacroStep>.requiresScreenCapture(): Boolean {
    return any { it.requiresScreenCapture() }
}