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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Same once-per-interval pattern as LowStockNotifier/CreditDueNotifier,
// but weekly rather than daily - a fresh Reorder List isn't worth a
// notification every single day. Checks once every 7 days whether
// DatabaseHelper.getReorderSuggestions() has anything in it, and if so
// posts one notification; tapping it opens the Reorder List screen.
// =====================
class ReorderDigestNotifier {

	private static final String PREFS_NAME = "reorder_digest_notifier";
	private static final String KEY_LAST_CHECKED_DATE = "last_checked_date";

	private static final String CHANNEL_ID = "reorder_digest";
	private static final int NOTIFICATION_ID = 5003;

	private static final int CHECK_INTERVAL_DAYS = 7;

	static void checkAndNotifyIfNeeded(final Context appContext) {

		final SharedPreferences prefs =
			appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

		final SimpleDateFormat dateFormat =
			new SimpleDateFormat("yyyy-MM-dd", Locale.US);

		String lastChecked = prefs.getString(KEY_LAST_CHECKED_DATE, null);

		if (lastChecked != null) {

			try {

				Calendar nextDue = Calendar.getInstance();
				nextDue.setTime(dateFormat.parse(lastChecked));
				nextDue.add(Calendar.DAY_OF_MONTH, CHECK_INTERVAL_DAYS);

				if (new Date().before(nextDue.getTime())) {
					return;
				}

			} catch (Exception e) {
			}
		}

		new Thread(new Runnable() {

				@Override
				public void run() {

					DatabaseHelper db = new DatabaseHelper(appContext);

					ArrayList<HashMap<String, Object>> suggestions =
						db.getReorderSuggestions(appContext);

					prefs.edit()
						.putString(KEY_LAST_CHECKED_DATE, dateFormat.format(new Date()))
						.apply();

					if (!suggestions.isEmpty()) {
						showNotification(appContext, suggestions.size());
					}
				}
			}
		).start();
	}

	private static void showNotification(Context context, int suggestionCount) {

		NotificationManager notificationManager =
			(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

			NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				"Weekly Reorder Digest",
				NotificationManager.IMPORTANCE_DEFAULT
			);

			channel.setDescription(
				"A weekly reminder of what's worth restocking, based on recent sales speed.");

			notificationManager.createNotificationChannel(channel);
		}

		Intent intent = new Intent(context, ReorderListActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

		int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(
			context, NOTIFICATION_ID, intent, pendingIntentFlags
		);

		String title =
			suggestionCount == 1
			? "1 item worth reordering"
			: suggestionCount + " items worth reordering";

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
			.setContentText("Tap to review your Reorder List")
			.setContentIntent(pendingIntent)
			.setAutoCancel(true);

		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}
}
