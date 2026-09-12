package com.yumiru11.githubapp.core.datastore.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yumiru11.githubapp.core.datastore.draft.DefaultDraftRepository
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.datastore.draft.DraftRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 草稿持久化装配（需求审计 §10 P2「草稿无持久化」）。
 *
 * 与用户偏好**共用** [DataStore]（同一个 `user_preferences` 文件，见 [DataStoreModule]）：
 * 草稿键有独立前缀（`draft.` / `draft_at.`），不与偏好键空间重叠。
 *
 * [DraftAutoSaver] 是单例：它持有应用级写入 scope（ViewModel 销毁后仍能完成落盘），
 * 单例化与"写入不随页面消失"的语义一致；按草稿键分别跟踪待写任务，故多页面并发安全。
 */
@Module
@InstallIn(SingletonComponent::class)
object DraftModule {
    @Provides
    @Singleton
    fun provideDraftRepository(dataStore: DataStore<Preferences>): DraftRepository = DefaultDraftRepository(dataStore)

    @Provides
    @Singleton
    fun provideDraftAutoSaver(repository: DraftRepository): DraftAutoSaver = DraftAutoSaver(repository)
}
