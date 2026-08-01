package com.blog.writer.ui.screens.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.blog.writer.data.model.GitHubRepo
import com.blog.writer.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoSelectScreen(
    repos: List<GitHubRepo>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onErrorShown: () -> Unit = {},
    onRepoSelected: (GitHubRepo) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // 加载/鉴权失败时会把错误信息放进 errorMessage，之前这个字段虽然有，但没有任何界面
    // 展示过它——遇到 401/限流等情况用户只会看到一个空列表，不知道发生了什么。
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("选择仓库") })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && repos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(repos, key = { it.id }) { repo ->
                        ListItem(
                            headlineContent = { Text(repo.name) },
                            supportingContent = { Text(repo.full_name) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = repo.name.take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            },
                            trailingContent = {
                                if (repo.private) {
                                    Icon(AppIcons.Lock(), contentDescription = "私有仓库")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onRepoSelected(repo) }
                        )
                    }
                }
            }
        }
    }
}
