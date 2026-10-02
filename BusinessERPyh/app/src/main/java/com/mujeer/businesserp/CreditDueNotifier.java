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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Checks once per calendar day - not on every single app open, tracked
// in its own SharedPreferences rather than the business database, since
// this is local notification state and not something a Vyapar backup
// needs to carry - whether any credit Sale's Due Date is today, and if
// so posts one system notification listing each one (party + amount).
// See BusinessERPApplication.onCreate() for the one call site: it fires
// once per process start regardless of which screen the app actually
// opens to (normally Transactioneditactivity, the launcher - see
// AndroidManifest.xml - not necessarily MainActivity's Dashboard).
//
// A single due-today Sale's notification opens straight to that Sale;
// more than one opens the Credit Due report, which already defaults to
// today (see CreditDueReportActivity.RANGE_TODAY).
// =====================
class CreditDueNotifier {

	private static final String PREFS_NAME = "credit_due_notifier";
	private static final String KEY_LAST_CHECKED_DATE = "last_checked_date";

	private static final String CHANNEL_ID = "credit_due_reminders";
	private static final int NOTIFICATION_ID = 5001;

	static void checkAndNotifyIfNeeded(final Context appContext) {

		final SharedPreferences prefs =
			appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

		final String today =
			new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

		if (today.equals(prefs.getString(KEY_LAST_CHECKED_DATE, null))) {
			return;
		}

		new Thread(new Runnable() {

				@Override
				public void run() {

					DatabaseHelper db = new DatabaseHelper(appContext);

					ArrayList<HashMap<String, Object>> dueToday =
						db.getCreditDueSales(today, today);

					// Marked checked regardless of whether anything was
					// actually due, so this doesn't re-query the database
					// on every later app open the same day.
					prefs.edit().putString(KEY_LAST_CHECKED_DATE, today).apply();

					if (!dueToday.isEmpty()) {
						showNotification(appContext, dueToday);
					}
				}
			}
		).start();
	}

	private static void showNotification(
		Context context, ArrayList<HashMap<String, Object>> dueToday) {

		NotificationManager notificationManager =
			(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

			NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				"Credit Due Reminders",
				NotificationManager.IMPORTANCE_DEFAULT
			);

			channel.setDescription("A daily reminder for credit Sales due today.");

			notificationManager.createNotificationChannel(channel);
		}

		Intent intent;

		if (dueToday.size() == 1) {

			intent = new Intent(context, Transactionviewactivity.class);
			intent.putExtra("transaction_type", DatabaseHelper.TRANSACTION_TYPE_SALE);
			intent.putExtra(
				"transaction_id", (Integer) dueToday.get(0).get("sale_id")
			);

		} else {

			intent = new Intent(context, CreditDueReportActivity.class);
		}

		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

		int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(
			context, NOTIFICATION_ID, intent, pendingIntentFlags
		);

		Notification.InboxStyle inboxStyle = new Notification.InboxStyle();

		double total = 0;

		for (HashMap<String, Object> row : dueToday) {

			Object partyNameObj = row.get("party_name");

			String partyName =
				partyNameObj == null ? "Unknown Party" : partyNameObj.toString();

			double balance = (Double) row.get("balance");

			total += balance;

			inboxStyle.addLine(partyName + " - " + AmountFormat.format(balance));
		}

		String title =
			dueToday.size() == 1
			? "1 payment due today"
			: dueToday.size() + " payments due today";

		inboxStyle.setBigContentTitle(title);
		inboxStyle.setSummaryText("Total " + AmountFormat.format(total));

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
			.setContentText("Total " + AmountFormat.format(total) + " - tap to view")
			.setStyle(inboxStyle)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true);

		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}
}
