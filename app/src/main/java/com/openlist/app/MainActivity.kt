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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
        bottomBar = {
            // 底部标题栏 + 操作栏
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
                // 标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                // 操作按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 返回
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

                    // 首页
                    IconButton(onClick = {
                        currentUrl = DEFAULT_URL
                        urlInput = DEFAULT_URL
                        webView?.loadUrl(DEFAULT_URL)
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "首页")
                    }

                    // 刷新
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 日志
                    IconButton(onClick = {
                        context.startActivity(Intent(context, LogViewerActivity::class.java))
                    }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_info_details),
                            contentDescription = "日志"
                        )
                    }

                    // 账号
                    IconButton(onClick = {
                        context.startActivity(Intent(context, AccountManagerActivity::class.java))
                    }) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_manage),
                            contentDescription = "账号"
                        )
                    }

                    // 更多菜单
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
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
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(Intent(context, SettingsActivity::class.java))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("开发者工具") },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(Intent(context, DevToolsActivity::class.java))
                                }
                            )
                        }
                    }
                }
            }
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
                // 地址输入框
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("输入网址...") },
                    textStyle = androidx.compose.ui.text.TextStyle(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        var url = urlInput.trim()
                        if (url.isNotBlank()) {
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
                        
                        useWideViewPort = true
                        loadWithOverviewMode = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
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
                            // 修复 OpenList 前端布局：
                            // OpenList 登录页使用 100vh + 垂直居中，
                            // WebView 中 100vh 包含了地址栏/底部栏高度，
                            // 导致实际内容被推到可视区域外
                            view?.evaluateJavascript("""
                                (function() {
                                    // 允许缩放
                                    var vp = document.querySelector('meta[name="viewport"]');
                                    if (vp) {
                                        vp.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
                                    }
                                    // 用 CSS 变量把 100vh 替换为 100dvh（动态视口高度）
                                    // dvh 会自动减去浏览器 UI 占用的空间
                                    var s = document.createElement('style');
                                    s.id = 'openlist-fix';
                                    s.textContent = ':root { --vh-fix: 100dvh; }';
                                    document.head.appendChild(s);
                                    // 遍历所有元素，把内联样式中的 100vh 替换为 100dvh
                                    var all = document.querySelectorAll('*');
                                    for (var i = 0; i < all.length; i++) {
                                        var el = all[i];
                                        var style = el.getAttribute('style');
                                        if (style && style.indexOf('100vh') !== -1) {
                                            el.setAttribute('style', style.replace(/100vh/g, '100dvh'));
                                        }
                                    }
                                    // 替换所有 stylesheet 中的 100vh
                                    var sheets = document.styleSheets;
                                    for (var i = 0; i < sheets.length; i++) {
                                        try {
                                            var rules = sheets[i].cssRules || sheets[i].rules;
                                            for (var j = 0; j < rules.length; j++) {
                                                if (rules[j].cssText && rules[j].cssText.indexOf('100vh') !== -1) {
                                                    // 无法直接修改 cssRules，用替换 stylesheet 文本方式
                                                }
                                            }
                                        } catch(e) {}
                                    }
                                })();
                            """.trimIndent(), null)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "未就齐，重试($retryCount/10)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    retryJob?.cancel()
                    retryCount++
                    webViewRef?.loadUrl(currentUrl)
                    loadState = LoadState.LOADING
                }) {
                    Text("重试")
                }
            }
        }
    }
}
