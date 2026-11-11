package com.example.sdclickassistant.data.local.dao

import androidx.room.*
import com.example.sdclickassistant.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    // ドラッグ&ドロップ並び替え後の一括更新用。
    // Roomはリストを引数に取るUpdateを自動的にトランザクション内で実行する。
    @Update
    suspend fun updateAll(folders: List<FolderEntity>)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY `order` ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    // 新規フォルダを一番最後に追加するための現在の最大order取得
    @Query("SELECT MAX(`order`) FROM folders")
    suspend fun getMaxOrder(): Int?
}