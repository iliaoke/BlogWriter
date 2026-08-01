package com.blog.writer.ui.screens.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blog.writer.data.model.GitHubContent
import com.blog.writer.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectScreen(
    currentPath: String,
    contents: List<GitHubContent>,
    isLoading: Boolean,
    onNavigate: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onBackToRepos: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (currentPath.isBlank()) "仓库根目录" else currentPath)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath.isBlank()) {
                            onBackToRepos()
                        } else {
                            val parent = currentPath.substringBeforeLast('/', "")
                            onNavigate(parent)
                        }
                    }) {
                        Icon(AppIcons.ArrowBack(), contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("将此文件夹设为博客目录") },
                icon = { Icon(AppIcons.CheckCircle(), contentDescription = null) },
                onClick = { onConfirm(currentPath) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (contents.isEmpty()) {
                Text(
                    "当前目录下没有子文件夹",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                    items(contents, key = { it.path }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            leadingContent = { Icon(AppIcons.Folder(), contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(item.path) }
                        )
                    }
                }
            }
        }
    }
}
