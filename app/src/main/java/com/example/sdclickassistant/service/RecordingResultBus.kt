package com.example.sdclickassistant.service

import com.example.sdclickassistant.domain.model.MacroStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecordingResultBus {
    private val _result = MutableStateFlow<List<MacroStep>?>(null)
    val result: StateFlow<List<MacroStep>?> = _result.asStateFlow()

    fun emit(steps: List<MacroStep>) {
        _result.value = steps
    }

    fun consume() {
        _result.value = null
    }
}