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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.openlist.app.service.OpenListService
import com.openlist.app.ui.theme.OpenListAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启动服务
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
        // 不停止服务，保持后台运行
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
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
            OpenListWebView()
        }
    }
}

@Composable
fun OpenListWebView() {
    val serverUrl = "http://127.0.0.1:5244"
    
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
                    ): Boolean {
                        return false
                    }
                }
                
                loadUrl(serverUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
