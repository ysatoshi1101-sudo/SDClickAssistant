package com.example.sdclickassistant.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * version 5 → 6
 *
 * 画像認識(IMAGE)用の3つの列を、stepsテーブルに追加する。
 * すべてnullを許す列のため、既存のステップはそのまま残る。
 *
 * 列の型は、StepEntityの定義(Float? → REAL、Int? → INTEGER)と
 * 正確に一致させること。Roomはマイグレーション後の構造を検査し、
 * 一致しないと起動時にクラッシュする。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE steps ADD COLUMN imageMatchThreshold REAL")
        db.execSQL("ALTER TABLE steps ADD COLUMN templateScreenWidth INTEGER")
        db.execSQL("ALTER TABLE steps ADD COLUMN templateScreenHeight INTEGER")
    }
}

/**
 * version 6 → 7
 *
 * 画像認識(IMAGE)の「見つかった位置をタップ」「見つかるまで待つ」用の
 * 2つの列を、stepsテーブルに追加する。
 *
 *  - tapOnImageFound: Boolean(NOT NULL)。既存のステップは0(=タップしない)にする。
 *    StepEntityの @ColumnInfo(defaultValue = "0") と揃えている。
 *  - imageWaitTimeoutSec: Int?(nullを許す)。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE steps ADD COLUMN tapOnImageFound INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE steps ADD COLUMN imageWaitTimeoutSec INTEGER")
    }
}

/**
 * DatabaseModuleへ登録するマイグレーションの一覧。
 * 新しいマイグレーションを作ったら、ここに追加する。
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_5_6,
    MIGRATION_6_7
)