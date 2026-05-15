package com.openlist.app

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openlist.app.service.OpenListService
import com.openlist.app.ui.theme.OpenListAppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LoadState { LOADING, LOADED, ERROR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startOpenListService()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ─── 上半部分：信息面板 ─────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 服务器状态卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "OpenList 服务器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "地址: http://127.0.0.1:5244",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "状态: 运行中",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 快捷操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("设置", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, LogViewerActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("日志", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            context.startActivity(Intent(context, AccountManagerActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("账号", fontSize = 13.sp)
                    }
                }

                // 服务控制按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, OpenListService::class.java)
                            intent.action = OpenListService.ACTION_RESTART
                            context.startService(intent)
                            webView?.postDelayed({ webView?.reload() }, 3000)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重启服务", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, OpenListService::class.java)
                            intent.action = OpenListService.ACTION_STOP
                            context.startService(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("停止服务", fontSize = 13.sp)
                    }
                }
            }

            // ─── 分隔线 ──────────────────────────────────────
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

            // ─── 下半部分：WebView ────────────────────────────
            OpenListWebView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.65f),
                onWebViewCreated = { wv -> webView = wv }
            )
        }
    }
}

@Composable
fun OpenListWebView(
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val serverUrl = "http://127.0.0.1:5244"
    val scope = rememberCoroutineScope()
    var loadState by remember { mutableStateOf(LoadState.LOADING) }
    var retryCount by remember { mutableIntStateOf(0) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = scope.launch {
            loadState = LoadState.LOADING
            delay(3000)
            retryCount++
            webViewRef?.post { webViewRef?.loadUrl(serverUrl) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { retryJob?.cancel() }
    }

    Column(modifier = modifier) {
        // 加载进度条
        if (loadState == LoadState.LOADING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        // WebView
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewRef = this
                    setBackgroundColor(Color.WHITE)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false

                        useWideViewPort = true
                        loadWithOverviewMode = false
                    }

                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = false
                    scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
                    overScrollMode = android.view.View.OVER_SCROLL_ALWAYS

                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            loadState = LoadState.LOADING
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (loadState == LoadState.LOADING) {
                                loadState = LoadState.LOADED
                                retryCount = 0
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            view?.loadData(
                                "<html><body style='background:white;'></body></html>",
                                "text/html",
                                "UTF-8"
                            )
                            if (request?.isForMainFrame == true && retryCount < 10) {
                                loadState = LoadState.ERROR
                                scheduleRetry()
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                        ) {
                            view?.loadData(
                                "<html><body style='background:white;'></body></html>",
                                "text/html",
                                "UTF-8"
                            )
                            if (request?.isForMainFrame == true && retryCount < 10) {
                                loadState = LoadState.ERROR
                                scheduleRetry()
                            }
                        }
                    }

                    onWebViewCreated(this)
                    loadUrl(serverUrl)
                }
            },
            modifier = Modifier.weight(1f)
        )

        // 底部错误提示
        if (loadState == LoadState.ERROR) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "服务器未就绪，重试中... ($retryCount/10)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = {
                    retryJob?.cancel()
                    retryCount++
                    webViewRef?.loadUrl(serverUrl)
                    loadState = LoadState.LOADING
                }) {
                    Text("重试", fontSize = 12.sp)
                }
            }
        }
    }
}
