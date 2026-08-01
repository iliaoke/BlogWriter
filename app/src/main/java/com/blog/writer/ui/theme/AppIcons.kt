package com.blog.writer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.blog.writer.R

/**
 * 项目里用到的所有图标，统一从这里取。
 * 全部是 res/drawable 下手写的矢量图（标准 Material Design 图标路径，Apache 2.0），
 * 不依赖 material-icons-core / material-icons-extended，避免引入几 MB 的图标库。
 */
object AppIcons {
    val Code: @Composable () -> Painter = { painterResource(R.drawable.ic_code) }
    val Lock: @Composable () -> Painter = { painterResource(R.drawable.ic_lock) }
    val Folder: @Composable () -> Painter = { painterResource(R.drawable.ic_folder) }
    val CheckCircle: @Composable () -> Painter = { painterResource(R.drawable.ic_check_circle) }
    val Article: @Composable () -> Painter = { painterResource(R.drawable.ic_article) }
    val Preview: @Composable () -> Painter = { painterResource(R.drawable.ic_preview) }
    val Save: @Composable () -> Painter = { painterResource(R.drawable.ic_save) }
    val Refresh: @Composable () -> Painter = { painterResource(R.drawable.ic_refresh) }
    val ArrowBack: @Composable () -> Painter = { painterResource(R.drawable.ic_arrow_back) }
    val Logout: @Composable () -> Painter = { painterResource(R.drawable.ic_logout) }
    val Edit: @Composable () -> Painter = { painterResource(R.drawable.ic_edit) }
}
