package com.blog.writer.data.api

/**
 * 统一的 GitHub API 请求异常。之前 Repository 层只判断 body() 是否为空，
 * 401/403/404 等失败响应的 body 同样是 null，会被 orEmpty() 悄悄当成
 * "没有数据"，导致鉴权失败、限流这些情况在 UI 上表现成"空列表"，
 * 用户和开发者都无法感知。改成显式检查 isSuccessful，失败时抛出这个异常，
 * 携带状态码和 GitHub 返回的错误信息，方便上层展示给用户。
 */
class GitHubApiException(
    val httpCode: Int,
    message: String
) : Exception("GitHub 请求失败（$httpCode）：$message")
