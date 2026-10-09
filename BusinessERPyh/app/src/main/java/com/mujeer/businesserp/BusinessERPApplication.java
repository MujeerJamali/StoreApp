package com.mujeer.businesserp;

import android.app.Application;

public class BusinessERPApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		// Once-per-calendar-day checks (see each notifier's own comment)
		// - live here, not in a specific Activity's onCreate()/onResume(),
		// so they fire exactly once per process start no matter which
		// screen the app actually opens to. The app's launcher is
		// Transactioneditactivity (straight to Add Sale), not
		// MainActivity's Dashboard - see AndroidManifest.xml.
		CreditDueNotifier.checkAndNotifyIfNeeded(getApplicationContext());
		LowStockNotifier.checkAndNotifyIfNeeded(getApplicationContext());
		BackupReminderNotifier.checkAndNotifyIfNeeded(getApplicationContext());
		ReorderDigestNotifier.checkAndNotifyIfNeeded(getApplicationContext());
		MarginErosionNotifier.checkAndNotifyIfNeeded(getApplicationContext());

		// Idempotent - re-registering the same alarm on every app open
		// just replaces its schedule, never stacks duplicates.
		AutoBackupScheduler.ensureScheduled(getApplicationContext());

		// A single cheap DELETE WHERE - safe to run unconditionally on
		// every app open rather than gating it to once a day like the
		// notifiers above, which each show a user-visible notification
		// that actually needs that throttling.
		new DatabaseHelper(getApplicationContext()).purgeOldRecentlyDeleted(30);
	}
}
