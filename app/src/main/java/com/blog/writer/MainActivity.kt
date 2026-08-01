package com.blog.writer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.blog.writer.nav.AppNavHost
import com.blog.writer.ui.theme.BlogWriterTheme

class MainActivity : ComponentActivity() {

    // 用 Compose State 持有当前 OAuth 回调 Uri，onNewIntent 更新它即可触发重组
    private var oauthUriState by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        oauthUriState = intent?.data

        setContent {
            BlogWriterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(oauthCallbackUri = oauthUriState)
                }
            }
        }
    }

    // launchMode="singleTask" 时，从系统浏览器跳回 APP 会走这里，而不是重新 onCreate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { oauthUriState = it }
    }
}
