package com.mujeer.businesserp;

import android.app.Application;

public class BusinessERPApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		// Once-per-calendar-day check for any credit Sale due today (see
		// CreditDueNotifier) - lives here, not in a specific Activity's
		// onCreate()/onResume(), so it fires exactly once per process
		// start no matter which screen the app actually opens to. The
		// app's launcher is Transactioneditactivity (straight to Add
		// Sale), not MainActivity's Dashboard - see AndroidManifest.xml.
		CreditDueNotifier.checkAndNotifyIfNeeded(getApplicationContext());
	}
}
