package com.openlist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlist.app.data.LogManager
import com.openlist.app.ui.theme.OpenListAppTheme
import kotlinx.coroutines.delay

class LogViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenListAppTheme {
                LogViewerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen() {
    val context = LocalContext.current
    var logs by remember { mutableStateOf("加载中...") }
    var logSize by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }

    fun loadLogs() {
        logs = LogManager.readLogs(context)
        val size = LogManager.getLogSize(context)
        logSize = if (size > 1024) "%.1f KB".format(size / 1024.0) else "$size B"
    }

    // 首次加载
    LaunchedEffect(Unit) {
        loadLogs()
    }

    // 自动刷新（每 2 秒）
    LaunchedEffect(autoScroll) {
        while (autoScroll) {
            delay(2000)
            loadLogs()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务日志") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Text(
                        text = logSize,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)
                    )
                    IconButton(onClick = { loadLogs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = {
                        LogManager.clearLogs(context)
                        loadLogs()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空日志")
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
            // 自动刷新开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { autoScroll = !autoScroll },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(if (autoScroll) "自动刷新: 开" else "自动刷新: 关")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 日志内容
            val logBackgroundColor = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }

            val logTextColor = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(logBackgroundColor)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = logs,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = logTextColor,
                    softWrap = false
                )
            }
        }
    }
}
