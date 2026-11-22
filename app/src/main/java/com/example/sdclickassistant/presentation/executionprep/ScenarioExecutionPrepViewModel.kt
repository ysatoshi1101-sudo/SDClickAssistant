package com.example.sdclickassistant.presentation.executionprep

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sdclickassistant.data.repository.ScenarioRepository
import com.example.sdclickassistant.domain.model.MacroScenario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScenarioExecutionPrepViewModel @Inject constructor(
    private val repository: ScenarioRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scenarioId: String = checkNotNull(savedStateHandle["scenarioId"])

    private val _scenario = MutableStateFlow<MacroScenario?>(null)
    val scenario: StateFlow<MacroScenario?> = _scenario.asStateFlow()

    init {
        reload()
    }

    /**
     * DBから最新のシナリオを読み直す。
     * 配置待機の画面などで、実行設定が変更されて戻ってきた時に使う。
     */
    fun reload() {
        viewModelScope.launch {
            _scenario.value = repository.getScenarioDetail(scenarioId)
        }
    }

    fun updateRepeatCount(count: Int) {
        updateScenario { it.copy(repeatCount = count.coerceAtLeast(1)) }
    }

    fun updateInfiniteLoop(enabled: Boolean) {
        updateScenario { it.copy(isInfiniteLoop = enabled) }
    }

    fun updateSpeed(speed: Float) {
        updateScenario { it.copy(speed = speed.coerceIn(0.1f, 5.0f)) }
    }


    private fun updateScenario(transform: (MacroScenario) -> MacroScenario) {
        val current = _scenario.value ?: return
        val updated = transform(current)
        _scenario.value = updated
        // 保存するのは実行設定の3項目だけ。ステップは、画面が持つ古いデータで
        // 上書きしないよう、触れない(配置待機で動かしたマーカー位置を守るため)。
        viewModelScope.launch {
            repository.updateExecutionSettings(
                scenarioId = updated.id,
                repeatCount = updated.repeatCount,
                isInfiniteLoop = updated.isInfiniteLoop,
                speed = updated.speed
            )
        }
    }
}