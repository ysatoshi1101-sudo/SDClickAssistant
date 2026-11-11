package com.example.sdclickassistant.data.repository

import com.example.sdclickassistant.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {

    fun getAllFolders(): Flow<List<Folder>>

    suspend fun createFolder(name: String)

    suspend fun renameFolder(folderId: String, newName: String)

    suspend fun deleteFolder(folder: Folder)

    // ドラッグ&ドロップ後の並び順をまとめて保存する
    suspend fun reorderFolders(folders: List<Folder>)
}