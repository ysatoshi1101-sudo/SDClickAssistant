package com.example.sdclickassistant.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * テンプレート作成の結果。
 *
 * 画像そのもの(Bitmap)は大きいため、ここには含めない。
 * 画像は、TemplateStorageに一時保存し、その鍵(tempKey)だけを渡す。
 *
 * @param tempKey TemplateStorage.saveTemp()で受け取った一時保存の鍵
 * @param templateWidth 切り出したテンプレート画像の幅(px)
 * @param templateHeight 切り出したテンプレート画像の高さ(px)
 * @param screenWidth 撮影した画面の幅(px)
 * @param screenHeight 撮影した画面の高さ(px)
 */
data class TemplateCaptureResult(
    val tempKey: String,
    val templateWidth: Int,
    val templateHeight: Int,
    val screenWidth: Int,
    val screenHeight: Int
)

/**
 * TemplateCaptureController(Accessibility Service側)から
 * Compose画面側へ、テンプレート作成の結果を一度だけ受け渡すためのBus。
 *
 * ColorPickResultBusと同じ考え方: 結果をStateFlowで保持し、
 * 受け取った側がconsume()で消費する。
 */
object TemplateCaptureResultBus {

    private val _result = MutableStateFlow<TemplateCaptureResult?>(null)
    val result: StateFlow<TemplateCaptureResult?> = _result.asStateFlow()

    fun emit(value: TemplateCaptureResult) {
        _result.value = value
    }

    fun consume() {
        _result.value = null
    }
}