package com.yumiru11.githubapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口：Hilt 图根节点。
 *
 * 所有模块的 DI（REST/GraphQL 通道、仓库层、Room、DataStore）在
 * SingletonComponent 上装配，MainActivity 经 @AndroidEntryPoint 注入。
 */
@HiltAndroidApp
class GitHubApp : Application()
