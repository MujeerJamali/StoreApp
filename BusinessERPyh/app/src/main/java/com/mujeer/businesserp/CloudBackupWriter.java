package com.mujeer.businesserp;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// =====================
// Shared by ExportVyaparActivity (after a manual export) and
// AutoBackupReceiver (after a scheduled one) - copies an already-built
// .vyb zip into the user's connected cloud folder, if any, using plain
// framework DocumentsContract calls (see CloudBackupSettings for why -
// no Drive API/Google Sign-In Maven dependency). Every call writes a
// NEW timestamped file rather than overwriting one fixed name, since
// DocumentsContract has no reliable cross-provider "overwrite by name"
// primitive and silently colliding filenames would be worse than a few
// timestamped backups the user can thin out in Drive itself.
// =====================
class CloudBackupWriter {

    static boolean writeIfConnected(Context context, File zipFile) {

        if (!CloudBackupSettings.isConnected(context)) {
            return false;
        }

        Uri treeUri = CloudBackupSettings.getFolderUri(context);

        InputStream in = null;
        OutputStream out = null;

        try {

            ContentResolver resolver = context.getContentResolver();

            Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));

            String filename = "BusinessERP_backup_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) +
                ".vyb";

            Uri newFileUri = DocumentsContract.createDocument(
                resolver, parentDocUri, "application/octet-stream", filename);

            if (newFileUri == null) {
                return false;
            }

            out = resolver.openOutputStream(newFileUri);

            if (out == null) {
                return false;
            }

            in = new FileInputStream(zipFile);

            byte[] buffer = new byte[8192];
            int len;

            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }

            CloudBackupSettings.recordBackupNow(context);
            return true;

        } catch (Exception e) {

            // The connected folder may have been deleted or had its
            // permission revoked outside the app (Drive cleanup,
            // Android's own Storage Access settings) - fail silently
            // here the same way the rest of the auto-backup path
            // already does; a manual export's own status text still
            // reports success/failure for the plain file-picker save,
            // which is unaffected by this.
            return false;

        } finally {

            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}

            try {
                if (out != null) out.close();
            } catch (Exception ignored) {}
        }
    }
}
