package com.example.sdclickassistant.presentation.scenariodetail

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sdclickassistant.data.repository.ScenarioRepository
import com.example.sdclickassistant.data.template.TemplateStorage
import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.ConditionType
import com.example.sdclickassistant.domain.model.MacroScenario
import com.example.sdclickassistant.domain.model.MacroStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScenarioDetailViewModel @Inject constructor(
    private val repository: ScenarioRepository,
    private val templateStorage: TemplateStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val TAG = "ScenarioDetailViewModel"
    }

    private val scenarioId: String = checkNotNull(savedStateHandle["scenarioId"])

    private val _scenario = MutableStateFlow<MacroScenario?>(null)
    val scenario: StateFlow<MacroScenario?> = _scenario.asStateFlow()

    init {
        loadScenario()
    }

    private fun loadScenario() {
        viewModelScope.launch {
            _scenario.value = repository.getScenarioDetail(scenarioId)
        }
    }

    /**
     * ステップを追加する。
     *
     * @param templateTempKey 画像認識(IMAGE)のステップで、新しく作ったテンプレートの
     *        一時保存の鍵。ステップを保存する際に、正式な保存へ移す。
     */
    fun addStep(step: MacroStep, templateTempKey: String? = null) {
        val current = _scenario.value ?: return
        val newStep = step.copy(order = current.steps.size)
        persist(
            current.copy(steps = current.steps + newStep),
            commitStepId = step.id,
            tempKey = resolveTempKey(step, templateTempKey)
        )
    }

    fun addSteps(newSteps: List<MacroStep>) {
        val current = _scenario.value ?: return
        val baseOrder = current.steps.size
        val reordered = newSteps.mapIndexed { index, step -> step.copy(order = baseOrder + index) }
        persist(current.copy(steps = current.steps + reordered))
    }

    /**
     * ステップを更新する。
     *
     * @param templateTempKey テンプレートを作り直した場合の、一時保存の鍵。
     *        作り直していない場合はnull(元のテンプレートがそのまま残る)。
     */
    fun updateStep(step: MacroStep, templateTempKey: String? = null) {
        val current = _scenario.value ?: return
        val previous = current.steps.find { it.id == step.id }
        val updatedSteps = current.steps.map { if (it.id == step.id) step.copy(order = it.order) else it }

        // 画像認識(IMAGE)でなくなったステップの、古いテンプレート画像は不要になるため削除する
        val staleTemplateStepId =
            if (previous != null && previous.isImageCondition() && !step.isImageCondition()) {
                step.id
            } else {
                null
            }

        persist(
            current.copy(steps = updatedSteps),
            commitStepId = step.id,
            tempKey = resolveTempKey(step, templateTempKey),
            deleteTemplateOf = staleTemplateStepId
        )
    }

    fun deleteStep(step: MacroStep) {
        val current = _scenario.value ?: return
        val remaining = current.steps
            .filter { it.id != step.id }
            .sortedBy { it.order }
            .mapIndexed { index, s -> s.copy(order = index) }
        // テンプレート画像は、リポジトリのsaveScenario()が削除する
        persist(current.copy(steps = remaining))
    }

    // ドラッグ&ドロップ後の並び順をまとめて保存する
    fun reorderSteps(newOrder: List<MacroStep>) {
        val current = _scenario.value ?: return
        val reordered = newOrder.mapIndexed { index, step -> step.copy(order = index) }
        persist(current.copy(steps = reordered))
    }

    // ------------------------------------------------------------
    // テンプレート画像(画像認識用)
    // ------------------------------------------------------------

    /** 保存済みのテンプレート画像を読み込む(プレビュー用)。無ければnull。 */
    suspend fun loadTemplate(stepId: String): Bitmap? {
        return templateStorage.load(stepId)
    }

    /** 作成直後(一時保存)のテンプレート画像を読み込む(プレビュー用)。 */
    suspend fun loadTempTemplate(tempKey: String): Bitmap? {
        return templateStorage.loadTemp(tempKey)
    }

    /** 一時保存したテンプレート画像を破棄する。 */
    fun discardTempTemplate(tempKey: String) {
        viewModelScope.launch {
            templateStorage.deleteTemp(tempKey)
        }
    }

    /**
     * 一時保存の鍵を、保存に使ってよいか判断する。
     *
     * 画像認識(IMAGE)のステップ以外に鍵が渡された場合は、
     * 使い道がないため、一時保存した画像を破棄してnullを返す。
     */
    private fun resolveTempKey(step: MacroStep, tempKey: String?): String? {
        if (tempKey == null) return null

        return if (step.isImageCondition()) {
            tempKey
        } else {
            discardTempTemplate(tempKey)
            null
        }
    }

    private fun MacroStep.isImageCondition(): Boolean {
        return actionType == ActionType.CONDITION &&
                ConditionType.fromName(conditionType) == ConditionType.IMAGE
    }

    // ------------------------------------------------------------
    // 保存
    // ------------------------------------------------------------

    /**
     * シナリオを保存する。
     *
     * @param commitStepId tempKeyのテンプレート画像の持ち主となるステップID
     * @param tempKey 正式な保存へ移す、一時保存したテンプレート画像の鍵
     * @param deleteTemplateOf 保存後に、テンプレート画像を削除するステップID
     */
    private fun persist(
        updated: MacroScenario,
        commitStepId: String? = null,
        tempKey: String? = null,
        deleteTemplateOf: String? = null
    ) {
        viewModelScope.launch {

            if (tempKey != null && commitStepId != null) {
                val committed = templateStorage.commitTemp(tempKey, commitStepId)
                if (!committed) {
                    Log.e(TAG, "Failed to commit template: tempKey=$tempKey step=$commitStepId")
                }
            }

            repository.saveScenario(updated)

            deleteTemplateOf?.let { templateStorage.delete(it) }

            _scenario.value = updated
        }
    }
}