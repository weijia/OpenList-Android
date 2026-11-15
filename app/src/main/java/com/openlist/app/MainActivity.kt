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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.compose.BackHandler

private enum class LoadState { LOADING, LOADED, ERROR }

private const val DEFAULT_URL = "http://127.0.0.1:5244/zb/"

// WebView 上报的是含 fragment 的完整地址；zb 把浏览状态塞在 #!<base64> 里，
// 直接回显会让地址栏变成一长串乱码，所以只显示去掉 fragment 的部分。
private fun stripFragment(url: String): String = url.substringBefore('#')

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸模式：隐藏系统状态栏和导航栏
        // 这样 WebView 的 100vh = 实际可用高度，和手机浏览器一致
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
    var canGoBack by remember { mutableStateOf(false) }

    // 启动流程：等服务起来 → 用 admin 登录 zb → 把 zb_token 写进 WebView Cookie → 刷新页面。
    // admin 由 OpenListService 通过 OPENLIST_ADMIN_PASSWORD 在首次启动时创建。
    LaunchedEffect(Unit) {
        if (!waitForServer()) return@LaunchedEffect
        val token = zbLogin()
        if (token != null) {
            android.webkit.CookieManager.getInstance().apply {
                setCookie("http://127.0.0.1:5244", "zb_token=$token; Path=/")
                flush()
            }
            delay(200)
            webView?.post { webView?.reload() }
        }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    // WebView 全屏，地址栏和底部栏浮在上面
    // 这样 100vh = screen.height = WebView 高度，和手机浏览器行为一致
    Box(modifier = Modifier.fillMaxSize()) {
        // WebView 占满整个屏幕
        OpenListWebView(
            modifier = Modifier.fillMaxSize(),
            currentUrl = currentUrl,
            onUrlChanged = { url ->
                currentUrl = url
            },
            onCanGoBackChanged = { canGoBack = it },
            onWebViewCreated = { wv -> webView = wv }
        )

        // 底部栏浮在底部
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )
            // 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    .background(MaterialTheme.colorScheme.surface)
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
                            text = { Text("重置管理员密码 (${AuthConfig.ADMIN_PASSWORD})") },
                            onClick = {
                                menuExpanded = false
                                val intent = Intent(context, OpenListService::class.java)
                                intent.action = OpenListService.ACTION_RESET_ADMIN
                                context.startService(intent)
                                webView?.postDelayed({ webView?.reload() }, 6000)
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
                        // 使用系统默认 User-Agent（和手机浏览器一致）
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        
                        // 和手机浏览器一致的设置
                        useWideViewPort = true
                        loadWithOverviewMode = true
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
                            // zb 用 #!<base64> 保存浏览状态，直接回显会让地址栏变成一长串乱码
                            url?.let { onUrlChanged(stripFragment(it)) }
                            onCanGoBackChanged(view?.canGoBack() == true)
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            // pushState/hash 变化也走这里；同样只显示去掉 fragment 的地址
                            url?.let { onUrlChanged(stripFragment(it)) }
                            onCanGoBackChanged(view?.canGoBack() == true)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // WebView 和手机浏览器的关键差异：
                            // 手机浏览器会自动设置 html 高度 = 视口高度
                            // WebView 的 html 高度默认为 0，导致 height:100% 无法继承
                            // 修复：手动设置完整的高度链 html → body → #root → .hope-center
                            view?.evaluateJavascript("""
                                (function(){
                                    var h = window.innerHeight;
                                    document.documentElement.style.height = h + 'px';
                                    document.body.style.height = '100%';
                                    var root = document.getElementById('root');
                                    if(root){
                                        root.style.height = '100%';
                                        root.style.minHeight = '100%';
                                    }
                                    var center = document.querySelector('.hope-center');
                                    if(center){
                                        center.style.height = '100%';
                                        center.style.minHeight = '100%';
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
                            // 只有主文档失败才处理。子资源（favicon、图片、脚本）失败
                            // 以前会把整页 loadData 成白页，导致 /zb/ 看起来“什么都没显示”。
                            if (request?.isForMainFrame != true) return
                            if (retryCount < 10) {
                                loadState = LoadState.ERROR
                                scheduleRetry()
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: android.webkit.WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame != true) return
                            if (retryCount < 10) {
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

// 轮询本机 OpenList 是否已经可以响应（服务异步启动，通常 1~3 秒）
private suspend fun waitForServer(timeoutMs: Long = 30000): Boolean = withContext(Dispatchers.IO) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        try {
            val conn = URL("http://127.0.0.1:5244/api/public/settings").openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val code = conn.responseCode
            conn.disconnect()
            if (code == 200) return@withContext true
        } catch (_: Exception) {
            // 服务还没起来，继续等
        }
        delay(400)
    }
    false
}

// 用本地管理员登录 zb，拿 30 天有效期的 zb_token（随后写进 WebView Cookie）
private suspend fun zbLogin(): String? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = URL("http://127.0.0.1:5244/zb/api/login").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 3000
        conn.readTimeout = 8000
        val body = JSONObject()
            .put("username", AuthConfig.ADMIN_USER)
            .put("password", AuthConfig.ADMIN_PASSWORD)
            .toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 200) JSONObject(text).optString("token").takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}
