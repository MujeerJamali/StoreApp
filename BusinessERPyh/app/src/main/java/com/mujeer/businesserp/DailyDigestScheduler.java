package com.mujeer.businesserp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

// =====================
// Schedules DailyDigestReceiver to fire once a day at a fixed local
// time (9:00 PM - a reasonable default end-of-day moment for a shop
// that's usually closed by then; not yet exposed as a setting). Uses
// wall-clock RTC (not RTC_WAKEUP) rather than AutoBackupScheduler's
// ELAPSED_REALTIME, since "end of day" means a specific time of day,
// not "24 hours after whenever the app was last opened" - a routine
// digest doesn't need to wake a sleeping device either, just fire next
// time it's awake at or after 9 PM. Safe/idempotent to call on every
// app open: re-registering the same PendingIntent just replaces the
// previous schedule rather than stacking duplicates.
// =====================
class DailyDigestScheduler {

	private static final int DIGEST_HOUR = 21;
	private static final int REQUEST_CODE = 9002;

	static void ensureScheduled(Context appContext) {

		AlarmManager alarmManager =
			(AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);

		if (alarmManager == null) {
			return;
		}

		Intent intent = new Intent(appContext, DailyDigestReceiver.class);

		int flags = PendingIntent.FLAG_UPDATE_CURRENT;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			flags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendingIntent = PendingIntent.getBroadcast(
			appContext, REQUEST_CODE, intent, flags
		);

		Calendar next = Calendar.getInstance();
		next.set(Calendar.HOUR_OF_DAY, DIGEST_HOUR);
		next.set(Calendar.MINUTE, 0);
		next.set(Calendar.SECOND, 0);
		next.set(Calendar.MILLISECOND, 0);

		if (next.before(Calendar.getInstance())) {
			next.add(Calendar.DAY_OF_MONTH, 1);
		}

		alarmManager.setRepeating(
			AlarmManager.RTC,
			next.getTimeInMillis(),
			AlarmManager.INTERVAL_DAY,
			pendingIntent
		);
	}
}
