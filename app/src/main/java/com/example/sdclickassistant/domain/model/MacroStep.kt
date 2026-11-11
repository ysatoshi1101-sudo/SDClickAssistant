package com.example.sdclickassistant.domain.model

data class MacroStep(
    val id: String,
    val order: Int,
    val actionType: ActionType,
    val x: Int? = null,
    val y: Int? = null,
    val endX: Int? = null,
    val endY: Int? = null,
    val duration: Long? = null,
    // 各ステップ共通の、実行後の待機時間(ms)。
    // IMAGE判定でtapOnImageFound=trueの場合は、見つかった位置をタップした後の待機時間として使う
    // (nullの場合は実行時に既定値を使う)。
    val waitTime: Long? = null,
    val condition: String? = null,
    val targetLabel: String? = null,
    // CONDITION(ループ制御)ステップ専用。指定回数ループの回数。
    // nullの場合は無限ループとして扱う。
    val loopCount: Int? = null,
    // CONDITIONステップの判定方式。ConditionType.nameを保存する。
    // nullの場合はLOOP_COUNT(既存のループ制御)として扱う。
    val conditionType: String? = null,
    // COLOR判定用。判定したい色(0〜255)
    val colorR: Int? = null,
    val colorG: Int? = null,
    val colorB: Int? = null,
    // COLOR判定用。RGB各チャンネルの許容誤差(0〜255)
    val colorTolerance: Int? = null,
    // true の場合、条件が「成立しなかった時」にジャンプする(反転)
    val invertCondition: Boolean = false,
    // IMAGE判定用。一致度のしきい値(0.0〜1.0)。
    // nullの場合は実行時に既定値(0.90)を使う。
    val imageMatchThreshold: Float? = null,
    // IMAGE判定用。テンプレート作成時の画面サイズ。
    // 将来、解像度の違う端末へシナリオを共有する際に使う。
    val templateScreenWidth: Int? = null,
    val templateScreenHeight: Int? = null,
    // IMAGE判定用。trueの場合、画像が見つかった時に、その位置(領域の中心)をタップする。
    // 反転(invertCondition)の設定には関係なく、見つかったら常にタップする。
    val tapOnImageFound: Boolean = false,
    // IMAGE判定用。見つかるまで待つ最大秒数。
    // nullの場合は待たず、1回だけ判定する。時間切れは「見つからなかった」として扱う。
    val imageWaitTimeoutSec: Int? = null
)