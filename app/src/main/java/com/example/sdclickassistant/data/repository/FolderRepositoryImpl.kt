package com.example.sdclickassistant.data.repository

import com.example.sdclickassistant.data.local.dao.FolderDao
import com.example.sdclickassistant.data.local.entity.FolderEntity
import com.example.sdclickassistant.domain.model.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createFolder(name: String) {
        val maxOrder = folderDao.getMaxOrder() ?: -1

        folderDao.insert(
            FolderEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                order = maxOrder + 1
            )
        )
    }

    override suspend fun renameFolder(folderId: String, newName: String) {
        val current = folderDao.getFolderById(folderId) ?: return
        folderDao.update(current.copy(name = newName))
    }

    override suspend fun deleteFolder(folder: Folder) {
        // ScenarioEntity.folderIdはonDelete=SET_NULLのため、
        // フォルダ削除時に中のシナリオは削除されず「未分類」に戻る。
        folderDao.delete(folder.toEntity())
    }

    override suspend fun reorderFolders(folders: List<Folder>) {
        val updated = folders.mapIndexed { index, folder ->
            folder.copy(order = index).toEntity()
        }
        folderDao.updateAll(updated)
    }
}

private fun FolderEntity.toDomain(): Folder {
    return Folder(
        id = id,
        name = name,
        order = order,
        createdDate = createdDate
    )
}

private fun Folder.toEntity(): FolderEntity {
    return FolderEntity(
        id = id,
        name = name,
        order = order,
        createdDate = createdDate
    )
}