package com.example.sdclickassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.sdclickassistant.data.local.dao.FolderDao
import com.example.sdclickassistant.data.local.dao.ScenarioDao
import com.example.sdclickassistant.data.local.dao.StepDao
import com.example.sdclickassistant.data.local.entity.FolderEntity
import com.example.sdclickassistant.data.local.entity.ScenarioEntity
import com.example.sdclickassistant.data.local.entity.StepEntity

/**
 * アプリのRoomデータベース。
 *
 * 実際のインスタンスは、Hiltを通じて
 * di/DatabaseModule.provideAppDatabase()が提供する。
 *
 * バージョンを上げる時は、必ずMigrations.ktにマイグレーションを追加し、
 * DatabaseModuleに登録すること。
 */
@Database(
    entities = [
        ScenarioEntity::class,
        StepEntity::class,
        FolderEntity::class
    ],
    version = 7, // 画像認識(IMAGE)の「位置をタップ」「見つかるまで待つ」の設定をステップに追加したためバージョンアップ
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scenarioDao(): ScenarioDao
    abstract fun stepDao(): StepDao
    abstract fun folderDao(): FolderDao
}