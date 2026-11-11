package com.example.sdclickassistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scenarios",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            // フォルダを削除してもシナリオは削除せず、
            // 未分類(folderId = null)に戻す。
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class ScenarioEntity(
    @PrimaryKey val id: String,       // UUID文字列
    val name: String,
    val repeatCount: Int = 1,
    val isInfiniteLoop: Boolean = false,
    val speed: Float = 1.0f,
    val createdDate: Long = System.currentTimeMillis(),
    val updatedDate: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val order: Int = 0,
    val folderId: String? = null
)