package com.example.sdclickassistant.presentation.scenariolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sdclickassistant.data.repository.FolderRepository
import com.example.sdclickassistant.data.repository.ScenarioRepository
import com.example.sdclickassistant.domain.model.Folder
import com.example.sdclickassistant.domain.model.MacroScenario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * シナリオ一覧の絞り込み条件。
 */
sealed class FolderFilter {
    data object All : FolderFilter()
    data object Unassigned : FolderFilter()
    data class Selected(val folderId: String) : FolderFilter()
}

@HiltViewModel
class ScenarioListViewModel @Inject constructor(
    private val scenarioRepository: ScenarioRepository,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val _selectedFilter =
        MutableStateFlow<FolderFilter>(FolderFilter.All)
    val selectedFilter: StateFlow<FolderFilter> =
        _selectedFilter.asStateFlow()

    val folders: StateFlow<List<Folder>> =
        folderRepository.getAllFolders()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val allScenarios: StateFlow<List<MacroScenario>> =
        scenarioRepository.getAllScenarios()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // 選択中のフィルタに応じて絞り込んだシナリオ一覧
    val scenarios: StateFlow<List<MacroScenario>> =
        combine(allScenarios, _selectedFilter) { list, filter ->
            when (filter) {
                is FolderFilter.All ->
                    list

                is FolderFilter.Unassigned ->
                    list.filter { it.folderId == null }

                is FolderFilter.Selected ->
                    list.filter { it.folderId == filter.folderId }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectFilter(filter: FolderFilter) {
        _selectedFilter.value = filter
    }

    // ------------------------------------------------------------
    // シナリオ
    // ------------------------------------------------------------

    fun createScenario(name: String) {
        if (name.isBlank()) return

        viewModelScope.launch {

            val maxOrder =
                allScenarios.value.maxOfOrNull { it.order } ?: -1

            // フォルダを絞り込んだ状態で新規作成した場合、
            // そのフォルダに属するシナリオとして作成する。
            val folderId =
                (_selectedFilter.value as? FolderFilter.Selected)
                    ?.folderId

            val newScenario = MacroScenario(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                order = maxOrder + 1,
                folderId = folderId
            )

            scenarioRepository.saveScenario(newScenario)
        }
    }

    fun deleteScenario(scenario: MacroScenario) {
        viewModelScope.launch {
            scenarioRepository.deleteScenario(scenario)
        }
    }

    fun renameScenario(scenarioId: String, newName: String) {
        if (newName.isBlank()) return

        viewModelScope.launch {
            scenarioRepository.renameScenario(
                scenarioId,
                newName.trim()
            )
        }
    }

    fun copyScenario(scenario: MacroScenario) {
        viewModelScope.launch {
            scenarioRepository.copyScenario(scenario)
        }
    }

    fun toggleFavorite(scenario: MacroScenario) {
        viewModelScope.launch {
            scenarioRepository.setFavorite(
                scenario.id,
                !scenario.isFavorite
            )
        }
    }

    fun moveToFolder(scenarioId: String, folderId: String?) {
        viewModelScope.launch {
            scenarioRepository.moveToFolder(scenarioId, folderId)
        }
    }

    // ドラッグ&ドロップ後の並び順をまとめて保存する
    fun reorderScenarios(newOrder: List<MacroScenario>) {
        viewModelScope.launch {
            scenarioRepository.reorderScenarios(newOrder)
        }
    }

    // ------------------------------------------------------------
    // フォルダ
    // ------------------------------------------------------------

    fun createFolder(name: String) {
        if (name.isBlank()) return

        viewModelScope.launch {
            folderRepository.createFolder(name.trim())
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return

        viewModelScope.launch {
            folderRepository.renameFolder(folderId, newName.trim())
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {

            folderRepository.deleteFolder(folder)

            // 削除したフォルダを絞り込み中だった場合は
            // 「すべて」に戻す。
            if (
                _selectedFilter.value ==
                FolderFilter.Selected(folder.id)
            ) {
                _selectedFilter.value = FolderFilter.All
            }
        }
    }
}