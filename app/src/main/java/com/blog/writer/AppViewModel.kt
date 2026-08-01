package com.blog.writer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blog.writer.data.GitHubRepository
import com.blog.writer.data.RepoConfigStore
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
    val errorMessage: String? = null,
    // 冷启动时“是否已经决定好该走哪条路径（恢复到文章列表 / 走仓库选择）”这件事是否完成，
    // 用于让导航层等这个结果出来之后再决定第一次自动跳转去哪个页面，避免跳转两次或跳错。
    val initialRestoreDone: Boolean = false
)

/**
 * access_token 持久化在 SharedPreferences（见 TokenStore）里，APP 重启后会自动读回，
 * 不需要重新走一遍 GitHub 授权；只有用户主动退出登录时才会清空。
 *
 * 仓库 + 博客目录的选择同样会固化（见 RepoConfigStore）：一旦选定，冷启动直接进入
 * 文章列表首页，不需要每次都重新选一遍；只有用户点击“退出”按钮时才清空，回到仓库选择页。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application)
    private val repoConfigStore = RepoConfigStore(application)
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

            // 仓库 + 目录之前固化过的话，直接恢复现场，跳过仓库/目录选择流程
            val savedRepo = repoConfigStore.getRepo()
            val savedFolder = repoConfigStore.getBaseFolder()
            if (savedRepo != null && savedFolder != null) {
                _uiState.value = _uiState.value.copy(
                    login = login,
                    isLoading = false,
                    selectedRepo = savedRepo,
                    currentFolderPath = savedFolder,
                    selectedBaseFolder = savedFolder,
                    initialRestoreDone = true
                )
                loadPosts(savedRepo, savedFolder)
            } else {
                _uiState.value = _uiState.value.copy(
                    login = login,
                    isLoading = false,
                    initialRestoreDone = true
                )
                loadRepos()
            }
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

    /**
     * 确认将当前浏览的路径作为博客存放文件夹：固化这次选择（仓库 + 目录），
     * 下次冷启动会直接恢复到文章列表首页；然后开始扫描该目录下所有 index.md。
     */
    fun confirmBaseFolder(path: String) {
        val selectedRepo = _uiState.value.selectedRepo ?: return
        _uiState.value = _uiState.value.copy(selectedBaseFolder = path)
        repoConfigStore.save(selectedRepo, path)
        loadPosts(selectedRepo, path)
    }

    private fun loadPosts(repo: GitHubRepo, path: String) {
        val gh = repository ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                gh.scanBlogPosts(repo.owner.login, repo.name, path)
            }.onSuccess { list ->
                _uiState.value = _uiState.value.copy(isLoading = false, posts = list)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = it.message)
            }
        }
    }

    fun refreshPosts() {
        val repo = _uiState.value.selectedRepo ?: return
        val base = _uiState.value.selectedBaseFolder ?: return
        loadPosts(repo, base)
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

    /**
     * 退出当前已固化的仓库/目录选择，回到仓库选择页重新选。
     * 不影响登录状态（token 不清空，不需要重新走 GitHub 授权）。
     */
    fun exitRepoSelection() {
        repoConfigStore.clear()
        _uiState.value = _uiState.value.copy(
            selectedRepo = null,
            currentFolderPath = "",
            folderContents = emptyList(),
            selectedBaseFolder = null,
            posts = emptyList()
        )
        loadRepos()
    }

    /** 退出登录：清空内存状态，并把 SharedPreferences 里的 token 和已固化的仓库/目录一并删掉 */
    fun logout() {
        tokenStore.clear()
        repoConfigStore.clear()
        repository = null
        _uiState.value = AppUiState()
    }
}
