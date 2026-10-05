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
// Checks once per calendar day - same pattern as CreditDueNotifier,
// its own SharedPreferences rather than the business database - for
// any active item whose stock is at or below its own Reorder
// Threshold, and if so posts one system notification listing each one
// (name + current stock). See BusinessERPApplication.onCreate() for
// the one call site.
//
// A single low-stock item's notification opens straight to that item;
// more than one opens the Low Stock report.
// =====================
class LowStockNotifier {

	private static final String PREFS_NAME = "low_stock_notifier";
	private static final String KEY_LAST_CHECKED_DATE = "last_checked_date";

	private static final String CHANNEL_ID = "low_stock_alerts";
	private static final int NOTIFICATION_ID = 5002;

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

					ArrayList<HashMap<String, Object>> lowStock = db.getLowStockItems();

					prefs.edit().putString(KEY_LAST_CHECKED_DATE, today).apply();

					if (!lowStock.isEmpty()) {
						showNotification(appContext, lowStock);
					}
				}
			}
		).start();
	}

	private static void showNotification(
		Context context, ArrayList<HashMap<String, Object>> lowStock) {

		NotificationManager notificationManager =
			(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

			NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				"Low Stock Alerts",
				NotificationManager.IMPORTANCE_DEFAULT
			);

			channel.setDescription("A daily alert for items at or below their reorder threshold.");

			notificationManager.createNotificationChannel(channel);
		}

		Intent intent;

		if (lowStock.size() == 1) {

			intent = new Intent(context, Itemviewactivity.class);
			intent.putExtra("item_id", (Integer) lowStock.get(0).get("item_id"));

		} else {

			intent = new Intent(context, LowStockReportActivity.class);
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

		for (HashMap<String, Object> row : lowStock) {

			Object nameObj = row.get("name");
			String name = nameObj == null ? "Unknown Item" : nameObj.toString();

			double balance = row.get("balance") == null ? 0 : (Double) row.get("balance");

			inboxStyle.addLine(name + " - " + AmountFormat.formatPlain(balance) + " left");
		}

		String title =
			lowStock.size() == 1
			? "1 item low on stock"
			: lowStock.size() + " items low on stock";

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
			.setContentText("Tap to view")
			.setStyle(inboxStyle)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true);

		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}
}
