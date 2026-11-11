package com.example.sdclickassistant.di

import com.example.sdclickassistant.data.repository.FolderRepository
import com.example.sdclickassistant.data.repository.FolderRepositoryImpl
import com.example.sdclickassistant.data.repository.ScenarioRepository
import com.example.sdclickassistant.data.repository.ScenarioRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScenarioRepository(
        impl: ScenarioRepositoryImpl
    ): ScenarioRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: FolderRepositoryImpl
    ): FolderRepository
}