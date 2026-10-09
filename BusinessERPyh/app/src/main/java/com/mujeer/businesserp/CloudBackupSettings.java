package com.mujeer.businesserp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

// =====================
// Persists the single folder cloud backup is "connected" to, via plain
// Storage Access Framework - no Google Sign-In/Drive API Maven
// dependency, since AIDE's on-device build can't reliably resolve a new
// one (same reasoning as SimpleBarChartView avoiding a charting
// library - see CLAUDE.md). "Connect" means the user picked a folder
// through the system's own storage chooser (see
// ExportVyaparActivity.promptConnectDrive()) and this app was granted
// persistent read/write access to it (takePersistableUriPermission).
// Picking "Drive" and a folder under mujeerahmed001@gmail.com in that
// system chooser is what ties this to that specific account - this
// class has no way to verify which Google account a chosen folder
// belongs to itself, since that's Google Drive's own DocumentsProvider's
// business, not something SAF exposes to the app that connected to it.
// =====================
public class CloudBackupSettings {

    private static final String PREFS = "cloud_backup_prefs";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final String KEY_LAST_BACKUP_AT = "last_backup_at";

    public static boolean isConnected(Context context) {
        return getFolderUri(context) != null;
    }

    public static Uri getFolderUri(Context context) {

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_FOLDER_URI, null);

        return raw == null ? null : Uri.parse(raw);
    }

    public static void setFolderUri(Context context, Uri uri) {

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply();
    }

    public static void disconnect(Context context) {

        Uri uri = getFolderUri(context);

        if (uri != null) {

            try {

                context.getContentResolver().releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );

            } catch (Exception e) {
                // Already released (e.g. the user revoked it from
                // Android's own Storage Access settings) - nothing else
                // to clean up here.
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_FOLDER_URI).remove(KEY_LAST_BACKUP_AT).apply();
    }

    static void recordBackupNow(Context context) {

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis()).apply();
    }

    public static long getLastBackupAt(Context context) {

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_BACKUP_AT, 0);
    }

    // The connected folder's own current display name (e.g. "BusinessERP
    // Backups"), read fresh from its DocumentsProvider every call rather
    // than cached, since the user could rename it outside the app. Null
    // if not connected or the provider can no longer resolve it (e.g.
    // the folder was deleted, or permission was revoked outside the app).
    public static String getFolderDisplayName(Context context) {

        Uri treeUri = getFolderUri(context);

        if (treeUri == null) {
            return null;
        }

        try {

            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));

            Cursor cursor = context.getContentResolver().query(
                docUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null
            );

            if (cursor == null) {
                return null;
            }

            String name = null;

            if (cursor.moveToFirst()) {
                name = cursor.getString(0);
            }

            cursor.close();
            return name;

        } catch (Exception e) {
            return null;
        }
    }
}
