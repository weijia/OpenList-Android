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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openlist.app.service.OpenListService
import com.openlist.app.ui.theme.OpenListAppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LoadState { LOADING, LOADED, ERROR }

private const val DEFAULT_URL = "http://127.0.0.1:5244"

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
    var currentUrl by remember { mutableStateOf(DEFAULT_URL) }
    var urlInput by remember { mutableStateOf(DEFAULT_URL) }
    var canGoBack by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
            // ─── 地址栏 ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 返回按钮
                IconButton(
                    onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        }
                    },
                    enabled = canGoBack
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }

                // 地址输入框
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("输入网址...", fontSize = androidx.compose.ui.unit.sp(13)) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.sp(13)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        var url = urlInput.trim()
                        if (url.isNotBlank()) {
                            // 自动补全 http://
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                url = "http://$url"
                                urlInput = url
                            }
                            currentUrl = url
                            webView?.loadUrl(url)
                        }
                    })
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 前往按钮
                IconButton(onClick = {
                    var url = urlInput.trim()
                    if (url.isNotBlank()) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "http://$url"
                            urlInput = url
                        }
                        currentUrl = url
                        webView?.loadUrl(url)
                    }
                }) {
                    Icon(Icons.Default.Search, contentDescription = "前往")
                }
            }

            // ─── WebView ─────────────────────────────────────
            OpenListWebView(
                modifier = Modifier.fillMaxSize(),
                currentUrl = currentUrl,
                onUrlChanged = { url ->
                    currentUrl = url
                    urlInput = url
                },
                onCanGoBackChanged = { canGoBack = it },
                onWebViewCreated = { wv -> webView = wv }
            )
        }
    }
}

@Composable
fun OpenListWebView(
    modifier: Modifier = Modifier,
    currentUrl: String = DEFAULT_URL,
    onUrlChanged: (String) -> Unit = {},
    onCanGoBackChanged: (Boolean) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {}
) {
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
            webViewRef?.post { webViewRef?.loadUrl(currentUrl) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { retryJob?.cancel() }
    }

    Column(modifier = modifier) {
        // 顶部加载进度条
        if (loadState == LoadState.LOADING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }

        // WebView 填充剩余空间
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
                        userAgentString = "OpenListApp/1.0"
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // 修复页面显示：启用宽视口+概览模式，让页面自动缩放适应屏幕
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        // 移除 setInitialScale，让系统自动计算缩放
                    }

                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = false
                    scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY

                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            loadState = LoadState.LOADING
                            url?.let { onUrlChanged(it) }
                            onCanGoBackChanged(view?.canGoBack() == true)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 不再强制 scrollTo(0,0)，让 loadWithOverviewMode 处理缩放和位置
                            if (loadState == LoadState.LOADING) {
                                loadState = LoadState.LOADED
                                retryCount = 0
                            }
                            onCanGoBackChanged(view?.canGoBack() == true)
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
                    loadUrl(currentUrl)
                }
            },
            modifier = Modifier.weight(1f)
        )

        // 底部错误提示栏
        if (loadState == LoadState.ERROR) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "服务器未就绪，正在重试... ($retryCount/10)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = {
                    retryJob?.cancel()
                    retryCount++
                    webViewRef?.loadUrl(currentUrl)
                    loadState = LoadState.LOADING
                }) {
                    Text("立即重试")
                }
            }
        }
    }
}
