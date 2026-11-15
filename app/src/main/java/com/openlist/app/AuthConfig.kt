package com.openlist.app

// 本地实例的管理员凭据。
// 这个 OpenList 分支不会自动创建管理员（只等 Web 安装向导），
// 所以 App 在启动服务时通过 OPENLIST_ADMIN_PASSWORD 环境变量把 admin 建好，
// 然后由 App 自动登录 /zb/api/login 并把 zb_token 写进 WebView Cookie。
object AuthConfig {
    const val ADMIN_USER = "admin"
    const val ADMIN_PASSWORD = "admin888"
}
