package com.example.sdclickassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.sdclickassistant.MainActivity
import com.example.sdclickassistant.data.matcher.TemplateMatcher
import com.example.sdclickassistant.data.repository.ScenarioRepository
import com.example.sdclickassistant.data.template.TemplateStorage
import com.example.sdclickassistant.domain.executor.MacroExecutor
import com.example.sdclickassistant.domain.executor.StepResult
import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.ConditionType
import com.example.sdclickassistant.domain.model.DEFAULT_IMAGE_MATCH_THRESHOLD
import com.example.sdclickassistant.domain.model.MacroStep
import com.example.sdclickassistant.domain.model.requiresScreenCapture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import javax.inject.Inject

@AndroidEntryPoint
class MacroAccessibilityService :
    AccessibilityService() {

    @Inject
    lateinit var scenarioRepository: ScenarioRepository

    @Inject
    lateinit var templateStorage: TemplateStorage

    @Inject
    lateinit var templateMatcher: TemplateMatcher

    private val executor =
        MacroExecutor()

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main
        )

    private var executionJob: Job? = null

    private lateinit var floatingButtonController:
            FloatingButtonController

    private lateinit var panelController:
            ExecutionPanelController

    private lateinit var stepMarkerController:
            StepMarkerController

    private lateinit var coordinateCaptureController:
            CoordinateCaptureController

    private lateinit var sequenceRecordingController:
            SequenceRecordingController

    private lateinit var colorPickerController:
            ColorPickerController

    private lateinit var templateCaptureController:
            TemplateCaptureController

    /**
     * 画像認識(IMAGE)の判定結果。
     *
     * 「見つからなかった」と「判定そのものができなかった」を区別するための型。
     * 判定できなかった場合に「見つからなかった」として扱うと、
     * 「見つからない時にジャンプ(反転)」のステップで誤ってジャンプしてしまうため、
     * 判定できなかった場合は実行を停止する。
     */
    private sealed class ImageEvaluation {

        /**
         * 判定できた。
         *
         * @param met 条件が成立したか(画像が見つかったか)
         * @param matchX 見つかった領域の中心X座標(画面全体を基準)。見つからなかった場合はnull
         * @param matchY 見つかった領域の中心Y座標(画面全体を基準)。見つからなかった場合はnull
         */
        data class Completed(
            val met: Boolean,
            val matchX: Int? = null,
            val matchY: Int? = null
        ) : ImageEvaluation()

        /** 判定できなかった(キャプチャ失敗・テンプレート異常・照合の失敗など) */
        data object Failed : ImageEvaluation()
    }

    companion object {

        private const val TAG =
            "MacroAccessibilityService"

        // 色判定で許容誤差(colorTolerance)が未指定の場合のデフォルト値
        private const val DEFAULT_COLOR_TOLERANCE = 10

        // 記録した操作を対象アプリへ再送する前に、オーバーレイのタッチ透過が
        // 反映されるのを待つ時間(ms)
        private const val REPLAY_SETTLE_DELAY_MS = 80L

        // テンプレート作成時の画面キャプチャを、待ち続けないための上限時間(ms)。
        // 画面が静止していると、次のフレームが届かず待ち続ける可能性があるため。
        private const val TEMPLATE_CAPTURE_TIMEOUT_MS = 3000L

        // 画像認識の判定時の、画面キャプチャの上限時間(ms)・最大試行回数・再試行の間隔(ms)。
        private const val IMAGE_CAPTURE_TIMEOUT_MS = 3000L
        private const val IMAGE_CAPTURE_MAX_ATTEMPTS = 3
        private const val IMAGE_CAPTURE_RETRY_DELAY_MS = 200L

        // 「見つかるまで待つ」設定の時の、次の判定までの間隔(ms)。
        // キャプチャと照合は負荷が高いため、休みなく繰り返さない。
        private const val IMAGE_POLL_INTERVAL_MS = 500L

        // 画像が見つかった位置をタップした後の、待機時間の既定値(ms)。
        // ステップに待機時間が設定されていない場合に使う。
        private const val DEFAULT_IMAGE_TAP_WAIT_MS = 300L

        var instance:
                MacroAccessibilityService? = null
            private set
    }

    // ------------------------------------------------------------
    // Service lifecycle
    // ------------------------------------------------------------

    override fun onServiceConnected() {

        super.onServiceConnected()

        instance = this

        floatingButtonController =
            FloatingButtonController(this)

        panelController =
            ExecutionPanelController(this)

        stepMarkerController =
            StepMarkerController(this)

        coordinateCaptureController =
            CoordinateCaptureController(this)

        sequenceRecordingController =
            SequenceRecordingController(this)

        colorPickerController =
            ColorPickerController(this)

        templateCaptureController =
            TemplateCaptureController(this)

        Log.d(
            TAG,
            "Accessibility Service connected"
        )
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "Accessibility Service destroying"
        )

        executionJob?.cancel()

        /*
         * ScreenshotCaptureServiceは、
         * Accessibility Serviceの終了時にも停止する。
         *
         * ただし通常のシナリオ完了やSTOPでは
         * 停止しない。
         */
        ScreenshotCaptureService.instance
            ?.stopCaptureSession()

        super.onDestroy()

        instance = null

        floatingButtonController.hide()
        panelController.hide()
        stepMarkerController.hide()
        coordinateCaptureController.hide()
        sequenceRecordingController.hide()
        colorPickerController.hide()
        templateCaptureController.hide()
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        // 現時点では未使用
    }

    override fun onInterrupt() {

        Log.d(
            TAG,
            "Accessibility Service interrupted"
        )
    }

    // ------------------------------------------------------------
    // Placement
    // ------------------------------------------------------------

    /**
     * 配置待機状態
     *
     * 再生ボタン＋戻るボタン＋繰り返し回数パネル＋
     * ステップ位置マーカーを表示する。
     */
    fun showPlacementButton(
        scenarioId: String
    ) {

        floatingButtonController.show(

            // シナリオ読み込み前の仮の初期値。
            // 読み込み完了後、updateRepeatDisplay()で正しい値に更新する。
            initialRepeatCount = 1,
            initialIsInfiniteLoop = false,

            onTap = {

                floatingButtonController.hide()
                stepMarkerController.hide()

                startExecution(
                    scenarioId
                )
            },

            onBack = {

                floatingButtonController.hide()
                stepMarkerController.hide()

                returnToApp()
            },

            onMarkersToggled = { visible ->

                if (visible) {

                    showStepMarkers(
                        scenarioId
                    )

                } else {

                    stepMarkerController.hide()
                }
            },

            onRepeatSettingsChanged = { repeatCount, isInfiniteLoop ->

                updateRepeatSettings(
                    scenarioId,
                    repeatCount,
                    isInfiniteLoop
                )
            }
        )

        serviceScope.launch {

            val scenario =
                scenarioRepository
                    .getScenarioDetail(
                        scenarioId
                    )

            scenario?.let { s ->

                floatingButtonController.updateRepeatDisplay(
                    s.repeatCount,
                    s.isInfiniteLoop
                )

                // マーカーは、ボタンで「表示」にした時に、初めて出す(最初は非表示)。
            }
        }
    }

    /**
     * 配置待機画面の繰り返し回数パネルでの操作を、
     * DBへ保存する。
     */
    private fun updateRepeatSettings(
        scenarioId: String,
        repeatCount: Int,
        isInfiniteLoop: Boolean
    ) {

        serviceScope.launch {

            val scenario =
                scenarioRepository
                    .getScenarioDetail(
                        scenarioId
                    )
                    ?: return@launch

            scenarioRepository.saveScenario(
                scenario.copy(
                    repeatCount = repeatCount,
                    isInfiniteLoop = isInfiniteLoop
                )
            )
        }
    }

    /**
     * ステップ位置のマーカーを表示する(配置待機の画面のボタンで、表示にした時)。
     *
     * 表示のたびに、保存済みの最新のステップを読み直す。
     * (ドラッグで動かして保存された位置が、反映される。)
     */
    private fun showStepMarkers(
        scenarioId: String
    ) {

        serviceScope.launch {

            val scenario =
                scenarioRepository
                    .getScenarioDetail(
                        scenarioId
                    )
                    ?: return@launch

            // 読み込んでいる間に、ボタンで非表示に戻された、
            // または実行が始まった場合は、表示しない。
            if (!floatingButtonController.areMarkersVisible()) {
                return@launch
            }

            stepMarkerController.show(
                scenario.steps
            ) { target, x, y ->

                updateStepPosition(
                    scenarioId,
                    target,
                    x,
                    y
                )
            }
        }
    }

    private fun updateStepPosition(
        scenarioId: String,
        target: MarkerTarget,
        x: Int,
        y: Int
    ) {

        serviceScope.launch {

            val scenario =
                scenarioRepository
                    .getScenarioDetail(
                        scenarioId
                    )
                    ?: return@launch

            val updatedSteps =
                scenario.steps.map { step ->

                    if (
                        step.id != target.stepId
                    ) {

                        step

                    } else if (
                        target.isEndPoint
                    ) {

                        step.copy(
                            endX = x,
                            endY = y
                        )

                    } else {

                        step.copy(
                            x = x,
                            y = y
                        )
                    }
                }

            scenarioRepository.saveScenario(
                scenario.copy(
                    steps = updatedSteps
                )
            )
        }
    }

    private fun returnToApp() {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }

        startActivity(intent)
    }

    // ------------------------------------------------------------
    // Capture / Recording
    // ------------------------------------------------------------

    fun startCoordinateCapture(
        mode: CaptureMode
    ) {

        coordinateCaptureController.show(
            mode
        ) { result ->

            CaptureResultBus.emit(
                result
            )

            returnToApp()
        }
    }

    fun startSequenceRecording() {

        sequenceRecordingController.show(

            onReplay = { step, onDone ->
                replayRecordedStep(step, onDone)
            },

            onFinished = { recordedSteps ->

                RecordingResultBus.emit(
                    recordedSteps
                )

                returnToApp()
            }
        )
    }

    /**
     * 記録した1回分の操作を、対象アプリへ実際に送信する。
     * 完了したら(失敗しても)onDoneを呼び、オーバーレイを記録状態へ戻させる。
     */
    private fun replayRecordedStep(
        step: MacroStep,
        onDone: () -> Unit
    ) {

        serviceScope.launch {

            try {

                // オーバーレイのタッチ透過が反映されるまで少し待つ
                delay(REPLAY_SETTLE_DELAY_MS)

                val x = step.x
                val y = step.y

                val success =
                    when (step.actionType) {

                        ActionType.TAP ->
                            if (x != null && y != null) {
                                performTap(x, y)
                            } else false

                        ActionType.LONG_PRESS ->
                            if (x != null && y != null && step.duration != null) {
                                performLongPress(x, y, step.duration)
                            } else false

                        ActionType.SWIPE -> {
                            val endX = step.endX
                            val endY = step.endY
                            if (
                                x != null && y != null &&
                                endX != null && endY != null &&
                                step.duration != null
                            ) {
                                performSwipe(x, y, endX, endY, step.duration)
                            } else false
                        }

                        else -> false
                    }

                Log.d(
                    TAG,
                    "RECORD REPLAY: action=${step.actionType} success=$success"
                )

            } finally {

                onDone()
            }
        }
    }

    /**
     * 画面上の色をスポイトで取得する。
     *
     * 操作モードから始まり、「この画面をキャプチャ」が押されるたびに
     * 現在の画面を撮影してColorPickerControllerへ渡す。
     */
    fun startColorPicker() {

        serviceScope.launch {

            val screenshotService =
                ensureScreenshotServiceReady()

            if (screenshotService == null) {

                Log.e(
                    TAG,
                    "COLOR PICKER: screenshot service is not ready"
                )

                returnToApp()

                return@launch
            }

            colorPickerController.show(

                onCaptureRequested = { onCaptured ->

                    serviceScope.launch {

                        // 直前にボタンを押した描画が完了するのを
                        // 少しだけ待ってからキャプチャする。
                        delay(150)

                        val bitmap =
                            screenshotService.captureBitmap()

                        if (bitmap == null) {
                            Log.e(
                                TAG,
                                "COLOR PICKER: failed to capture screen"
                            )
                        }

                        onCaptured(bitmap)
                    }
                },

                onDecide = { result ->

                    ColorPickResultBus.emit(result)

                    returnToApp()
                },
                onCancel = {

                    returnToApp()
                }
            )
        }
    }

    /**
     * 画像認識(IMAGE)用のテンプレート画像を、画面から切り出して作る。
     *
     * 操作モードから始まり、「この画面をキャプチャ」が押されると、
     * 操作パネルを隠してから現在の画面を撮影し、範囲選択モードへ進む。
     * 「決定」で切り出した画像をTemplateStorageへ一時保存し、
     * その鍵(tempKey)をTemplateCaptureResultBus経由でCompose側へ渡す。
     */
    fun startTemplateCapture() {

        serviceScope.launch {

            val screenshotService =
                ensureScreenshotServiceReady()

            if (screenshotService == null) {

                Log.e(
                    TAG,
                    "TEMPLATE CAPTURE: screenshot service is not ready"
                )

                returnToApp()

                return@launch
            }

            templateCaptureController.show(

                onCaptureRequested = { onCaptured ->

                    serviceScope.launch {

                        // 画面が静止していて次のフレームが届かない場合に備え、
                        // 待ち続けないよう上限時間を設ける。
                        val bitmap =
                            withTimeoutOrNull(
                                TEMPLATE_CAPTURE_TIMEOUT_MS
                            ) {
                                screenshotService.captureBitmap()
                            }

                        if (bitmap == null) {
                            Log.e(
                                TAG,
                                "TEMPLATE CAPTURE: failed to capture screen (failed or timed out)"
                            )
                        }

                        onCaptured(bitmap)
                    }
                },

                onDecide = { cropped ->

                    serviceScope.launch {

                        val templateWidth = cropped.bitmap.width
                        val templateHeight = cropped.bitmap.height

                        // 一時保存が終わったら、切り出した画像は不要なのでrecycleする。
                        val tempKey =
                            try {
                                templateStorage.saveTemp(cropped.bitmap)
                            } finally {
                                cropped.bitmap.recycle()
                            }

                        if (tempKey != null) {

                            TemplateCaptureResultBus.emit(
                                TemplateCaptureResult(
                                    tempKey = tempKey,
                                    templateWidth = templateWidth,
                                    templateHeight = templateHeight,
                                    screenWidth = cropped.screenWidth,
                                    screenHeight = cropped.screenHeight
                                )
                            )

                        } else {

                            Log.e(
                                TAG,
                                "TEMPLATE CAPTURE: failed to save template temporarily"
                            )
                        }

                        returnToApp()
                    }
                },

                onCancel = {

                    returnToApp()
                }
            )
        }
    }

    // ------------------------------------------------------------
    // Scenario execution
    // ------------------------------------------------------------

    private fun startExecution(
        scenarioId: String
    ) {

        /*
         * 前回実行が残っていた場合に備える。
         */
        executionJob?.cancel()

        executionJob =
            serviceScope.launch {

                /*
                 * 画像認識(IMAGE)ステップのテンプレート画像。
                 * 実行の開始前にまとめて読み込む。
                 * key = ステップID
                 *
                 * 実行が終わる時(正常終了・STOP・エラーのどれでも)、
                 * 下のfinallyで必ず解放する。
                 */
                val templates =
                    mutableMapOf<String, Bitmap>()

                try {

                    val scenario =
                        scenarioRepository
                            .getScenarioDetail(
                                scenarioId
                            )

                    val steps =
                        scenario
                            ?.steps
                            ?.sortedBy {
                                it.order
                            }
                            .orEmpty()

                    if (
                        scenario == null ||
                        steps.isEmpty()
                    ) {

                        showPlacementButton(
                            scenarioId
                        )

                        return@launch
                    }

                    /*
                     * 画面キャプチャが必要なステップ
                     * (スクリーンショット・色判定・画像認識)が
                     * 存在するか確認。判定は共通関数に任せる。
                     */
                    val needsScreenshot =
                        steps.requiresScreenCapture()

                    /*
                     * 画面キャプチャが必要な場合、
                     * ScreenshotCaptureServiceが
                     * キャプチャ可能になるまで待つ。
                     */
                    if (needsScreenshot) {

                        val screenshotService =
                            ensureScreenshotServiceReady()

                        if (screenshotService == null) {

                            Log.e(
                                TAG,
                                "Screenshot service is not ready"
                            )

                            stopExecution(
                                scenarioId
                            )

                            return@launch
                        }
                    }

                    /*
                     * 画像認識(IMAGE)ステップのテンプレート画像を、
                     * 実行の開始前にまとめて読み込む。
                     *
                     * 1つでも読み込めない場合は、実行の途中で
                     * 判定できなくなるのを避けるため、開始せずに停止する。
                     */
                    steps.forEachIndexed { index, step ->

                        if (step.isImageCondition()) {

                            val template =
                                templateStorage.load(
                                    step.id
                                )

                            if (template == null) {

                                Log.e(
                                    TAG,
                                    "IMAGE TEMPLATE NOT FOUND: step=${index + 1}, id=${step.id}"
                                )

                                stopExecution(
                                    scenarioId
                                )

                                return@launch
                            }

                            templates[step.id] = template
                        }
                    }

                    /*
                     * 現在の配置位置を取得。
                     */
                    val (buttonX, buttonY) =
                        floatingButtonController.lastPosition

                    panelController.show(
                        buttonX,
                        buttonY,
                        onStop = {
                            stopExecution(
                                scenarioId
                            )
                        }
                    )

                    var loopIndex = 1

                    while (
                        scenario.isInfiniteLoop ||
                        loopIndex <= scenario.repeatCount
                    ) {

                        Log.d(
                            TAG,
                            "========== LOOP START: $loopIndex =========="
                        )

                        val loopLabel =
                            if (
                                scenario.isInfiniteLoop
                            ) {
                                "∞"
                            } else {
                                "$loopIndex / ${scenario.repeatCount}"
                            }

                        /*
                         * CONDITION(ループ制御)ステップごとの
                         * 現在の繰り返し回数を記録する。
                         *
                         * key = ステップID, value = これまでに
                         * ラベルへ戻った回数。
                         *
                         * シナリオ全体のループ(loopIndex)が
                         * 1周するたびにリセットする。
                         */
                        val loopCounters =
                            mutableMapOf<String, Int>()

                        var stepIndex = 0

                        while (stepIndex < steps.size) {

                            val step = steps[stepIndex]

                            Log.d(
                                TAG,
                                "STEP START: loop=$loopIndex, step=${stepIndex + 1}, action=${step.actionType}"
                            )

                            panelController.updateStatus(
                                loopLabel,
                                "S${stepIndex + 1} / ${steps.size}"
                            )

                            /*
                             * ------------------------------------------------
                             * LABEL
                             * ------------------------------------------------
                             * 目印のみ。何もせず次のステップへ進む。
                             */
                            if (
                                step.actionType ==
                                ActionType.LABEL
                            ) {

                                stepIndex++

                                continue
                            }

                            /*
                             * ------------------------------------------------
                             * JUMP
                             * ------------------------------------------------
                             * targetLabelと同じラベル名を持つ
                             * LABELステップへ無条件に移動する。
                             */
                            if (
                                step.actionType ==
                                ActionType.JUMP
                            ) {

                                val targetIndex =
                                    findLabelIndex(
                                        steps,
                                        step.targetLabel
                                    )

                                if (targetIndex == -1) {

                                    Log.e(
                                        TAG,
                                        "JUMP TARGET LABEL NOT FOUND: label=${step.targetLabel}"
                                    )

                                    stopExecution(
                                        scenarioId
                                    )

                                    return@launch
                                }

                                stepIndex = targetIndex

                                /*
                                 * suspendを挟まないジャンプが
                                 * 連続すると、Dispatchers.Mainを
                                 * 占有し続けてANRになるため、
                                 * 必ず制御を一度譲る。
                                 */
                                yield()

                                continue
                            }

                            /*
                             * ------------------------------------------------
                             * CONDITION
                             * ------------------------------------------------
                             * 判定方式(conditionType)によって処理を分ける。
                             * LOOP_COUNT: targetLabelへ指定回数まで戻る(既存)
                             * COLOR: 指定座標の色を判定してジャンプ可否を決める
                             * IMAGE: 画面にテンプレート画像が見えるかを判定してジャンプ可否を決める
                             *        (設定に応じて、見つかるまで待つ・見つかった位置をタップする)
                             */
                            if (
                                step.actionType ==
                                ActionType.CONDITION
                            ) {

                                when (
                                    ConditionType.fromName(
                                        step.conditionType
                                    )
                                ) {

                                    ConditionType.LOOP_COUNT -> {

                                        val currentCount =
                                            loopCounters[step.id] ?: 0

                                        val maxCount =
                                            step.loopCount

                                        val shouldLoopAgain =
                                            maxCount == null ||
                                                    currentCount < maxCount

                                        if (shouldLoopAgain) {

                                            val targetIndex =
                                                findLabelIndex(
                                                    steps,
                                                    step.targetLabel
                                                )

                                            if (targetIndex == -1) {

                                                Log.e(
                                                    TAG,
                                                    "CONDITION TARGET LABEL NOT FOUND: label=${step.targetLabel}"
                                                )

                                                stopExecution(
                                                    scenarioId
                                                )

                                                return@launch
                                            }

                                            loopCounters[step.id] =
                                                currentCount + 1

                                            Log.d(
                                                TAG,
                                                "CONDITION LOOP: step=${stepIndex + 1}, count=${currentCount + 1}/${maxCount ?: "∞"}"
                                            )

                                            stepIndex = targetIndex

                                            /*
                                             * JUMPと同様、suspendを挟まず
                                             * ループし続けないようにする。
                                             */
                                            yield()

                                        } else {

                                            Log.d(
                                                TAG,
                                                "CONDITION LOOP FINISHED: step=${stepIndex + 1}"
                                            )

                                            /*
                                             * 完了したのでカウンターをリセット。
                                             * (シナリオ全体を再度ループする際に
                                             * 同じCONDITIONを再利用できるように)
                                             */
                                            loopCounters.remove(step.id)

                                            stepIndex++
                                        }
                                    }

                                    ConditionType.IMAGE -> {

                                        /*
                                         * 画像が見つかるかを判定する。
                                         * 「見つかるまで待つ」が設定されていれば、
                                         * 見つかるか時間切れになるまで繰り返す。
                                         */
                                        val evaluation =
                                            evaluateImageConditionWithWait(
                                                step,
                                                templates[step.id],
                                                stepIndex + 1
                                            )

                                        /*
                                         * 判定できなかった場合は、
                                         * 「見つからなかった」として扱わず、停止する。
                                         * (反転ジャンプの誤動作を避けるため)
                                         */
                                        val completed =
                                            when (evaluation) {

                                                is ImageEvaluation.Completed ->
                                                    evaluation

                                                ImageEvaluation.Failed -> {

                                                    Log.e(
                                                        TAG,
                                                        "IMAGE CONDITION FAILED: step=${stepIndex + 1}"
                                                    )

                                                    stopExecution(
                                                        scenarioId
                                                    )

                                                    return@launch
                                                }
                                            }

                                        val conditionMet =
                                            completed.met

                                        /*
                                         * 見つかった場合、「位置をタップ」の設定があれば
                                         * その位置をタップする。
                                         * 反転(invertCondition)のON/OFFには関係なく、
                                         * 見つかったら常にタップする。
                                         */
                                        if (
                                            conditionMet &&
                                            step.tapOnImageFound
                                        ) {

                                            tapFoundImage(
                                                step,
                                                completed,
                                                scenario.speed,
                                                stepIndex + 1
                                            )
                                        }

                                        val shouldJump =
                                            if (step.invertCondition) {
                                                !conditionMet
                                            } else {
                                                conditionMet
                                            }

                                        Log.d(
                                            TAG,
                                            "IMAGE CONDITION RESULT: step=${stepIndex + 1}, met=$conditionMet, invert=${step.invertCondition}, tap=${step.tapOnImageFound}, shouldJump=$shouldJump"
                                        )

                                        if (shouldJump) {

                                            val targetIndex =
                                                findLabelIndex(
                                                    steps,
                                                    step.targetLabel
                                                )

                                            if (targetIndex == -1) {

                                                Log.e(
                                                    TAG,
                                                    "CONDITION TARGET LABEL NOT FOUND: label=${step.targetLabel}"
                                                )

                                                stopExecution(
                                                    scenarioId
                                                )

                                                return@launch
                                            }

                                            stepIndex = targetIndex

                                            yield()

                                        } else {

                                            stepIndex++
                                        }
                                    }

                                    ConditionType.COLOR -> {

                                        val conditionMet =
                                            evaluateColorCondition(step)

                                        val shouldJump =
                                            if (step.invertCondition) {
                                                !conditionMet
                                            } else {
                                                conditionMet
                                            }

                                        Log.d(
                                            TAG,
                                            "COLOR CONDITION: step=${stepIndex + 1}, met=$conditionMet, invert=${step.invertCondition}, shouldJump=$shouldJump"
                                        )

                                        if (shouldJump) {

                                            val targetIndex =
                                                findLabelIndex(
                                                    steps,
                                                    step.targetLabel
                                                )

                                            if (targetIndex == -1) {

                                                Log.e(
                                                    TAG,
                                                    "CONDITION TARGET LABEL NOT FOUND: label=${step.targetLabel}"
                                                )

                                                stopExecution(
                                                    scenarioId
                                                )

                                                return@launch
                                            }

                                            stepIndex = targetIndex

                                            yield()

                                        } else {

                                            stepIndex++
                                        }
                                    }
                                }

                                continue
                            }

                            /*
                             * ------------------------------------------------
                             * SCREENSHOT
                             * ------------------------------------------------
                             */
                            if (
                                step.actionType ==
                                ActionType.SCREENSHOT
                            ) {

                                Log.d(
                                    TAG,
                                    "SCREENSHOT START: loop=$loopIndex, step=${stepIndex + 1}"
                                )

                                val screenshotService =
                                    ScreenshotCaptureService.instance

                                if (
                                    screenshotService == null
                                ) {

                                    Log.e(
                                        TAG,
                                        "SCREENSHOT SERVICE IS NULL: loop=$loopIndex, step=${stepIndex + 1}"
                                    )

                                    stopExecution(
                                        scenarioId
                                    )

                                    return@launch
                                }

                                val success =
                                    screenshotService
                                        .captureAndSave(
                                            scenarioName =
                                                scenario.name,

                                            stepNumber =
                                                ((loopIndex - 1) *
                                                        steps.size) +
                                                        stepIndex + 1
                                        )

                                Log.d(
                                    TAG,
                                    "SCREENSHOT RESULT: loop=$loopIndex, step=${stepIndex + 1}, success=$success"
                                )

                                /*
                                 * スクリーンショット失敗時は、
                                 * 後続ステップへ進まない。
                                 */
                                if (!success) {

                                    Log.e(
                                        TAG,
                                        "SCREENSHOT FAILED: loop=$loopIndex, step=${stepIndex + 1}"
                                    )

                                    stopExecution(
                                        scenarioId
                                    )

                                    return@launch
                                }

                                step.waitTime?.let {

                                    Log.d(
                                        TAG,
                                        "SCREENSHOT WAIT: ${it}ms"
                                    )

                                    delay(it)
                                }

                                stepIndex++

                                continue
                            }

                            /*
                             * ------------------------------------------------
                             * 通常ステップ
                             * ------------------------------------------------
                             */
                            val adjustedStep =
                                applySpeed(
                                    step,
                                    scenario.speed
                                )

                            val result =
                                executor.executeStep(
                                    adjustedStep
                                )

                            Log.d(
                                TAG,
                                "STEP RESULT: loop=$loopIndex, step=${stepIndex + 1}, result=$result"
                            )

                            if (
                                result is
                                        StepResult.ServiceNotEnabled
                            ) {

                                Log.e(
                                    TAG,
                                    "SERVICE NOT ENABLED: loop=$loopIndex, step=${stepIndex + 1}"
                                )

                                stopExecution(
                                    scenarioId
                                )

                                return@launch
                            }

                            stepIndex++
                        }

                        Log.d(
                            TAG,
                            "========== LOOP END: $loopIndex =========="
                        )

                        loopIndex++

                        Log.d(
                            TAG,
                            "NEXT LOOP: $loopIndex"
                        )
                    }

                    /*
                     * シナリオ正常終了。
                     *
                     * ScreenshotCaptureServiceは停止しない。
                     * これにより次回のシナリオ実行でも
                     * 同じMediaProjectionを利用できる。
                     */
                    stopExecution(
                        scenarioId
                    )

                } finally {

                    /*
                     * 実行が終わった(正常終了・STOP・エラー・キャンセル)ので、
                     * 読み込んだテンプレート画像を解放する。
                     */
                    templates.values.forEach {
                        it.recycle()
                    }

                    templates.clear()
                }
            }
    }

    /**
     * ScreenshotCaptureServiceが使用可能になるまで待つ。
     *
     * 通常は準備画面で既に起動済み。
     *
     * TokenHolderに未使用Tokenが残っている場合は、
     * 念のためここでもServiceを起動する。
     */
    private suspend fun ensureScreenshotServiceReady():
            ScreenshotCaptureService? {

        /*
         * すでにServiceが存在している場合。
         */
        ScreenshotCaptureService.instance?.let {
            return ScreenshotCaptureService.awaitReady()
        }

        /*
         * Tokenが存在しない場合、
         * 新しいMediaProjection許可が必要。
         *
         * AccessibilityServiceから許可ダイアログを
         * 出すことはしない。
         */
        if (
            !MediaProjectionTokenHolder.hasToken()
        ) {

            Log.e(
                TAG,
                "No MediaProjection token available"
            )

            return null
        }

        /*
         * Tokenが残っている場合のみ、
         * ScreenshotCaptureServiceを起動。
         */
        try {

            ContextCompat.startForegroundService(
                this,
                Intent(
                    this,
                    ScreenshotCaptureService::class.java
                )
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to start ScreenshotCaptureService",
                e
            )

            return null
        }

        /*
         * Serviceの初期化完了を待つ。
         */
        return ScreenshotCaptureService.awaitReady()
    }

    // ------------------------------------------------------------
    // Stop
    // ------------------------------------------------------------

    /**
     * STOPボタン、または実行完了時に呼ばれる。
     *
     * 配置待機状態へ戻す。
     *
     * ScreenshotCaptureServiceは停止しない。
     */
    fun stopExecution(
        scenarioId: String
    ) {

        executionJob?.cancel()

        executionJob = null

        panelController.hide()

        /*
         * ここではMediaProjectionを停止しない。
         *
         * 同じMediaProjection / VirtualDisplayを
         * 次回のシナリオ実行でも利用するため。
         */
        showPlacementButton(
            scenarioId
        )
    }

    // ------------------------------------------------------------
    // Color condition
    // ------------------------------------------------------------

    /**
     * 指定座標の色が、指定色と許容誤差の範囲内で
     * 一致するかを判定する。
     *
     * 座標が範囲外、画面キャプチャ失敗、
     * 設定値が不足している場合はfalseを返す。
     */
    private suspend fun evaluateColorCondition(
        step: MacroStep
    ): Boolean {

        val x = step.x ?: return false
        val y = step.y ?: return false
        val targetR = step.colorR ?: return false
        val targetG = step.colorG ?: return false
        val targetB = step.colorB ?: return false
        val tolerance = step.colorTolerance ?: DEFAULT_COLOR_TOLERANCE

        val screenshotService =
            ScreenshotCaptureService.instance
                ?: run {
                    Log.e(
                        TAG,
                        "COLOR CONDITION: screenshot service is null"
                    )
                    return false
                }

        val bitmap =
            screenshotService.captureBitmap()
                ?: run {
                    Log.e(
                        TAG,
                        "COLOR CONDITION: failed to capture screen"
                    )
                    return false
                }

        return try {

            if (
                x !in 0 until bitmap.width ||
                y !in 0 until bitmap.height
            ) {

                Log.e(
                    TAG,
                    "COLOR CONDITION: coordinates out of bounds x=$x y=$y"
                )

                return false
            }

            val pixel = bitmap.getPixel(x, y)

            val actualR = android.graphics.Color.red(pixel)
            val actualG = android.graphics.Color.green(pixel)
            val actualB = android.graphics.Color.blue(pixel)

            val matched =
                kotlin.math.abs(actualR - targetR) <= tolerance &&
                        kotlin.math.abs(actualG - targetG) <= tolerance &&
                        kotlin.math.abs(actualB - targetB) <= tolerance

            Log.d(
                TAG,
                "COLOR CONDITION: actual=($actualR,$actualG,$actualB) " +
                        "target=($targetR,$targetG,$targetB) tolerance=$tolerance matched=$matched"
            )

            matched

        } finally {

            bitmap.recycle()
        }
    }

    // ------------------------------------------------------------
    // Image condition
    // ------------------------------------------------------------

    /**
     * 画像認識(IMAGE)のステップか。
     */
    private fun MacroStep.isImageCondition(): Boolean {
        return actionType == ActionType.CONDITION &&
                ConditionType.fromName(conditionType) == ConditionType.IMAGE
    }

    /**
     * 画像認識の判定を行う。「見つかるまで待つ」の設定があれば、
     * 見つかるか、時間切れになるまで繰り返す。
     *
     *  - 最大秒数が未設定: 1回だけ判定する。
     *  - 最大秒数が設定済み: 見つかるまで、判定→一定時間待つ、を繰り返す。
     *    時間切れは「見つからなかった」(Completed(met = false))として扱う。
     *  - 判定そのものができなかった場合(Failed)は、待たずにすぐ返す。
     *
     * 待っている間も、STOPで中断できる(delayとキャプチャは中断可能)。
     * 最大秒数は実際の経過時間で数え、実行速度の倍率の影響は受けない。
     */
    private suspend fun evaluateImageConditionWithWait(
        step: MacroStep,
        template: Bitmap?,
        stepNumber: Int
    ): ImageEvaluation {

        val timeoutSec = step.imageWaitTimeoutSec

        // 待たない設定。今までどおり、1回だけ判定する。
        if (timeoutSec == null) {

            return evaluateImageCondition(
                step,
                template,
                stepNumber
            )
        }

        val startedAt = SystemClock.elapsedRealtime()
        val deadline = startedAt + timeoutSec * 1000L

        while (true) {

            val evaluation =
                evaluateImageCondition(
                    step,
                    template,
                    stepNumber
                )

            when (evaluation) {

                // 判定できなかった。待ち続けず、すぐ呼び出し側へ返す(停止される)。
                ImageEvaluation.Failed ->
                    return evaluation

                is ImageEvaluation.Completed ->
                    if (evaluation.met) {

                        Log.d(
                            TAG,
                            "IMAGE WAIT: found: step=$stepNumber, " +
                                    "waited=${SystemClock.elapsedRealtime() - startedAt}ms"
                        )

                        return evaluation
                    }
            }

            val now = SystemClock.elapsedRealtime()

            if (now >= deadline) {

                Log.d(
                    TAG,
                    "IMAGE WAIT: timed out: step=$stepNumber, " +
                            "waited=${now - startedAt}ms, timeout=${timeoutSec}s"
                )

                // 時間切れは「見つからなかった」として扱う。
                return ImageEvaluation.Completed(met = false)
            }

            // 次の判定まで待つ。ただし、締め切りを大きく超えないようにする。
            delay(
                minOf(
                    IMAGE_POLL_INTERVAL_MS,
                    deadline - now
                )
            )
        }
    }

    /**
     * 画像が見つかった位置をタップし、その後の待機時間だけ待つ。
     *
     * 待機時間は、ステップに設定された値(waitTime)を使う。
     * 未設定の場合は既定値を使う。実行速度の倍率は、他のステップの
     * 待機時間と同様に、この待機時間にも適用する。
     *
     * タップに失敗した場合は、通常のタップのステップと同様に、
     * ログを残して先へ進む。
     */
    private suspend fun tapFoundImage(
        step: MacroStep,
        found: ImageEvaluation.Completed,
        speed: Float,
        stepNumber: Int
    ) {

        val x = found.matchX
        val y = found.matchY

        if (x == null || y == null) {

            Log.e(
                TAG,
                "IMAGE TAP: match position is missing: step=$stepNumber"
            )

            return
        }

        val success =
            performTap(
                x,
                y
            )

        Log.d(
            TAG,
            "IMAGE TAP: step=$stepNumber, x=$x, y=$y, success=$success"
        )

        val baseWaitMs =
            step.waitTime ?: DEFAULT_IMAGE_TAP_WAIT_MS

        val waitMs =
            if (speed > 0f) {
                (baseWaitMs / speed).toLong()
            } else {
                baseWaitMs
            }

        if (waitMs > 0) {

            delay(waitMs)
        }
    }

    /**
     * 画面にテンプレート画像が見えているかを、1回判定する。
     *
     * 1. 画面をキャプチャする(上限時間あり。失敗したら最大回数まで再試行)
     * 2. テンプレート画像と照合する(重い処理のため、別スレッドで実行)
     * 3. 一致度が、しきい値以上なら「見つかった」とする(位置も結果に含める)
     *
     * 判定そのものができなかった場合は、「見つからなかった」ではなく
     * ImageEvaluation.Failed を返す。呼び出し側は、その場合に実行を停止する。
     *
     * @param template 実行の開始前に読み込んだテンプレート画像(recycleしない)
     * @param stepNumber ログ表示用のステップ番号(1始まり)
     */
    private suspend fun evaluateImageCondition(
        step: MacroStep,
        template: Bitmap?,
        stepNumber: Int
    ): ImageEvaluation {

        if (template == null) {

            Log.e(
                TAG,
                "IMAGE CONDITION: template is not loaded: step=$stepNumber"
            )

            return ImageEvaluation.Failed
        }

        val screenshotService =
            ScreenshotCaptureService.instance
                ?: run {
                    Log.e(
                        TAG,
                        "IMAGE CONDITION: screenshot service is null: step=$stepNumber"
                    )
                    return ImageEvaluation.Failed
                }

        val screen: Bitmap =
            captureScreenWithRetry(
                screenshotService,
                stepNumber
            )
                ?: run {
                    Log.e(
                        TAG,
                        "IMAGE CONDITION: failed to capture screen: step=$stepNumber"
                    )
                    return ImageEvaluation.Failed
                }

        return try {

            // テンプレートを作った時と画面サイズが違うと、一致しにくくなる。
            // 停止はせず、原因調査のための警告だけを残す。
            val templateScreenWidth = step.templateScreenWidth
            val templateScreenHeight = step.templateScreenHeight

            if (
                templateScreenWidth != null &&
                templateScreenHeight != null &&
                (
                        templateScreenWidth != screen.width ||
                                templateScreenHeight != screen.height
                        )
            ) {

                Log.w(
                    TAG,
                    "IMAGE CONDITION: screen size differs from template creation: " +
                            "step=$stepNumber, " +
                            "created=${templateScreenWidth}x${templateScreenHeight}, " +
                            "now=${screen.width}x${screen.height}"
                )
            }

            val startedAt = System.currentTimeMillis()

            // 重い処理なので、メインスレッドではなくDefaultで実行する。
            val match =
                withContext(Dispatchers.Default) {
                    templateMatcher.findBestMatch(
                        screen,
                        template
                    )
                }

            val elapsedMs = System.currentTimeMillis() - startedAt

            if (match == null) {

                Log.e(
                    TAG,
                    "IMAGE CONDITION: matching failed: step=$stepNumber, elapsed=${elapsedMs}ms"
                )

                ImageEvaluation.Failed

            } else {

                val threshold =
                    step.imageMatchThreshold ?: DEFAULT_IMAGE_MATCH_THRESHOLD

                val found = match.score >= threshold

                Log.d(
                    TAG,
                    "IMAGE CONDITION: step=$stepNumber, score=${"%.3f".format(match.score)}, " +
                            "threshold=${"%.2f".format(threshold)}, found=$found, " +
                            "at=(${match.centerX},${match.centerY}), elapsed=${elapsedMs}ms"
                )

                if (found) {

                    // 見つかった場合は、タップに使えるよう、位置も結果に含める。
                    ImageEvaluation.Completed(
                        met = true,
                        matchX = match.centerX,
                        matchY = match.centerY
                    )

                } else {

                    ImageEvaluation.Completed(met = false)
                }
            }

        } finally {

            screen.recycle()
        }
    }

    /**
     * 画面をキャプチャする。上限時間内に撮れなかった場合は、
     * 最大回数まで再試行する。それでも撮れなければnull。
     *
     * 画面が静止していると、次のフレームが届かず、キャプチャが待ち続ける
     * 可能性があるため、1回ごとに上限時間を設けている。
     */
    private suspend fun captureScreenWithRetry(
        screenshotService: ScreenshotCaptureService,
        stepNumber: Int
    ): Bitmap? {

        for (attempt in 1..IMAGE_CAPTURE_MAX_ATTEMPTS) {

            val bitmap =
                withTimeoutOrNull(
                    IMAGE_CAPTURE_TIMEOUT_MS
                ) {
                    screenshotService.captureBitmap()
                }

            if (bitmap != null) {
                return bitmap
            }

            Log.w(
                TAG,
                "IMAGE CONDITION: capture failed or timed out: " +
                        "step=$stepNumber, attempt=$attempt/$IMAGE_CAPTURE_MAX_ATTEMPTS"
            )

            if (attempt < IMAGE_CAPTURE_MAX_ATTEMPTS) {
                delay(IMAGE_CAPTURE_RETRY_DELAY_MS)
            }
        }

        return null
    }

    // ------------------------------------------------------------
    // Label lookup
    // ------------------------------------------------------------

    /**
     * targetLabelと同じラベル名を持つLABELステップの
     * インデックスを探す。
     *
     * 見つからない場合は-1を返す。
     */
    private fun findLabelIndex(
        steps: List<MacroStep>,
        targetLabel: String?
    ): Int {

        if (targetLabel.isNullOrBlank()) {
            return -1
        }

        return steps.indexOfFirst {
            it.actionType == ActionType.LABEL &&
                    it.targetLabel == targetLabel
        }
    }

    // ------------------------------------------------------------
    // Speed
    // ------------------------------------------------------------

    private fun applySpeed(
        step: MacroStep,
        speed: Float
    ): MacroStep {

        if (speed <= 0f) {
            return step
        }

        return step.copy(

            duration =
                step.duration?.let {
                    (it / speed).toLong()
                },

            waitTime =
                step.waitTime?.let {
                    (it / speed).toLong()
                }
        )
    }

    // ------------------------------------------------------------
    // Gesture
    // ------------------------------------------------------------

    suspend fun performTap(
        x: Int,
        y: Int
    ): Boolean {

        Log.d(
            TAG,
            "performTap x=$x y=$y"
        )

        val path =
            Path().apply {

                moveTo(
                    x.toFloat(),
                    y.toFloat()
                )
            }

        return dispatchStroke(
            path,
            startTime = 0,
            duration = 100
        )
    }

    suspend fun performLongPress(
        x: Int,
        y: Int,
        duration: Long
    ): Boolean {

        val path =
            Path().apply {

                moveTo(
                    x.toFloat(),
                    y.toFloat()
                )
            }

        return dispatchStroke(
            path,
            startTime = 0,
            duration = duration
        )
    }

    suspend fun performSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): Boolean {

        val path =
            Path().apply {

                moveTo(
                    startX.toFloat(),
                    startY.toFloat()
                )

                lineTo(
                    endX.toFloat(),
                    endY.toFloat()
                )
            }

        return dispatchStroke(
            path,
            startTime = 0,
            duration = duration
        )
    }

    private suspend fun dispatchStroke(
        path: Path,
        startTime: Long,
        duration: Long
    ): Boolean {

        val stroke =
            GestureDescription.StrokeDescription(
                path,
                startTime,
                duration
            )

        val gesture =
            GestureDescription.Builder()
                .addStroke(stroke)
                .build()

        val result =
            withTimeoutOrNull(3000) {

                suspendCancellableCoroutine<Boolean> {
                        continuation ->

                    val callback =
                        object :
                            GestureResultCallback() {

                            override fun onCompleted(
                                gestureDescription:
                                GestureDescription?
                            ) {

                                Log.d(
                                    TAG,
                                    "Gesture completed"
                                )

                                if (
                                    continuation.isActive
                                ) {

                                    continuation.resume(
                                        true
                                    )
                                }
                            }

                            override fun onCancelled(
                                gestureDescription:
                                GestureDescription?
                            ) {

                                Log.w(
                                    TAG,
                                    "Gesture cancelled"
                                )

                                if (
                                    continuation.isActive
                                ) {

                                    continuation.resume(
                                        false
                                    )
                                }
                            }
                        }

                    val dispatched =
                        dispatchGesture(
                            gesture,
                            callback,
                            null
                        )

                    Log.d(
                        TAG,
                        "dispatchGesture() returned: $dispatched"
                    )

                    if (
                        !dispatched &&
                        continuation.isActive
                    ) {

                        continuation.resume(
                            false
                        )
                    }
                }
            }

        if (result == null) {

            Log.e(
                TAG,
                "Gesture callback timed out (3s) - treating as failure"
            )
        }

        return result ?: false
    }
}