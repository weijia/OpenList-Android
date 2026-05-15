package com.openlist.app

import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class DevToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DevToolsScreen()
        }
    }
}

data class ConsoleLog(
    val level: String,
    val message: String,
    val source: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen() {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var jsInput by remember { mutableStateOf("") }
    var pageSource by remember { mutableStateOf("") }
    val consoleLogs = remember { mutableStateListOf<ConsoleLog>() }
    val listState = rememberLazyListState()
    var currentUrl by remember { mutableStateOf("http://127.0.0.1:5244") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者工具") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 当前 URL
            Text(
                text = "URL: $currentUrl",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // WebView (隐藏，用于调试)
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                message?.let {
                                    val level = when (it.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> "E"
                                        ConsoleMessage.MessageLevel.WARNING -> "W"
                                        ConsoleMessage.MessageLevel.LOG -> "L"
                                        ConsoleMessage.MessageLevel.DEBUG -> "D"
                                        else -> "I"
                                    }
                                    consoleLogs.add(ConsoleLog(
                                        level = level,
                                        message = it.message(),
                                        source = "${it.sourceId()}:${it.lineNumber()}"
                                    ))
                                }
                                return true
                            }
                        }

                        loadUrl(currentUrl)
                    }
                },
                modifier = Modifier.height(1.dp)
            )

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Button(onClick = {
                    webView?.evaluateJavascript(
                        "(function(){return document.documentElement.outerHTML;})();"
                    ) { result ->
                        pageSource = result ?: ""
                    }
                }) {
                    Text("获取源码")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    webView?.evaluateJavascript(
                        """(function(){
                            var s = document.createElement('style');
                            s.textContent = 'html,body{height:auto!important;min-height:100vh;}';
                            document.head.appendChild(s);
                            return 'CSS injected';
                        })();"""
                    ) { result ->
                        consoleLogs.add(ConsoleLog("I", "注入结果: $result"))
                    }
                }) {
                    Text("注入修复CSS")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    webView?.evaluateJavascript(
                        """(function(){
                            var h = document.documentElement.scrollHeight;
                            var vh = window.innerHeight;
                            var sh = window.visualViewport ? window.visualViewport.height : 'N/A';
                            return 'scrollHeight=' + h + ', innerHeight=' + vh + ', viewport=' + sh;
                        })();"""
                    ) { result ->
                        consoleLogs.add(ConsoleLog("I", "视口信息: $result"))
                    }
                }) {
                    Text("视口信息")
                }
            }

            // JS 执行输入框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = jsInput,
                    onValueChange = { jsInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入 JavaScript...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(4.dp))
                Button(onClick = {
                    if (jsInput.isNotBlank()) {
                        webView?.evaluateJavascript(jsInput) { result ->
                            consoleLogs.add(ConsoleLog("I", "结果: $result"))
                        }
                        jsInput = ""
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "执行")
                }
            }

            // 控制台日志
            Text(
                text = "控制台日志:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState
            ) {
                items(consoleLogs) { log ->
                    val decoded = log.message
                        .replace("\\u003c", "<").replace("\\u003e", ">")
                        .replace("\\u0026", "&")
                        .replace("\\n", "\n")
                    Text(
                        text = "[${log.level}] $decoded${if (log.source.isNotBlank()) " (${log.source})" else ""}",
                        fontSize = 11.sp,
                        color = when (log.level) {
                            "E" -> Color.Red
                            "W" -> Color(0xFFFFA500)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            // 清空按钮
            TextButton(
                onClick = { consoleLogs.clear() },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text("清空日志")
            }

            // 页面源码显示
            if (pageSource.isNotEmpty()) {
                Text(
                    text = "页面源码 (前1000字符):",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                val decodedSource = pageSource.take(2000)
                    .replace("\\u003C", "<").replace("\\u003c", "<")
                    .replace("\\u003E", ">").replace("\\u003e", ">")
                    .replace("\\u0026", "&").replace("\\n", "\n")
                    .replace("\\\"", "\"").replace("\\/", "/")
                SelectionContainer {
                    Text(
                        text = decodedSource,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
                TextButton(
                    onClick = { pageSource = "" },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("关闭源码")
                }
            }
        }
    }
}
