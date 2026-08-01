package com.blog.writer.data

import android.content.Context
import androidx.core.content.edit
import com.blog.writer.data.model.GitHubRepo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 持久化“已选仓库 + 已选博客目录”这一步的结果（固化选择）。
 * 用户一旦选定仓库和目录，下次冷启动会跳过仓库/目录选择流程，
 * 直接进入文章列表首页；只有用户主动点击“退出”按钮时才会清空，
 * 回到仓库选择页重新选。和登录态（TokenStore）分开存储、互不影响。
 */
class RepoConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("repo_config_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_REPO = "selected_repo_json"
        private const val KEY_BASE_FOLDER = "selected_base_folder"
    }

    fun getRepo(): GitHubRepo? {
        val raw = prefs.getString(KEY_REPO, null) ?: return null
        return runCatching { json.decodeFromString<GitHubRepo>(raw) }.getOrNull()
    }

    /** 注意：仓库根目录对应空字符串 ""，也是合法的已固化目录，不能当作“未设置”处理 */
    fun getBaseFolder(): String? = prefs.getString(KEY_BASE_FOLDER, null)

    fun save(repo: GitHubRepo, baseFolder: String) {
        prefs.edit {
            putString(KEY_REPO, json.encodeToString(repo))
            putString(KEY_BASE_FOLDER, baseFolder)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
