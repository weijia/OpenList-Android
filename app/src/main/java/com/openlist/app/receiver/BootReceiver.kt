package com.openlist.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openlist.app.data.SettingsDataStore
import com.openlist.app.service.OpenListService
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsDataStore = SettingsDataStore(context)
            
            runBlocking {
                val autoStart = settingsDataStore.getAutoStart()
                if (autoStart) {
                    val serviceIntent = Intent(context, OpenListService::class.java)
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}
