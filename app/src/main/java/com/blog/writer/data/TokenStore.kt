package com.blog.writer.data

import android.content.Context
import androidx.core.content.edit

/**
 * 用最朴素的 SharedPreferences 做持久化：一个键值对文件，读写都是同步调用，
 * 没有 DataStore 那套 Flow/协程封装。APP 重启后依然能读到上次保存的 token，
 * 不需要每次都重新走 GitHub 授权；只有用户主动退出登录时才清空。
 */
class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "access_token"
        private const val KEY_SCOPE = "scope"
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getScope(): String? = prefs.getString(KEY_SCOPE, null)

    fun saveToken(token: String, scope: String) {
        prefs.edit {
            putString(KEY_TOKEN, token)
            putString(KEY_SCOPE, scope)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
