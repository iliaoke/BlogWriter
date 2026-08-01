package com.blog.writer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val default_branch: String = "main",
    val owner: GitHubOwner
)

@Serializable
data class GitHubOwner(
    val login: String,
    val avatar_url: String? = null
)

@Serializable
data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val type: String, // "file" or "dir"
    val content: String? = null, // base64, 仅文件详情接口返回
    val encoding: String? = null,
    val download_url: String? = null
)

@Serializable
data class GitHubUser(
    val login: String,
    val avatar_url: String? = null,
    val name: String? = null
)

/** 用于更新/创建文件的请求体 */
@Serializable
data class UpdateFileRequest(
    val message: String,
    val content: String, // base64
    val sha: String? = null,
    val branch: String? = null
)

@Serializable
data class UpdateFileResponse(
    val content: GitHubContent? = null
)

/** APP 内部表示一篇博客文章 */
data class BlogPost(
    val folderName: String,   // 子文件夹名，作为文章标题/slug
    val path: String,         // index.md 在仓库中的完整路径
    val sha: String,          // 用于后续更新
    val title: String,        // 从 front-matter 或文件名解析出的标题
    val excerpt: String = ""  // 内容摘要，供列表预览
)
