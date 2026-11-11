package com.example.sdclickassistant.di

import com.example.sdclickassistant.data.matcher.OpenCvTemplateMatcher
import com.example.sdclickassistant.data.matcher.TemplateMatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 画像認識(テンプレートマッチング)の実装を登録する。
 *
 * 呼び出し側は TemplateMatcher(インターフェース)だけを受け取り、
 * OpenCVを使っていることを知らなくてよい。
 * 将来、別の方式に入れ替える時は、ここの1か所を変えればよい。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MatcherModule {

    @Binds
    @Singleton
    abstract fun bindTemplateMatcher(
        impl: OpenCvTemplateMatcher
    ): TemplateMatcher
}