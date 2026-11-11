package com.example.sdclickassistant.di

import android.content.Context
import androidx.room.Room
import com.example.sdclickassistant.data.local.ALL_MIGRATIONS
import com.example.sdclickassistant.data.local.AppDatabase
import com.example.sdclickassistant.data.local.dao.FolderDao
import com.example.sdclickassistant.data.local.dao.ScenarioDao
import com.example.sdclickassistant.data.local.dao.StepDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sd_click_assistant_db"
        )
            // バージョン5以降は、Migrations.ktのマイグレーションでデータを引き継ぐ。
            .addMigrations(*ALL_MIGRATIONS)
            // マイグレーションを用意していない古いバージョン(1〜4)から来た場合だけ、
            // DBを作り直す(既存データは消える)。
            // 5以降でマイグレーションの書き漏れがあった場合は、
            // 黙ってデータを消さず、エラーで止まるようにしている。
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
            .build()
    }

    @Provides
    fun provideScenarioDao(database: AppDatabase): ScenarioDao {
        return database.scenarioDao()
    }

    @Provides
    fun provideStepDao(database: AppDatabase): StepDao {
        return database.stepDao()
    }

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao {
        return database.folderDao()
    }
}