package com.example.stockview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Jadwal ulang notifikasi pasar
            val mainIntent = Intent(context, MainMenuAuthActivity::class.java)
            context.startActivity(mainIntent)
        }
    }
}
