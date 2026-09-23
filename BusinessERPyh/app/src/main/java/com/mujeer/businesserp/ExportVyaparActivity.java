package com.mujeer.businesserp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
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
// importer actually reads, nothing more), plus this app's own item
// varieties (groups/values/combos, and each line item's chosen combo_id)
// in four extra businesserp_variety_* tables that have no equivalent in
// Vyapar's own schema - see createVyaparShapedTables() below.
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

	// Large fixed offsets keep synthesized ids from different local
	// tables from colliding once they all share Vyapar's single
	// kb_transactions/kb_lineitems/kb_names id space. No business using
	// this app will ever have anywhere near a million rows in one table,
	// so these can never overlap with a real local id or each other.
	private static final long OFFSET_PURCHASE_TXN = 10_000_000L;
	private static final long OFFSET_SALE_TXN = 20_000_000L;
	private static final long OFFSET_PAYMENT_TXN = 30_000_000L;
	private static final long OFFSET_EXPENSE_TXN = 40_000_000L;
	private static final long OFFSET_TRANSFER_PAID_TXN = 50_000_000L;
	private static final long OFFSET_TRANSFER_RECEIVED_TXN = 60_000_000L;
	private static final long OFFSET_SALE_LINEITEM = 5_000_000L;
	private static final long OFFSET_EXPENSE_CATEGORY_NAME = 1_000_000L;

	private Button btn_export_vyb;
	private TextView tv_export_result;

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
	}

	private void setStatus(final String text) {

		runOnUiThread(new Runnable() {
				@Override
				public void run() {
					tv_export_result.setText(text);
				}
			});
	}

	// =====================
	// Builds the .vyb (zipped SQLite db) into cache storage, then hands
	// off to the system file picker to choose where it's actually saved -
	// the app has no storage permission and doesn't need one this way.
	// =====================
	private void buildBackupFile() {

		setStatus("Reading your data...");

		File dbFile = new File(getCacheDir(), "vyb_export_raw.tmp");
		File zipFile = new File(getCacheDir(), "vyb_export.tmp");

		if (dbFile.exists()) {
			dbFile.delete();
		}

		SQLiteDatabase vyb = null;

		try {

			vyb = SQLiteDatabase.openOrCreateDatabase(dbFile, null);

			createVyaparShapedTables(vyb);

			DatabaseHelper helper = new DatabaseHelper(this);
			SQLiteDatabase local = helper.getReadableDatabase();

			HashMap<String, Long> expenseCategoryNameId = new HashMap<String, Long>();

			vyb.beginTransaction();

			exportParties(local, vyb);
			exportItems(local, vyb);
			exportVarietyGroups(local, vyb);
			exportVarietyValues(local, vyb);
			exportVarietyCombos(local, vyb);
			exportVarietyComboValues(local, vyb);
			exportExpenseCategories(local, vyb, expenseCategoryNameId);
			exportPurchases(local, vyb);
			exportPurchaseItems(local, vyb);
			exportSales(local, vyb);
			exportSaleItems(local, vyb);
			exportPayments(local, vyb);
			exportExpenses(local, vyb, expenseCategoryNameId);
			exportPartyTransfers(local, vyb);

			vyb.setTransactionSuccessful();
			vyb.endTransaction();

			vyb.close();
			vyb = null;

			setStatus("Compressing backup...");

			zipDatabaseFile(dbFile, zipFile);

			pendingZipFile = zipFile;

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

			setStatus(
				"Backup saved.\n\nRestore it later - on this device after a reinstall, or on " +
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
	private void zipDatabaseFile(File dbFile, File zipFile) throws Exception {

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
	private void createVyaparShapedTables(SQLiteDatabase vyb) {

		vyb.execSQL(
			"CREATE TABLE kb_names (" +
			"name_id INTEGER PRIMARY KEY, " +
			"full_name TEXT, " +
			"name_type INTEGER" +
			")"
		);

		vyb.execSQL(
			"CREATE TABLE kb_items (" +
			"item_id INTEGER PRIMARY KEY, " +
			"item_code TEXT, " +
			"item_name TEXT, " +
			"item_purchase_unit_price REAL, " +
			"item_sale_unit_price REAL, " +
			"item_type INTEGER" +
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
			"txn_category_id INTEGER" +
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
	}

	// =====================
	// PARTIES -> kb_names (name_type=1)
	// =====================
	private void exportParties(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery("SELECT id, name FROM parties", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("name_id", c.getLong(0));
			values.put("full_name", c.getString(1));
			values.put("name_type", 1);

			vyb.insert("kb_names", null, values);
		}

		c.close();
	}

	// =====================
	// ITEMS -> kb_items (item_type=1, i.e. a real item, never the
	// importer's item_type=2 "expense line item" convention)
	// =====================
	private void exportItems(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, code, name, purchase_price, sale_price FROM items", null);

		while (c.moveToNext()) {

			ContentValues values = new ContentValues();
			values.put("item_id", c.getLong(0));
			values.put("item_code", c.getString(1));
			values.put("item_name", c.getString(2));
			values.put("item_purchase_unit_price", c.getDouble(3));
			values.put("item_sale_unit_price", c.getDouble(4));
			values.put("item_type", 1);

			vyb.insert("kb_items", null, values);
		}

		c.close();
	}

	// =====================
	// VARIETY GROUPS -> businesserp_variety_groups (1:1 copy)
	// =====================
	private void exportVarietyGroups(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	private void exportVarietyValues(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	private void exportVarietyCombos(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	private void exportVarietyComboValues(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	// One kb_names (name_type=2) row per distinct expense description, so
	// re-importing this file falls back to that exact text as the
	// expense's category - see ImportVyaparActivity.importExpenses(). This
	// app has no separate category concept of its own to preserve
	// instead, so the description doubles as both.
	// =====================
	private void exportExpenseCategories(
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
	private void exportPurchases(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, party_id, date, time, invoice_number, grand_total, amount_paid, notes " +
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

			vyb.insert("kb_transactions", null, values);
		}

		c.close();
	}

	// =====================
	// PURCHASE ITEMS -> kb_lineitems
	// =====================
	private void exportPurchaseItems(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	private void exportSales(SQLiteDatabase local, SQLiteDatabase vyb) {

		Cursor c = local.rawQuery(
			"SELECT id, party_id, date, time, invoice_no, discount, other_charges, " +
			"grand_total, paid_amount, balance, notes FROM sales", null);

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
	private void exportSaleItems(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	private void exportPayments(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	// =====================
	private void exportExpenses(
		SQLiteDatabase local, SQLiteDatabase vyb, HashMap<String, Long> categoryNameId) {

		Cursor c = local.rawQuery(
			"SELECT id, item, date, time, amount, notes FROM expenses", null);

		while (c.moveToNext()) {

			long id = c.getLong(0);
			String item = c.getString(1);
			String date = c.getString(2);
			String time = c.getString(3);
			double amount = c.getDouble(4);
			String notes = c.getString(5);

			Long categoryId = item == null ? null : categoryNameId.get(item);

			ContentValues values = new ContentValues();
			values.put("txn_id", OFFSET_EXPENSE_TXN + id);
			values.put("txn_type", 7);
			values.put("txn_date", toVyaparDate(date));
			values.put("txn_time", toVyaparTime(time));
			values.put("txn_cash_amount", amount);
			values.put("txn_balance_amount", 0.0);
			values.put("txn_description", notes == null ? "" : notes);

			if (categoryId != null) {
				values.put("txn_category_id", categoryId);
			}

			vyb.insert("kb_transactions", null, values);
		}

		c.close();
	}

	// =====================
	// PARTY TRANSFERS -> party_to_party_transfer, backed by two
	// synthesized kb_transactions "leg" rows (txn_type 50/51) purely so
	// ImportVyaparActivity.lookupTxnNameId()/lookupTxnTime() can resolve
	// the paying/receiving party and time the same way it would for a
	// real Vyapar backup - see its importPartyTransfers() comment.
	// =====================
	private void exportPartyTransfers(SQLiteDatabase local, SQLiteDatabase vyb) {

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
	private String toVyaparDate(String localDate) {
		return (localDate == null ? "" : localDate) + " 00:00:00";
	}

	// Reverses ImportVyaparActivity.formatTime(): the app's "HH:mm" back
	// into seconds-since-midnight. Loses no precision - the app never
	// stored more than minute granularity in the first place.
	private int toVyaparTime(String localTime) {

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
