package dev.rushi.stickynotes.ui.common

import android.content.Context
import android.text.format.DateUtils

/** "Tomorrow, 9:00 AM", "Mon, 9:00 AM", "in 2 hours", in the phone's language and clock style. */
fun reminderLabel(context: Context, at: Long, now: Long = System.currentTimeMillis()): String =
    if (at - now in 0 until DateUtils.HOUR_IN_MILLIS * 3) {
        DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString()
    } else {
        DateUtils.getRelativeDateTimeString(
            context, at, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL,
        ).toString()
    }

/** "Vanishes in 5h", "Vanishes in 3d". */
fun vanishLabel(expiresAt: Long, now: Long = System.currentTimeMillis()): String {
    val left = (expiresAt - now).coerceAtLeast(0)
    val minutes = left / DateUtils.MINUTE_IN_MILLIS
    val hours = left / DateUtils.HOUR_IN_MILLIS
    val days = left / DateUtils.DAY_IN_MILLIS
    return when {
        days >= 1 -> "Vanishes in ${days}d"
        hours >= 1 -> "Vanishes in ${hours}h"
        minutes >= 1 -> "Vanishes in ${minutes}m"
        else -> "Vanishing now"
    }
}
