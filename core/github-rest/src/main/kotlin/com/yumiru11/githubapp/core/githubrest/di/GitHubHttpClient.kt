package com.yumiru11.githubapp.core.githubrest.di

import javax.inject.Qualifier

/** 限定 GitHub API 专用 OkHttpClient（与认证流程等其他 client 区分） */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubHttpClient
