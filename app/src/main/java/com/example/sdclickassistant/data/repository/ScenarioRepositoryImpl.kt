package com.example.sdclickassistant.data.repository

import com.example.sdclickassistant.data.local.dao.ScenarioDao
import com.example.sdclickassistant.data.local.dao.StepDao
import com.example.sdclickassistant.data.mapper.toDomain
import com.example.sdclickassistant.data.mapper.toEntity
import com.example.sdclickassistant.data.template.TemplateStorage
import com.example.sdclickassistant.domain.model.MacroScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class ScenarioRepositoryImpl @Inject constructor(
    private val scenarioDao: ScenarioDao,
    private val stepDao: StepDao,
    private val templateStorage: TemplateStorage
) : ScenarioRepository {

    override fun getAllScenarios(): Flow<List<MacroScenario>> {
        return scenarioDao.getAllScenarios().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getScenarioDetail(scenarioId: String): MacroScenario? {
        return scenarioDao.getScenarioWithSteps(scenarioId)?.toDomain()
    }

    override suspend fun saveScenario(scenario: MacroScenario) {
        // 保存前にDBに残っているステップのIDを控えておく。
        // 今回の保存で無くなったステップ(=削除されたステップ)の
        // テンプレート画像を、後で掃除するため。
        val previousStepIds = stepDao
            .getStepsForScenario(scenario.id)
            .map { it.id }

        scenarioDao.insert(scenario.toEntity())
        stepDao.deleteAllForScenario(scenario.id)
        stepDao.insertAll(scenario.steps.map { it.toEntity(scenario.id) })

        val currentStepIds = scenario.steps.map { it.id }.toSet()
        previousStepIds
            .filter { it !in currentStepIds }
            .forEach { templateStorage.delete(it) }
    }

    override suspend fun deleteScenario(scenario: MacroScenario) {
        // シナリオを消すとステップもDBから消えるため、
        // 先にテンプレート画像の持ち主(ステップID)を控えておく。
        val stepIds = stepDao
            .getStepsForScenario(scenario.id)
            .map { it.id }

        scenarioDao.delete(scenario.toEntity())

        stepIds.forEach { templateStorage.delete(it) }
    }

    override suspend fun renameScenario(scenarioId: String, newName: String) {
        val current = getScenarioDetail(scenarioId) ?: return

        saveScenario(
            current.copy(
                name = newName,
                updatedDate = System.currentTimeMillis()
            )
        )
    }

    override suspend fun copyScenario(scenario: MacroScenario) {
        val maxOrder = scenarioDao.getMaxOrder() ?: -1

        // 複製元は、DBの最新の状態(ステップ込み)から読み直す。
        // 読めなかった場合のみ、渡されたシナリオをそのまま使う。
        val source = getScenarioDetail(scenario.id) ?: scenario

        // ステップIDも複製元と重複しないよう新しく振り直す。
        // テンプレート画像を新しいIDへ複製するため、対応表を作っておく。
        val newStepIds = source.steps.associate { step ->
            step.id to UUID.randomUUID().toString()
        }

        val copiedSteps = source.steps.map { step ->
            step.copy(id = newStepIds.getValue(step.id))
        }

        val copiedScenario = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name}（コピー）",
            order = maxOrder + 1,
            createdDate = System.currentTimeMillis(),
            updatedDate = System.currentTimeMillis(),
            steps = copiedSteps
        )

        // テンプレート画像を、新しいステップIDへ複製する。
        // 画像を持たないステップは、複製元が無いため何も起きない。
        newStepIds.forEach { (oldId, newId) ->
            templateStorage.copy(oldId, newId)
        }

        try {
            saveScenario(copiedScenario)
        } catch (e: Exception) {
            // 保存に失敗した場合は、複製した画像が残らないように片付ける。
            newStepIds.values.forEach { templateStorage.delete(it) }
            throw e
        }
    }

    override suspend fun setFavorite(scenarioId: String, isFavorite: Boolean) {
        val current = getScenarioDetail(scenarioId) ?: return

        saveScenario(
            current.copy(isFavorite = isFavorite)
        )
    }

    override suspend fun reorderScenarios(scenarios: List<MacroScenario>) {
        // リストの並び順(index)をそのままorder値として保存する
        val updated = scenarios.mapIndexed { index, scenario ->
            scenario.copy(order = index).toEntity()
        }

        scenarioDao.updateAll(updated)
    }

    override suspend fun moveToFolder(scenarioId: String, folderId: String?) {
        val current = getScenarioDetail(scenarioId) ?: return

        saveScenario(
            current.copy(folderId = folderId)
        )
    }
}