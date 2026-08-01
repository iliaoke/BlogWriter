package com.blog.writer.nav

object Routes {
    const val AUTH = "auth"
    const val REPO_SELECT = "repo_select"
    const val FOLDER_SELECT = "folder_select"
    const val POST_LIST = "post_list"
    const val EDITOR = "editor/{encodedPath}"

    fun editor(encodedPath: String) = "editor/$encodedPath"
}
