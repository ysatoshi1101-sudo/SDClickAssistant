package com.example.sdclickassistant.data.local.dao

import androidx.room.*
import com.example.sdclickassistant.data.local.entity.StepEntity

@Dao
interface StepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: StepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(steps: List<StepEntity>)

    @Update
    suspend fun update(step: StepEntity)

    @Delete
    suspend fun delete(step: StepEntity)

    @Query("DELETE FROM steps WHERE scenarioId = :scenarioId")
    suspend fun deleteAllForScenario(scenarioId: String)

    @Query("SELECT * FROM steps WHERE scenarioId = :scenarioId ORDER BY `order` ASC")
    suspend fun getStepsForScenario(scenarioId: String): List<StepEntity>
}