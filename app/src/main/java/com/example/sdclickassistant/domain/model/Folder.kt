package com.example.sdclickassistant.domain.model

/**
 * シナリオを整理するためのフォルダ。
 *
 * V1.0では単純な1階層のみ対応する
 * (フォルダの中にさらにフォルダは作れない)。
 */
data class Folder(
    val id: String,
    val name: String,
    val order: Int = 0,
    val createdDate: Long = System.currentTimeMillis()
)