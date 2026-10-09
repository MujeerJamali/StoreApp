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
// Checks once per calendar day - same pattern as LowStockNotifier -
// for any item whose margin % has eroded 5+ points this month vs last
// (see DatabaseHelper.getMarginErosionAlerts(), the same query the
// Margin & Profit Alerts report runs, always across all items
// regardless of that report's own shoes filter). Posts one system
// notification listing each one (name + point drop) if there's at
// least one.
//
// A single eroded item's notification opens straight to that item;
// more than one opens the Margin & Profit Alerts report.
// =====================
class MarginErosionNotifier {

	private static final String PREFS_NAME = "margin_erosion_notifier";
	private static final String KEY_LAST_CHECKED_DATE = "last_checked_date";

	private static final String CHANNEL_ID = "margin_erosion_alerts";
	private static final int NOTIFICATION_ID = 5004;

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

					SimpleDateFormat dateFormat =
						new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

					Calendar thisMonthStart = Calendar.getInstance();
					thisMonthStart.set(Calendar.DAY_OF_MONTH, 1);
					thisMonthStart.set(Calendar.HOUR_OF_DAY, 0);
					thisMonthStart.set(Calendar.MINUTE, 0);
					thisMonthStart.set(Calendar.SECOND, 0);
					thisMonthStart.set(Calendar.MILLISECOND, 0);

					String thisFrom = dateFormat.format(thisMonthStart.getTime());
					String thisTo = dateFormat.format(new Date());

					Calendar lastMonthEnd = (Calendar) thisMonthStart.clone();
					lastMonthEnd.add(Calendar.DAY_OF_MONTH, -1);

					Calendar lastMonthStart = (Calendar) lastMonthEnd.clone();
					lastMonthStart.set(Calendar.DAY_OF_MONTH, 1);

					String lastFrom = dateFormat.format(lastMonthStart.getTime());
					String lastTo = dateFormat.format(lastMonthEnd.getTime());

					ArrayList<HashMap<String, Object>> erosionList = db.getMarginErosionAlerts(
						thisFrom, thisTo, lastFrom, lastTo, DatabaseHelper.SHOES_FILTER_ALL
					);

					prefs.edit().putString(KEY_LAST_CHECKED_DATE, today).apply();

					if (!erosionList.isEmpty()) {
						showNotification(appContext, erosionList);
					}
				}
			}
		).start();
	}

	private static void showNotification(
		Context context, ArrayList<HashMap<String, Object>> erosionList) {

		NotificationManager notificationManager =
			(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

			NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				"Margin Erosion Alerts",
				NotificationManager.IMPORTANCE_DEFAULT
			);

			channel.setDescription(
				"A daily alert for items whose margin % has dropped 5+ points this month vs last."
			);

			notificationManager.createNotificationChannel(channel);
		}

		Intent intent;

		if (erosionList.size() == 1) {

			intent = new Intent(context, Itemviewactivity.class);
			intent.putExtra("item_id", (Integer) erosionList.get(0).get("item_id"));

		} else {

			intent = new Intent(context, MarginProfitAlertReportActivity.class);
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

		for (HashMap<String, Object> row : erosionList) {

			Object nameObj = row.get("item_name");
			String name = nameObj == null ? "Unknown Item" : nameObj.toString();

			double erosion = row.get("erosion_points") == null ? 0 : (Double) row.get("erosion_points");

			inboxStyle.addLine(name + " - down " + AmountFormat.formatPlain(erosion) + "pp");
		}

		String title =
			erosionList.size() == 1
			? "1 item's margin has eroded"
			: erosionList.size() + " items' margins have eroded";

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
