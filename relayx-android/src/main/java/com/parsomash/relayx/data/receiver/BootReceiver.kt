package com.parsomash.relayx.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.parsomash.relayx.RelayApplication
import com.parsomash.relayx.data.worker.MessageDispatchWorker
import com.parsomash.relayx.util.RelayLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        RelayLogger.i("BootReceiver", "System restart or package replace detected: $action")
        val app = context.applicationContext as RelayApplication
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = app.preferencesRepository.getConfig()
                if (config.forwardingEnabled) {
                    RelayLogger.i("BootReceiver", "Forwarding is enabled; scheduling outbox message dispatch")
                    MessageDispatchWorker.enqueue(context)
                } else {
                    RelayLogger.d("BootReceiver", "Forwarding is disabled; omitting automatic worker schedule")
                }
            } catch (e: Exception) {
                RelayLogger.e("BootReceiver", "Failed to check config or trigger dispatch worker on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
