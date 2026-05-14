package com.openlist.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openlist.app.MainActivity
import com.openlist.app.OpenListApplication
import com.openlist.app.data.LogManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class OpenListService : Service() {

    private var process: Process? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var serverThread: Thread? = null
    @Volatile
    private var isRunning = false

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        const val ACTION_RESTART = "RESTART"
        const val TAG = "OpenListService"
        private const val ACTION_NOTIFY_STOP = "com.openlist.app.ACTION_NOTIFY_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_NOTIFY_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                stopServer()
                Thread.sleep(1000)
                startForeground()
                startServer()
                return START_STICKY
            }
            else -> {
                if (!isRunning) {
                    startForeground()
                    startServer()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── 前台通知 ────────────────────────────────────────────

    private fun startForeground() {
        val notification = createNotification("OpenList 正在启动...")
        startForeground(OpenListApplication.NOTIFICATION_ID, notification)
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(OpenListApplication.NOTIFICATION_ID, notification)
    }

    private fun createNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OpenListService::class.java).apply {
                action = ACTION_NOTIFY_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, OpenListApplication.CHANNEL_ID)
            .setContentTitle("OpenList")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止服务",
                stopIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ─── 服务器生命周期 ──────────────────────────────────────

    private fun startServer() {
        serverThread = Thread({
            try {
                val nativeLibDir = applicationInfo.nativeLibraryDir
                val binaryFile = File(nativeLibDir, "libopenlist.so")

                if (!binaryFile.exists()) {
                    val msg = "二进制文件不存在: ${binaryFile.absolutePath}"
                    Log.e(TAG, msg)
                    LogManager.appendLog(this, "[ERROR] $msg")
                    updateNotification("错误: 找不到 OpenList 二进制文件")
                    return@Thread
                }

                LogManager.appendLog(this, "找到二进制文件: ${binaryFile.absolutePath}")
                LogManager.appendLog(this, "文件大小: ${binaryFile.length() / 1024 / 1024} MB")
                Log.i(TAG, "找到二进制文件: ${binaryFile.absolutePath}")

                val dataDir = File(filesDir, "openlist-data")
                if (!dataDir.exists()) dataDir.mkdirs()

                val env = System.getenv().toMutableMap()
                env["GOGC"] = "50"
                env["GOMEMLIMIT"] = "256MiB"

                val pb = ProcessBuilder(
                    binaryFile.absolutePath,
                    "server",
                    "--data", dataDir.absolutePath,
                    "--force-bin-dir"
                ).apply {
                    directory(filesDir)
                    redirectErrorStream(true)
                    environment().putAll(env)
                }

                LogManager.appendLog(this, "正在启动 OpenList 服务器...")
                process = pb.start()
                isRunning = true
                updateNotification("OpenList 服务器运行中 (端口 5244)")
                LogManager.appendLog(this, "OpenList 服务器进程已启动")
                Log.i(TAG, "OpenList 服务器已启动")

                BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, ">>> $line")
                        LogManager.appendLog(this, line ?: "")
                    }
                }

                process?.waitFor()
                val exitCode = process?.exitValue() ?: -1
                val msg = "OpenList 服务器已退出 (exit code: $exitCode)"
                Log.i(TAG, msg)
                LogManager.appendLog(this, msg)
                isRunning = false
                updateNotification("OpenList 服务器已停止")

            } catch (e: Exception) {
                val msg = "服务器启动失败: ${e.message}"
                Log.e(TAG, msg, e)
                LogManager.appendLog(this, "[ERROR] $msg")
                LogManager.appendLog(this, "[ERROR] ${e.stackTraceToString()}")
                updateNotification("错误: ${e.message}")
                isRunning = false
            }
        }, "OpenListServerThread").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopServer() {
        process?.let {
            try {
                LogManager.appendLog(this, "正在停止 OpenList 服务器...")
                Log.i(TAG, "正在停止 OpenList 服务器...")
                it.destroy()
                val exited = it.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                if (!exited) {
                    LogManager.appendLog(this, "服务器未在 3 秒内退出，强制终止")
                    Log.w(TAG, "服务器未在 3 秒内退出，强制终止")
                    it.destroyForcibly()
                }
            } catch (e: Exception) {
                LogManager.appendLog(this, "[ERROR] 停止服务器时出错: ${e.message}")
                Log.e(TAG, "停止服务器时出错: ${e.message}", e)
            }
        }
        process = null
        isRunning = false
    }

    // ─── WakeLock ────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenList::ServerWakeLock"
        ).apply {
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        releaseWakeLock()
    }
}
