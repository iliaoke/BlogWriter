package com.blog.writer.data

import android.util.Base64
import com.blog.writer.data.api.ApiClient
import com.blog.writer.data.api.GitHubApiException
import com.blog.writer.data.model.BlogPost
import com.blog.writer.data.model.GitHubContent
import com.blog.writer.data.model.GitHubRepo
import com.blog.writer.data.model.UpdateFileRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.Response

/** 扫描目录时的最大并发请求数，避免子文件夹很多时一次性打出几十上百个并发请求触发 GitHub 的二级限流 */
private const val SCAN_CONCURRENCY = 6

class GitHubRepository(private val token: String) {

    private val api = ApiClient.githubApi
    private fun auth() = "Bearer $token"

    /**
     * 统一的“取 body 或抛异常”辅助函数。之前的写法是 resp.body().orEmpty()，
     * 401/403/404 等失败响应的 body 同样是 null，会被 orEmpty() 悄悄当成
     * “没有数据”，导致鉴权失败、限流这些情况在 UI 上表现成空列表，且没有
     * 任何报错。这里显式检查 isSuccessful，失败时抛出携带状态码和 GitHub
     * 错误信息的异常，交给上层 runCatching 捕获并展示给用户。
     */
    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) {
            val detail = errorBody()?.string()?.takeIf { it.isNotBlank() } ?: message()
            throw GitHubApiException(code(), detail)
        }
        return body() ?: throw GitHubApiException(code(), "响应内容为空")
    }

    suspend fun getCurrentLogin(): String? =
        runCatching { api.getUser(auth()).bodyOrThrow().login }.getOrNull()

    suspend fun listRepos(): List<GitHubRepo> {
        val result = mutableListOf<GitHubRepo>()
        var page = 1
        while (true) {
            val body = api.getUserRepos(auth(), page = page).bodyOrThrow()
            if (body.isEmpty()) break
            result += body
            if (body.size < 100) break
            page++
        }
        return result
    }

    /** 列出某路径下的目录/文件，path 传空字符串表示仓库根目录 */
    suspend fun listContents(owner: String, repo: String, path: String): List<GitHubContent> {
        return api.getContents(auth(), owner, repo, path).bodyOrThrow()
    }

    /**
     * 遍历 baseFolder 下的所有一级子文件夹，找到每个子文件夹里的 index.md，
     * 解析出标题与摘要，组装为 BlogPost 列表。
     * 用 Semaphore 限制并发数，避免子文件夹很多时一次性打出大量并发请求。
     */
    suspend fun scanBlogPosts(owner: String, repo: String, baseFolder: String): List<BlogPost> = coroutineScope {
        val subDirs = listContents(owner, repo, baseFolder).filter { it.type == "dir" }
        val semaphore = Semaphore(SCAN_CONCURRENCY)
        val deferred = subDirs.map { dir ->
            async {
                semaphore.withPermit {
                    runCatching {
                        // index.md 路径是约定固定的，直接按路径取文件内容即可；
                        // 不存在就是接口报错/返回空，走 getOrNull 兜底为 null。
                        val indexPath = if (baseFolder.isBlank()) "${dir.name}/index.md" else "${dir.path}/index.md"
                        val fileDetail = api.getFileContent(auth(), owner, repo, indexPath).body()
                            ?: return@runCatching null
                        val content = resolveContent(owner, repo, fileDetail)
                        val title = parseTitle(content) ?: dir.name
                        val excerpt = parseExcerpt(content)
                        BlogPost(
                            folderName = dir.name,
                            path = fileDetail.path,
                            sha = fileDetail.sha,
                            title = title,
                            excerpt = excerpt
                        )
                    }.getOrNull()
                }
            }
        }
        deferred.awaitAll().filterNotNull()
    }

    suspend fun getFileRaw(owner: String, repo: String, path: String): Pair<String, String>? {
        val resp = api.getFileContent(auth(), owner, repo, path).body() ?: return null
        return resolveContent(owner, repo, resp) to resp.sha
    }

    /**
     * @return 保存成功时返回 GitHub 侧生成的新 sha（下次保存要用这个新 sha，
     * 否则会因为 sha 不匹配被 GitHub 拒绝），失败返回 null。
     * 之前这里只返回 Boolean，调用方为了拿新 sha 还要额外发一次 GET 请求，
     * 其实 PUT 的响应体里就带着新 sha，直接从这里返回即可，省一次网络往返。
     */
    suspend fun saveFile(
        owner: String,
        repo: String,
        path: String,
        newContent: String,
        sha: String,
        commitMessage: String = "更新文章 via BlogWriter"
    ): String? {
        val encoded = Base64.encodeToString(newContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = UpdateFileRequest(message = commitMessage, content = encoded, sha = sha)
        val resp = api.updateFile(auth(), owner, repo, path, body)
        if (!resp.isSuccessful) return null
        return resp.body()?.content?.sha
    }

    /**
     * Contents API 对 >1MB 的文件不会内联返回 base64 内容（content 字段为 null），
     * 之前遇到这种情况会被静默解码成空字符串，用户打开的文章看起来内容丢了。
     * 这里在 content 为空时，改用 Git Blobs API（以文件的 sha 查询，不受 1MB 限制）兜底取内容。
     */
    private suspend fun resolveContent(owner: String, repo: String, file: GitHubContent): String {
        if (file.content != null) return decodeBase64Content(file.content)
        val blob = runCatching { api.getBlob(auth(), owner, repo, file.sha).bodyOrThrow() }.getOrNull()
        return decodeBase64Content(blob?.content)
    }

    private fun decodeBase64Content(content: String?): String {
        if (content == null) return ""
        val cleaned = content.replace("\n", "")
        return String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
    }

    /** 简单解析 Markdown front-matter 或首个一级标题作为文章标题 */
    private fun parseTitle(markdown: String): String? {
        val frontMatterRegex = Regex("""^---\s*\n([\s\S]*?)\n---""")
        val fm = frontMatterRegex.find(markdown)?.groupValues?.get(1)
        if (fm != null) {
            val titleLine = fm.lines().firstOrNull { it.trim().startsWith("title:") }
            if (titleLine != null) {
                return titleLine.substringAfter("title:").trim().trim('"', '\'')
            }
        }
        val h1 = Regex("""^#\s+(.+)$""", RegexOption.MULTILINE).find(markdown)?.groupValues?.get(1)
        return h1?.trim()
    }

    private fun parseExcerpt(markdown: String): String {
        val withoutFrontMatter = markdown.replace(Regex("""^---[\s\S]*?---"""), "")
        val plain = withoutFrontMatter
            .replace(Regex("""^#.*$""", RegexOption.MULTILINE), "")
            .replace(Regex("""[#*`>\-]"""), "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return plain.take(80)
    }
}
