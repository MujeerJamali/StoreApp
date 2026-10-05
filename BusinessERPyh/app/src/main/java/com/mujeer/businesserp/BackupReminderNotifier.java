package com.mujeer.businesserp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// =====================
// Checks once per calendar day (own SharedPreferences, same pattern as
// CreditDueNotifier/LowStockNotifier) whether it's been at least
// REMINDER_THRESHOLD_DAYS since the last successful Vyapar backup
// export (see recordBackupNow(), called by
// ExportVyaparActivity.writeZipToDestination() right after a save
// actually succeeds) and, if so, posts a reminder notification opening
// straight to Export Vyapar Backup.
//
// A brand-new install has never backed up anything, so counting from
// "never" would nag on day one - instead it counts from first_seen
// (stamped once, the first time this ever runs on a device), giving a
// new install the same grace period an overdue backup would get.
//
// Unlike the due-today/low-stock notifiers, this one is allowed to
// re-fire on consecutive days once overdue - that's the point of a
// reminder: it keeps nagging until a backup actually happens (which
// resets the countdown via recordBackupNow()).
// =====================
class BackupReminderNotifier {

	private static final String PREFS_NAME = "backup_reminder_notifier";
	private static final String KEY_LAST_CHECKED_DATE = "last_checked_date";
	private static final String KEY_LAST_BACKUP_MILLIS = "last_backup_millis";
	private static final String KEY_FIRST_SEEN_MILLIS = "first_seen_millis";

	private static final String CHANNEL_ID = "backup_reminders";
	private static final int NOTIFICATION_ID = 5003;

	private static final int REMINDER_THRESHOLD_DAYS = 7;

	// Called from ExportVyaparActivity right after a backup file is
	// actually written to its destination - resets the countdown.
	static void recordBackupNow(Context appContext) {

		appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit()
			.putLong(KEY_LAST_BACKUP_MILLIS, System.currentTimeMillis())
			.apply();
	}

	static void checkAndNotifyIfNeeded(final Context appContext) {

		final SharedPreferences prefs =
			appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

		final String today =
			new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

		if (today.equals(prefs.getString(KEY_LAST_CHECKED_DATE, null))) {
			return;
		}

		prefs.edit().putString(KEY_LAST_CHECKED_DATE, today).apply();

		long firstSeen = prefs.getLong(KEY_FIRST_SEEN_MILLIS, 0);

		if (firstSeen == 0) {

			firstSeen = System.currentTimeMillis();
			prefs.edit().putLong(KEY_FIRST_SEEN_MILLIS, firstSeen).apply();
		}

		long lastBackup = prefs.getLong(KEY_LAST_BACKUP_MILLIS, firstSeen);

		long daysSinceBackup =
			TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastBackup);

		if (daysSinceBackup >= REMINDER_THRESHOLD_DAYS) {
			showNotification(appContext, daysSinceBackup);
		}
	}

	private static void showNotification(Context context, long daysSinceBackup) {

		NotificationManager notificationManager =
			(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

			NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				"Backup Reminders",
				NotificationManager.IMPORTANCE_DEFAULT
			);

			channel.setDescription("A reminder to export a Vyapar backup when it's overdue.");

			notificationManager.createNotificationChannel(channel);
		}

		Intent intent = new Intent(context, ExportVyaparActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

		int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(
			context, NOTIFICATION_ID, intent, pendingIntentFlags
		);

		String title = "Backup overdue";

		String text =
			"No backup in " + daysSinceBackup + " days - tap to export one now.";

		Notification.Builder builder =
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
			? new Notification.Builder(context, CHANNEL_ID)
			: new Notification.Builder(context);

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			builder.setPriority(Notification.PRIORITY_DEFAULT);
		}

		builder
			.setSmallIcon(R.drawable.ic_launcher)
			.setContentTitle(title)
			.setContentText(text)
			.setStyle(new Notification.BigTextStyle().bigText(text))
			.setContentIntent(pendingIntent)
			.setAutoCancel(true);

		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}
}
