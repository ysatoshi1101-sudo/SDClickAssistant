package com.example.sdclickassistant.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.sdclickassistant.data.local.entity.ScenarioEntity
import com.example.sdclickassistant.data.local.entity.StepEntity

data class ScenarioWithSteps(
    @Embedded val scenario: ScenarioEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "scenarioId"
    )
    val steps: List<StepEntity>
)