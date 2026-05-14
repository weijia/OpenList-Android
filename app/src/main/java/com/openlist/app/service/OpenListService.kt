package com.openlist.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openlist.app.MainActivity
import com.openlist.app.OpenListApplication
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * OpenList 后台服务
 *
 * 核心原理：
 * - 将 OpenList 二进制文件以 libopenlist.so 命名放入 jniLibs/<abi>/
 * - Android 安装时会将其提取到 nativeLibraryDir（具有 exec_type SELinux 标签）
 * - 通过 ProcessBuilder 执行该文件，绕过 W^X 限制
 */
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

        /** 通知使用的 Action 字符串 */
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
                // 1. 找到 nativeLibraryDir 中的 libopenlist.so
                val nativeLibDir = applicationInfo.nativeLibraryDir
                val binaryFile = File(nativeLibDir, "libopenlist.so")

                if (!binaryFile.exists()) {
                    Log.e(TAG, "二进制文件不存在: ${binaryFile.absolutePath}")
                    updateNotification("错误: 找不到 OpenList 二进制文件")
                    return@Thread
                }

                Log.i(TAG, "找到二进制文件: ${binaryFile.absolutePath}")
                Log.i(TAG, "文件大小: ${binaryFile.length() / 1024 / 1024} MB")

                // 2. 准备数据目录
                val dataDir = File(filesDir, "openlist-data")
                if (!dataDir.exists()) {
                    dataDir.mkdirs()
                }

                // 3. 准备配置文件目录
                val configDir = File(filesDir, "openlist-data")
                if (!configDir.exists()) {
                    configDir.mkdirs()
                }

                // 4. 设置环境变量
                val env = System.getenv().toMutableMap()
                env["GOGC"] = "50"
                env["GOMEMLIMIT"] = "256MiB"

                // 5. 启动进程
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

                process = pb.start()
                isRunning = true
                updateNotification("OpenList 服务器运行中 (端口 5244)")

                Log.i(TAG, "OpenList 服务器已启动")

                // 6. 读取输出日志
                BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, ">>> $line")
                    }
                }

                process?.waitFor()
                Log.i(TAG, "OpenList 服务器已退出 (exit code: ${process?.exitValue()})")
                isRunning = false
                updateNotification("OpenList 服务器已停止")

            } catch (e: Exception) {
                Log.e(TAG, "服务器启动失败: ${e.message}", e)
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
                Log.i(TAG, "正在停止 OpenList 服务器...")
                it.destroy()
                // 等待 3 秒优雅关闭
                val exited = it.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                if (!exited) {
                    Log.w(TAG, "服务器未在 3 秒内退出，强制终止")
                    it.destroyForcibly()
                }
            } catch (e: Exception) {
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
            acquire(30 * 60 * 1000L) // 30 分钟，服务会续期
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
