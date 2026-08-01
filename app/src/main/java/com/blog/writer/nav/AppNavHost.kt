package com.blog.writer.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blog.writer.AppViewModel
import com.blog.writer.data.model.BlogPost
import com.blog.writer.ui.screens.auth.AuthScreen
import com.blog.writer.ui.screens.editor.EditorScreen
import com.blog.writer.ui.screens.folder.FolderSelectScreen
import com.blog.writer.ui.screens.list.PostListScreen
import com.blog.writer.ui.screens.repo.RepoSelectScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavHost(oauthCallbackUri: Uri?) {
    val viewModel: AppViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val navController: NavHostController = rememberNavController()

    // 处理来自浏览器的 OAuth 回调深链接
    LaunchedEffect(oauthCallbackUri) {
        oauthCallbackUri?.let { uri ->
            val token = uri.getQueryParameter("access_token")
            val scope = uri.getQueryParameter("scope").orEmpty()
            if (!token.isNullOrBlank()) {
                viewModel.onTokenReceived(token, scope)
            }
        }
    }

    // 仅在“未登录 -> 已登录”这一次跳转时自动导航到仓库选择页，
    // 其余页面切换均由各界面的按钮回调显式触发 navController.navigate，避免和用户手动导航冲突。
    var hasAutoNavigatedAfterLogin by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.token) {
        if (!uiState.token.isNullOrBlank() && !hasAutoNavigatedAfterLogin) {
            hasAutoNavigatedAfterLogin = true
            navController.navigate(Routes.REPO_SELECT) {
                popUpTo(0)
                launchSingleTop = true
            }
        }
    }

    var currentEditingPost by remember { mutableStateOf<BlogPost?>(null) }

    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen()
        }

        composable(Routes.REPO_SELECT) {
            RepoSelectScreen(
                repos = uiState.repos,
                isLoading = uiState.isLoading,
                onRepoSelected = {
                    viewModel.selectRepo(it)
                    navController.navigate(Routes.FOLDER_SELECT)
                }
            )
        }

        composable(Routes.FOLDER_SELECT) {
            FolderSelectScreen(
                currentPath = uiState.currentFolderPath,
                contents = uiState.folderContents,
                isLoading = uiState.isLoading,
                onNavigate = { viewModel.browseFolder(it) },
                onConfirm = {
                    viewModel.confirmBaseFolder(it)
                    navController.navigate(Routes.POST_LIST) {
                        popUpTo(Routes.REPO_SELECT) { inclusive = false }
                    }
                },
                onBackToRepos = {
                    navController.navigate(Routes.REPO_SELECT) { popUpTo(0) }
                }
            )
        }

        composable(Routes.POST_LIST) {
            PostListScreen(
                repoName = uiState.selectedRepo?.full_name.orEmpty(),
                baseFolder = uiState.selectedBaseFolder.orEmpty(),
                posts = uiState.posts,
                isLoading = uiState.isLoading,
                onOpenPost = { post ->
                    currentEditingPost = post
                    navController.navigate(Routes.editor(URLEncoder.encode(post.path, "UTF-8")))
                },
                onRefresh = { viewModel.refreshPosts() },
                onChangeFolder = {
                    navController.navigate(Routes.FOLDER_SELECT)
                }
            )
        }

        composable(Routes.EDITOR) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("encodedPath").orEmpty()
            val path = URLDecoder.decode(encoded, "UTF-8")
            val post = currentEditingPost

            var content by remember(path) { mutableStateOf<String?>(null) }
            var sha by remember(path) { mutableStateOf("") }
            var loading by remember(path) { mutableStateOf(true) }

            LaunchedEffect(path) {
                val result = viewModel.loadPostContent(path)
                content = result?.first ?: ""
                sha = result?.second ?: ""
                loading = false
            }

            EditorScreen(
                title = post?.title ?: "编辑文章",
                initialContent = content ?: "",
                sha = sha,
                isLoading = loading,
                onBack = { navController.popBackStack() },
                onSave = { newContent ->
                    val success = viewModel.savePostContent(path, newContent, sha)
                    if (success) {
                        val fresh = viewModel.loadPostContent(path)
                        sha = fresh?.second ?: sha
                    }
                    success
                }
            )
        }
    }
}
