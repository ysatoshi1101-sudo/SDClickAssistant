package com.example.sdclickassistant.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedTouch(
    val x: Int,
    val y: Int,
    val endX: Int? = null,
    val endY: Int? = null
)

object CaptureResultBus {
    private val _result = MutableStateFlow<CapturedTouch?>(null)
    val result: StateFlow<CapturedTouch?> = _result.asStateFlow()

    fun emit(touch: CapturedTouch) {
        _result.value = touch
    }

    fun consume() {
        _result.value = null
    }
}