package com.example.sdclickassistant.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * スポイトで取得した座標と色。
 */
data class ColorPickResult(
    val x: Int,
    val y: Int,
    val r: Int,
    val g: Int,
    val b: Int
)

/**
 * ColorPickerController(Accessibility Service側)から
 * Compose画面側へ、スポイトの結果を一度だけ受け渡すためのBus。
 *
 * CaptureResultBusと同じ考え方: 結果をStateFlowで保持し、
 * 受け取った側がconsume()で消費する。
 */
object ColorPickResultBus {

    private val _result = MutableStateFlow<ColorPickResult?>(null)
    val result: StateFlow<ColorPickResult?> = _result.asStateFlow()

    fun emit(value: ColorPickResult) {
        _result.value = value
    }

    fun consume() {
        _result.value = null
    }
}