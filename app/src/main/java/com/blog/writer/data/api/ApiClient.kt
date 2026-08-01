package com.blog.writer.data.api

import com.blog.writer.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClient {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        // BuildConfig.DEBUG 在 release 编译期是常量 false，R8 会把这个分支
        // 和 HttpLoggingInterceptor 一起当作死代码整体裁掉，不会计入 release 包体积
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        builder.build()
    }

    val githubApi: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }
}
