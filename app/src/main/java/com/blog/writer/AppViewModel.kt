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

/** 把异常转成给用户看的文案；GitHubApiException 自带状态码信息，其它异常（比如断网）大多 message 为空，兜底一句通用提示 */
private fun errorMessageOf(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: "网络请求失败，请检查网络连接后重试"

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
        // 仓库 + 目录之前固化过的话，直接恢复现场，跳过仓库/目录选择流程。
        // 这一步只依赖本地 SharedPreferences（同步、瞬时），不需要等任何网络请求，
        // 所以立刻做，尽快把 initialRestoreDone 置为 true，让导航层马上跳转，
        // 不要为了拿 login（仅用于展示用户名，非阻塞信息）而多等一次网络往返。
        val savedRepo = repoConfigStore.getRepo()
        val savedFolder = repoConfigStore.getBaseFolder()
        if (savedRepo != null && savedFolder != null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                selectedRepo = savedRepo,
                currentFolderPath = savedFolder,
                selectedBaseFolder = savedFolder,
                initialRestoreDone = true
            )
            loadPosts(savedRepo, savedFolder)
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                initialRestoreDone = true
            )
            loadRepos()
        }

        // login 只用于展示，跟导航/加载文章无关，单独异步拉取，拿到后再更新即可
        viewModelScope.launch {
            val login = runCatching { repository?.getCurrentLogin() }.getOrNull()
            _uiState.value = _uiState.value.copy(login = login)
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
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = errorMessageOf(it))
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
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = errorMessageOf(it))
            }
        }
    }

    /**
     * 确认将当前浏览的路径作为博客存放文件夹：固化这次选择（仓库 + 目录），
     * 下次冷启动会直接恢复到文章列表首页；然后开始扫描该目录下各子文件夹的第一个 .md 文件。
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
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = errorMessageOf(it))
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

    /** @return 保存成功时返回 GitHub 生成的新 sha（下次保存要用这个值），失败返回 null */
    suspend fun savePostContent(path: String, content: String, sha: String): String? {
        val repo = repository ?: return null
        val selectedRepo = _uiState.value.selectedRepo ?: return null
        return runCatching {
            repo.saveFile(selectedRepo.owner.login, selectedRepo.name, path, content, sha)
        }.getOrNull()
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
