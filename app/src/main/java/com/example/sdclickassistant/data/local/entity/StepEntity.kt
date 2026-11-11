package com.example.sdclickassistant.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = ScenarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["scenarioId"])]
)
data class StepEntity(
    @PrimaryKey val id: String,
    val scenarioId: String,
    val order: Int,
    val actionType: String,
    val x: Int? = null,
    val y: Int? = null,
    val endX: Int? = null,
    val endY: Int? = null,
    val duration: Long? = null,
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
    // IMAGE判定用。一致度のしきい値(0.0〜1.0)。nullなら実行時に既定値を使う。
    val imageMatchThreshold: Float? = null,
    // IMAGE判定用。テンプレート作成時の画面サイズ。
    val templateScreenWidth: Int? = null,
    val templateScreenHeight: Int? = null,
    // IMAGE判定用。trueの場合、画像が見つかった時に、その位置をタップする。
    // マイグレーションで追加した列と、既定値(0)を揃えている。
    @ColumnInfo(defaultValue = "0")
    val tapOnImageFound: Boolean = false,
    // IMAGE判定用。見つかるまで待つ最大秒数。nullなら待たず、1回だけ判定する。
    val imageWaitTimeoutSec: Int? = null
)