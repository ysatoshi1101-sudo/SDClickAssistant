package com.example.sdclickassistant.data.mapper

import com.example.sdclickassistant.data.local.entity.ScenarioEntity
import com.example.sdclickassistant.data.local.entity.StepEntity
import com.example.sdclickassistant.data.local.relation.ScenarioWithSteps
import com.example.sdclickassistant.domain.model.ActionType
import com.example.sdclickassistant.domain.model.MacroScenario
import com.example.sdclickassistant.domain.model.MacroStep

// ---- Entity → ドメインモデル ----

fun StepEntity.toDomain(): MacroStep {
    return MacroStep(
        id = id,
        order = order,
        actionType = ActionType.valueOf(actionType),
        x = x,
        y = y,
        endX = endX,
        endY = endY,
        duration = duration,
        waitTime = waitTime,
        condition = condition,
        targetLabel = targetLabel,
        loopCount = loopCount,
        conditionType = conditionType,
        colorR = colorR,
        colorG = colorG,
        colorB = colorB,
        colorTolerance = colorTolerance,
        invertCondition = invertCondition,
        imageMatchThreshold = imageMatchThreshold,
        templateScreenWidth = templateScreenWidth,
        templateScreenHeight = templateScreenHeight,
        tapOnImageFound = tapOnImageFound,
        imageWaitTimeoutSec = imageWaitTimeoutSec
    )
}

fun ScenarioEntity.toDomain(steps: List<MacroStep> = emptyList()): MacroScenario {
    return MacroScenario(
        id = id,
        name = name,
        repeatCount = repeatCount,
        isInfiniteLoop = isInfiniteLoop,
        speed = speed,
        createdDate = createdDate,
        updatedDate = updatedDate,
        steps = steps,
        isFavorite = isFavorite,
        order = order,
        folderId = folderId
    )
}

fun ScenarioWithSteps.toDomain(): MacroScenario {
    return scenario.toDomain(steps.sortedBy { it.order }.map { it.toDomain() })
}

// ---- ドメインモデル → Entity ----

fun MacroScenario.toEntity(): ScenarioEntity {
    return ScenarioEntity(
        id = id,
        name = name,
        repeatCount = repeatCount,
        isInfiniteLoop = isInfiniteLoop,
        speed = speed,
        createdDate = createdDate,
        updatedDate = updatedDate,
        isFavorite = isFavorite,
        order = order,
        folderId = folderId
    )
}

fun MacroStep.toEntity(scenarioId: String): StepEntity {
    return StepEntity(
        id = id,
        scenarioId = scenarioId,
        order = order,
        actionType = actionType.name,
        x = x,
        y = y,
        endX = endX,
        endY = endY,
        duration = duration,
        waitTime = waitTime,
        condition = condition,
        targetLabel = targetLabel,
        loopCount = loopCount,
        conditionType = conditionType,
        colorR = colorR,
        colorG = colorG,
        colorB = colorB,
        colorTolerance = colorTolerance,
        invertCondition = invertCondition,
        imageMatchThreshold = imageMatchThreshold,
        templateScreenWidth = templateScreenWidth,
        templateScreenHeight = templateScreenHeight,
        tapOnImageFound = tapOnImageFound,
        imageWaitTimeoutSec = imageWaitTimeoutSec
    )
}