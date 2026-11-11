package com.example.sdclickassistant.data.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 画像認識(IMAGE)のテンプレート画像を、アプリ内部のファイルとして管理する。
 *
 * 保存先: filesDir/templates/<ステップID>.png (PNG・品質100)
 * 一時保存: filesDir/templates/tmp/<一時キー>.png
 *
 * 一時保存は、次の2つの場合のために用意している。
 *  - ステップの新規作成中で、まだステップIDが決まっていない
 *  - 既存ステップのテンプレートを撮り直して、「キャンセル」した時に
 *    元の画像を失わないようにする
 *
 * ファイル入出力を行うため、suspend関数はメインスレッドから呼んでよい
 * (内部でDispatchers.IOに切り替える)。
 *
 * Bitmapのrecycleは呼び出し側の責任。
 * 特に、load()・loadTemp()が返したBitmapは、使い終わったらrecycleすること
 * (画面のプレビューに使っている間はrecycleしないこと)。
 */
@Singleton
class TemplateStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "TemplateStorage"
        private const val DIR_NAME = "templates"
        private const val TEMP_DIR_NAME = "tmp"
        private const val EXTENSION = "png"
    }

    // ------------------------------------------------------------
    // ファイルの場所
    // ------------------------------------------------------------

    private fun templateDir(): File {
        return File(context.filesDir, DIR_NAME).also { it.mkdirs() }
    }

    private fun tempDir(): File {
        return File(templateDir(), TEMP_DIR_NAME).also { it.mkdirs() }
    }

    private fun templateFile(stepId: String): File {
        requireSafeName(stepId)
        return File(templateDir(), "$stepId.$EXTENSION")
    }

    private fun tempFile(tempKey: String): File {
        requireSafeName(tempKey)
        return File(tempDir(), "$tempKey.$EXTENSION")
    }

    /**
     * ファイル名に使うIDが、フォルダの外を指さないことを確認する。
     * ステップIDはUUIDのため通常は問題ないが、念のための検査。
     */
    private fun requireSafeName(name: String) {
        require(
            name.isNotBlank() &&
                    '/' !in name &&
                    '\\' !in name &&
                    ".." !in name
        ) {
            "Invalid file name: $name"
        }
    }

    // ------------------------------------------------------------
    // 正式な保存(ステップIDに紐づく)
    // ------------------------------------------------------------

    /** テンプレート画像を保存する。既にある場合は上書きする。 */
    suspend fun save(stepId: String, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            writePng(bitmap, templateFile(stepId))
        }
    }

    /**
     * テンプレート画像を読み込む。ファイルが無い・読めない場合はnull。
     * 返したBitmapは、呼び出し側が使い終わったらrecycleすること。
     */
    suspend fun load(stepId: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            val file = templateFile(stepId)
            if (!file.exists()) {
                null
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
        }
    }

    /** テンプレート画像のファイルがあるか。 */
    fun exists(stepId: String): Boolean {
        return templateFile(stepId).exists()
    }

    /** テンプレート画像を削除する。ファイルが無ければ何もしない。 */
    suspend fun delete(stepId: String) {
        withContext(Dispatchers.IO) {
            val file = templateFile(stepId)
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete template: $stepId")
            }
        }
    }

    /**
     * テンプレート画像を、別のステップIDへ複製する(シナリオのコピー用)。
     * 複製元が無い・複製に失敗した場合はfalse。
     */
    suspend fun copy(fromStepId: String, toStepId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val source = templateFile(fromStepId)
            if (!source.exists()) {
                false
            } else {
                try {
                    source.copyTo(templateFile(toStepId), overwrite = true)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy template: $fromStepId -> $toStepId", e)
                    false
                }
            }
        }
    }

    // ------------------------------------------------------------
    // 一時保存(ステップIDが未確定の間)
    // ------------------------------------------------------------

    /**
     * テンプレート画像を一時保存する。
     * @return 一時キー。失敗した場合はnull。
     */
    suspend fun saveTemp(bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            val tempKey = UUID.randomUUID().toString()
            if (writePng(bitmap, tempFile(tempKey))) tempKey else null
        }
    }

    /**
     * 一時保存した画像を読み込む(プレビュー表示用)。
     * ファイルが無い・読めない場合はnull。
     */
    suspend fun loadTemp(tempKey: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            val file = tempFile(tempKey)
            if (!file.exists()) {
                null
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
        }
    }

    /**
     * 一時保存した画像を、ステップIDに紐づく正式な保存へ移す。
     * 同じステップIDの画像が既にあれば、置き換える。
     */
    suspend fun commitTemp(tempKey: String, stepId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val source = tempFile(tempKey)
            if (!source.exists()) {
                false
            } else {
                moveFile(source, templateFile(stepId))
            }
        }
    }

    /** 一時保存した画像を破棄する。 */
    suspend fun deleteTemp(tempKey: String) {
        withContext(Dispatchers.IO) {
            val file = tempFile(tempKey)
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete temp template: $tempKey")
            }
        }
    }

    // ------------------------------------------------------------
    // ファイル操作の共通処理
    // ------------------------------------------------------------

    /**
     * BitmapをPNG(品質100)で保存する。
     *
     * 書きかけのファイルが残らないよう、いったん別名(.part)に書き込み、
     * 完了してから正式な名前に変更する。
     */
    private fun writePng(bitmap: Bitmap, target: File): Boolean {
        val partFile = File(target.parentFile, "${target.name}.part")

        try {
            val compressed = partFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            if (!compressed) {
                Log.e(TAG, "Bitmap.compress() failed: ${target.name}")
                partFile.delete()
                return false
            }

            return moveFile(partFile, target)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write PNG: ${target.name}", e)
            partFile.delete()
            return false
        }
    }

    /**
     * ファイルを移動する。移動先に同名のファイルがあれば置き換える。
     */
    private fun moveFile(from: File, to: File): Boolean {
        // 同じ端末内のフォルダ間であれば、通常はこれで置き換え移動ができる
        if (from.renameTo(to)) {
            return true
        }

        // renameToに失敗した場合は、コピーしてから元を削除する
        return try {
            from.copyTo(to, overwrite = true)
            from.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move file: ${from.name} -> ${to.name}", e)
            false
        }
    }
}