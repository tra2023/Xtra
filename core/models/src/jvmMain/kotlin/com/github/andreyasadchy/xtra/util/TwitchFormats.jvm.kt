package com.github.andreyasadchy.xtra.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

actual fun formatGroupedCount(count: Int): String =
    NumberFormat.getInstance().format(count)

actual fun formatChatDate(timeMillis: Long): String {
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
    val pattern = if (date.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        "MMM d"
    } else {
        "MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date.time)
}
