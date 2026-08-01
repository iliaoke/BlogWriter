package com.blog.writer.ui.screens.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blog.writer.data.model.BlogPost
import com.blog.writer.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    repoName: String,
    baseFolder: String,
    posts: List<BlogPost>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onErrorShown: () -> Unit = {},
    onOpenPost: (BlogPost) -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(repoName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "/${baseFolder.ifBlank { "根目录" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(AppIcons.Refresh(), contentDescription = "刷新")
                    }
                    IconButton(onClick = onExit) {
                        Icon(AppIcons.Logout(), contentDescription = "退出，重新选择仓库和目录")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && posts.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (posts.isEmpty()) {
                Text(
                    "未在该目录下发现任何 index.md 文章",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(posts, key = { it.path }) { post ->
                        ListItem(
                            headlineContent = {
                                Text(post.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    post.excerpt.ifBlank { post.folderName },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = { Icon(AppIcons.Article(), contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPost(post) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
