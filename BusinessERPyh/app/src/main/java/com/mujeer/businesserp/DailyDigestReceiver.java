package com.mujeer.businesserp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// =====================
// Fired by DailyDigestScheduler's repeating alarm. Does the whole
// digest lookup and notification post itself, entirely within this
// receiver's own extended lifetime (goAsync()) on a background thread
// - same pattern as AutoBackupReceiver, and for the same reason:
// posting a notification is always allowed from a background trigger,
// it's only starting an Activity directly (not through a notification
// tap, which is a normal foreground user action) that API 29+ blocks.
// =====================
public class DailyDigestReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		final Context appContext = context.getApplicationContext();
		final PendingResult pendingResult = goAsync();

		new Thread(new Runnable() {
				@Override
				public void run() {

					try {
						DailyDigestNotifier.showDigestNotification(appContext);
					} finally {
						pendingResult.finish();
					}
				}
			}
		).start();
	}
}
