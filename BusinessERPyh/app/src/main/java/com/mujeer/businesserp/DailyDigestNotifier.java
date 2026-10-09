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
import java.util.HashMap;
import java.util.Locale;

// =====================
// Today's Sales/Purchases/Expenses/Net Cash Movement (see
// DatabaseHelper.getDayCloseSummary()), posted as one notification by
// DailyDigestReceiver's fixed-time daily alarm (DailyDigestScheduler,
// default 9 PM) - the same numbers Day Close shows, delivered without
// needing to open the app. Unlike LowStockNotifier/CreditDueNotifier,
// this always posts (even on a zero-activity day - "nothing happened
// today" is still worth confirming, not something to silently skip)
// but still guards against posting twice for the same date, in case
// the alarm ever fires more than once.
// =====================
class DailyDigestNotifier {

	private static final String PREFS_NAME = "daily_digest_notifier";
	private static final String KEY_LAST_POSTED_DATE = "last_posted_date";

	private static final String CHANNEL_ID = "daily_digest";
	private static final int NOTIFICATION_ID = 5005;

	static void showDigestNotification(Context context) {

		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

		if (today.equals(prefs.getString(KEY_LAST_POSTED_DATE, null))) {
			return;
		}

		DatabaseHelper db = new DatabaseHelper(context);
		HashMap<String, Object> summary = db.getDayCloseSummary(today);

		prefs.edit().putString(KEY_LAST_POSTED_DATE, today).apply();

		NotificationManager notificationManager =
			(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

			NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				"Daily Digest",
				NotificationManager.IMPORTANCE_DEFAULT
			);

			channel.setDescription("One end-of-day summary of Sales, Purchases, Expenses, and cash movement.");

			notificationManager.createNotificationChannel(channel);
		}

		Intent intent = new Intent(context, DayCloseReportActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

		int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(
			context, NOTIFICATION_ID, intent, pendingIntentFlags
		);

		int salesCount = (Integer) summary.get("sales_count");
		double salesTotal = (Double) summary.get("sales_total");
		int purchasesCount = (Integer) summary.get("purchases_count");
		double purchasesTotal = (Double) summary.get("purchases_total");
		double expensesTotal = (Double) summary.get("expenses_total");
		double netCashMovement = (Double) summary.get("net_cash_movement");

		Notification.InboxStyle inboxStyle = new Notification.InboxStyle();

		inboxStyle.addLine(
			"Sales: " + AmountFormat.format(salesTotal) +
			" (" + salesCount + (salesCount == 1 ? " sale" : " sales") + ")"
		);

		inboxStyle.addLine(
			"Purchases: " + AmountFormat.format(purchasesTotal) +
			" (" + purchasesCount + (purchasesCount == 1 ? " purchase" : " purchases") + ")"
		);

		inboxStyle.addLine("Expenses: " + AmountFormat.format(expensesTotal));

		inboxStyle.addLine(
			"Net Cash: " + (netCashMovement >= 0 ? "+" : "") + AmountFormat.format(netCashMovement)
		);

		String title = "Today's Digest: " + AmountFormat.format(salesTotal) + " in sales";

		inboxStyle.setBigContentTitle(title);

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
			.setContentText("Tap to see today's Day Close")
			.setStyle(inboxStyle)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true);

		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}
}
