package com.parsomash.relayx.domain.usecase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.util.AppDispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

data class SmsSenderInfo(
    val address: String,
    val snippet: String? = null,
    val timestamp: Long = 0L
)

@Factory
class GetSmsSendersUseCase(
    private val context: Context? = null,
    private val outboxDao: OutboxMessageDao,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend operator fun invoke(): List<SmsSenderInfo> = withContext(dispatchers.io) {
        val sendersMap = linkedMapOf<String, SmsSenderInfo>()

        // 1. Fetch distinct senders and latest snippet from local outbox table
        try {
            val outboxSummaries = outboxDao.getSenderSummaries()
            for (summary in outboxSummaries) {
                if (summary.sender.isNotBlank()) {
                    sendersMap[summary.sender] = SmsSenderInfo(
                        address = summary.sender,
                        snippet = summary.snippet,
                        timestamp = summary.receivedAt
                    )
                }
            }
        } catch (_: Exception) {
        }

        // 2. Query device SMS Inbox if READ_SMS permission is granted
        val ctx = context
        if (ctx != null && ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cursor = ctx.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
                cursor?.use {
                    val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                    while (it.moveToNext() && sendersMap.size < 200) {
                        if (addressIndex != -1) {
                            val addr = it.getString(addressIndex)?.trim()
                            if (!addr.isNullOrBlank() && !sendersMap.containsKey(addr)) {
                                val body = if (bodyIndex != -1) it.getString(bodyIndex)?.trim() else null
                                val date = if (dateIndex != -1) it.getLong(dateIndex) else 0L
                                sendersMap[addr] = SmsSenderInfo(
                                    address = addr,
                                    snippet = body,
                                    timestamp = date
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        sendersMap.values.sortedByDescending { it.timestamp }
    }
}
