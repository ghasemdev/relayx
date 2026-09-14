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

@Factory
class GetSmsSendersUseCase(
    private val context: Context? = null,
    private val outboxDao: OutboxMessageDao,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend operator fun invoke(): List<String> = withContext(dispatchers.io) {
        val senders = linkedSetOf<String>()

        // 1. Fetch distinct senders from local outbox table
        try {
            senders.addAll(outboxDao.getDistinctSenders())
        } catch (_: Exception) {
        }

        // 2. Query device SMS Inbox if READ_SMS permission is granted
        val ctx = context
        if (ctx != null && ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cursor = ctx.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
                cursor?.use {
                    val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    while (it.moveToNext() && senders.size < 200) {
                        if (addressIndex != -1) {
                            val addr = it.getString(addressIndex)?.trim()
                            if (!addr.isNullOrBlank()) {
                                senders.add(addr)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        senders.toList()
    }
}
