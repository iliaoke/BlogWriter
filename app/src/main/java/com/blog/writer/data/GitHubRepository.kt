package com.blog.writer.data

import android.util.Base64
import com.blog.writer.data.api.ApiClient
import com.blog.writer.data.model.BlogPost
import com.blog.writer.data.model.GitHubContent
import com.blog.writer.data.model.GitHubRepo
import com.blog.writer.data.model.UpdateFileRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GitHubRepository(private val token: String) {

    private val api = ApiClient.githubApi
    private fun auth() = "Bearer $token"

    suspend fun getCurrentLogin(): String? =
        api.getUser(auth()).body()?.login

    suspend fun listRepos(): List<GitHubRepo> {
        val result = mutableListOf<GitHubRepo>()
        var page = 1
        while (true) {
            val resp = api.getUserRepos(auth(), page = page)
            val body = resp.body().orEmpty()
            if (body.isEmpty()) break
            result += body
            if (body.size < 100) break
            page++
        }
        return result
    }

    /** 列出某路径下的目录/文件，path 传空字符串表示仓库根目录 */
    suspend fun listContents(owner: String, repo: String, path: String): List<GitHubContent> {
        val resp = api.getContents(auth(), owner, repo, path)
        return resp.body().orEmpty()
    }

    /**
     * 遍历 baseFolder 下的所有一级子文件夹，找到每个子文件夹里的 index.md，
     * 解析出标题与摘要，组装为 BlogPost 列表。
     */
    suspend fun scanBlogPosts(owner: String, repo: String, baseFolder: String): List<BlogPost> = coroutineScope {
        val subDirs = listContents(owner, repo, baseFolder).filter { it.type == "dir" }
        val deferred = subDirs.map { dir ->
            async {
                runCatching {
                    // index.md 路径是约定固定的，直接按路径取文件内容即可；
                    // 不存在就是接口报错/返回空，走 getOrNull 兜底为 null，
                    // 不需要像之前那样先 listContents 确认存在再取一次内容（省一次网络往返）。
                    val indexPath = if (baseFolder.isBlank()) "${dir.name}/index.md" else "${dir.path}/index.md"
                    val fileDetail = api.getFileContent(auth(), owner, repo, indexPath).body()
                        ?: return@runCatching null
                    val content = decodeBase64Content(fileDetail.content)
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
        deferred.awaitAll().filterNotNull()
    }

    suspend fun getFileRaw(owner: String, repo: String, path: String): Pair<String, String>? {
        val resp = api.getFileContent(auth(), owner, repo, path).body() ?: return null
        return decodeBase64Content(resp.content) to resp.sha
    }

    suspend fun saveFile(
        owner: String,
        repo: String,
        path: String,
        newContent: String,
        sha: String,
        commitMessage: String = "更新文章 via BlogWriter"
    ): Boolean {
        val encoded = Base64.encodeToString(newContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = UpdateFileRequest(message = commitMessage, content = encoded, sha = sha)
        val resp = api.updateFile(auth(), owner, repo, path, body)
        return resp.isSuccessful
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
