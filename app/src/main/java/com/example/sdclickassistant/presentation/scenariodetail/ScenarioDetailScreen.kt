package com.example.sdclickassistant.presentation.scenariodetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.ConditionType
import com.example.sdclickassistant.domain.model.DEFAULT_IMAGE_MATCH_THRESHOLD
import com.example.sdclickassistant.domain.model.MAX_IMAGE_MATCH_THRESHOLD
import com.example.sdclickassistant.domain.model.MIN_IMAGE_MATCH_THRESHOLD
import com.example.sdclickassistant.domain.model.MacroStep
import java.util.Locale
import java.util.UUID
import androidx.compose.material.icons.filled.PlayArrow
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.example.sdclickassistant.service.CaptureMode
import com.example.sdclickassistant.service.CaptureResultBus
import com.example.sdclickassistant.service.MacroAccessibilityService
import androidx.compose.material3.Button
import com.example.sdclickassistant.service.RecordingResultBus
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.sdclickassistant.service.ColorPickResultBus
import com.example.sdclickassistant.service.MediaProjectionTokenHolder
import com.example.sdclickassistant.service.ScreenshotCaptureService
import com.example.sdclickassistant.service.TemplateCaptureResultBus

/** 画像認識(IMAGE)の「見つかるまで待つ」に設定できる最大の秒数 */
private const val MAX_IMAGE_WAIT_TIMEOUT_SEC = 600

/**
 * 画面キャプチャを使う機能の種類。
 * MediaProjectionの許可ダイアログから戻った後に、どちらを起動するか覚えておくために使う。
 */
private enum class CaptureTarget {
    COLOR,
    TEMPLATE
}

/**
 * 画像認識(IMAGE)のテンプレート画像を扱う操作の一式。
 * ステップ編集ダイアログからViewModelの機能を呼ぶために、まとめて渡す。
 */
private class TemplateOps(
    // 保存済みのテンプレート画像を読み込む(無ければnull)
    val loadSaved: suspend (stepId: String) -> Bitmap?,
    // 作成直後(一時保存)のテンプレート画像を読み込む
    val loadTemp: suspend (tempKey: String) -> Bitmap?,
    // 一時保存したテンプレート画像を破棄する
    val discardTemp: (tempKey: String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioDetailScreen(
    scenarioId: String,
    onBackClick: () -> Unit,
    onExecuteClick: () -> Unit,
    viewModel: ScenarioDetailViewModel = hiltViewModel()
) {
    val scenario by viewModel.scenario.collectAsStateWithLifecycle()
    var showAddStepDialog by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<MacroStep?>(null) }
    var stepToDelete by remember { mutableStateOf<MacroStep?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val templateOps = remember(viewModel) {
        TemplateOps(
            loadSaved = { stepId -> viewModel.loadTemplate(stepId) },
            loadTemp = { tempKey -> viewModel.loadTempTemplate(tempKey) },
            discardTemp = { tempKey -> viewModel.discardTempTemplate(tempKey) }
        )
    }

    LaunchedEffect(Unit) {
        RecordingResultBus.result.collect { recorded ->
            if (recorded != null && recorded.isNotEmpty()) {
                viewModel.addSteps(recorded)
                RecordingResultBus.consume()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(scenario?.name ?: "読み込み中...") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    Button(
                        onClick = onExecuteClick,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("実行")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingStep = null
                showAddStepDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "ステップ追加")
            }
        }
    ) { innerPadding ->
        val currentScenario = scenario

        when {
            currentScenario == null -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            )

            currentScenario.steps.isEmpty() -> EmptyStepList(
                onRecordClick = {
                    startRecording(context) {
                        val service = com.example.sdclickassistant.service.MacroAccessibilityService.instance
                        service?.startSequenceRecording()
                        (context as? Activity)?.moveTaskToBack(true)
                    }
                },
                modifier = Modifier.padding(innerPadding)
            )

            else -> StepList(
                steps = currentScenario.steps.sortedBy { it.order },
                onStepClick = { step ->
                    editingStep = step
                    showAddStepDialog = true
                },
                onDeleteClick = { stepToDelete = it },
                onReorder = { viewModel.reorderSteps(it) },
                onRecordClick = {
                    val service = com.example.sdclickassistant.service.MacroAccessibilityService.instance
                    if (service == null) {
                        context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        return@StepList
                    }
                    if (!android.provider.Settings.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                        return@StepList
                    }
                    service.startSequenceRecording()
                    (context as? Activity)?.moveTaskToBack(true)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showAddStepDialog) {
        AddStepDialog(
            editingStep = editingStep,
            availableLabels = scenario?.steps
                ?.filter { it.actionType == ActionType.LABEL }
                ?.mapNotNull { it.targetLabel }
                ?.distinct()
                .orEmpty(),
            templateOps = templateOps,
            onConfirm = { step, templateTempKey ->
                if (editingStep == null) {
                    viewModel.addStep(step, templateTempKey)
                } else {
                    viewModel.updateStep(step, templateTempKey)
                }
                showAddStepDialog = false
                editingStep = null
            },
            onDismiss = {
                showAddStepDialog = false
                editingStep = null
            }
        )
    }

    stepToDelete?.let { step ->
        AlertDialog(
            onDismissRequest = { stepToDelete = null },
            title = { Text("ステップを削除") },
            text = { Text("「${actionTypeLabel(step.actionType)}」のステップを削除します。この操作は取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStep(step)
                    stepToDelete = null
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { stepToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

private val supportedActionTypes = listOf(
    ActionType.TAP,
    ActionType.LONG_PRESS,
    ActionType.SWIPE,
    ActionType.WAIT,
    ActionType.SCREENSHOT,
    ActionType.LABEL,
    ActionType.JUMP,
    ActionType.CONDITION
)

private fun actionTypeLabel(type: ActionType): String = when (type) {
    ActionType.TAP -> "タップ"
    ActionType.LONG_PRESS -> "長押し"
    ActionType.SWIPE -> "スワイプ"
    ActionType.WAIT -> "待機"
    ActionType.SCREENSHOT -> "スクリーンショット"
    ActionType.CONDITION -> "条件分岐"
    ActionType.LABEL -> "ラベル"
    ActionType.JUMP -> "ジャンプ"
}

/** 一致度のしきい値を、画面表示用の文字列(小数点以下2桁)にする。 */
private fun formatThreshold(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

/**
 * 入力されたしきい値の文字列を数値にする。
 * 「0,9」のようなカンマ表記も受け付ける。数値にできない場合はnull。
 */
private fun parseThreshold(text: String): Float? {
    return text.trim().replace(',', '.').toFloatOrNull()
}

@Composable
private fun AddStepDialog(
    editingStep: MacroStep?,
    availableLabels: List<String>,
    templateOps: TemplateOps,
    onConfirm: (MacroStep, String?) -> Unit,
    onDismiss: () -> Unit
) {
    // 編集時はアクション種別が確定しているため、選択ステップを飛ばして直接入力ダイアログへ
    var selectedType by remember { mutableStateOf(editingStep?.actionType) }

    if (selectedType == null) {
        ActionTypeSelectionDialog(
            onSelect = { selectedType = it },
            onDismiss = onDismiss
        )
    } else {
        StepInputDialog(
            actionType = selectedType!!,
            editingStep = editingStep,
            availableLabels = availableLabels,
            templateOps = templateOps,
            onBack = if (editingStep == null) {
                { selectedType = null }
            } else null,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ActionTypeSelectionDialog(
    onSelect: (ActionType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("アクションを選択") },
        text = {
            Column {
                supportedActionTypes.forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = false, onClick = { onSelect(type) })
                        Text(
                            text = actionTypeLabel(type),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun StepInputDialog(
    actionType: ActionType,
    editingStep: MacroStep?,
    availableLabels: List<String>,
    templateOps: TemplateOps,
    onBack: (() -> Unit)?,
    onConfirm: (MacroStep, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var x by remember { mutableStateOf(editingStep?.x?.toString() ?: "") }
    var y by remember { mutableStateOf(editingStep?.y?.toString() ?: "") }
    var endX by remember { mutableStateOf(editingStep?.endX?.toString() ?: "") }
    var endY by remember { mutableStateOf(editingStep?.endY?.toString() ?: "") }
    var duration by remember { mutableStateOf(editingStep?.duration?.toString() ?: "") }
    var waitTime by remember { mutableStateOf(editingStep?.waitTime?.toString() ?: "") }

    // LABEL: 自分自身のラベル名
    var labelName by remember { mutableStateOf(editingStep?.targetLabel ?: "") }

    // JUMP/CONDITION: ジャンプ先ラベル名（既存ラベルから選択）
    var selectedLabel by remember { mutableStateOf(editingStep?.targetLabel) }

    // CONDITION: ループ回数（空欄なら無限ループ）
    var loopCountText by remember { mutableStateOf(editingStep?.loopCount?.toString() ?: "") }

    // CONDITION: 判定方式
    var conditionType by remember {
        mutableStateOf(ConditionType.fromName(editingStep?.conditionType))
    }

    // CONDITION(COLOR): 判定したい色(0〜255)と許容誤差
    var colorRText by remember { mutableStateOf(editingStep?.colorR?.toString() ?: "") }
    var colorGText by remember { mutableStateOf(editingStep?.colorG?.toString() ?: "") }
    var colorBText by remember { mutableStateOf(editingStep?.colorB?.toString() ?: "") }
    var colorToleranceText by remember { mutableStateOf(editingStep?.colorTolerance?.toString() ?: "") }
    var invertCondition by remember { mutableStateOf(editingStep?.invertCondition ?: false) }

    // CONDITION(IMAGE): 一致度のしきい値（空欄なら既定値）
    var imageThresholdText by remember {
        mutableStateOf(editingStep?.imageMatchThreshold?.let { formatThreshold(it) } ?: "")
    }

    // CONDITION(IMAGE): 見つかるまで待つ最大秒数（空欄なら待たず、1回だけ判定）
    var imageWaitTimeoutText by remember {
        mutableStateOf(editingStep?.imageWaitTimeoutSec?.toString() ?: "")
    }

    // CONDITION(IMAGE): 見つかった時、その位置をタップするか
    var tapOnImageFound by remember { mutableStateOf(editingStep?.tapOnImageFound ?: false) }

    // CONDITION(IMAGE): テンプレート作成時の画面サイズ
    var templateScreenWidth by remember { mutableStateOf(editingStep?.templateScreenWidth) }
    var templateScreenHeight by remember { mutableStateOf(editingStep?.templateScreenHeight) }

    // CONDITION(IMAGE): このダイアログで新しく作った(まだ正式保存していない)テンプレートの一時キー
    var templateTempKey by remember { mutableStateOf<String?>(null) }

    // CONDITION(IMAGE): プレビュー表示するテンプレート画像
    // (画面に表示している間は使われ続けるため、recycleしない)
    var templatePreview by remember { mutableStateOf<Bitmap?>(null) }

    // 「追加」「保存」が押され、一時保存したテンプレートの扱いをViewModelに任せたかどうか
    var tempCommitted by remember { mutableStateOf(false) }

    // 画面キャプチャの許可ダイアログから戻った後に、どの機能を起動するか
    var permissionTarget by remember { mutableStateOf(CaptureTarget.COLOR) }

    // 編集時は、保存済みのテンプレート画像を読み込んでプレビューに表示する
    LaunchedEffect(Unit) {
        val step = editingStep
        if (
            step != null &&
            step.actionType == ActionType.CONDITION &&
            ConditionType.fromName(step.conditionType) == ConditionType.IMAGE
        ) {
            val saved = templateOps.loadSaved(step.id)
            // 読み込み中に新しいテンプレートが作られていた場合は、そちらを優先する
            if (templateTempKey == null) {
                templatePreview = saved
            }
        }
    }

    // ダイアログが閉じられた時、確定されなかった一時保存のテンプレートを破棄する
    // (「キャンセル」「戻る」で閉じた場合など)
    DisposableEffect(Unit) {
        onDispose {
            val key = templateTempKey
            if (!tempCommitted && key != null) {
                templateOps.discardTemp(key)
            }
        }
    }

    // 座標取得結果を監視し、届いたらフィールドへ反映する
    LaunchedEffect(Unit) {
        CaptureResultBus.result.collect { captured ->
            if (captured != null) {
                x = captured.x.toString()
                y = captured.y.toString()
                captured.endX?.let { endX = it.toString() }
                captured.endY?.let { endY = it.toString() }
                CaptureResultBus.consume()
            }
        }
    }

    // スポイトの結果を監視し、届いたら座標・RGBへ反映する
    LaunchedEffect(Unit) {
        ColorPickResultBus.result.collect { picked ->
            if (picked != null) {
                x = picked.x.toString()
                y = picked.y.toString()
                colorRText = picked.r.toString()
                colorGText = picked.g.toString()
                colorBText = picked.b.toString()
                ColorPickResultBus.consume()
            }
        }
    }

    // テンプレート作成の結果を監視し、届いたらプレビューへ反映する
    LaunchedEffect(Unit) {
        TemplateCaptureResultBus.result.collect { captured ->
            if (captured != null) {
                // 作り直した場合は、前に作った(確定前の)テンプレートを破棄する
                val previousKey = templateTempKey
                if (previousKey != null) {
                    templateOps.discardTemp(previousKey)
                }

                val preview = templateOps.loadTemp(captured.tempKey)

                templateTempKey = captured.tempKey
                templateScreenWidth = captured.screenWidth
                templateScreenHeight = captured.screenHeight
                templatePreview = preview

                TemplateCaptureResultBus.consume()
            }
        }
    }

    // MediaProjectionの許可ダイアログ(未許可の場合のみ表示される)
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionTokenHolder.store(result.resultCode, result.data!!)
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScreenshotCaptureService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            MacroAccessibilityService.instance?.let { service ->
                when (permissionTarget) {
                    CaptureTarget.COLOR -> service.startColorPicker()
                    CaptureTarget.TEMPLATE -> service.startTemplateCapture()
                }
                (context as? Activity)?.moveTaskToBack(true)
            }
        }
    }

    // 画面キャプチャを使う機能の開始処理。
    // アクセシビリティ→オーバーレイ→MediaProjectionの順に確認する。
    val startCaptureFeature: (CaptureTarget) -> Unit = { target ->
        val service = MacroAccessibilityService.instance
        if (service == null) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else if (!Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        } else if (
            ScreenshotCaptureService.instance == null &&
            !MediaProjectionTokenHolder.hasToken()
        ) {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            if (manager != null) {
                // 許可ダイアログから戻った後に起動する機能を覚えておく
                permissionTarget = target
                screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
            }
        } else {
            when (target) {
                CaptureTarget.COLOR -> service.startColorPicker()
                CaptureTarget.TEMPLATE -> service.startTemplateCapture()
            }
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    // 色のスポイト開始
    val startColorPicker: () -> Unit = { startCaptureFeature(CaptureTarget.COLOR) }

    // テンプレート作成開始
    val startTemplateCapture: () -> Unit = { startCaptureFeature(CaptureTarget.TEMPLATE) }

    val isImageCondition =
        actionType == ActionType.CONDITION && conditionType == ConditionType.IMAGE

    val isValid = when (actionType) {
        ActionType.TAP -> x.toIntOrNull() != null && y.toIntOrNull() != null
        ActionType.LONG_PRESS -> x.toIntOrNull() != null && y.toIntOrNull() != null && duration.toLongOrNull() != null
        ActionType.SWIPE -> x.toIntOrNull() != null && y.toIntOrNull() != null &&
                endX.toIntOrNull() != null && endY.toIntOrNull() != null && duration.toLongOrNull() != null
        ActionType.WAIT -> waitTime.toLongOrNull() != null
        ActionType.SCREENSHOT -> true
        ActionType.LABEL -> labelName.isNotBlank()
        ActionType.JUMP -> selectedLabel != null
        ActionType.CONDITION -> selectedLabel != null && when (conditionType) {
            ConditionType.LOOP_COUNT ->
                loopCountText.isBlank() || loopCountText.toIntOrNull()?.let { it > 0 } == true

            ConditionType.COLOR ->
                x.toIntOrNull() != null &&
                        y.toIntOrNull() != null &&
                        colorRText.toIntOrNull()?.let { it in 0..255 } == true &&
                        colorGText.toIntOrNull()?.let { it in 0..255 } == true &&
                        colorBText.toIntOrNull()?.let { it in 0..255 } == true &&
                        (colorToleranceText.isBlank() || colorToleranceText.toIntOrNull()?.let { it in 0..255 } == true)

            ConditionType.IMAGE ->
                templatePreview != null &&
                        (
                                imageThresholdText.isBlank() ||
                                        parseThreshold(imageThresholdText)?.let {
                                            it in MIN_IMAGE_MATCH_THRESHOLD..MAX_IMAGE_MATCH_THRESHOLD
                                        } == true
                                ) &&
                        (
                                imageWaitTimeoutText.isBlank() ||
                                        imageWaitTimeoutText.toIntOrNull()?.let {
                                            it in 1..MAX_IMAGE_WAIT_TIMEOUT_SEC
                                        } == true
                                ) &&
                        (
                                !tapOnImageFound ||
                                        waitTime.isBlank() ||
                                        waitTime.toLongOrNull()?.let { it >= 0 } == true
                                )
        }
        else -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingStep == null) actionTypeLabel(actionType) else "${actionTypeLabel(actionType)}を編集") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                val showCoordinateCaptureButton =
                    actionType == ActionType.TAP ||
                            actionType == ActionType.LONG_PRESS ||
                            actionType == ActionType.SWIPE

                if (showCoordinateCaptureButton) {
                    Button(
                        onClick = {
                            val service = MacroAccessibilityService.instance
                            if (service == null) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                return@Button
                            }
                            if (!Settings.canDrawOverlays(context)) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                                return@Button
                            }
                            val mode = if (actionType == ActionType.SWIPE) CaptureMode.SWIPE else CaptureMode.SINGLE_POINT
                            service.startCoordinateCapture(mode)
                            (context as? Activity)?.moveTaskToBack(true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("画面から取得")
                    }
                }

                when (actionType) {
                    ActionType.TAP -> {
                        NumberField("X座標", x) { x = it }
                        NumberField("Y座標", y) { y = it }
                        NumberField("待機時間(ms)", waitTime) { waitTime = it }
                    }
                    ActionType.LONG_PRESS -> {
                        NumberField("X座標", x) { x = it }
                        NumberField("Y座標", y) { y = it }
                        NumberField("押下時間(ms)", duration) { duration = it }
                        NumberField("待機時間(ms)", waitTime) { waitTime = it }
                    }
                    ActionType.SWIPE -> {
                        NumberField("開始X座標", x) { x = it }
                        NumberField("開始Y座標", y) { y = it }
                        NumberField("終了X座標", endX) { endX = it }
                        NumberField("終了Y座標", endY) { endY = it }
                        NumberField("スワイプ時間(ms)", duration) { duration = it }
                        NumberField("待機時間(ms)", waitTime) { waitTime = it }
                    }
                    ActionType.WAIT -> {
                        NumberField("待機時間(ms)", waitTime) { waitTime = it }
                    }
                    ActionType.SCREENSHOT -> {
                        Text("このステップは追加設定が不要です。")
                    }
                    ActionType.LABEL -> {
                        OutlinedTextField(
                            value = labelName,
                            onValueChange = { labelName = it },
                            label = { Text("ラベル名") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                    ActionType.JUMP -> {
                        LabelSelectionField(
                            availableLabels = availableLabels,
                            selectedLabel = selectedLabel,
                            onSelect = { selectedLabel = it }
                        )
                    }
                    ActionType.CONDITION -> {
                        LabelSelectionField(
                            availableLabels = availableLabels,
                            selectedLabel = selectedLabel,
                            onSelect = { selectedLabel = it }
                        )

                        ConditionTypeSelectionField(
                            selected = conditionType,
                            onSelect = { conditionType = it }
                        )

                        when (conditionType) {
                            ConditionType.LOOP_COUNT -> {
                                NumberField("繰り返し回数（空欄で無限ループ）", loopCountText) { loopCountText = it }
                            }
                            ConditionType.COLOR -> {
                                Button(
                                    onClick = startColorPicker,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Text("画面からスポイトで取得（座標＋色）")
                                }
                                NumberField("X座標", x) { x = it }
                                NumberField("Y座標", y) { y = it }
                                Text(
                                    text = "判定したい色 (0〜255)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                NumberField("R", colorRText) { colorRText = it }
                                NumberField("G", colorGText) { colorGText = it }
                                NumberField("B", colorBText) { colorBText = it }
                                NumberField("許容誤差（空欄で10）", colorToleranceText) { colorToleranceText = it }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = invertCondition,
                                        onCheckedChange = { invertCondition = it }
                                    )
                                    Text("条件が成立しなかった時にジャンプする（反転）")
                                }
                            }
                            ConditionType.IMAGE -> {
                                val preview = templatePreview

                                Button(
                                    onClick = startTemplateCapture,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Text(
                                        if (preview == null) {
                                            "画面からテンプレートを作成"
                                        } else {
                                            "テンプレートを作り直す"
                                        }
                                    )
                                }

                                if (preview != null) {
                                    Image(
                                        bitmap = preview.asImageBitmap(),
                                        contentDescription = "テンプレート画像",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 160.dp)
                                            .padding(top = 8.dp)
                                    )
                                    Text(
                                        text = "${preview.width}×${preview.height}px",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                } else {
                                    Text(
                                        text = "テンプレートが未作成です",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = imageThresholdText,
                                    onValueChange = { imageThresholdText = it },
                                    label = {
                                        Text(
                                            "一致度のしきい値（" +
                                                    "${formatThreshold(MIN_IMAGE_MATCH_THRESHOLD)}〜" +
                                                    "${formatThreshold(MAX_IMAGE_MATCH_THRESHOLD)}、" +
                                                    "空欄で${formatThreshold(DEFAULT_IMAGE_MATCH_THRESHOLD)}）"
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )

                                // 見つかるまで待つ最大秒数
                                NumberField(
                                    "見つかるまで待つ最大秒数（1〜$MAX_IMAGE_WAIT_TIMEOUT_SEC、空欄で待たない）",
                                    imageWaitTimeoutText
                                ) { imageWaitTimeoutText = it }
                                Text(
                                    text = "時間切れの場合は「見つからなかった」として扱います。",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                // 見つかった位置のタップ
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = tapOnImageFound,
                                        onCheckedChange = { tapOnImageFound = it }
                                    )
                                    Text("見つかった時、その位置をタップする")
                                }

                                if (tapOnImageFound) {
                                    NumberField(
                                        "タップ後の待機時間(ms)（空欄で300）",
                                        waitTime
                                    ) { waitTime = it }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = invertCondition,
                                        onCheckedChange = { invertCondition = it }
                                    )
                                    Text("画像が見つからなかった時にジャンプする（反転）")
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    // 一時保存したテンプレートの扱い(正式保存または破棄)は、ViewModelに任せる。
                    // ダイアログが閉じる時に、こちらで破棄してしまわないよう印を付ける。
                    tempCommitted = true

                    onConfirm(
                        MacroStep(
                            id = editingStep?.id ?: UUID.randomUUID().toString(),
                            order = editingStep?.order ?: 0,
                            actionType = actionType,
                            x = x.toIntOrNull(),
                            y = y.toIntOrNull(),
                            endX = endX.toIntOrNull(),
                            endY = endY.toIntOrNull(),
                            duration = duration.toLongOrNull(),
                            // 画像認識で「位置をタップ」がオフの場合、待機時間は使わない
                            waitTime = if (isImageCondition && !tapOnImageFound) {
                                null
                            } else {
                                waitTime.toLongOrNull()
                            },
                            targetLabel = when (actionType) {
                                ActionType.LABEL -> labelName.trim()
                                ActionType.JUMP, ActionType.CONDITION -> selectedLabel
                                else -> null
                            },
                            loopCount = if (actionType == ActionType.CONDITION && conditionType == ConditionType.LOOP_COUNT) {
                                loopCountText.toIntOrNull()
                            } else {
                                null
                            },
                            conditionType = if (actionType == ActionType.CONDITION) {
                                conditionType.name
                            } else {
                                null
                            },
                            colorR = if (actionType == ActionType.CONDITION && conditionType == ConditionType.COLOR) {
                                colorRText.toIntOrNull()
                            } else {
                                null
                            },
                            colorG = if (actionType == ActionType.CONDITION && conditionType == ConditionType.COLOR) {
                                colorGText.toIntOrNull()
                            } else {
                                null
                            },
                            colorB = if (actionType == ActionType.CONDITION && conditionType == ConditionType.COLOR) {
                                colorBText.toIntOrNull()
                            } else {
                                null
                            },
                            colorTolerance = if (actionType == ActionType.CONDITION && conditionType == ConditionType.COLOR) {
                                colorToleranceText.toIntOrNull()
                            } else {
                                null
                            },
                            invertCondition = actionType == ActionType.CONDITION &&
                                    (conditionType == ConditionType.COLOR || conditionType == ConditionType.IMAGE) &&
                                    invertCondition,
                            imageMatchThreshold = if (isImageCondition) {
                                parseThreshold(imageThresholdText)
                            } else {
                                null
                            },
                            templateScreenWidth = if (isImageCondition) {
                                templateScreenWidth
                            } else {
                                null
                            },
                            templateScreenHeight = if (isImageCondition) {
                                templateScreenHeight
                            } else {
                                null
                            },
                            tapOnImageFound = isImageCondition && tapOnImageFound,
                            imageWaitTimeoutSec = if (isImageCondition) {
                                imageWaitTimeoutText.toIntOrNull()
                            } else {
                                null
                            }
                        ),
                        templateTempKey
                    )
                }
            ) {
                Text(if (editingStep == null) "追加" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onBack ?: onDismiss) {
                Text(if (onBack != null) "戻る" else "キャンセル")
            }
        }
    )
}

/**
 * JUMP/CONDITIONステップ用、既存ラベルからの選択UI。
 * ラベルが1つも登録されていない場合は案内文のみ表示する。
 */
@Composable
private fun LabelSelectionField(
    availableLabels: List<String>,
    selectedLabel: String?,
    onSelect: (String) -> Unit
) {
    if (availableLabels.isEmpty()) {
        Text(
            text = "先にラベルを追加してください",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }

    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "ジャンプ先ラベル",
            style = MaterialTheme.typography.bodySmall
        )
        availableLabels.forEach { label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedLabel == label,
                    onClick = { onSelect(label) }
                )
                Text(text = label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * CONDITIONステップ用、判定方式の選択UI。
 * 将来OCRを追加する際はここに選択肢を足すだけでよい。
 */
@Composable
private fun ConditionTypeSelectionField(
    selected: ConditionType,
    onSelect: (ConditionType) -> Unit
) {
    val options = listOf(
        ConditionType.LOOP_COUNT to "ループ回数",
        ConditionType.COLOR to "色判定",
        ConditionType.IMAGE to "画像認識"
    )

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = "判定方式",
            style = MaterialTheme.typography.bodySmall
        )
        options.forEach { (type, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == type,
                    onClick = { onSelect(type) }
                )
                Text(text = label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
}

@Composable
private fun EmptyStepList(onRecordClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "まだステップがありません",
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = onRecordClick,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("操作を記録して追加")
            }
        }
    }
}

@Composable
private fun StepList(
    steps: List<MacroStep>,
    onStepClick: (MacroStep) -> Unit,
    onDeleteClick: (MacroStep) -> Unit,
    onReorder: (List<MacroStep>) -> Unit,
    onRecordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ドラッグ中に画面上で一時的に並べ替えるためのローカルリスト。
    // ドラッグ中でない時だけ、外から渡された最新の並び順(steps)に同期する。
    var items by remember { mutableStateOf(steps) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    LaunchedEffect(steps) {
        if (draggingIndex == null) {
            items = steps
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Button(
                onClick = onRecordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("操作を記録して追加")
            }
        }

        itemsIndexed(items, key = { _, step -> step.id }) { index, step ->

            val isDragging = draggingIndex == index

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        // カードの高さを一度だけ計測し、
                        // ドラッグ量から「何個分移動したか」を計算するのに使う。
                        if (itemHeightPx == 0f) {
                            itemHeightPx = coordinates.size.height.toFloat()
                        }
                    }
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    }
                    .zIndex(if (isDragging) 1f else 0f)
                    .alpha(if (isDragging) 0.9f else 1f)
            ) {
                StepCard(
                    step = step,
                    onClick = { onStepClick(step) },
                    onDeleteClick = { onDeleteClick(step) },
                    dragHandleModifier = Modifier.pointerInput(step.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffsetY = 0f
                                onReorder(items)
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y

                                val currentIndex = draggingIndex
                                if (currentIndex == null || itemHeightPx <= 0f) {
                                    return@detectDragGesturesAfterLongPress
                                }

                                val moveBy = (dragOffsetY / itemHeightPx).toInt()

                                if (moveBy != 0) {
                                    val targetIndex =
                                        (currentIndex + moveBy)
                                            .coerceIn(items.indices)

                                    if (targetIndex != currentIndex) {
                                        items = items.toMutableList().apply {
                                            add(targetIndex, removeAt(currentIndex))
                                        }
                                        draggingIndex = targetIndex
                                        dragOffsetY -= moveBy * itemHeightPx
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    step: MacroStep,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .then(dragHandleModifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "≡",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            ) {
                Text(text = actionTypeLabel(step.actionType), style = MaterialTheme.typography.titleSmall)
                StepDetailText(step)
            }

            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Filled.Delete, contentDescription = "削除")
            }
        }
    }
}

@Composable
private fun StepDetailText(step: MacroStep) {
    val detail = when (step.actionType) {
        ActionType.TAP -> "X:${step.x} Y:${step.y} 待機:${step.waitTime}ms"
        ActionType.LONG_PRESS -> "X:${step.x} Y:${step.y} 押下:${step.duration}ms 待機:${step.waitTime}ms"
        ActionType.SWIPE -> "(${step.x},${step.y}) → (${step.endX},${step.endY}) ${step.duration}ms"
        ActionType.WAIT -> "${step.waitTime}ms"
        ActionType.SCREENSHOT -> ""
        ActionType.LABEL -> "ラベル名: ${step.targetLabel}"
        ActionType.JUMP -> "ジャンプ先: ${step.targetLabel}"
        ActionType.CONDITION -> when (ConditionType.fromName(step.conditionType)) {
            ConditionType.LOOP_COUNT ->
                "「${step.targetLabel}」へ ${step.loopCount?.let { "${it}回" } ?: "無限に"} 繰り返す"

            ConditionType.COLOR -> {
                val colorText = "(${step.colorR},${step.colorG},${step.colorB})"
                val matchText = if (step.invertCondition) "一致しない" else "一致する"
                "(${step.x},${step.y})の色が${colorText}に${matchText}時「${step.targetLabel}」へ"
            }

            ConditionType.IMAGE -> {
                val threshold = step.imageMatchThreshold ?: DEFAULT_IMAGE_MATCH_THRESHOLD
                val foundText = if (step.invertCondition) "見つからない" else "見つかった"
                val baseText =
                    "画像が${foundText}時「${step.targetLabel}」へ（一致度${formatThreshold(threshold)}以上）"

                // 設定した動作(最大待ち時間・位置のタップ)があれば、2行目に表示する
                val options = buildList {
                    step.imageWaitTimeoutSec?.let { add("最大${it}秒待つ") }
                    if (step.tapOnImageFound) add("見つけたら位置をタップ")
                }

                if (options.isEmpty()) {
                    baseText
                } else {
                    baseText + "\n" + options.joinToString(" ／ ")
                }
            }
        }
        else -> ""
    }
    if (detail.isNotBlank()) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
private fun startRecording(context: android.content.Context, onReady: () -> Unit) {
    val service = com.example.sdclickassistant.service.MacroAccessibilityService.instance
    if (service == null) {
        context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return
    }
    if (!android.provider.Settings.canDrawOverlays(context)) {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
        return
    }
    onReady()
}