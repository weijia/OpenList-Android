package com.openlist.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志管理器
 * 将 OpenList 服务端输出写入文件，供日志查看界面读取
 */
object LogManager {

    private const val LOG_DIR = "openlist-logs"
    private const val LOG_FILE = "server.log"
    private const val MAX_LOG_SIZE = 2 * 1024 * 1024L // 2MB

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun getLogFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, LOG_FILE)
    }

    fun appendLog(context: Context, message: String) {
        try {
            val logFile = getLogFile(context)
            // 超过大小限制时截断
            if (logFile.length() > MAX_LOG_SIZE) {
                logFile.writeText("")
            }
            val timestamp = dateFormat.format(Date())
            logFile.appendText("[$timestamp] $message\n")
        } catch (_: Exception) {
            // 日志写入失败时静默处理
        }
    }

    fun readLogs(context: Context): String {
        return try {
            val logFile = getLogFile(context)
            if (logFile.exists()) logFile.readText() else "暂无日志"
        } catch (_: Exception) {
            "读取日志失败"
        }
    }

    fun clearLogs(context: Context) {
        try {
            val logFile = getLogFile(context)
            if (logFile.exists()) logFile.writeText("")
        } catch (_: Exception) {
            // ignore
        }
    }

    fun getLogSize(context: Context): Long {
        return try {
            val logFile = getLogFile(context)
            if (logFile.exists()) logFile.length() else 0L
        } catch (_: Exception) {
            0L
        }
    }
}
