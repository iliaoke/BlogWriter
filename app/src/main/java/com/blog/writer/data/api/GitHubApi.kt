package com.blog.writer.data.api

import com.blog.writer.data.model.GitHubContent
import com.blog.writer.data.model.GitHubRepo
import com.blog.writer.data.model.GitHubUser
import com.blog.writer.data.model.UpdateFileRequest
import com.blog.writer.data.model.UpdateFileResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {

    @GET("user")
    suspend fun getUser(@Header("Authorization") token: String): Response<GitHubUser>

    // 获取当前用户可访问的所有仓库（含自己的和有权限的组织仓库）
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "updated"
    ): Response<List<GitHubRepo>>

    // 获取某个路径下的目录内容；path 为空字符串表示仓库根目录
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<List<GitHubContent>>

    // 获取单个文件内容（同一 URL，当 path 指向文件时返回单对象，这里单独声明便于类型区分）
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<GitHubContent>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Body body: UpdateFileRequest
    ): Response<UpdateFileResponse>
}
