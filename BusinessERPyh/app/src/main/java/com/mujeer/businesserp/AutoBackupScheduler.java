package com.mujeer.businesserp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

// =====================
// Schedules AutoBackupReceiver to fire roughly every 7 days (matching
// BackupReminderNotifier's own threshold - a successful auto-backup
// calls recordBackupNow() too, so the reminder should rarely ever
// actually need to nag). Plain, non-waking AlarmManager repeating
// alarm - no WorkManager dependency (AIDE can't reliably resolve a new
// one - see CLAUDE.md), no special permissions. Safe/idempotent to
// call on every app open: re-registering the same PendingIntent just
// replaces the previous schedule rather than stacking duplicates.
// =====================
class AutoBackupScheduler {

	private static final long INTERVAL_MILLIS = AlarmManager.INTERVAL_DAY * 7;
	private static final int REQUEST_CODE = 9001;

	static void ensureScheduled(Context appContext) {

		AlarmManager alarmManager =
			(AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);

		if (alarmManager == null) {
			return;
		}

		Intent intent = new Intent(appContext, AutoBackupReceiver.class);

		int flags = PendingIntent.FLAG_UPDATE_CURRENT;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			flags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendingIntent = PendingIntent.getBroadcast(
			appContext, REQUEST_CODE, intent, flags
		);

		// ELAPSED_REALTIME (not _WAKEUP) - a routine backup doesn't need
		// to wake a sleeping device, just run next time it's awake.
		alarmManager.setInexactRepeating(
			AlarmManager.ELAPSED_REALTIME,
			SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
			INTERVAL_MILLIS,
			pendingIntent
		);
	}
}
