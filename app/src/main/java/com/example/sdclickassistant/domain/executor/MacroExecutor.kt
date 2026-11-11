package com.example.sdclickassistant.domain.executor

import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.MacroStep
import com.example.sdclickassistant.service.MacroAccessibilityService
import kotlinx.coroutines.delay

sealed class StepResult {
    data object Success : StepResult()
    data object ServiceNotEnabled : StepResult()
    data class Failed(val step: MacroStep) : StepResult()
}

class MacroExecutor {

    /**
     * 1ステップを実行する。
     * Accessibility Serviceが有効でない場合はServiceNotEnabledを返す。
     */
    suspend fun executeStep(step: MacroStep): StepResult {
        val service = MacroAccessibilityService.instance
            ?: return StepResult.ServiceNotEnabled

        val success = when (step.actionType) {
            ActionType.TAP -> {
                val x = step.x ?: return StepResult.Failed(step)
                val y = step.y ?: return StepResult.Failed(step)
                service.performTap(x, y)
            }

            ActionType.LONG_PRESS -> {
                val x = step.x ?: return StepResult.Failed(step)
                val y = step.y ?: return StepResult.Failed(step)
                val duration = step.duration ?: return StepResult.Failed(step)
                service.performLongPress(x, y, duration)
            }

            ActionType.SWIPE -> {
                val x = step.x ?: return StepResult.Failed(step)
                val y = step.y ?: return StepResult.Failed(step)
                val endX = step.endX ?: return StepResult.Failed(step)
                val endY = step.endY ?: return StepResult.Failed(step)
                val duration = step.duration ?: return StepResult.Failed(step)
                service.performSwipe(x, y, endX, endY, duration)
            }

            ActionType.WAIT -> true // 待機自体はジェスチャーではないため常に成功扱い

            else -> return StepResult.Failed(step) // SCREENSHOT等は次フェーズで対応
        }

        if (!success) return StepResult.Failed(step)

        // 各ステップ共通の待機時間(waitTime)を、アクション実行後に適用
        step.waitTime?.let { delay(it) }

        return StepResult.Success
    }
}