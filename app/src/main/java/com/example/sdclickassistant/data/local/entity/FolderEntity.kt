package com.example.sdclickassistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val order: Int = 0,
    val createdDate: Long = System.currentTimeMillis()
)