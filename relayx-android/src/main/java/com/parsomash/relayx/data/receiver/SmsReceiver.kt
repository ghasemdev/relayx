package com.parsomash.relayx.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.parsomash.relayx.RelayApplication
import com.parsomash.relayx.data.worker.MessageDispatchWorker
import com.parsomash.relayx.domain.usecase.IngestSmsUseCase
import com.parsomash.relayx.util.RelayLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // Group multipart SMS PDUs by originating sender address
        val groupedMessages = messages.groupBy { it.originatingAddress ?: "UNKNOWN" }

        val pendingResult = goAsync()
        val app = context.applicationContext as RelayApplication
        val ingestUseCase = IngestSmsUseCase(app.database.outboxMessageDao(), app.preferencesRepository)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((sender, segments) in groupedMessages) {
                    val fullBody = buildString {
                        for (msg in segments) {
                            append(msg.messageBody ?: "")
                        }
                    }

                    RelayLogger.i("SmsReceiver", "Intercepted SMS from $sender (${fullBody.length} bytes, ${segments.size} segments)")
                    val ingested = ingestUseCase(sender, fullBody)
                    if (ingested != null) {
                        MessageDispatchWorker.enqueue(context)
                    }
                }
            } catch (e: Exception) {
                RelayLogger.e("SmsReceiver", "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
