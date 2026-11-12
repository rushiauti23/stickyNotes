package dev.rushi.stickynotes.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.rushi.stickynotes.MainActivity
import dev.rushi.stickynotes.R
import dev.rushi.stickynotes.data.Note
import dev.rushi.stickynotes.data.plainBody

object Notifications {
    private const val CHANNEL_REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Reminders you set on notes" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Sends a sample notification right now, to check that reminders can reach the user. */
    @SuppressLint("MissingPermission") // checked by canNotify()
    fun showTest(context: Context): Boolean {
        if (!canNotify(context)) return false
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle("Reminders work")
            .setContentText("This is how a note reminder will look.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    private const val TEST_NOTIFICATION_ID = Int.MAX_VALUE

    /**
     * @param hideContent true when the app lock is on: the notification then never shows note
     *   text, so the lock can't be sidestepped by reading notifications.
     */
    @SuppressLint("MissingPermission") // checked by canNotify()
    fun showReminder(context: Context, note: Note, hideContent: Boolean) {
        if (!canNotify(context)) return

        val open = PendingIntent.getActivity(
            context,
            note.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = note.plainBody().trim()
        val title = note.title.ifBlank { body.lineSequence().firstOrNull().orEmpty() }.ifBlank { "Reminder" }

        // Shown on the lock screen instead of the real content.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle("Note reminder")
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(open)

        if (hideContent) {
            builder.setContentTitle("Note reminder").setContentText("Unlock Sticky Notes to view")
        } else {
            builder.setContentTitle(title)
            if (body.isNotEmpty()) {
                builder.setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
        }

        NotificationManagerCompat.from(context).notify(note.id.toInt(), builder.build())
    }
}
