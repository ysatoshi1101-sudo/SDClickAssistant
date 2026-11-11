package com.example.sdclickassistant.service

import android.content.Intent

/**
 * MediaProjectionのユーザー許可結果を、
 * Activity/Compose画面からScreenshotCaptureServiceへ
 * 一時的に受け渡すためのHolder。
 *
 * MediaProjectionのresultDataは再利用できないため、
 * Serviceが取得した時点でconsume()により消費する。
 */
object MediaProjectionTokenHolder {

    private var resultCode: Int? = null
    private var data: Intent? = null

    /**
     * MediaProjectionの許可結果を保存する。
     */
    fun store(
        resultCode: Int,
        data: Intent
    ) {
        this.resultCode = resultCode
        this.data = data
    }

    /**
     * Tokenが存在するか確認する。
     */
    fun hasToken(): Boolean {
        return resultCode != null && data != null
    }

    /**
     * Tokenを一度だけ取得する。
     *
     * 取得と同時にHolderから削除する。
     *
     * 同じMediaProjectionのresultDataを
     * 再利用しないための重要な処理。
     */
    fun consume(): Pair<Int, Intent>? {

        val currentResultCode = resultCode
        val currentData = data

        if (currentResultCode == null || currentData == null) {
            return null
        }

        clear()

        return Pair(
            currentResultCode,
            currentData
        )
    }

    /**
     * 保持しているTokenを破棄する。
     */
    fun clear() {
        resultCode = null
        data = null
    }
}