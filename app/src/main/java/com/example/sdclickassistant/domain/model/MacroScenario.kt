package com.example.sdclickassistant.domain.model

data class MacroScenario(
    val id: String,
    val name: String,
    val repeatCount: Int = 1,
    val isInfiniteLoop: Boolean = false,
    val speed: Float = 1.0f,
    val createdDate: Long = System.currentTimeMillis(),
    val updatedDate: Long = System.currentTimeMillis(),
    val steps: List<MacroStep> = emptyList(),
    val isFavorite: Boolean = false,
    // シナリオ一覧でのドラッグ&ドロップ並び順
    val order: Int = 0,
    // 所属フォルダ。nullの場合は未分類(フォルダなし)
    val folderId: String? = null
)