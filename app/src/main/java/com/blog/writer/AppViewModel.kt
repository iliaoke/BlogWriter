package com.blog.writer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blog.writer.data.GitHubRepository
import com.blog.writer.data.TokenStore
import com.blog.writer.data.model.BlogPost
import com.blog.writer.data.model.GitHubContent
import com.blog.writer.data.model.GitHubRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val isLoading: Boolean = false,
    val token: String? = null,
    val login: String? = null,
    val repos: List<GitHubRepo> = emptyList(),
    val selectedRepo: GitHubRepo? = null,
    val currentFolderPath: String = "",
    val folderContents: List<GitHubContent> = emptyList(), // 目录选择时展示的内容
    val selectedBaseFolder: String? = null,
    val posts: List<BlogPost> = emptyList(),
    val errorMessage: String? = null
)

/**
 * access_token 只保存在这个 ViewModel 的内存状态里（进程存活期间有效），
 * 不落盘、不写 DataStore/SharedPreferences —— APP 被杀掉或重启后需要重新走一遍 GitHub 授权。
 * 好处是简单、不用管加密存储和过期清理；代价是每次冷启动都要重新登录。
 */
/**
 * access_token 持久化在 SharedPreferences（见 TokenStore）里，APP 重启后会自动读回，
 * 不需要重新走一遍 GitHub 授权；只有用户主动退出登录时才会清空。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application)
    private var repository: GitHubRepository? = null

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        // SharedPreferences 读取是同步的、几乎瞬时完成，直接在 init 里做即可
        val savedToken = tokenStore.getToken()
        if (!savedToken.isNullOrBlank()) {
            onTokenReceived(savedToken, tokenStore.getScope().orEmpty(), persist = false)
        }
    }

    fun onTokenReceived(token: String, scope: String = "", persist: Boolean = true) {
        repository = GitHubRepository(token)
        _uiState.value = _uiState.value.copy(token = token, isLoading = true)
        if (persist) {
            tokenStore.saveToken(token, scope)
        }
        viewModelScope.launch {
            val login = runCatching { repository?.getCurrentLogin() }.getOrNull()
            _uiState.value = _uiState.value.copy(login = login, isLoading = false)
            loadRepos()
        }
    }

    fun loadRepos() {
        val repo = repository ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching { repo.listRepos() }
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, repos = list)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
                }
        }
    }

    fun selectRepo(repo: GitHubRepo) {
        _uiState.value = _uiState.value.copy(selectedRepo = repo, currentFolderPath = "")
        browseFolder("")
    }

    /** 浏览仓库目录，用于让用户选择存放博客的文件夹 */
    fun browseFolder(path: String) {
        val repo = repository ?: return
        val selectedRepo = _uiState.value.selectedRepo ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                repo.listContents(selectedRepo.owner.login, selectedRepo.name, path)
            }.onSuccess { list ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentFolderPath = path,
                    folderContents = list.filter { it.type == "dir" }
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
            }
        }
    }

    /** 确认将当前浏览的路径作为博客存放文件夹，并开始扫描所有 index.md */
    fun confirmBaseFolder(path: String) {
        val repo = repository ?: return
        val selectedRepo = _uiState.value.selectedRepo ?: return
        _uiState.value = _uiState.value.copy(selectedBaseFolder = path)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                repo.scanBlogPosts(selectedRepo.owner.login, selectedRepo.name, path)
            }.onSuccess { list ->
                _uiState.value = _uiState.value.copy(isLoading = false, posts = list)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
            }
        }
    }

    fun refreshPosts() {
        val base = _uiState.value.selectedBaseFolder ?: return
        confirmBaseFolder(base)
    }

    suspend fun loadPostContent(path: String): Pair<String, String>? {
        val repo = repository ?: return null
        val selectedRepo = _uiState.value.selectedRepo ?: return null
        return runCatching {
            repo.getFileRaw(selectedRepo.owner.login, selectedRepo.name, path)
        }.getOrNull()
    }

    suspend fun savePostContent(path: String, content: String, sha: String): Boolean {
        val repo = repository ?: return false
        val selectedRepo = _uiState.value.selectedRepo ?: return false
        return runCatching {
            repo.saveFile(selectedRepo.owner.login, selectedRepo.name, path, content, sha)
        }.getOrDefault(false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** 退出登录：清空内存状态，并把 SharedPreferences 里的 token 一并删掉 */
    fun logout() {
        tokenStore.clear()
        repository = null
        _uiState.value = AppUiState()
    }
}
