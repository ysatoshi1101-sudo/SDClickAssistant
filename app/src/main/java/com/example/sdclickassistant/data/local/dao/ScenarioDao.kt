package com.example.sdclickassistant.data.local.dao

import androidx.room.*
import com.example.sdclickassistant.data.local.entity.ScenarioEntity
import com.example.sdclickassistant.data.local.relation.ScenarioWithSteps
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scenario: ScenarioEntity)

    @Update
    suspend fun update(scenario: ScenarioEntity)

    // ドラッグ&ドロップ並び替え後の一括更新用。
    // Roomはリストを引数に取るUpdateを自動的にトランザクション内で実行する。
    @Update
    suspend fun updateAll(scenarios: List<ScenarioEntity>)

    @Delete
    suspend fun delete(scenario: ScenarioEntity)

    // 一覧表示用：ユーザーが並び替えた順(order)で取得する
    @Query("SELECT * FROM scenarios ORDER BY `order` ASC")
    fun getAllScenarios(): Flow<List<ScenarioEntity>>

    // 詳細表示用：シナリオ本体＋ステップ一覧をまとめて取得
    @Transaction
    @Query("SELECT * FROM scenarios WHERE id = :scenarioId")
    suspend fun getScenarioWithSteps(scenarioId: String): ScenarioWithSteps?

    // 新規シナリオ・コピーしたシナリオを一番最後に追加するための
    // 現在の最大order取得
    @Query("SELECT MAX(`order`) FROM scenarios")
    suspend fun getMaxOrder(): Int?
}