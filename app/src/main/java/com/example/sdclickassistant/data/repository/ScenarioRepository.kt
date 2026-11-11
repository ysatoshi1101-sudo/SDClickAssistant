package com.example.sdclickassistant.data.repository

import com.example.sdclickassistant.domain.model.MacroScenario
import kotlinx.coroutines.flow.Flow

interface ScenarioRepository {

    // 一覧表示用（ステップは含めない軽量版）。orderの昇順で返す
    fun getAllScenarios(): Flow<List<MacroScenario>>

    // 詳細表示用（ステップを含む）
    suspend fun getScenarioDetail(scenarioId: String): MacroScenario?

    // 新規作成・上書き保存（ステップも含めてまとめて保存）
    suspend fun saveScenario(scenario: MacroScenario)

    // 削除（紐づくステップも自動で削除される）
    suspend fun deleteScenario(scenario: MacroScenario)

    // 名前変更
    suspend fun renameScenario(scenarioId: String, newName: String)

    // 複製（ステップも含めて全て新しいIDで複製し、一覧の最後に追加する）
    suspend fun copyScenario(scenario: MacroScenario)

    // お気に入りのON/OFF切り替え
    suspend fun setFavorite(scenarioId: String, isFavorite: Boolean)

    // ドラッグ&ドロップ後の並び順をまとめて保存する
    suspend fun reorderScenarios(scenarios: List<MacroScenario>)

    // フォルダへ移動する。folderId=nullで未分類に戻す
    suspend fun moveToFolder(scenarioId: String, folderId: String?)
}