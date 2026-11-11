package com.example.sdclickassistant.presentation.executionprep

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.MacroStep
import com.example.sdclickassistant.domain.model.requiresScreenCapture
import com.example.sdclickassistant.service.MacroAccessibilityService
import com.example.sdclickassistant.service.MediaProjectionTokenHolder
import com.example.sdclickassistant.service.ScreenshotCaptureService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioExecutionPrepScreen(
    scenarioId: String,
    onBackClick: () -> Unit,
    viewModel: ScenarioExecutionPrepViewModel = hiltViewModel()
) {
    val scenario by viewModel.scenario.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasOverlayPermission by remember {
        mutableStateOf(
            Settings.canDrawOverlays(context)
        )
    }

    var hasScreenCapturePermission by remember {
        mutableStateOf(
            MediaProjectionTokenHolder.hasToken()
        )
    }

    /*
     * MediaProjectionの許可ダイアログ。
     */
    val screenCaptureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode == Activity.RESULT_OK &&
                result.data != null
            ) {
                /*
                 * MediaProjectionの結果を保存。
                 */
                MediaProjectionTokenHolder.store(
                    result.resultCode,
                    result.data!!
                )

                /*
                 * ScreenshotCaptureServiceを起動。
                 */
                try {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(
                            context,
                            ScreenshotCaptureService::class.java
                        )
                    )

                    hasScreenCapturePermission = true

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        scenario?.name ?: "読み込み中..."
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        },
        bottomBar = {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {

                /*
                 * オーバーレイ権限
                 */
                if (!hasOverlayPermission) {

                    Text(
                        text =
                            "実行にはオーバーレイ表示の権限が必要です",
                        style =
                            MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier.padding(bottom = 8.dp)
                    )
                }

                /*
                 * Accessibility Service
                 */
                if (MacroAccessibilityService.instance == null) {

                    Text(
                        text =
                            "実行にはユーザー補助（アクセシビリティ）でSD Click Assistantを有効にする必要があります",
                        style =
                            MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier.padding(bottom = 8.dp)
                    )
                }

                /*
                 * 画面キャプチャが必要なステップ
                 * (スクリーンショット・色判定・画像認識)があるか確認。
                 * 判定は共通関数(requiresScreenCapture)に任せる。
                 */
                val needsScreenCapture =
                    scenario
                        ?.steps
                        ?.requiresScreenCapture() == true

                /*
                 * MediaProjection許可が必要な場合。
                 */
                if (
                    needsScreenCapture &&
                    !hasScreenCapturePermission
                ) {

                    Text(
                        text =
                            "スクリーンショット・色判定・画像認識の実行には画面キャプチャの許可が必要です",
                        style =
                            MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier.padding(bottom = 8.dp)
                    )

                    Button(
                        onClick = {

                            val manager =
                                context.getSystemService(
                                    MediaProjectionManager::class.java
                                )

                            if (manager != null) {

                                val captureIntent =
                                    manager.createScreenCaptureIntent()

                                screenCaptureLauncher.launch(
                                    captureIntent
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text("画面キャプチャを許可する")
                    }
                }

                /*
                 * 配置開始ボタン。
                 */
                Button(
                    onClick = {

                        /*
                         * オーバーレイ権限がない場合。
                         */
                        if (!Settings.canDrawOverlays(context)) {

                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse(
                                        "package:${context.packageName}"
                                    )
                                )
                            )

                            return@Button
                        }

                        /*
                         * 最新状態を再確認。
                         */
                        hasOverlayPermission = true

                        /*
                         * Accessibility Serviceを取得。
                         */
                        val service =
                            MacroAccessibilityService.instance

                        if (service == null) {

                            context.startActivity(
                                Intent(
                                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                                )
                            )

                            return@Button
                        }

                        /*
                         * 配置モード開始。
                         */
                        service.showPlacementButton(
                            scenarioId
                        )

                        /*
                         * アプリをバックグラウンドへ。
                         */
                        (context as? Activity)
                            ?.moveTaskToBack(true)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        if (hasOverlayPermission) {
                            "配置する（対象アプリに移動します）"
                        } else {
                            "オーバーレイ権限を許可する"
                        }
                    )
                }
            }
        }
    ) { innerPadding ->

        val currentScenario = scenario

        if (currentScenario == null) {
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding =
                PaddingValues(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            /*
             * 実行設定
             */
            item {

                ExecutionSettingsCard(
                    repeatCount =
                        currentScenario.repeatCount,
                    isInfiniteLoop =
                        currentScenario.isInfiniteLoop,
                    speed =
                        currentScenario.speed,
                    onRepeatCountChange =
                        viewModel::updateRepeatCount,
                    onInfiniteLoopChange =
                        viewModel::updateInfiniteLoop,
                    onSpeedChange =
                        viewModel::updateSpeed
                )
            }

            /*
             * ステップ数
             */
            item {

                Text(
                    text =
                        "ステップ一覧（${currentScenario.steps.size}件）",
                    style =
                        MaterialTheme.typography.titleMedium
                )
            }

            /*
             * ステップ一覧
             */
            items(
                currentScenario.steps
                    .sortedBy { it.order }
            ) { step ->

                StepSummaryCard(step)
            }
        }
    }
}


/**
 * 実行設定カード
 */
@Composable
private fun ExecutionSettingsCard(
    repeatCount: Int,
    isInfiniteLoop: Boolean,
    speed: Float,
    onRepeatCountChange: (Int) -> Unit,
    onInfiniteLoopChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "実行設定",
                style =
                    MaterialTheme.typography.titleMedium
            )

            /*
             * 無限ループ
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text("無限ループ")

                Switch(
                    checked = isInfiniteLoop,
                    onCheckedChange =
                        onInfiniteLoopChange
                )
            }

            /*
             * 繰り返し回数
             *
             * 無限ループOFFの時のみ入力欄を表示する。
             * ONの時は無限ループが優先されるため非表示。
             */
            if (!isInfiniteLoop) {

                var repeatCountText by remember(repeatCount) {
                    mutableStateOf(repeatCount.toString())
                }

                OutlinedTextField(
                    value = repeatCountText,
                    onValueChange = { input ->

                        repeatCountText = input

                        input.toIntOrNull()?.let { count ->
                            onRepeatCountChange(count)
                        }
                    },
                    label = { Text("繰り返し回数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            Text(
                text =
                    "実行速度: ${String.format("%.1f", speed)}倍",
                modifier =
                    Modifier.padding(top = 16.dp)
            )

            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = 0.1f..5.0f
            )
        }
    }
}


/**
 * ステップ概要カード
 */
@Composable
private fun StepSummaryCard(
    step: MacroStep
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Text(
                text =
                    actionTypeLabel(
                        step.actionType
                    ),
                style =
                    MaterialTheme.typography.titleSmall
            )
        }
    }
}


/**
 * ActionTypeを画面表示用文字列へ変換。
 */
private fun actionTypeLabel(
    type: ActionType
): String {

    return when (type) {

        ActionType.TAP ->
            "タップ"

        ActionType.LONG_PRESS ->
            "長押し"

        ActionType.SWIPE ->
            "スワイプ"

        ActionType.WAIT ->
            "待機"

        ActionType.SCREENSHOT ->
            "スクリーンショット"

        ActionType.CONDITION ->
            "条件分岐"

        ActionType.LABEL ->
            "ラベル"

        ActionType.JUMP ->
            "ジャンプ"
    }
}