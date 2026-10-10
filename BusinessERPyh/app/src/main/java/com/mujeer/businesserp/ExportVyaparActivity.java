package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// =====================
// Exports this app's data into a .vyb file - a zip wrapping a SQLite
// database shaped exactly like the subset of Vyapar's own schema that
// ImportVyaparActivity reads (kb_names, kb_items, kb_transactions,
// kb_lineitems, party_to_party_transfer - see that class's comments for
// the exact fields each one carries; every column written here is one the
// importer actually reads, nothing more), plus a set of this app's own
// extensions that have no equivalent in Vyapar's own schema - item
// varieties (groups/values/combos, and each line item's chosen combo_id),
// cash adjustments, cost items, purchase-linked expenses (landed cost),
// recurring expense rules, and drafts - each in its own businesserp_*
// table (or, for combo_id/extra_cost_per_unit, an extra column on a
// Vyapar-shaped table) - see createVyaparShapedTables() below.
//
// This is NOT a claim that a real Vyapar app can restore from this file -
// Vyapar's actual schema is closed source and almost certainly has many
// more tables/columns this app has no way to know about, plus whatever
// internal versioning/checksum bookkeeping the real app relies on. What
// this DOES give you is a working round trip through this app's own
// importer: export here, then "Restore Vyapar Backup" restores everything
// from it, using the exact same file format and mechanism as restoring an
// actual Vyapar backup.
//
// ImportVyaparActivity is a full restore, not a merge - it clears every
// party/item/purchase/sale/payment/expense/transfer already in the app
// before writing this file's data in fresh (with a confirmation first,
// since that's destructive). So restoring your own export back into the
// same live app resets it to exactly that export's snapshot, discarding
// anything added since - it's meant for a fresh install or a different
// device, not as a way to merge data back in.
// =====================
public class ExportVyaparActivity extends Activity {

	private static final int REQUEST_SAVE_VYB = 3001;
	private static final int REQUEST_CONNECT_DRIVE = 3002;

	// Large fixed offsets keep synthesized ids from different local
	// tables from colliding once they all share Vyapar's single
	// kb_transactions/kb_lineitems/kb_names id space. No business using
	// this app will ever have anywhere near a million rows in one table,
	// so these can never overlap with a real local id or each other.
	private static final long OFFSET_PURCHASE_TXN = 10_000_000L;
	private static final long OFFSET_SALE_TXN = 20_000_000L;
	private static final long OFFSET_PAYMENT_TXN = 30_000_000L;
	// Package-private (not private) - ImportVyaparActivity.importDrafts()
	// also needs this exact value, to translate a Purchase draft's raw,
	// un-offset embedded expense_id (see Transactioneditactivity's
	// pendingLinkedExpenses) into the same offset kb_transactions-style key
	// exportExpenses()/importExpenses() already use for every real
	// (committed) expense, rather than duplicating this constant.
	static final long OFFSET_EXPENSE_TXN = 40_000_000L;
	private static final long OFFSET_TRANSFER_PAID_TXN = 50_000_000L;
	private static final long OFFSET_TRANSFER_RECEIVED_TXN = 60_000_000L;
	private static final long OFFSET_SALE_LINEITEM = 5_000_000L;
	private static final long OFFSET_EXPENSE_CATEGORY_NAME = 1_000_000L;

	private Button btn_export_vyb;
	private TextView tv_export_result;
	private Button btn_connect_drive;
	private TextView tv_drive_status;
	private TextView tv_backup_health;

	// The zipped .vyb sitting in cache storage, waiting to be copied to
	// wherever the user picks in writeZipToDestination() - null whenever
	// there isn't one pending.
	private File pendingZipFile;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.export_vyapar);

		btn_export_vyb = (Button) findViewById(R.id.btn_export_vyb);
		tv_export_result = (TextView) findViewById(R.id.tv_export_result);
		btn_connect_drive = (Button) findViewById(R.id.btn_connect_drive);
		tv_drive_status = (TextView) findViewById(R.id.tv_drive_status);
		tv_backup_health = (TextView) findViewById(R.id.tv_backup_health);

		applyBackupHealthStatus();

		btn_export_vyb.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					btn_export_vyb.setEnabled(false);

					new Thread(new Runnable() {
							@Override
							public void run() {
								buildBackupFile();
							}
						}).start();
				}
			});

		btn_connect_drive.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					if (CloudBackupSettings.isConnected(ExportVyaparActivity.this)) {
						confirmDisconnectDrive();
					} else {
						promptConnectDrive();
					}
				}
			});

		refreshDriveStatus();
	}

	// =====================
	// CLOUD BACKUP (GOOGLE DRIVE) - a "connect once" folder picker, not a
	// Google Sign-In integration: this app has no Drive API/Google
	// Sign-In Maven dependency (see CloudBackupSettings), so "connect"
	// means picking a folder through the system's own storage chooser,
	// which already lets the user navigate into "Drive" and pick a
	// folder under whichever Google account (mujeerahmed001@gmail.com)
	// is signed into the Drive app on this device, then persisting
	// read/write access to exactly that folder.
	// =====================
	private void promptConnectDrive() {

		new AlertDialog.Builder(this)
			.setTitle("Connect Google Drive")
			.setMessage(
				"You'll be shown the system's \"Save to\" picker. Choose \"Drive\" from the " +
				"left-hand list, sign into mujeerahmed001@gmail.com if asked, then pick or " +
				"create a folder there (e.g. \"BusinessERP Backups\") and tap Select/Use " +
				"this folder.\n\nOnce connected, every backup - manual and automatic - also " +
				"saves a timestamped copy into that folder.")
			.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {

						Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
						startActivityForResult(intent, REQUEST_CONNECT_DRIVE);
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void confirmDisconnectDrive() {

		new AlertDialog.Builder(this)
			.setTitle("Disconnect Google Drive")
			.setMessage(
				"Future backups will no longer be copied to this folder. Nothing already " +
				"saved there is deleted.")
			.setPositiveButton("Disconnect", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						CloudBackupSettings.disconnect(ExportVyaparActivity.this);
						refreshDriveStatus();
					}
				})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void refreshDriveStatus() {

		if (CloudBackupSettings.isConnected(this)) {

			String folderName = CloudBackupSettings.getFolderDisplayName(this);

			tv_drive_status.setText(
				"Connected: " + (folderName != null ? folderName : "Drive folder"));
			btn_connect_drive.setText("Disconnect");

		} else {

			tv_drive_status.setText("Not connected. Backups are not copied to the cloud.");
			btn_connect_drive.setText("Connect Google Drive");
		}
	}

	private void setStatus(final String text) {

		runOnUiThread(new Runnable() {
				@Override
				public void run() {
					tv_export_result.setText(text);
				}
			});
	}

	// Backup health check (approved feature "last successful backup:
	// X days ago" warning) - a visible, always-there readout next to
	// the Export button, on top of BackupReminderNotifier's existing
	// silent 7-day-overdue notification. Same threshold/wording as
	// that notification, so the two never disagree.
	private void applyBackupHealthStatus() {

		long daysSince = BackupReminderNotifier.getDaysSinceLastBackup(this);

		String text;

		if (daysSince < 0) {

			text = "Never backed up yet - export one below.";

		} else if (daysSince == 0) {

			text = "Last successful backup: today.";

		} else if (daysSince == 1) {

			text = "Last successful backup: 1 day ago.";

		} else {

			text = "Last successful backup: " + daysSince + " days ago.";
		}

		tv_backup_health.setText(text);

		boolean overdue = daysSince < 0
			|| daysSince >= BackupReminderNotifier.getReminderThresholdDays();

		tv_backup_health.setTextColor(
			getResources().getColor(overdue ? R.color.danger : R.color.text_secondary)
		);
	}

	// =====================
	// Builds the .vyb (zipped SQLite db) into cache storage via
	// buildBackupZipFile() below, then hands off to the system file
	// picker to choose where it's actually saved - the app has no
	// storage permission and doesn't need one this way.
	// =====================
	private void buildBackupFile() {

		setStatus("Reading your data...");

		try {

			pendingZipFile = buildBackupZipFile(this, getCacheDir());

			runOnUiThread(new Runnable() {
					@Override
					public void run() {
						openSavePicker();
					}
				});

		} catch (final Exception e) {

			setStatus("Export failed: " + e.toString());

			runOnUiThread(new Runnable() {
					@Override
					public void run() {
						btn_export_vyb.setEnabled(true);
					}
				});
		}
	}

	// =====================
	// The actual export work, needing nothing but a plain Context - no
	// Activity dependency. Split out from buildBackupFile() above so
	// AutoBackupReceiver can call this exact same logic directly from
	// its own background thread (see that class) instead of starting
	// this Activity, which Android blocks when triggered unattended
	// from the background (e.g. an AlarmManager alarm) on API 29+ (this
	// app's targetSdkVersion) unless the app already has a visible
	// window or an active foreground service - neither applies to a
	// routine background backup. Every exportXxx()/createVyaparShapedTables()/
	// zipDatabaseFile() method this calls is static for the same reason:
	// none of them ever touched instance/UI state anyway, only the
	// SQLiteDatabase/File arguments already passed in. Returns the
	// finished zip file; the caller decides what to do with it and is
	// responsible for deleting it once done.
	// =====================
	static File buildBackupZipFile(Context context, File cacheDir) throws Exception {

		File dbFile = new File(cacheDir, "vyb_export_raw.tmp");
		File zipFile = new File(cacheDir, "vyb_export.tmp");

		if (dbFile.exists()) {
			dbFile.delete();
		}

		SQLiteDatabase vyb = null;

		try {

			vyb = SQLiteDatabase.openOrCreateDatabase(dbFile, null);

			createVyaparShapedTables(vyb);

			DatabaseHelper helper = new DatabaseHelper(context);
			SQLiteDatabase local = helper.getReadableDatabase();

			HashMap<String, Long> expenseCategoryNameId = new HashMap<String, Long>();

			vyb.beginTransaction();

			exportParties(local, vyb);
			exportItems(local, vyb);
			exportVarietyGroups(local, vyb);
			exportVarietyValues(local, vyb);
			exportVarietyCombos(local, vyb);
			exportVarietyComboValues(local, vyb);
			exportCashAdjustments(local, vyb);
			exportExpenseCategories(local, vyb, expenseCategoryNameId);
			exportPurchases(local, vyb);
			exportPurchaseItems(local, vyb);
			exportSales(local, vyb);
			exportSaleItems(local, vyb);
			exportPayments(local, vyb);
			exportExpenses(local, vyb, expenseCategoryNameId);
			exportPartyTransfers(local, vyb);
			exportCostItems(local, vyb);
			exportPurchaseExpenseLinks(local, vyb);
			exportRecurringExpenses(local, vyb);
			exportDrafts(local, vyb);
			exportWantedItems(local, vyb);
			exportDisplayShoes(local, vyb);
			exportSampleShoes(local, vyb);
			exportItemClearance(local, vyb);
			exportLoyaltyPoints(local, vyb);
			exportSeasonalCalendar(local, vyb);
			exportStockTake(local, vyb);

			// Deliberately NOT exported: TABLE_RECENTLY_DELETED. Unlike
			// every table above, it holds already-deleted data's
			// temporary echo (a 30-day undo grace period, auto-purged -
			// see DatabaseHelper.purgeOldRecentlyDeleted()), not real
			// standing user data the way Drafts (unsaved future work)
			// or Wanted Items are. Its snapshot JSON is also keyed to
			// this exact database's own party_id/item_id/combo_id
			// values, which a restore-into-a-different-install backup
			// has no way to remap the way importDrafts() remaps a
			// draft's embedded ids - carrying it over would risk
			// restoring a transaction against the wrong party/item in
			// the destination database.
			//
			// Also deliberately NOT exported: TABLE_REORDER_SUGGESTION_LOG
			// - see its own DATABASE_VERSION bump comment in
			// DatabaseHelper. It's the Reorder List's own operational
			// memory (what was suggested and what happened to it), not a
			// business record - same reasoning as recently_deleted above,
			// and for the same id-remapping reason a restore just starts
			// that learning loop fresh rather than carrying it over.

			vyb.setTransactionSuccessful();
			vyb.endTransaction();

			vyb.close();
			vyb = null;

			zipDatabaseFile(dbFile, zipFile);

			return zipFile;

		} finally {

			if (vyb != null) {
				vyb.close();
			}

			if (dbFile.exists()) {
				dbFile.delete();
			}
		}
	}

	private void openSavePicker() {

		String filename = "BusinessERP_backup_" +
			new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) +
			".vyb";

		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("application/octet-stream");
		intent.putExtra(Intent.EXTRA_TITLE, filename);

		setStatus("Choose where to save the backup...");

		startActivityForResult(intent, REQUEST_SAVE_VYB);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_CONNECT_DRIVE) {

			if (resultCode == RESULT_OK && data != null && data.getData() != null) {

				Uri treeUri = data.getData();

				getContentResolver().takePersistableUriPermission(
					treeUri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				);

				CloudBackupSettings.setFolderUri(this, treeUri);
			}

			refreshDriveStatus();
			return;
		}

		if (requestCode != REQUEST_SAVE_VYB) {
			return;
		}

		btn_export_vyb.setEnabled(true);

		if (resultCode != RESULT_OK || data == null || data.getData() == null) {

			setStatus("Save cancelled. The backup was not written anywhere.");
			cleanupPendingZip();
			return;
		}

		final Uri destUri = data.getData();

		new Thread(new Runnable() {
				@Override
				public void run() {
					writeZipToDestination(destUri);
				}
			}).start();
	}

	private void writeZipToDestination(Uri destUri) {

		setStatus("Saving...");

		File zipFile = pendingZipFile;

		if (zipFile == null || !zipFile.exists()) {
			setStatus("Export failed: the backup file went missing before it could be saved.");
			return;
		}

		try {

			ContentResolver resolver = getContentResolver();
			OutputStream out = resolver.openOutputStream(destUri);

			if (out == null) {
				throw new Exception("Could not open the destination file for writing.");
			}

			InputStream in = new FileInputStream(zipFile);
			byte[] buffer = new byte[8192];
			int len;

			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			in.close();
			out.close();

			// Resets BackupReminderNotifier's countdown - a backup just
			// actually succeeded, so there's nothing overdue right now.
			BackupReminderNotifier.recordBackupNow(getApplicationContext());

			runOnUiThread(new Runnable() {
					@Override
					public void run() {
						applyBackupHealthStatus();
					}
				});

			// If a Google Drive folder is connected (see the Cloud Backup
			// card below), also drop a timestamped copy there - on top
			// of, not instead of, the file the user just picked above.
			boolean alsoWroteToCloud =
				CloudBackupWriter.writeIfConnected(getApplicationContext(), zipFile);

			setStatus(
				"Backup saved." +
				(alsoWroteToCloud ?
					"\n\nAlso saved a copy to your connected Google Drive folder." : "") +
				"\n\nRestore it later - on this device after a reinstall, or on " +
				"another device with this app - using \"Restore Vyapar Backup\". Restoring it " +
				"replaces everything currently there, so don't restore it back into this same " +
				"app unless you want to reset it to this exact snapshot.");

		} catch (Exception e) {

			setStatus("Export failed while saving: " + e.toString());

		} finally {

			cleanupPendingZip();
		}
	}

	private void cleanupPendingZip() {

		if (pendingZipFile != null && pendingZipFile.exists()) {
			pendingZipFile.delete();
		}

		pendingZipFile = null;
	}

	// =====================
	// Wraps the raw SQLite file in a zip, exactly the shape
	// ImportVyaparActivity.prepareVyaparDatabaseFile() expects (a zip
	// whose first non-directory entry is the database) - the entry name
	// itself isn't read by the importer, so any name works.
	// =====================
	private static void zipDatabaseFile(File dbFile, File zipFile) throws Exception {

		OutputStream fos = new FileOutputStream(zipFile);
		ZipOutputStream zos = new ZipOutputStream(fos);

		zos.putNextEntry(new ZipEntry("vyapar_user_db"));

		InputStream in = new FileInputStream(dbFile);
		byte[] buffer = new byte[8192];
		int len;

		while ((len = in.read(buffer)) > 0) {
			zos.write(buffer, 0, len);
		}

		in.close();
		zos.closeEntry();
		zos.close();
	}

	// =====================
	// Only the columns ImportVyaparActivity actually reads - see its
	// importParties/importItems/importPurchases/importPurchaseLineItems/
	// importSales/importSaleLineItems/importPayments/importPartyTransfers/
	// importExpenses methods for exactly which ones and why.
	// =====================
	private static void createVyaparShapedTables(SQLiteDatabase vyb) {

		vyb.execSQL(
			"CREATE TABLE kb_names (" +
			"name_id INTEGER PRIMARY KEY, " +
			"full_name TEXT, " +
			"name_type INTEGER, " +
			// This app's own extension (party appearance/description notes,
			// used to help recognize a walk-in customer later) - absent from
			// a real Vyapar backup or an export made before this field
			// existed, which ImportVyaparActivity's columnExists check
			// detects up front so an older backup still restores cleanly.
			"full_name_appearance TEXT, " +
			// Another of this app's own extensions (customer tagging:
			// Regular/One-Time/Wholesale) - same columnExists gating as
			// full_name_appearance above.
			"full_name_customer_type TEXT" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE kb_items (" +
			"item_id INTEGER PRIMARY KEY, " +
			"item_code TEXT, " +
			"item_name TEXT, " +
			"item_purchase_unit_price REAL, " +
			"item_sale_unit_price REAL, " +
			"item_type INTEGER, " +
			// This app's own extension, same reasoning as
			// kb_lineitems.combo_id below - absent from a real Vyapar
			// backup or an export made before landed cost existed, which
			// ImportVyaparActivity's itemsHaveExtraCost detects up front
			// so it defaults to 0 instead of failing.
			"item_extra_cost_per_unit REAL, " +
			// Another of this app's own extensions - absent from a real
			// Vyapar backup or one exported before Active/Inactive
			// existed, which ImportVyaparActivity's itemsHaveActive
			// detects up front so it defaults to active (1) instead of
			// failing.
			"item_active INTEGER, " +
			// Another of this app's own extensions (Low Stock alert
			// threshold) - absent from a real Vyapar backup or one
			// exported before it existed, which ImportVyaparActivity's
			// itemsHaveReorderThreshold detects up front so it defaults
			// to 0 (no alert) instead of failing.
			"item_reorder_threshold REAL, " +
			// Another of this app's own extensions (free-form bin/shelf
			// tag) - absent from a real Vyapar backup or one exported
			// before it existed, which ImportVyaparActivity's
			// itemsHaveLocations detects up front so it defaults to ""
			// instead of failing.
			"item_locations TEXT, " +
			// Another of this app's own extensions (manual holiday/gift-
			// driven flag) - absent from a real Vyapar backup or one
			// exported before it existed, which ImportVyaparActivity's
			// itemsHaveHolidaySeasonal detects up front so it defaults
			// to 0 instead of failing.
			"item_holiday_seasonal INTEGER" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE kb_transactions (" +
			"txn_id INTEGER PRIMARY KEY, " +
			"txn_type INTEGER, " +
			"txn_name_id INTEGER, " +
			"txn_date TEXT, " +
			"txn_time INTEGER, " +
			"txn_cash_amount REAL, " +
			"txn_balance_amount REAL, " +
			"txn_invoice_prefix TEXT, " +
			"txn_ref_number_char TEXT, " +
			"txn_description TEXT, " +
			"txn_discount_amount REAL, " +
			"txn_tax_amount REAL, " +
			"txn_category_id INTEGER, " +
			// This app's own extension, same reasoning as kb_items'
			// item_extra_cost_per_unit/item_active above - a credit
			// Sale's due date, absent from a real Vyapar backup or one
			// exported before this existed (and meaningless for every
			// other txn_type, which just leaves it null).
			"txn_due_date TEXT" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE kb_lineitems (" +
			"lineitem_id INTEGER PRIMARY KEY, " +
			"lineitem_txn_id INTEGER, " +
			"item_id INTEGER, " +
			"quantity REAL, " +
			"priceperunit REAL, " +
			"total_amount REAL, " +
			"combo_id INTEGER" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE party_to_party_transfer (" +
			"p_txn_id INTEGER PRIMARY KEY, " +
			"p_amount REAL, " +
			"p_received_txn_id INTEGER, " +
			"p_paid_txn_id INTEGER, " +
			"p_txn_date TEXT, " +
			"p_txn_description TEXT" +
			")"
		);

		// This app's own extension, not part of the Vyapar-shaped subset
		// above - a real Vyapar backup will never have these tables (or
		// kb_lineitems.combo_id), which is exactly how ImportVyaparActivity
		// tells the two apart and skips variety import for a plain Vyapar
		// backup instead of failing. Ids are copied straight from the
		// local database (fresh tables of their own, so unlike the
		// kb_transactions/kb_lineitems id space there's no risk of
		// collision that would need an offset).
		vyb.execSQL(
			"CREATE TABLE businesserp_variety_groups (" +
			"group_id INTEGER PRIMARY KEY, " +
			"item_id INTEGER, " +
			"name TEXT, " +
			"sort_order INTEGER" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE businesserp_variety_values (" +
			"value_id INTEGER PRIMARY KEY, " +
			"group_id INTEGER, " +
			"label TEXT, " +
			"sort_order INTEGER, " +
			"is_default INTEGER" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE businesserp_variety_combos (" +
			"combo_id INTEGER PRIMARY KEY, " +
			"item_id INTEGER" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE businesserp_variety_combo_values (" +
			"combo_id INTEGER, " +
			"group_id INTEGER, " +
			"value_id INTEGER, " +
			"PRIMARY KEY (combo_id, group_id)" +
			")"
		);

		// Manual cash-in-hand corrections (see CashActivity) - another of
		// this app's own extensions with no Vyapar equivalent, same
		// reasoning as the variety_* tables above: ids copied straight
		// from the local table, no offset needed.
		vyb.execSQL(
			"CREATE TABLE businesserp_cash_adjustments (" +
			"id INTEGER PRIMARY KEY, " +
			"date TEXT, " +
			"time TEXT, " +
			"amount REAL, " +
			"notes TEXT, " +
			"source TEXT" +
			")"
		);

		// The reusable cost-item category list (Petrol, Shipping, ...) -
		// another of this app's own extensions. Nothing else references a
		// cost item by id (Expenses.item is plain text), so id is not even
		// preserved on the way back in - see importCostItems().
		// cost_item_monthly_budget is itself a further extension on top
		// of that (expense category budgets) - absent from a backup made
		// before it existed, which ImportVyaparActivity's columnExists
		// check detects up front so it defaults to 0 (no budget/alert)
		// instead of failing.
		vyb.execSQL(
			"CREATE TABLE businesserp_cost_items (" +
			"id INTEGER PRIMARY KEY, " +
			"name TEXT, " +
			"cost_item_monthly_budget REAL" +
			")"
		);

		// Which Expense is linked to which Purchase as landed cost, and for
		// how much - see TABLE_PURCHASE_EXPENSE_LINKS. expense_id/
		// purchase_id are written in the SAME offset id-space as
		// kb_transactions.txn_id (OFFSET_EXPENSE_TXN/OFFSET_PURCHASE_TXN)
		// rather than the raw local id, purely so the importer can resolve
		// them through the exact same purchaseIdMap/"expense" vyb-local-id
		// map it already builds while importing purchases/expenses above,
		// with no separate id space of its own to track.
		vyb.execSQL(
			"CREATE TABLE businesserp_purchase_expense_links (" +
			"id INTEGER PRIMARY KEY, " +
			"expense_id INTEGER, " +
			"purchase_id INTEGER, " +
			"share_percent REAL, " +
			"allocated_amount REAL" +
			")"
		);

		// Recurring expense rules (weekly/monthly/specific dates) - only
		// the rule itself; the concrete expense rows it has already
		// generated travel as ordinary expenses via exportExpenses().
		vyb.execSQL(
			"CREATE TABLE businesserp_recurring_expenses (" +
			"id INTEGER PRIMARY KEY, " +
			"item TEXT, " +
			"amount REAL, " +
			"notes TEXT, " +
			"party_id INTEGER, " +
			"frequency INTEGER, " +
			"day_of_week INTEGER, " +
			"day_of_month INTEGER, " +
			"specific_dates TEXT, " +
			"start_date TEXT, " +
			"last_generated_date TEXT, " +
			"active INTEGER" +
			")"
		);

		// Parked mid-entry Sale/Purchase/Payment/Expense drafts - "data" is
		// that screen's own opaque blob (see DraftCodec), which a Purchase
		// or Sale draft's ids inside get remapped before being written back
		// in - see ImportVyaparActivity.importDrafts().
		vyb.execSQL(
			"CREATE TABLE businesserp_drafts (" +
			"id INTEGER PRIMARY KEY, " +
			"type TEXT, " +
			"label TEXT, " +
			"data TEXT, " +
			"date TEXT, " +
			"time TEXT" +
			")"
		);

		// A customer request for something not currently in stock (an
		// existing catalog item that's out, or something not in the
		// catalog at all) - not tied to any transaction, so item_id/
		// party_id are just the local ids, copied straight across like
		// businesserp_cash_adjustments above.
		vyb.execSQL(
			"CREATE TABLE businesserp_wanted_items (" +
			"id INTEGER PRIMARY KEY, " +
			"item_id INTEGER, " +
			"item_name TEXT, " +
			"date TEXT, " +
			"time TEXT, " +
			"party_id INTEGER, " +
			"notes TEXT, " +
			"fulfilled INTEGER" +
			")"
		);

		// The free-form Display Shoes grid - row_pos/col_pos are the
		// user's own manual shelf-layout positions, copied straight
		// across like every other businesserp_* table's ids.
		vyb.execSQL(
			"CREATE TABLE businesserp_display_shoes (" +
			"id INTEGER PRIMARY KEY, " +
			"item_id INTEGER, " +
			"combo_id INTEGER, " +
			"row_pos INTEGER, " +
			"col_pos INTEGER" +
			")"
		);

		// The Sample Shoes list - same shape as Display Shoes minus the
		// grid position (it's a plain list, not a grid).
		vyb.execSQL(
			"CREATE TABLE businesserp_sample_shoes (" +
			"id INTEGER PRIMARY KEY, " +
			"item_id INTEGER, " +
			"combo_id INTEGER" +
			")"
		);

		// Items currently marked down for clearance (see
		// DatabaseHelper.TABLE_ITEM_CLEARANCE) - item_id is itself the
		// primary key (an item can only be in one clearance at a time),
		// copied straight across like every other businesserp_* table.
		vyb.execSQL(
			"CREATE TABLE businesserp_item_clearance (" +
			"item_id INTEGER PRIMARY KEY, " +
			"started_date TEXT, " +
			"discount_percent REAL, " +
			"starting_balance REAL" +
			")"
		);

		// Loyalty points ledger (see DatabaseHelper.TABLE_LOYALTY_POINTS) -
		// an append-only history, so (unlike item_clearance) this keeps
		// its own AUTOINCREMENT-style id rather than using party_id as
		// the primary key - a party can have many entries.
		vyb.execSQL(
			"CREATE TABLE businesserp_loyalty_points (" +
			"id INTEGER PRIMARY KEY, " +
			"party_id INTEGER, " +
			"points INTEGER, " +
			"date TEXT, " +
			"time TEXT, " +
			"reason TEXT" +
			")"
		);

		// The user's own manually marked busy-period months (see
		// DatabaseHelper.TABLE_SEASONAL_CALENDAR) - another of this
		// app's own extensions. Nothing else references an entry by id,
		// so id is not even preserved on the way back in (same reasoning
		// as businesserp_cost_items above) - see importSeasonalCalendar().
		vyb.execSQL(
			"CREATE TABLE businesserp_seasonal_calendar (" +
			"id INTEGER PRIMARY KEY, " +
			"label TEXT, " +
			"month INTEGER, " +
			"multiplier REAL" +
			")"
		);

		// Only completed Stock Take sessions ever reach here (see
		// exportStockTake() below) - a session's own id is preserved so
		// its lines can reference it back, same convention as a
		// purchase/sale and its own line items.
		vyb.execSQL(
			"CREATE TABLE businesserp_stock_take_sessions (" +
			"id INTEGER PRIMARY KEY, " +
			"started_date TEXT, " +
			"started_time TEXT, " +
			"completed_date TEXT, " +
			"completed_time TEXT, " +
			"item_count INTEGER, " +
			"discrepancy_count INTEGER" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE businesserp_stock_take_lines (" +
			"id INTEGER PRIMARY KEY, " +
			"session_id INTEGER, " +
			"item_id INTEGER, " +
			"combo_id INTEGER, " +
			"expected_qty REAL, " +
			"counted_qty REAL" +
			")"
		);
	}

	// =====================
	// PARTIES -> kb_names (name_type=1)
	// =====================
	private static void exportParties(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, name, appearance_notes, customer_type FROM parties", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("name_id", c.getLong(0));
			values.put("full_name", c.getString(1));
			values.put("name_type", 1);
			values.put("full_name_appearance", c.getString(2));
			values.put("full_name_customer_type", c.getString(3));

			vyb.insert("kb_names", null, values);
		}

		c.close();
	}

	// =====================
	// ITEMS -> kb_items (item_type=1, i.e. a real item, never the
	// importer's item_type=2 "expense line item" convention)
	// =====================
	private static void exportItems(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, code, name, purchase_price, sale_price, extra_cost_per_unit, active, " +
			"reorder_threshold, locations, holiday_seasonal FROM items", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("item_id", c.getLong(0));
			values.put("item_code", c.getString(1));
			values.put("item_name", c.getString(2));
			values.put("item_purchase_unit_price", c.getDouble(3));
			values.put("item_sale_unit_price", c.getDouble(4));
			values.put("item_type", 1);
			values.put("item_active", c.getInt(6));
			values.put("item_extra_cost_per_unit", c.getDouble(5));
			values.put("item_reorder_threshold", c.getDouble(7));
			values.put("item_locations", c.getString(8));
			values.put("item_holiday_seasonal", c.getInt(9));

			vyb.insert("kb_items", null, values);
		}

		c.close();
	}

	// =====================
	// VARIETY GROUPS -> businesserp_variety_groups (1:1 copy)
	// =====================
	private static void exportVarietyGroups(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, item_id, name, sort_order FROM variety_groups", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("group_id", c.getLong(0));
			values.put("item_id", c.getLong(1));
			values.put("name", c.getString(2));
			values.put("sort_order", c.getInt(3));

			vyb.insert("businesserp_variety_groups", null, values);
		}

		c.close();
	}

	// =====================
	// VARIETY VALUES -> businesserp_variety_values (1:1 copy)
	// =====================
	private static void exportVarietyValues(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, group_id, label, sort_order, is_default FROM variety_values", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("value_id", c.getLong(0));
			values.put("group_id", c.getLong(1));
			values.put("label", c.getString(2));
			values.put("sort_order", c.getInt(3));
			values.put("is_default", c.getInt(4));

			vyb.insert("businesserp_variety_values", null, values);
		}

		c.close();
	}

	// =====================
	// VARIETY COMBOS -> businesserp_variety_combos. balance is
	// intentionally not exported - like items.balance itself, the
	// importer rebuilds it by replaying the backup's purchase/sale line
	// items rather than trusting a point-in-time snapshot number, so
	// there's nothing here for it to read.
	// =====================
	private static void exportVarietyCombos(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, item_id FROM variety_combos", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("combo_id", c.getLong(0));
			values.put("item_id", c.getLong(1));

			vyb.insert("businesserp_variety_combos", null, values);
		}

		c.close();
	}

	// =====================
	// VARIETY COMBO VALUES -> businesserp_variety_combo_values (1:1 copy)
	// =====================
	private static void exportVarietyComboValues(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT combo_id, group_id, value_id FROM variety_combo_values", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("combo_id", c.getLong(0));
			values.put("group_id", c.getLong(1));
			values.put("value_id", c.getLong(2));

			vyb.insert("businesserp_variety_combo_values", null, values);
		}

		c.close();
	}

	// =====================
	// CASH ADJUSTMENTS -> businesserp_cash_adjustments (1:1 copy)
	// =====================
	private static void exportCashAdjustments(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, date, time, amount, notes, source FROM cash_adjustments", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("date", c.getString(1));
			values.put("time", c.getString(2));
			values.put("amount", c.getDouble(3));
			values.put("notes", c.getString(4));
			values.put("source", c.isNull(5) ? "Manual" : c.getString(5));

			vyb.insert("businesserp_cash_adjustments", null, values);
		}

		c.close();
	}

	// =====================
	// One kb_names (name_type=2) row per distinct expense description, so
	// re-importing this file falls back to that exact text as the
	// expense's category - see ImportVyaparActivity.importExpenses(). This
	// app has no separate category concept of its own to preserve
	// instead, so the description doubles as both.
	// =====================
	private static void exportExpenseCategories(
		SQLiteDatabase local, SQLiteDatabase vyb, HashMap<String, Long> categoryNameId) {

		Cursor c = local.rawQuery("SELECT DISTINCT item FROM expenses", null);

		long nextId = OFFSET_EXPENSE_CATEGORY_NAME;

		while (c.moveToNext()) {

			String item = c.getString(0);

			if (item == null) {
				continue;
			}

			long nameId = nextId++;

			ContentValues values = new ContentValues();
			values.put("name_id", nameId);
			values.put("full_name", item);
			values.put("name_type", 2);

			vyb.insert("kb_names", null, values);

			categoryNameId.put(item, nameId);
		}

		c.close();
	}

	// =====================
	// PURCHASES -> kb_transactions (txn_type=2)
	// =====================
	private static void exportPurchases(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, party_id, date, time, invoice_number, grand_total, amount_paid, notes, due_date " +
			"FROM purchases", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			int partyId = c.getInt(1);
			String date = c.getString(2);
			String time = c.getString(3);
			String invoiceNumber = c.getString(4);
			double grandTotal = c.getDouble(5);
			double amountPaid = c.getDouble(6);
			String notes = c.getString(7);
			String dueDate = c.getString(8);

			ContentValues values = new ContentValues();
			values.put("txn_id", OFFSET_PURCHASE_TXN + id);
			values.put("txn_type", 2);
			values.put("txn_name_id", partyId);
			values.put("txn_date", toVyaparDate(date));
			values.put("txn_time", toVyaparTime(time));
			values.put("txn_cash_amount", amountPaid);
			values.put("txn_balance_amount", grandTotal - amountPaid);
			values.put("txn_invoice_prefix", "");
			values.put("txn_ref_number_char", invoiceNumber == null ? "" : invoiceNumber);
			values.put("txn_description", notes == null ? "" : notes);
			values.put("txn_discount_amount", 0.0);
			values.put("txn_tax_amount", 0.0);
			values.put("txn_due_date", dueDate);

			vyb.insert("kb_transactions", null, values);
		}

		c.close();
	}

	// =====================
	// PURCHASE ITEMS -> kb_lineitems
	// =====================
	private static void exportPurchaseItems(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, purchase_id, item_id, quantity, purchase_price, total, combo_id " +
			"FROM purchase_items", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			long purchaseId = c.getLong(1);
			int itemId = c.getInt(2);
			double quantity = c.getDouble(3);
			double price = c.getDouble(4);
			double total = c.getDouble(5);
			Integer comboId = c.isNull(6) ? null : c.getInt(6);

			ContentValues values = new ContentValues();
			values.put("lineitem_id", id);
			values.put("lineitem_txn_id", OFFSET_PURCHASE_TXN + purchaseId);
			values.put("item_id", itemId);
			values.put("quantity", quantity);
			values.put("priceperunit", price);
			values.put("total_amount", total);
			values.put("combo_id", comboId);

			vyb.insert("kb_lineitems", null, values);
		}

		c.close();
	}

	// =====================
	// SALES -> kb_transactions (txn_type=1)
	// =====================
	private static void exportSales(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, party_id, date, time, invoice_no, discount, other_charges, " +
			"grand_total, paid_amount, balance, notes, due_date FROM sales", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			Integer partyId = c.isNull(1) ? null : c.getInt(1);
			String date = c.getString(2);
			String time = c.getString(3);
			String invoiceNo = c.getString(4);
			double discount = c.getDouble(5);
			double otherCharges = c.getDouble(6);
			double grandTotal = c.getDouble(7);
			double paidAmount = c.getDouble(8);
			double balance = c.getDouble(9);
			String notes = c.getString(10);
			String dueDate = c.getString(11);

			ContentValues values = new ContentValues();
			values.put("txn_id", OFFSET_SALE_TXN + id);
			values.put("txn_type", 1);

			if (partyId != null) {
				values.put("txn_name_id", partyId);
			}

			values.put("txn_date", toVyaparDate(date));
			values.put("txn_time", toVyaparTime(time));
			values.put("txn_cash_amount", paidAmount);
			values.put("txn_balance_amount", balance);
			values.put("txn_invoice_prefix", "");
			values.put("txn_ref_number_char", invoiceNo == null ? "" : invoiceNo);
			values.put("txn_description", notes == null ? "" : notes);
			values.put("txn_discount_amount", discount);
			values.put("txn_tax_amount", otherCharges);
			values.put("txn_due_date", dueDate);
			// grand_total is intentionally not stored separately, mirroring
			// how the importer derives it as cash+balance on the way in.

			vyb.insert("kb_transactions", null, values);
		}

		c.close();
	}

	// =====================
	// SALE ITEMS -> kb_lineitems (offset so lineitem_id never collides
	// with a purchase line item's, since they share one id space)
	// =====================
	private static void exportSaleItems(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, sale_id, item_id, qty, rate, amount, combo_id FROM sale_items", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			long saleId = c.getLong(1);
			int itemId = c.getInt(2);
			double qty = c.getDouble(3);
			double rate = c.getDouble(4);
			double amount = c.getDouble(5);
			Integer comboId = c.isNull(6) ? null : c.getInt(6);

			ContentValues values = new ContentValues();
			values.put("lineitem_id", OFFSET_SALE_LINEITEM + id);
			values.put("lineitem_txn_id", OFFSET_SALE_TXN + saleId);
			values.put("item_id", itemId);
			values.put("quantity", qty);
			values.put("priceperunit", rate);
			values.put("total_amount", amount);
			values.put("combo_id", comboId);

			vyb.insert("kb_lineitems", null, values);
		}

		c.close();
	}

	// =====================
	// PAYMENTS (both directions) -> kb_transactions (txn_type 3/4)
	// =====================
	private static void exportPayments(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, type, party_id, date, time, amount, notes FROM payments", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			int type = c.getInt(1);
			int partyId = c.getInt(2);
			String date = c.getString(3);
			String time = c.getString(4);
			double amount = c.getDouble(5);
			String notes = c.getString(6);

			ContentValues values = new ContentValues();
			values.put("txn_id", OFFSET_PAYMENT_TXN + id);
			values.put("txn_type", type == DatabaseHelper.PAYMENT_IN ? 3 : 4);
			values.put("txn_name_id", partyId);
			values.put("txn_date", toVyaparDate(date));
			values.put("txn_time", toVyaparTime(time));
			values.put("txn_cash_amount", amount);
			values.put("txn_balance_amount", 0.0);
			values.put("txn_description", notes == null ? "" : notes);

			vyb.insert("kb_transactions", null, values);
		}

		c.close();
	}

	// =====================
	// EXPENSES -> kb_transactions (txn_type=7), with txn_category_id
	// pointing at the matching row exportExpenseCategories() already
	// wrote. No kb_lineitems are written for expenses - the importer only
	// uses those for a more specific "what was this for" than the
	// category, and this app doesn't track anything more specific than
	// the description already captured as the category itself.
	//
	// txn_cash_amount/txn_balance_amount split paid_amount from the
	// unpaid/credit remainder (amount - paid_amount), exactly mirroring
	// how exportPurchases()/exportSales() already split cash vs balance -
	// so importExpenses() can reconstruct both amount (cash+balance) and
	// paid_amount (cash) the same way importPurchases() reconstructs
	// grand_total, instead of every imported expense coming back fully
	// paid regardless of its original paid/credit split.
	// =====================
	private static void exportExpenses(
		SQLiteDatabase local, SQLiteDatabase vyb, HashMap<String, Long> categoryNameId) {

		Cursor c = local.rawQuery(
			"SELECT id, item, date, time, amount, paid_amount, notes, party_id FROM expenses", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			String item = c.getString(1);
			String date = c.getString(2);
			String time = c.getString(3);
			double amount = c.getDouble(4);
			double paidAmount = c.getDouble(5);
			String notes = c.getString(6);
			Integer partyId = c.isNull(7) ? null : c.getInt(7);

			Long categoryId = item == null ? null : categoryNameId.get(item);

			ContentValues values = new ContentValues();
			values.put("txn_id", OFFSET_EXPENSE_TXN + id);
			values.put("txn_type", 7);
			values.put("txn_date", toVyaparDate(date));
			values.put("txn_time", toVyaparTime(time));
			values.put("txn_cash_amount", paidAmount);
			values.put("txn_balance_amount", amount - paidAmount);
			values.put("txn_description", notes == null ? "" : notes);

			if (categoryId != null) {
				values.put("txn_category_id", categoryId);
			}

			// Expenses' party is optional (unlike purchases/sales/payments,
			// which always have one) - only write txn_name_id when there
			// actually is one, same as exportSales() does.
			if (partyId != null) {
				values.put("txn_name_id", partyId);
			}

			vyb.insert("kb_transactions", null, values);
		}

		c.close();
	}

	// =====================
	// COST ITEMS -> businesserp_cost_items (1:1 copy). Nothing else
	// references a cost item by id (Expenses.item is plain text), so id is
	// carried across purely for readability, not because anything resolves
	// through it on the way back in.
	// =====================
	private static void exportCostItems(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, name, monthly_budget FROM cost_items", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("name", c.getString(1));
			values.put("cost_item_monthly_budget", c.getDouble(2));

			vyb.insert("businesserp_cost_items", null, values);
		}

		c.close();
	}

	// =====================
	// LINKED EXPENSES -> businesserp_purchase_expense_links. expense_id/
	// purchase_id are written in the same offset id-space as
	// kb_transactions.txn_id (see exportExpenses()/exportPurchases()) so
	// ImportVyaparActivity.importPurchaseExpenseLinks() can resolve them
	// through the exact same maps those two steps already build.
	// =====================
	private static void exportPurchaseExpenseLinks(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, expense_id, purchase_id, share_percent, allocated_amount " +
			"FROM purchase_expense_links", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("expense_id", OFFSET_EXPENSE_TXN + c.getLong(1));
			values.put("purchase_id", OFFSET_PURCHASE_TXN + c.getLong(2));
			values.put("share_percent", c.getDouble(3));
			values.put("allocated_amount", c.getDouble(4));

			vyb.insert("businesserp_purchase_expense_links", null, values);
		}

		c.close();
	}

	// =====================
	// RECURRING EXPENSE RULES -> businesserp_recurring_expenses (1:1
	// copy). party_id is a raw local party id - the same id space
	// kb_names.name_id already copies parties into with no offset, so
	// ImportVyaparActivity.importRecurringExpenses() resolves it through
	// the same partyIdMap purchases/sales/expenses already use.
	// =====================
	private static void exportRecurringExpenses(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, item, amount, notes, party_id, frequency, day_of_week, day_of_month, " +
			"specific_dates, start_date, last_generated_date, active FROM recurring_expenses", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("item", c.getString(1));
			values.put("amount", c.getDouble(2));
			values.put("notes", c.getString(3));

			if (!c.isNull(4)) {
				values.put("party_id", c.getLong(4));
			}

			values.put("frequency", c.getInt(5));

			if (!c.isNull(6)) {
				values.put("day_of_week", c.getInt(6));
			}

			if (!c.isNull(7)) {
				values.put("day_of_month", c.getInt(7));
			}

			values.put("specific_dates", c.getString(8));
			values.put("start_date", c.getString(9));
			values.put("last_generated_date", c.getString(10));
			values.put("active", c.getInt(11));

			vyb.insert("businesserp_recurring_expenses", null, values);
		}

		c.close();
	}

	// =====================
	// DRAFTS -> businesserp_drafts (1:1 copy of the row; the "data" blob's
	// own embedded ids are remapped on the way back IN, not out - see
	// ImportVyaparActivity.importDrafts() - since export doesn't yet know
	// what ids this backup will be restored into).
	// =====================
	private static void exportDrafts(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery("SELECT id, type, label, data, date, time FROM drafts", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("type", c.getString(1));
			values.put("label", c.getString(2));
			values.put("data", c.getString(3));
			values.put("date", c.getString(4));
			values.put("time", c.getString(5));

			vyb.insert("businesserp_drafts", null, values);
		}

		c.close();
	}

	// =====================
	// WANTED ITEMS -> businesserp_wanted_items (1:1 copy)
	// =====================
	private static void exportWantedItems(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, item_id, item_name, date, time, party_id, notes, fulfilled " +
			"FROM wanted_items", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));

			if (!c.isNull(1)) {
				values.put("item_id", c.getLong(1));
			}

			values.put("item_name", c.getString(2));
			values.put("date", c.getString(3));
			values.put("time", c.getString(4));

			if (!c.isNull(5)) {
				values.put("party_id", c.getLong(5));
			}

			values.put("notes", c.getString(6));
			values.put("fulfilled", c.getInt(7));

			vyb.insert("businesserp_wanted_items", null, values);
		}

		c.close();
	}

	// =====================
	// DISPLAY SHOES -> businesserp_display_shoes (1:1 copy)
	// =====================
	private static void exportDisplayShoes(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, item_id, combo_id, row_pos, col_pos FROM display_shoes", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("item_id", c.getLong(1));
			values.put("combo_id", c.getLong(2));
			values.put("row_pos", c.getInt(3));
			values.put("col_pos", c.getInt(4));

			vyb.insert("businesserp_display_shoes", null, values);
		}

		c.close();
	}

	// =====================
	// SAMPLE SHOES -> businesserp_sample_shoes (1:1 copy)
	// =====================
	private static void exportSampleShoes(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, item_id, combo_id FROM sample_shoes", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("item_id", c.getLong(1));
			values.put("combo_id", c.getLong(2));

			vyb.insert("businesserp_sample_shoes", null, values);
		}

		c.close();
	}

	// =====================
	// ITEM CLEARANCE -> businesserp_item_clearance (1:1 copy)
	// =====================
	private static void exportItemClearance(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT item_id, started_date, discount_percent, starting_balance FROM item_clearance",
			null
		);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("item_id", c.getLong(0));
			values.put("started_date", c.getString(1));
			values.put("discount_percent", c.getDouble(2));
			values.put("starting_balance", c.getDouble(3));

			vyb.insert("businesserp_item_clearance", null, values);
		}

		c.close();
	}

	// =====================
	// LOYALTY POINTS -> businesserp_loyalty_points (1:1 copy, keeps its
	// own id since nothing else references a ledger row by id)
	// =====================
	private static void exportLoyaltyPoints(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, party_id, points, date, time, reason FROM loyalty_points_ledger",
			null
		);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("party_id", c.getLong(1));
			values.put("points", c.getInt(2));
			values.put("date", c.getString(3));
			values.put("time", c.getString(4));
			values.put("reason", c.getString(5));

			vyb.insert("businesserp_loyalty_points", null, values);
		}

		c.close();
	}

	// =====================
	// SEASONAL CALENDAR -> businesserp_seasonal_calendar (1:1 copy).
	// Nothing else references an entry by id, so id is not even
	// preserved on the way back in - see importSeasonalCalendar().
	// =====================
	private static void exportSeasonalCalendar(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, label, month, multiplier FROM seasonal_calendar", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("id", c.getLong(0));
			values.put("label", c.getString(1));
			values.put("month", c.getInt(2));
			values.put("multiplier", c.getDouble(3));

			vyb.insert("businesserp_seasonal_calendar", null, values);
		}

		c.close();
	}

	// =====================
	// STOCK TAKE -> businesserp_stock_take_sessions/lines. Only
	// completed sessions - an open (in-progress) one is mid-workflow
	// device state, not a finished record, same reasoning as a drafts
	// autosave row never being a real Draft. Only counted lines are
	// carried over too; an uncounted line contributed nothing (no
	// counted_qty to even write) and would just be noise on restore.
	// =====================
	private static void exportStockTake(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor sessionCursor = local.rawQuery(
			"SELECT id, started_date, started_time, completed_date, completed_time, " +
			"item_count, discrepancy_count FROM stock_take_sessions WHERE status='completed'",
			null
		);

		while (sessionCursor.moveToNext()) {

			long sessionId = sessionCursor.getLong(0);

			ContentValues sessionValues = new ContentValues();
			sessionValues.put("id", sessionId);
			sessionValues.put("started_date", sessionCursor.getString(1));
			sessionValues.put("started_time", sessionCursor.getString(2));
			sessionValues.put("completed_date", sessionCursor.getString(3));
			sessionValues.put("completed_time", sessionCursor.getString(4));
			sessionValues.put("item_count", sessionCursor.getInt(5));
			sessionValues.put("discrepancy_count", sessionCursor.getInt(6));

			vyb.insert("businesserp_stock_take_sessions", null, sessionValues);

			Cursor lineCursor = local.rawQuery(
				"SELECT id, item_id, combo_id, expected_qty, counted_qty FROM " +
				"stock_take_lines WHERE session_id=? AND counted=1",
				new String[]{String.valueOf(sessionId)}
			);

			while (lineCursor.moveToNext()) {

				ContentValues lineValues = new ContentValues();
				lineValues.put("id", lineCursor.getLong(0));
				lineValues.put("session_id", sessionId);
				lineValues.put("item_id", lineCursor.getLong(1));
				lineValues.put(
					"combo_id", lineCursor.isNull(2) ? null : lineCursor.getLong(2));
				lineValues.put("expected_qty", lineCursor.getDouble(3));
				lineValues.put("counted_qty", lineCursor.getDouble(4));

				vyb.insert("businesserp_stock_take_lines", null, lineValues);
			}

			lineCursor.close();
		}

		sessionCursor.close();
	}

	// =====================
	// PARTY TRANSFERS -> party_to_party_transfer, backed by two
	// synthesized kb_transactions "leg" rows (txn_type 50/51) purely so
	// ImportVyaparActivity.lookupTxnNameId()/lookupTxnTime() can resolve
	// the paying/receiving party and time the same way it would for a
	// real Vyapar backup - see its importPartyTransfers() comment.
	// =====================
	private static void exportPartyTransfers(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, from_party_id, to_party_id, date, time, amount, notes FROM party_transfers", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			int fromPartyId = c.getInt(1);
			int toPartyId = c.getInt(2);
			String date = c.getString(3);
			String time = c.getString(4);
			double amount = c.getDouble(5);
			String notes = c.getString(6);

			long paidTxnId = OFFSET_TRANSFER_PAID_TXN + id;
			long receivedTxnId = OFFSET_TRANSFER_RECEIVED_TXN + id;
			String vyaparDate = toVyaparDate(date);
			int vyaparTime = toVyaparTime(time);

			ContentValues paidLeg = new ContentValues();
			paidLeg.put("txn_id", paidTxnId);
			paidLeg.put("txn_type", 50);
			paidLeg.put("txn_name_id", fromPartyId);
			paidLeg.put("txn_date", vyaparDate);
			paidLeg.put("txn_time", vyaparTime);
			vyb.insert("kb_transactions", null, paidLeg);

			ContentValues receivedLeg = new ContentValues();
			receivedLeg.put("txn_id", receivedTxnId);
			receivedLeg.put("txn_type", 51);
			receivedLeg.put("txn_name_id", toPartyId);
			receivedLeg.put("txn_date", vyaparDate);
			receivedLeg.put("txn_time", vyaparTime);
			vyb.insert("kb_transactions", null, receivedLeg);

			ContentValues transfer = new ContentValues();
			transfer.put("p_txn_id", id);
			transfer.put("p_amount", amount);
			transfer.put("p_received_txn_id", receivedTxnId);
			transfer.put("p_paid_txn_id", paidTxnId);
			transfer.put("p_txn_date", vyaparDate);
			transfer.put("p_txn_description", notes == null ? "" : notes);

			vyb.insert("party_to_party_transfer", null, transfer);
		}

		c.close();
	}

	// Reverses ImportVyaparActivity.formatDate(): the app's "yyyy-MM-dd"
	// back into Vyapar's "yyyy-MM-dd 00:00:00".
	private static String toVyaparDate(String localDate) {
		return (localDate == null ? "" : localDate) + " 00:00:00";
	}

	// Reverses ImportVyaparActivity.formatTime(): the app's "HH:mm" back
	// into seconds-since-midnight. Loses no precision - the app never
	// stored more than minute granularity in the first place.
	private static int toVyaparTime(String localTime) {

		if (localTime == null || !localTime.contains(":")) {
			return 0;
		}

		String[] parts = localTime.split(":");

		try {

			int hours = Integer.parseInt(parts[0]);
			int minutes = Integer.parseInt(parts[1]);

			return hours * 3600 + minutes * 60;

		} catch (NumberFormatException e) {

			return 0;
		}
	}
}
