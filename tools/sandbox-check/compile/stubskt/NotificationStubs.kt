@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

class NotificationCompat {
    class Builder(context: Context, channelId: String) {
        fun setSmallIcon(icon: Int) = this
        fun setContentTitle(title: CharSequence?) = this
        fun setContentText(text: CharSequence?) = this
        fun setContentIntent(intent: PendingIntent?) = this
        fun addAction(icon: Int, title: CharSequence?, intent: PendingIntent?) = this
        fun setOngoing(ongoing: Boolean) = this
        fun setOnlyAlertOnce(onlyAlertOnce: Boolean) = this
        fun setCategory(category: String?) = this
        fun setPriority(priority: Int) = this
        fun setAutoCancel(autoCancel: Boolean) = this
        fun build(): Notification = error("stub")
    }
    companion object {
        const val PRIORITY_LOW = -1
        const val CATEGORY_SERVICE = "service"
    }
}
