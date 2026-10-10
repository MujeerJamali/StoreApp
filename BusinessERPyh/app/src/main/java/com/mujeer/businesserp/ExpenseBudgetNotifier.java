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
// Checks once per calendar day - same pattern as MarginErosionNotifier -
// for any expense category that's gone over its own Monthly Budget (see
// CostItemEditActivity's own field and
// DatabaseHelper.getCategoryBudgetStatus(), the same query the Cost
// Items list's own status badge uses). Posts one system notification
// listing each over-budget category (spent vs budget) if there's at
// least one - a category with no budget set never triggers this at all.
//
// A single over-budget category's notification opens straight to Cost
// Items (there's no per-category expense list screen to open instead);
// more than one does the same.
// =====================
class ExpenseBudgetNotifier {

	private static final String PREFS_NAME = "expense_budget_notifier";
	private static final String KEY_LAST_CHECKED_DATE = "last_checked_date";

	private static final String CHANNEL_ID = "expense_budget_alerts";
	private static final int NOTIFICATION_ID = 5006;

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

					ArrayList<HashMap<String, Object>> budgetStatus = db.getCategoryBudgetStatus();

					ArrayList<HashMap<String, Object>> overBudget =
						new ArrayList<HashMap<String, Object>>();

					for (HashMap<String, Object> row : budgetStatus) {

						if (Boolean.TRUE.equals(row.get("over_budget"))) {
							overBudget.add(row);
						}
					}

					prefs.edit().putString(KEY_LAST_CHECKED_DATE, today).apply();

					if (!overBudget.isEmpty()) {
						showNotification(appContext, overBudget);
					}
				}
			}
		).start();
	}

	private static void showNotification(
		Context context, ArrayList<HashMap<String, Object>> overBudget) {

		NotificationManager notificationManager =
			(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (notificationManager == null) {
			return;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

			NotificationChannel channel = new NotificationChannel(
				CHANNEL_ID,
				"Expense Budget Alerts",
				NotificationManager.IMPORTANCE_DEFAULT
			);

			channel.setDescription(
				"A daily alert for any expense category that's gone over its monthly budget."
			);

			notificationManager.createNotificationChannel(channel);
		}

		Intent intent = new Intent(context, CostItemsActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

		int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendingIntent = PendingIntent.getActivity(
			context, NOTIFICATION_ID, intent, pendingIntentFlags
		);

		Notification.InboxStyle inboxStyle = new Notification.InboxStyle();

		for (HashMap<String, Object> row : overBudget) {

			String name = String.valueOf(row.get("name"));
			double spent = row.get("spent") == null ? 0 : (Double) row.get("spent");
			double budget = row.get("budget") == null ? 0 : (Double) row.get("budget");

			inboxStyle.addLine(
				name + ": " + AmountFormat.format(spent) + " of " + AmountFormat.format(budget)
			);
		}

		String title =
			overBudget.size() == 1
			? "1 expense category is over budget"
			: overBudget.size() + " expense categories are over budget";

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
