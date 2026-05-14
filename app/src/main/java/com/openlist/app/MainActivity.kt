package com.openlist.app

import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.openlist.app.service.OpenListService
import com.openlist.app.ui.theme.OpenListAppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startOpenListService()
        enableEdgeToEdge()
        setContent {
            OpenListAppTheme {
                MainScreen()
            }
        }
    }

    private fun startOpenListService() {
        val intent = Intent(this, OpenListService::class.java)
        startForegroundService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新页面")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("刷新页面") },
                            onClick = {
                                menuExpanded = false
                                webView?.reload()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("查看日志") },
                            onClick = {
                                menuExpanded = false
                                context.startActivity(Intent(context, LogViewerActivity::class.java))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("账号管理") },
                            onClick = {
                                menuExpanded = false
                                context.startActivity(Intent(context, AccountManagerActivity::class.java))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重启服务") },
                            onClick = {
                                menuExpanded = false
                                val intent = Intent(context, OpenListService::class.java)
                                intent.action = OpenListService.ACTION_RESTART
                                context.startService(intent)
                                webView?.postDelayed({ webView?.reload() }, 3000)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("停止服务") },
                            onClick = {
                                menuExpanded = false
                                val intent = Intent(context, OpenListService::class.java)
                                intent.action = OpenListService.ACTION_STOP
                                context.startService(intent)
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OpenListWebView(
                onWebViewCreated = { wv -> webView = wv }
            )
        }
    }
}

@Composable
fun OpenListWebView(onWebViewCreated: (WebView) -> Unit = {}) {
    val serverUrl = "http://127.0.0.1:5244"
    var pageLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var isWaitingServer by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var waitJob by remember { mutableStateOf<Job?>(null) }

    fun waitForServerThenLoad(wv: WebView) {
        waitJob?.cancel()
        waitJob = scope.launch {
            isWaitingServer = true
            pageLoaded = false
            loadError = false
            var attempts = 0
            val maxAttempts = 30
            while (attempts < maxAttempts) {
                try {
                    val conn = URL(serverUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.requestMethod = "HEAD"
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..399) {
                        isWaitingServer = false
                        wv.post { wv.loadUrl(serverUrl) }
                        return@launch
                    }
                } catch (_: Exception) {
                    // 服务器还没准备好
                }
                attempts++
                delay(1000)
            }
            // 超时，仍然尝试加载
            isWaitingServer = false
            wv.post { wv.loadUrl(serverUrl) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "OpenListApp/1.0"
                        allowFileAccess = true
                        allowContentAccess = true
                    }

                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            pageLoaded = true
                            loadError = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                loadError = true
                            }
                        }
                    }

                    onWebViewCreated(this)
                    // WebView 创建后，等待服务器就绪再加载
                    waitForServerThenLoad(this)
                }
            },
            update = { wv ->
                // 外部调用 reload 时，直接加载
            },
            modifier = Modifier.fillMaxSize()
        )

        // 等待服务器 / 加载失败 覆盖层
        if (isWaitingServer || (!pageLoaded && !loadError)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isWaitingServer) "等待服务器启动..." else "正在加载...",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "服务器正在后台启动，请稍候",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (loadError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "页面加载失败",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "服务器可能尚未启动完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    waitJob?.cancel()
                    isWaitingServer = true
                    pageLoaded = false
                    loadError = false
                    waitJob = scope.launch {
                        var attempts = 0
                        val maxAttempts = 30
                        while (attempts < maxAttempts) {
                            try {
                                val conn = URL(serverUrl).openConnection() as HttpURLConnection
                                conn.connectTimeout = 1000
                                conn.readTimeout = 1000
                                conn.requestMethod = "HEAD"
                                val code = conn.responseCode
                                conn.disconnect()
                                if (code in 200..399) {
                                    isWaitingServer = false
                                    return@launch
                                }
                            } catch (_: Exception) {
                            }
                            attempts++
                            delay(1000)
                        }
                        isWaitingServer = false
                    }
                }) {
                    Text("重新加载")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            waitJob?.cancel()
        }
    }
}
