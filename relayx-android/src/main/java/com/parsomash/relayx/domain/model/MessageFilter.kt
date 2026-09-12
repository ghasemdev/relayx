package com.parsomash.relayx.domain.model

import com.parsomash.relayx.R

enum class MessageFilter(val titleResId: Int) {
    ALL(R.string.filter_all),
    PENDING(R.string.filter_pending),
    FORWARDED(R.string.filter_forwarded),
    FAILED(R.string.filter_failed),
    FILTERED(R.string.filter_filtered);

    companion object {
        fun fromString(value: String): MessageFilter {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALL
        }
    }
}
