package com.mujeer.businesserp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

// =====================
// Fired by AutoBackupScheduler's repeating alarm. Does the whole backup
// itself, entirely within this receiver's own extended lifetime
// (goAsync()) on a background thread - deliberately NEVER starts
// ExportVyaparActivity or any other Activity/Service. Android blocks
// starting an Activity from an unattended background trigger like this
// on API 29+ (this app's targetSdkVersion) unless the app already has
// a visible window or an active foreground service, neither of which
// applies to a routine background backup - that would make the whole
// feature silently do nothing on any real Android 10+ device.
//
// Reuses ExportVyaparActivity.buildBackupZipFile() - the same Context-
// only, Activity-independent method the manual "Export to .vyb File"
// button itself calls - so the actual export logic (every exportXxx()
// method) lives in exactly one place, not duplicated here.
//
// Writes to this app's own external-files directory (no storage
// permission needed on any supported API level), one rolling filename
// overwritten each run - a safety net against data loss, not an
// archive; the manual export is still what archiving/moving to
// another device is for.
// =====================
public class AutoBackupReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		final Context appContext = context.getApplicationContext();
		final PendingResult pendingResult = goAsync();

		new Thread(new Runnable() {
				@Override
				public void run() {

					try {
						runBackup(appContext);
					} finally {
						pendingResult.finish();
					}
				}
			}
		).start();
	}

	private void runBackup(Context context) {

		File zipFile = null;

		try {

			zipFile = ExportVyaparActivity.buildBackupZipFile(context, context.getCacheDir());

			File destDir = context.getExternalFilesDir(null);

			if (destDir == null) {
				return;
			}

			File destFile = new File(destDir, "BusinessERP_autobackup.vyb");

			InputStream in = new FileInputStream(zipFile);
			OutputStream out = new FileOutputStream(destFile);

			byte[] buffer = new byte[8192];
			int len;

			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			in.close();
			out.close();

			BackupReminderNotifier.recordBackupNow(context);

		} catch (Exception e) {

			// Silent failure - there's no one watching an alarm-triggered
			// background backup. BackupReminderNotifier's own daily check
			// is the safety net: if this keeps failing, the user still
			// gets nagged to back up manually.

		} finally {

			if (zipFile != null && zipFile.exists()) {
				zipFile.delete();
			}
		}
	}
}
