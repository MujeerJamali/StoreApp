package com.mujeer.businesserp;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;




public class DatabaseHelper extends SQLiteOpenHelper {



    public static final String DATABASE_NAME = "business_erp.db";
    // Bumped 14 -> 15 to add: a nullable "source" column on every
    // transaction-like table (purchases/sales/payments/expenses/
    // party_transfers) recording what created/last touched that row
    // ("Manual", "Vyapar Import", "Generate Entries", "Bulk Purchase
    // Import"); expenses.paid_amount, splitting an expense into a paid
    // portion and a credit/due remainder the same way purchases/sales
    // already do (existing rows are backfilled to fully paid, matching
    // their previous implicit behavior); and two new tables,
    // cash_adjustments (manual cash-in-hand corrections) and
    // wanted_items (customer requests for something not currently in
    // stock).
    public static final int DATABASE_VERSION = 15;

    // Tables
    public static final String TABLE_PARTIES = "parties";
	public static final String TABLE_ITEMS = "items";
	public static final String TABLE_PURCHASES = "purchases";
	public static final String TABLE_PURCHASE_ITEMS = "purchase_items";
	public static final String TABLE_PAYMENTS = "payments";
	public static final String TABLE_EXPENSES = "expenses";
	public static final String TABLE_IMPORT_LOG = "import_log";

	// Records a party-to-party money transfer imported from a Vyapar
	// backup (Vyapar has no equivalent concept in the existing Excel
	// importer, so this is its own table rather than being folded into
	// TABLE_PAYMENTS).
	public static final String TABLE_PARTY_TRANSFERS = "party_transfers";

	// Maps a source record from an external backup (Vyapar today) to the
	// row it created in this database, so a second import run of the same
	// backup can (a) recognize the row already exists and (b) still find
	// its local id to link child records (e.g. a purchase's line items)
	// even if the parent itself was skipped this run as a duplicate.
	public static final String TABLE_VYB_IMPORT_MAP = "vyb_import_map";

	// Item varieties (e.g. "Size", "Color"). An item can have zero, one, or
	// several groups; each group belongs to exactly one item.
	public static final String TABLE_VARIETY_GROUPS = "variety_groups";

	// A single labeled value within a group (e.g. "S", "M", "L" under a
	// "Size" group). Every group gets a "?" value auto-created alongside it,
	// used as the fallback when a group exists but the user doesn't pick a
	// specific value.
	public static final String TABLE_VARIETY_VALUES = "variety_values";

	// One row per real stock-keeping combination for an item - one value
	// chosen from each of the item's groups. This is what purchase_items /
	// sale_items link to (combo_id) and what actually carries the stock
	// quantity; items.balance stays the authoritative total and is kept in
	// lockstep as the sum of its combos.
	public static final String TABLE_VARIETY_COMBOS = "variety_combos";

	// Junction table: which variety_value (one per group) makes up a given
	// variety_combo.
	public static final String TABLE_VARIETY_COMBO_VALUES = "variety_combo_values";

	// Manual corrections to the cash-in-hand figure (e.g. reconciling a
	// physical till count against what the app computes) - amount is
	// positive to add cash, negative to remove it. Not tied to any party
	// or item, so it gets its own table rather than reusing payments.
	public static final String TABLE_CASH_ADJUSTMENTS = "cash_adjustments";

	// A customer asked for an item this business doesn't currently have
	// in stock - either an existing catalog item (item_id set) or
	// something not in the catalog at all (item_id null, item_name is
	// whatever the customer called it).
	public static final String TABLE_WANTED_ITEMS = "wanted_items";



	// Payment Types
	public static final int PAYMENT_IN = 0;
	public static final int PAYMENT_OUT = 1;


	private SQLiteDatabase transactionDb;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_PARTIES + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"name TEXT NOT NULL UNIQUE, " +
			"balance REAL NOT NULL DEFAULT 0" +
			");"
        );

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_ITEMS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"code TEXT NOT NULL UNIQUE, " +
			"name TEXT NOT NULL, " +
			"purchase_price REAL NOT NULL DEFAULT 0, " +
			"sale_price REAL NOT NULL DEFAULT 0, " +
			"balance REAL NOT NULL DEFAULT 0" +
			");"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_PURCHASES + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"party_id INTEGER NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"invoice_number TEXT, " +
			"grand_total REAL NOT NULL DEFAULT 0, " +
			"amount_paid REAL NOT NULL DEFAULT 0, " +
			"notes TEXT" +
			");"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_PURCHASE_ITEMS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"purchase_id INTEGER NOT NULL, " +
			"item_id INTEGER NOT NULL, " +
			"quantity REAL NOT NULL, " +
			"purchase_price REAL NOT NULL, " +
			"total REAL NOT NULL" +
			");"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS sales (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"invoice_no TEXT, " +
			"date TEXT, " +
			"time TEXT, " +
			"party_id INTEGER, " +
			"subtotal REAL DEFAULT 0, " +
			"discount REAL DEFAULT 0, " +
			"other_charges REAL DEFAULT 0, " +
			"grand_total REAL DEFAULT 0, " +
			"paid_amount REAL DEFAULT 0, " +
			"balance REAL DEFAULT 0, " +
			"notes TEXT" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS sale_items (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"sale_id INTEGER, " +
			"item_id INTEGER, " +
			"qty REAL DEFAULT 0, " +
			"rate REAL DEFAULT 0, " +
			"amount REAL DEFAULT 0" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_PAYMENTS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"code TEXT NOT NULL UNIQUE, " +
			"type INTEGER NOT NULL, " +
			"party_id INTEGER NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"amount REAL NOT NULL DEFAULT 0, " +
			"notes TEXT" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_EXPENSES + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"code TEXT NOT NULL UNIQUE, " +
			"item TEXT NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"amount REAL NOT NULL DEFAULT 0, " +
			"notes TEXT, " +
			"party_id INTEGER" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_IMPORT_LOG + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"import_key TEXT NOT NULL UNIQUE" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_PARTY_TRANSFERS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"from_party_id INTEGER NOT NULL, " +
			"to_party_id INTEGER NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"amount REAL NOT NULL DEFAULT 0, " +
			"notes TEXT" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_VYB_IMPORT_MAP + " (" +
			"vyb_type TEXT NOT NULL, " +
			"vyb_id INTEGER NOT NULL, " +
			"local_id INTEGER NOT NULL, " +
			"PRIMARY KEY (vyb_type, vyb_id)" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_VARIETY_GROUPS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"item_id INTEGER NOT NULL, " +
			"name TEXT NOT NULL, " +
			"sort_order INTEGER NOT NULL DEFAULT 0" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_VARIETY_VALUES + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"group_id INTEGER NOT NULL, " +
			"label TEXT NOT NULL, " +
			"sort_order INTEGER NOT NULL DEFAULT 0, " +
			"is_default INTEGER NOT NULL DEFAULT 0" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_VARIETY_COMBOS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"item_id INTEGER NOT NULL, " +
			"balance REAL NOT NULL DEFAULT 0" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_VARIETY_COMBO_VALUES + " (" +
			"combo_id INTEGER NOT NULL, " +
			"group_id INTEGER NOT NULL, " +
			"value_id INTEGER NOT NULL, " +
			"PRIMARY KEY (combo_id, group_id)" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_CASH_ADJUSTMENTS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"amount REAL NOT NULL DEFAULT 0, " +
			"notes TEXT, " +
			"source TEXT" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_WANTED_ITEMS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"item_id INTEGER, " +
			"item_name TEXT NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"party_id INTEGER, " +
			"notes TEXT, " +
			"fulfilled INTEGER NOT NULL DEFAULT 0" +
			")"
		);


    }
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		// NOTE: previously this dropped every table on upgrade, which wipes
		// all of your data whenever the app version changes. onCreate now
		// uses "CREATE TABLE IF NOT EXISTS" everywhere, so calling it again
		// safely adds any new tables without touching existing data.
		onCreate(db);

		// CREATE TABLE IF NOT EXISTS only helps for brand-new tables - it
		// does nothing to a table (like items) that already exists from an
		// earlier version, so new columns added to an existing table need
		// an explicit ALTER TABLE here.
		addColumnIfMissing(db, TABLE_ITEMS, "balance", "REAL NOT NULL DEFAULT 0");
		addColumnIfMissing(db, TABLE_PARTIES, "balance", "REAL NOT NULL DEFAULT 0");
		addColumnIfMissing(db, TABLE_EXPENSES, "party_id", "INTEGER");
		addColumnIfMissing(db, TABLE_PURCHASE_ITEMS, "combo_id", "INTEGER");
		addColumnIfMissing(db, "sale_items", "combo_id", "INTEGER");

		addColumnIfMissing(db, TABLE_PURCHASES, "source", "TEXT");
		addColumnIfMissing(db, "sales", "source", "TEXT");
		addColumnIfMissing(db, TABLE_PAYMENTS, "source", "TEXT");
		addColumnIfMissing(db, TABLE_EXPENSES, "source", "TEXT");
		addColumnIfMissing(db, TABLE_PARTY_TRANSFERS, "source", "TEXT");

		// Expenses had no paid/credit split before - every existing
		// expense was implicitly "fully paid in cash" the moment it was
		// recorded, so backfill paid_amount to match amount for every
		// row that already existed when this column was added. Only
		// runs the one time the column is actually created; a later
		// legitimately-unpaid (credit) expense with paid_amount=0 must
		// never get overwritten by a repeat of this backfill.
		if (addColumnIfMissing(db, TABLE_EXPENSES, "paid_amount", "REAL NOT NULL DEFAULT 0")) {

			db.execSQL("UPDATE " + TABLE_EXPENSES + " SET paid_amount = amount");
		}

		dropPurchaseCodeColumnIfPresent(db);
	}

	// =====================
	// Purchases used to have their own auto-generated "code" (PU00001
	// style) as well as an invoice_number. That's been retired -
	// invoice_number is now the only identifier a purchase carries.
	// SQLite can't drop a column or its NOT NULL/UNIQUE constraint
	// in place, so an existing "code" column is removed by rebuilding
	// the table without it and copying the data across.
	// =====================
	private void dropPurchaseCodeColumnIfPresent(SQLiteDatabase db) {

		boolean hasCode = false;

		Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE_PURCHASES + ")", null);

		int nameIndex = cursor.getColumnIndex("name");

		while (cursor.moveToNext()) {

			if ("code".equalsIgnoreCase(cursor.getString(nameIndex))) {
				hasCode = true;
				break;
			}
		}

		cursor.close();

		if (!hasCode) {
			return;
		}

		db.execSQL(
			"CREATE TABLE purchases_new (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"party_id INTEGER NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"invoice_number TEXT, " +
			"grand_total REAL NOT NULL DEFAULT 0, " +
			"amount_paid REAL NOT NULL DEFAULT 0, " +
			"notes TEXT" +
			")"
		);

		db.execSQL(
			"INSERT INTO purchases_new " +
			"(id, party_id, date, time, invoice_number, grand_total, amount_paid, notes) " +
			"SELECT id, party_id, date, time, invoice_number, grand_total, amount_paid, notes " +
			"FROM " + TABLE_PURCHASES
		);

		db.execSQL("DROP TABLE " + TABLE_PURCHASES);
		db.execSQL("ALTER TABLE purchases_new RENAME TO " + TABLE_PURCHASES);
	}

	// =====================
	// Adds `column` to `table` via ALTER TABLE, but only if it isn't
	// already there - safe to call on every upgrade (and even after
	// onCreate on a fresh install, where it's a harmless no-op).
	// =====================
	// Returns true only when the column didn't already exist and this
	// call just added it - callers that need to backfill a newly-added
	// column's data (once, not on every future onUpgrade run) check this
	// instead of re-deriving "was this just added" themselves.
	private boolean addColumnIfMissing(
		SQLiteDatabase db,
		String table,
		String column,
		String columnDefinition) {

		boolean exists = false;

		Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);

		int nameIndex = cursor.getColumnIndex("name");

		while (cursor.moveToNext()) {

			if (column.equalsIgnoreCase(cursor.getString(nameIndex))) {
				exists = true;
				break;
			}
		}

		cursor.close();

		if (!exists) {

			db.execSQL(
				"ALTER TABLE " + table +
				" ADD COLUMN " + column + " " + columnDefinition
			);

			return true;
		}

		return false;
	}

    // =====================
    // INSERT PARTY
    // =====================

    public long insertParty(String name) {

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name);

        long id = db.insert(TABLE_PARTIES, null, values);


        return id;
    }
// =====================
// GET PARTIES
// =====================

	public ArrayList<java.util.HashMap<String, Object>> getParties() {

		ArrayList<java.util.HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
            "SELECT id, name, balance FROM " + TABLE_PARTIES +
            " ORDER BY name",
            null
		);

		while (cursor.moveToNext()) {

			java.util.HashMap<String, Object> map = new java.util.HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("name", cursor.getString(1));
			map.put("balance", cursor.getDouble(2));

			list.add(map);
		}

		cursor.close();

		return list;
	}
	// =====================
// GET PARTY BY ID
// =====================

	public String getPartyById(int id) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
            "SELECT name FROM " + TABLE_PARTIES + " WHERE id=?",
            new String[]{String.valueOf(id)}
		);

		String name = "";

		if (cursor.moveToFirst()) {
			name = cursor.getString(0);
		}

		cursor.close();

		return name;
	}
	// =====================
// GET PARTY BALANCE BY ID
// =====================
// Positive means the party owes us (receivable, from sale dues);
// negative means we owe the party (payable, from purchase dues).
// =====================

	public double getPartyBalance(int id) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
            "SELECT balance FROM " + TABLE_PARTIES + " WHERE id=?",
            new String[]{String.valueOf(id)}
		);

		double balance = 0;

		if (cursor.moveToFirst()) {
			balance = cursor.getDouble(0);
		}

		cursor.close();

		return balance;
	}
	// =====================
// DELETE PARTY
// =====================

	public boolean deleteParty(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.delete(
            TABLE_PARTIES,
            "id=?",
            new String[]{String.valueOf(id)}
		);


		return rows > 0;
	}
	// =====================
// UPDATE PARTY
// =====================

	public boolean updateParty(int id, String name) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put("name", name);

		int rows = db.update(
            TABLE_PARTIES,
            values,
            "id=?",
            new String[]{String.valueOf(id)}
		);


		return rows > 0;
	}
	// =====================
// PARTY EXISTS
// =====================

	public boolean partyExists(String name) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
            "SELECT id FROM " + TABLE_PARTIES + " WHERE name=?",
            new String[]{name}
		);

		boolean exists = cursor.moveToFirst();

		cursor.close();

		return exists;
	}
	// =====================
// =====================
// INSERT ITEM
// =====================

	public long insertItem(String name,
						   double purchasePrice,
						   double salePrice) {

		return insertItem(name, purchasePrice, salePrice, 0);
	}

	public long insertItem(String name,
						   double purchasePrice,
						   double salePrice,
						   double balance) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("code", generateNextItemCode());
		values.put("name", name);
		values.put("purchase_price", purchasePrice);
		values.put("sale_price", salePrice);
		values.put("balance", balance);

		long id = db.insert(TABLE_ITEMS, null, values);


		return id;
	}

// =====================
// BULK INSERT ITEMS
// =====================

	public HashMap<String, Integer> insertItems(ArrayList<HashMap<String, Object>> listMap) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		int imported = 0;
		int skipped = 0;

		try {

			Cursor cursor = db.rawQuery(
				"SELECT code FROM " + TABLE_ITEMS +
				" ORDER BY id DESC LIMIT 1",
				null);

			char letter = 'C';
			int number = 1;

			if (cursor.moveToFirst()) {

				String lastCode = cursor.getString(0);

				if (lastCode != null &&
					lastCode.length() >= 4 &&
					Character.isLetter(lastCode.charAt(0))) {

					letter = lastCode.charAt(0);

					try {
						number = Integer.parseInt(lastCode.substring(1)) + 1;
					} catch (Exception e) {
						number = 1;
					}
				}
			}

			cursor.close();

			ContentValues values = new ContentValues();

			for (HashMap<String, Object> map : listMap) {

				String code = "";

				if (map.get("code") != null) {
					code = map.get("code").toString().trim();
				}

				String name = "";

				if (map.get("name") != null) {
					name = map.get("name").toString().trim();
				}

				if (code.length() == 0) {

					code = String.format("%c%03d", letter, number);

					number++;

					if (number > 999) {
						letter++;
						number = 1;
					}
				}

				Cursor check = db.rawQuery(
					"SELECT id FROM " + TABLE_ITEMS +
					" WHERE code=? OR name=? LIMIT 1",
					new String[]{code, name});

				boolean exists = check.moveToFirst();

				check.close();

				if (exists) {
					skipped++;
					continue;
				}

				values.clear();

				values.put("code", code);
				values.put("name", name);

				double purchasePrice = 0;
				double salePrice = 0;

				if (map.get("purchase_price") != null &&
					map.get("purchase_price").toString().trim().length() > 0) {

					purchasePrice = Double.parseDouble(
						map.get("purchase_price").toString().trim());
				}

				if (map.get("sale_price") != null &&
					map.get("sale_price").toString().trim().length() > 0) {

					salePrice = Double.parseDouble(
						map.get("sale_price").toString().trim());
				}

				values.put("purchase_price", purchasePrice);
				values.put("sale_price", salePrice);

				double balance = 0;

				if (map.get("balance") != null &&
					map.get("balance").toString().trim().length() > 0) {

					try {
						balance = Double.parseDouble(
							map.get("balance").toString().trim());
					} catch (Exception e) {
					}
				}

				values.put("balance", balance);

				db.insert(TABLE_ITEMS, null, values);

				imported++;
			}

			db.setTransactionSuccessful();

		} finally {

			db.endTransaction();
		}

		HashMap<String, Integer> result = new HashMap<String, Integer>();
		result.put("imported", imported);
		result.put("skipped", skipped);

		return result;
	}
// =====================
// GET ITEMS
// =====================

	public ArrayList<java.util.HashMap<String, Object>> getItems() {

		ArrayList<java.util.HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, code, name, purchase_price, sale_price, balance FROM " +
			TABLE_ITEMS +
			" ORDER BY code",
			null
		);

		while (cursor.moveToNext()) {

			java.util.HashMap<String, Object> map = new java.util.HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("name", cursor.getString(2));
			map.put("purchase_price", cursor.getDouble(3));
			map.put("sale_price", cursor.getDouble(4));
			map.put("balance", cursor.getDouble(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}

// =====================
// GET ITEM BY ID
// =====================

	public java.util.HashMap<String, Object> getItemById(int id) {

		java.util.HashMap<String, Object> map = new java.util.HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT code, name, purchase_price, sale_price, balance FROM " +
			TABLE_ITEMS +
			" WHERE id=?",
			new String[]{String.valueOf(id)}
		);

		if (cursor.moveToFirst()) {

			map.put("code", cursor.getString(0));
			map.put("name", cursor.getString(1));
			map.put("purchase_price", cursor.getDouble(2));
			map.put("sale_price", cursor.getDouble(3));
			map.put("balance", cursor.getDouble(4));
		}

		cursor.close();

		return map;
	}

	// The current stock figure a sale should be checked against: a
	// combo's own balance when the line has one, otherwise the item's
	// own balance. Used to keep stock from ever going negative on a
	// sale - see Transactioneditactivity/GenerateEntriesActivity.
	public double getAvailableStock(int itemId, Integer comboId) {

		SQLiteDatabase db = this.getReadableDatabase();

		String table = comboId != null ? TABLE_VARIETY_COMBOS : TABLE_ITEMS;
		int id = comboId != null ? comboId : itemId;

		Cursor cursor = db.rawQuery(
			"SELECT balance FROM " + table + " WHERE id=?",
			new String[]{String.valueOf(id)}
		);

		double balance = 0;

		if (cursor.moveToFirst()) {
			balance = cursor.getDouble(0);
		}

		cursor.close();

		return balance;
	}

// =====================
// UPDATE ITEM
// =====================

	public boolean updateItem(int id,
							  String name,
							  double purchasePrice,
							  double salePrice) {

		return updateItem(id, name, purchasePrice, salePrice, null);
	}

	// balance == null leaves the stored balance untouched (used by the
	// old 4-arg overload, and by any edit screen that doesn't expose a
	// balance field yet). Pass a non-null value to set it explicitly.
	public boolean updateItem(int id,
							  String name,
							  double purchasePrice,
							  double salePrice,
							  Double balance) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("name", name);
		values.put("purchase_price", purchasePrice);
		values.put("sale_price", salePrice);

		if (balance != null) {
			values.put("balance", balance);
		}

		int rows = db.update(
			TABLE_ITEMS,
			values,
			"id=?",
			new String[]{String.valueOf(id)}
		);


		return rows > 0;
	}

// =====================
// DELETE ITEM
// =====================

	public boolean deleteItem(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.delete(
			TABLE_ITEMS,
			"id=?",
			new String[]{String.valueOf(id)}
		);


		return rows > 0;
	}

// =====================
// ITEM EXISTS
// =====================

	public boolean itemExists(String name) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_ITEMS + " WHERE name=?",
			new String[]{name}
		);

		boolean exists = cursor.moveToFirst();

		cursor.close();

		return exists;
	}
	// =====================
// GENERATE NEXT ITEM CODE
// =====================

	private String generateNextItemCode() {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT code FROM " + TABLE_ITEMS +
			" ORDER BY id DESC LIMIT 1",
			null
		);

		if (!cursor.moveToFirst()) {

			cursor.close();

			return "C001";
		}

		String lastCode = cursor.getString(0);

		cursor.close();

		char letter = lastCode.charAt(0);
		int number = Integer.parseInt(lastCode.substring(1));

		if (number < 999) {

			number++;

		} else {

			letter++;
			number = 1;

			if (letter > 'Z') {
				throw new RuntimeException("Maximum item codes reached.");
			}
		}

		return String.format("%c%03d", letter, number);
	}
	// =====================
// DELETE ALL ITEMS
// =====================

	public void deleteAllItems() {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_ITEMS, null, null);

		// The import log's "already imported" keys are only meaningful
		// while the imported data still exists - once the items are
		// gone, clear the log too so re-importing the same file isn't
		// wrongly treated as a duplicate.
		db.delete(TABLE_IMPORT_LOG, null, null);

	}
	// =====================
// DELETE ALL PARTIES
// =====================

	public void deleteAllParties() {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_PARTIES, null, null);

		// See deleteAllItems() - the import log must be cleared too, or
		// a re-import of the same parties gets skipped as "duplicate".
		db.delete(TABLE_IMPORT_LOG, null, null);

	}
	// =====================
	// GENERATE NEXT PURCHASE INVOICE NUMBER
	// =====================
	// This is separate from the "code" above (our own internal
	// PUxxxxx reference). It fills in a purchase's own invoice
	// number - normally the supplier's invoice number, typed in
	// manually or carried over from an import - only when the
	// purchase has none of its own. Same numbering scheme/floor as
	// getNextSaleInvoiceNo().
	// =====================

	public String getNextPurchaseInvoiceNo() {
		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT MAX(CAST(invoice_number AS INTEGER)) FROM " + TABLE_PURCHASES,
			null
		);

		int nextNo = DEFAULT_SALE_INVOICE_NO;

		if (cursor.moveToFirst() && !cursor.isNull(0)) {
			nextNo = Math.max(cursor.getInt(0) + 1, DEFAULT_SALE_INVOICE_NO);
		}

		cursor.close();

		return String.valueOf(nextNo);
	}

	// Same numbering scheme as getNextPurchaseInvoiceNo(), but runs on
	// the shared bulk-transaction connection so it can be called
	// mid-import without opening a second database connection.
	public String getNextPurchaseInvoiceNoBulk(SQLiteDatabase db) {

		Cursor cursor = db.rawQuery(
			"SELECT MAX(CAST(invoice_number AS INTEGER)) FROM " + TABLE_PURCHASES,
			null
		);

		int nextNo = DEFAULT_SALE_INVOICE_NO;

		if (cursor.moveToFirst() && !cursor.isNull(0)) {
			nextNo = Math.max(cursor.getInt(0) + 1, DEFAULT_SALE_INVOICE_NO);
		}

		cursor.close();

		return String.valueOf(nextNo);
	}
	// =====================
// INSERT PURCHASE
// =====================

	// =====================
	// Adds `delta` to a party's running balance. Positive balance means
	// the party owes us (receivable, from sale dues); negative means we
	// owe the party (payable, from purchase dues). Caller passes an
	// already-open db so this can share the same connection/transaction
	// as the insert it's following.
	// =====================
	private void adjustPartyBalance(SQLiteDatabase db, int partyId, double delta) {

		if (delta == 0) {
			return;
		}

		db.execSQL(
			"UPDATE " + TABLE_PARTIES +
			" SET balance = balance + ? WHERE id = ?",
			new Object[]{delta, partyId}
		);
	}

	// =====================
	// ADJUST ITEM BALANCE (STOCK)
	// =====================
	// items.balance doubles as the item's current stock quantity. A
	// positive delta is stock coming in (a purchase line), a negative
	// delta is stock going out (a sale line). Caller passes an
	// already-open db so this can share the same connection/transaction
	// as the insert/delete it's following.
	// =====================
	private void adjustItemBalance(SQLiteDatabase db, int itemId, double delta) {

		if (delta == 0) {
			return;
		}

		db.execSQL(
			"UPDATE " + TABLE_ITEMS +
			" SET balance = balance + ? WHERE id = ?",
			new Object[]{delta, itemId}
		);
	}

	// =====================
	// ITEM VARIETIES
	// =====================
	// An item can have zero or more variety groups (e.g. "Size", "Color").
	// Every group gets a "?" value created alongside it as the fallback
	// for "no specific variety picked" in Purchase/Sale. Stock is tracked
	// per combination (one value from each of the item's groups) in
	// variety_combos; items.balance stays the authoritative total stock
	// and is kept in lockstep as the sum of its combos via
	// adjustComboBalance(), called everywhere adjustItemBalance() is.
	// =====================

	private static final String DEFAULT_VARIETY_VALUE_LABEL = "?";

	public long createVarietyGroup(int itemId, String name) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			ArrayList<HashMap<String, Object>> existingGroups =
				getVarietyGroups(db, itemId);

			ContentValues groupValues = new ContentValues();
			groupValues.put("item_id", itemId);
			groupValues.put("name", name);
			groupValues.put("sort_order", existingGroups.size());

			long groupId = db.insert(TABLE_VARIETY_GROUPS, null, groupValues);

			long defaultValueId = insertVarietyValueRow(
				db, groupId, DEFAULT_VARIETY_VALUE_LABEL, 0, true
			);

			if (existingGroups.isEmpty()) {

				// First group for this item - migrate its current plain
				// balance into a single new "?" combo so no stock is lost.
				double currentBalance = getItemBalance(db, itemId);

				long comboId = insertComboRow(db, itemId, currentBalance);

				linkComboValue(db, comboId, groupId, defaultValueId);

			} else {

				// Item already has combos from earlier groups - extend
				// every existing combo with this new group's "?" value
				// instead of creating new combos. The combo count is
				// unchanged by adding a GROUP; it only grows when a VALUE
				// is added to an existing group (see addVarietyValue()).
				ArrayList<Integer> comboIds = getComboIdsForItem(db, itemId);

				for (int comboId : comboIds) {
					linkComboValue(db, comboId, groupId, defaultValueId);
				}
			}

			db.setTransactionSuccessful();

			return groupId;

		} finally {

			db.endTransaction();
		}
	}

	public long addVarietyValue(int groupId, String label) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			int itemId = getItemIdForGroup(db, groupId);

			ArrayList<HashMap<String, Object>> existingValues =
				getVarietyValues(db, groupId);

			long valueId = insertVarietyValueRow(
				db, groupId, label, existingValues.size(), false
			);

			// Auto-grid: create one new combo, at 0 stock, for every
			// existing combination of the item's OTHER groups' values,
			// paired with this new value - so a combination is never
			// missing when it's time to sell it.
			ArrayList<HashMap<Integer, Integer>> otherGroupTuples =
				getDistinctOtherGroupTuples(db, itemId, groupId);

			if (otherGroupTuples.isEmpty()) {

				// This is the item's only group - no other-group
				// dimensions to combine with.
				long comboId = insertComboRow(db, itemId, 0);
				linkComboValue(db, comboId, groupId, valueId);

			} else {

				for (HashMap<Integer, Integer> tuple : otherGroupTuples) {

					long comboId = insertComboRow(db, itemId, 0);
					linkComboValue(db, comboId, groupId, valueId);

					for (Map.Entry<Integer, Integer> entry : tuple.entrySet()) {
						linkComboValue(
							db, comboId, entry.getKey(), entry.getValue()
						);
					}
				}
			}

			db.setTransactionSuccessful();

			return valueId;

		} finally {

			db.endTransaction();
		}
	}

	public ArrayList<HashMap<String, Object>> getVarietyGroups(int itemId) {
		return getVarietyGroups(this.getReadableDatabase(), itemId);
	}

	private ArrayList<HashMap<String, Object>> getVarietyGroups(
		SQLiteDatabase db, int itemId) {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		Cursor cursor = db.rawQuery(
			"SELECT id, name, sort_order FROM " + TABLE_VARIETY_GROUPS +
			" WHERE item_id=? ORDER BY sort_order, id",
			new String[]{String.valueOf(itemId)}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<String, Object>();
			map.put("id", cursor.getInt(0));
			map.put("name", cursor.getString(1));
			map.put("sort_order", cursor.getInt(2));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	public ArrayList<HashMap<String, Object>> getVarietyValues(int groupId) {
		return getVarietyValues(this.getReadableDatabase(), groupId);
	}

	private ArrayList<HashMap<String, Object>> getVarietyValues(
		SQLiteDatabase db, int groupId) {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		Cursor cursor = db.rawQuery(
			"SELECT id, label, sort_order, is_default FROM " +
			TABLE_VARIETY_VALUES +
			" WHERE group_id=? ORDER BY is_default DESC, sort_order, id",
			new String[]{String.valueOf(groupId)}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<String, Object>();
			map.put("id", cursor.getInt(0));
			map.put("label", cursor.getString(1));
			map.put("sort_order", cursor.getInt(2));
			map.put("is_default", cursor.getInt(3) != 0);

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// One row per real stock-keeping combination for this item, with a
	// human-readable "label" (e.g. "Red / M") built by joining its values
	// in group sort order.
	public ArrayList<HashMap<String, Object>> getVarietyCombos(int itemId) {

		ArrayList<HashMap<String, Object>> combos =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor comboCursor = db.rawQuery(
			"SELECT id, balance FROM " + TABLE_VARIETY_COMBOS +
			" WHERE item_id=? ORDER BY id",
			new String[]{String.valueOf(itemId)}
		);

		while (comboCursor.moveToNext()) {

			int comboId = comboCursor.getInt(0);
			double balance = comboCursor.getDouble(1);

			HashMap<String, Object> map = new HashMap<String, Object>();
			map.put("id", comboId);
			map.put("balance", balance);
			map.put("label", getComboLabel(db, comboId));

			combos.add(map);
		}

		comboCursor.close();

		return combos;
	}

	private String getComboLabel(SQLiteDatabase db, int comboId) {

		StringBuilder label = new StringBuilder();

		Cursor cursor = db.rawQuery(
			"SELECT v.label FROM " + TABLE_VARIETY_COMBO_VALUES + " cv " +
			"INNER JOIN " + TABLE_VARIETY_VALUES + " v ON v.id = cv.value_id " +
			"INNER JOIN " + TABLE_VARIETY_GROUPS + " g ON g.id = cv.group_id " +
			"WHERE cv.combo_id=? ORDER BY g.sort_order, g.id",
			new String[]{String.valueOf(comboId)}
		);

		while (cursor.moveToNext()) {

			if (label.length() > 0) {
				label.append(" / ");
			}

			label.append(cursor.getString(0));
		}

		cursor.close();

		return label.toString();
	}

	// Given the value selected for every group of an item, finds the
	// combo row that matches all of them. Returns null if there are no
	// selections (the item has no variety groups - nothing to resolve).
	public Integer resolveComboId(Map<Integer, Integer> groupIdToValueId) {

		if (groupIdToValueId == null || groupIdToValueId.isEmpty()) {
			return null;
		}

		SQLiteDatabase db = this.getReadableDatabase();

		StringBuilder where = new StringBuilder();
		ArrayList<String> args = new ArrayList<String>();

		for (Map.Entry<Integer, Integer> entry : groupIdToValueId.entrySet()) {

			if (where.length() > 0) {
				where.append(" OR ");
			}

			where.append("(group_id=? AND value_id=?)");
			args.add(String.valueOf(entry.getKey()));
			args.add(String.valueOf(entry.getValue()));
		}

		args.add(String.valueOf(groupIdToValueId.size()));

		Cursor cursor = db.rawQuery(
			"SELECT combo_id FROM " + TABLE_VARIETY_COMBO_VALUES +
			" WHERE " + where.toString() +
			" GROUP BY combo_id HAVING COUNT(*)=?",
			args.toArray(new String[0])
		);

		Integer comboId = null;

		if (cursor.moveToFirst()) {
			comboId = cursor.getInt(0);
		}

		cursor.close();

		return comboId;
	}

	// The inverse of resolveComboId(): given a combo id, returns its
	// group_id -> value_id selections, so a dropdown can be restored to
	// its previous selection when re-editing an existing line.
	public Map<Integer, Integer> getComboSelections(int comboId) {

		Map<Integer, Integer> selections = new HashMap<Integer, Integer>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT group_id, value_id FROM " + TABLE_VARIETY_COMBO_VALUES +
			" WHERE combo_id=?",
			new String[]{String.valueOf(comboId)}
		);

		while (cursor.moveToNext()) {
			selections.put(cursor.getInt(0), cursor.getInt(1));
		}

		cursor.close();

		return selections;
	}

	// Mirrors adjustItemBalance() but for a variety combo's own stock
	// count. comboId is null whenever the item a purchase/sale line
	// belongs to has no variety groups - in that case there's nothing to
	// adjust here, items.balance (updated separately) is the only figure
	// that exists for that item.
	private void adjustComboBalance(SQLiteDatabase db, Integer comboId, double delta) {

		if (comboId == null || delta == 0) {
			return;
		}

		db.execSQL(
			"UPDATE " + TABLE_VARIETY_COMBOS +
			" SET balance = balance + ? WHERE id = ?",
			new Object[]{delta, comboId}
		);
	}

	private long insertVarietyValueRow(
		SQLiteDatabase db, long groupId, String label,
		int sortOrder, boolean isDefault) {

		ContentValues values = new ContentValues();
		values.put("group_id", groupId);
		values.put("label", label);
		values.put("sort_order", sortOrder);
		values.put("is_default", isDefault ? 1 : 0);

		return db.insert(TABLE_VARIETY_VALUES, null, values);
	}

	private long insertComboRow(SQLiteDatabase db, int itemId, double balance) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("balance", balance);

		return db.insert(TABLE_VARIETY_COMBOS, null, values);
	}

	private void linkComboValue(
		SQLiteDatabase db, long comboId, long groupId, long valueId) {

		ContentValues values = new ContentValues();
		values.put("combo_id", comboId);
		values.put("group_id", groupId);
		values.put("value_id", valueId);

		db.insertWithOnConflict(
			TABLE_VARIETY_COMBO_VALUES,
			null,
			values,
			SQLiteDatabase.CONFLICT_REPLACE
		);
	}

	private double getItemBalance(SQLiteDatabase db, int itemId) {

		double balance = 0;

		Cursor cursor = db.rawQuery(
			"SELECT balance FROM " + TABLE_ITEMS + " WHERE id=?",
			new String[]{String.valueOf(itemId)}
		);

		if (cursor.moveToFirst()) {
			balance = cursor.getDouble(0);
		}

		cursor.close();

		return balance;
	}

	private int getItemIdForGroup(SQLiteDatabase db, int groupId) {

		int itemId = 0;

		Cursor cursor = db.rawQuery(
			"SELECT item_id FROM " + TABLE_VARIETY_GROUPS + " WHERE id=?",
			new String[]{String.valueOf(groupId)}
		);

		if (cursor.moveToFirst()) {
			itemId = cursor.getInt(0);
		}

		cursor.close();

		return itemId;
	}

	private ArrayList<Integer> getComboIdsForItem(SQLiteDatabase db, int itemId) {

		ArrayList<Integer> ids = new ArrayList<Integer>();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_VARIETY_COMBOS + " WHERE item_id=?",
			new String[]{String.valueOf(itemId)}
		);

		while (cursor.moveToNext()) {
			ids.add(cursor.getInt(0));
		}

		cursor.close();

		return ids;
	}

	// Returns, for every existing combo of this item, the map of
	// (group_id -> value_id) for every group OTHER than groupId - i.e.
	// the "other dimensions" a new value in groupId needs to be combined
	// with. Combos are kept as a full grid at all times, so every
	// existing combo already has exactly one value per other group.
	private ArrayList<HashMap<Integer, Integer>> getDistinctOtherGroupTuples(
		SQLiteDatabase db, int itemId, int groupId) {

		ArrayList<HashMap<Integer, Integer>> tuples =
			new ArrayList<HashMap<Integer, Integer>>();

		ArrayList<Integer> comboIds = getComboIdsForItem(db, itemId);

		for (int comboId : comboIds) {

			HashMap<Integer, Integer> tuple = new HashMap<Integer, Integer>();

			Cursor cursor = db.rawQuery(
				"SELECT group_id, value_id FROM " +
				TABLE_VARIETY_COMBO_VALUES +
				" WHERE combo_id=? AND group_id<>?",
				new String[]{String.valueOf(comboId), String.valueOf(groupId)}
			);

			while (cursor.moveToNext()) {
				tuple.put(cursor.getInt(0), cursor.getInt(1));
			}

			cursor.close();

			if (tuple.isEmpty()) {
				// groupId was this item's only group - nothing to
				// combine with.
				continue;
			}

			boolean alreadyPresent = false;

			for (HashMap<Integer, Integer> existing : tuples) {

				if (existing.equals(tuple)) {
					alreadyPresent = true;
					break;
				}
			}

			if (!alreadyPresent) {
				tuples.add(tuple);
			}
		}

		return tuples;
	}

	public long insertPurchase(
        int partyId,
        String date,
        String time,
        String invoiceNumber,
        double grandTotal,
        double amountPaid,
        String notes
	) {

		SQLiteDatabase db = this.getWritableDatabase();

		if (invoiceNumber == null || invoiceNumber.trim().length() == 0) {
			invoiceNumber = getNextPurchaseInvoiceNo();
		}

		ContentValues values = new ContentValues();

		values.put("party_id", partyId);
		values.put("date", date);
		values.put("time", time);
		values.put("invoice_number", invoiceNumber);
		values.put("grand_total", grandTotal);
		values.put("amount_paid", amountPaid);
		values.put("notes", notes);
		values.put("source", "Manual");

		long id = db.insert(
            TABLE_PURCHASES,
            null,
            values
		);

		// Any unpaid portion of this purchase is money we now owe the
		// supplier, so it comes off their balance.
		double due = grandTotal - amountPaid;
		adjustPartyBalance(db, partyId, -due);


		return id;
	}
	// =====================
// GET PURCHASES
// =====================

	// =====================
	// GET TRANSACTIONS FOR A PARTY (PURCHASES + SALES)
	// =====================

	public static final int TRANSACTION_TYPE_PURCHASE = 0;
	public static final int TRANSACTION_TYPE_SALE = 1;
	public static final int TRANSACTION_TYPE_PAYMENT_IN = 2;
	public static final int TRANSACTION_TYPE_PAYMENT_OUT = 3;

	public ArrayList<HashMap<String, Object>> getTransactionsByParty(int partyId) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT p.id, p.invoice_number, pa.name, p.date, p.grand_total, " +
			TRANSACTION_TYPE_PURCHASE + " AS transaction_type " +
			"FROM " + TABLE_PURCHASES + " p " +
			"INNER JOIN " + TABLE_PARTIES + " pa " +
			"ON p.party_id = pa.id " +
			"WHERE p.party_id = ? " +
			"UNION ALL " +
			"SELECT s.id, s.invoice_no, pa2.name, s.date, s.grand_total, " +
			TRANSACTION_TYPE_SALE + " AS transaction_type " +
			"FROM sales s " +
			"LEFT JOIN " + TABLE_PARTIES + " pa2 " +
			"ON s.party_id = pa2.id " +
			"WHERE s.party_id = ? " +
			"UNION ALL " +
			"SELECT py.id, py.code, pa3.name, py.date, py.amount, " +
			TRANSACTION_TYPE_PAYMENT_IN + " AS transaction_type " +
			"FROM " + TABLE_PAYMENTS + " py " +
			"LEFT JOIN " + TABLE_PARTIES + " pa3 " +
			"ON py.party_id = pa3.id " +
			"WHERE py.party_id = ? AND py.type = " + PAYMENT_IN + " " +
			"UNION ALL " +
			"SELECT py2.id, py2.code, pa4.name, py2.date, py2.amount, " +
			TRANSACTION_TYPE_PAYMENT_OUT + " AS transaction_type " +
			"FROM " + TABLE_PAYMENTS + " py2 " +
			"LEFT JOIN " + TABLE_PARTIES + " pa4 " +
			"ON py2.party_id = pa4.id " +
			"WHERE py2.party_id = ? AND py2.type = " + PAYMENT_OUT + " " +
			"ORDER BY date DESC",

			new String[]{
				String.valueOf(partyId),
				String.valueOf(partyId),
				String.valueOf(partyId),
				String.valueOf(partyId)
			}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("party_name", cursor.getString(2));
			map.put("date", cursor.getString(3));
			map.put("grand_total", cursor.getDouble(4));
			map.put("transaction_type", cursor.getInt(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
	// GET TRANSACTIONS FOR AN ITEM (PURCHASES + SALES CONTAINING IT)
	// =====================

	public ArrayList<HashMap<String, Object>> getTransactionsByItem(int itemId) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT DISTINCT p.id, p.invoice_number, pa.name, p.date, p.grand_total, " +
			TRANSACTION_TYPE_PURCHASE + " AS transaction_type " +
			"FROM " + TABLE_PURCHASE_ITEMS + " pi " +
			"INNER JOIN " + TABLE_PURCHASES + " p " +
			"ON pi.purchase_id = p.id " +
			"INNER JOIN " + TABLE_PARTIES + " pa " +
			"ON p.party_id = pa.id " +
			"WHERE pi.item_id = ? " +
			"UNION ALL " +
			"SELECT DISTINCT s.id, s.invoice_no, pa2.name, s.date, s.grand_total, " +
			TRANSACTION_TYPE_SALE + " AS transaction_type " +
			"FROM sale_items si " +
			"INNER JOIN sales s " +
			"ON si.sale_id = s.id " +
			"LEFT JOIN " + TABLE_PARTIES + " pa2 " +
			"ON s.party_id = pa2.id " +
			"WHERE si.item_id = ? " +
			"ORDER BY date DESC",

			new String[]{
				String.valueOf(itemId),
				String.valueOf(itemId)
			}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("party_name", cursor.getString(2));
			map.put("date", cursor.getString(3));
			map.put("grand_total", cursor.getDouble(4));
			map.put("transaction_type", cursor.getInt(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	public ArrayList<HashMap<String, Object>> getPurchases() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT p.id, pa.name, p.date, " +
			"p.invoice_number, p.grand_total " +
			"FROM " + TABLE_PURCHASES + " p " +
			"INNER JOIN " + TABLE_PARTIES + " pa " +
			"ON p.party_id = pa.id " +
			"ORDER BY p.id DESC",

			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("party_name", cursor.getString(1));
			map.put("date", cursor.getString(2));
			map.put("invoice_no", cursor.getString(3));
			map.put("grand_total", cursor.getDouble(4));

			list.add(map);
		}

		cursor.close();

		return list;
	}
	// =====================
// GET ITEMS FOR SPINNER
// =====================

	public ArrayList<HashMap<String, Object>> getItemsForSpinner() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, code, name, purchase_price, sale_price, balance " +
			"FROM " + TABLE_ITEMS +
			" ORDER BY name",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("name", cursor.getString(2));
			map.put("purchase_price", cursor.getDouble(3));
			map.put("sale_price", cursor.getDouble(4));
			map.put("stock", cursor.getDouble(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}
	// =====================
// GET PURCHASE BY ID
// =====================

	public HashMap<String, Object> getPurchaseById(int purchaseId) {

		HashMap<String, Object> map = new HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT p.party_id, pa.name, p.date, p.time, " +
			"p.invoice_number, p.grand_total, " +
			"p.amount_paid, p.notes " +
			"FROM " + TABLE_PURCHASES + " p " +
			"INNER JOIN " + TABLE_PARTIES + " pa " +
			"ON p.party_id = pa.id " +
			"WHERE p.id=?",

			new String[]{
				String.valueOf(purchaseId)
			}
		);

		if (cursor.moveToFirst()) {

			map.put("party_id", cursor.getInt(0));
			map.put("party_name", cursor.getString(1));
			map.put("date", cursor.getString(2));
			map.put("time", cursor.getString(3));
			map.put("invoice_number", cursor.getString(4));
			map.put("grand_total", cursor.getDouble(5));
			map.put("amount_paid", cursor.getDouble(6));
			map.put("notes", cursor.getString(7));
		}

		cursor.close();

		return map;
	}
	// =====================
// INSERT PURCHASE ITEM
// =====================

	public long insertPurchaseItem(
        long purchaseId,
        int itemId,
        double quantity,
        double purchasePrice,
        double total) {

		return insertPurchaseItem(
			purchaseId, itemId, quantity, purchasePrice, total, null
		);
	}

	// comboId is null for an item with no variety groups; when non-null it
	// is the specific variety combination this purchase line is stocking.
	public long insertPurchaseItem(
        long purchaseId,
        int itemId,
        double quantity,
        double purchasePrice,
        double total,
        Integer comboId) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("purchase_id", purchaseId);
		values.put("item_id", itemId);
		values.put("quantity", quantity);
		values.put("purchase_price", purchasePrice);
		values.put("total", total);
		values.put("combo_id", comboId);

		long id = db.insert(
            TABLE_PURCHASE_ITEMS,
            null,
            values
		);

		// A purchase brings stock in.
		adjustItemBalance(db, itemId, quantity);
		adjustComboBalance(db, comboId, quantity);


		return id;
	}
	// =====================
// GET PURCHASE ITEMS
// =====================

	public ArrayList<HashMap<String, Object>> getPurchaseItems(int purchaseId) {

		ArrayList<HashMap<String, Object>> list =
            new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT i.code, i.name, pi.quantity, " +
			"pi.purchase_price, pi.total " +
			"FROM " + TABLE_PURCHASE_ITEMS + " pi " +
			"INNER JOIN " + TABLE_ITEMS + " i " +
			"ON pi.item_id = i.id " +
			"WHERE pi.purchase_id=?",

			new String[]{String.valueOf(purchaseId)}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
                new HashMap<String, Object>();

			map.put("code", cursor.getString(0));
			map.put("name", cursor.getString(1));
			map.put("quantity", cursor.getDouble(2));
			map.put("purchase_price", cursor.getDouble(3));
			map.put("total", cursor.getDouble(4));

			list.add(map);
		}

		cursor.close();

		return list;
	}
	// =====================
// DELETE PURCHASE
// =====================

	public boolean deletePurchase(int purchaseId) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			// This purchase's line items added stock when they were
			// inserted - reverse that before the rows disappear, or the
			// item balances would be left stranded with stock that no
			// longer has a purchase behind it.
			Cursor qtyCursor = db.rawQuery(
				"SELECT item_id, SUM(quantity) FROM " +
				TABLE_PURCHASE_ITEMS +
				" WHERE purchase_id=? GROUP BY item_id",
				new String[]{String.valueOf(purchaseId)}
			);

			while (qtyCursor.moveToNext()) {

				adjustItemBalance(
					db,
					qtyCursor.getInt(0),
					-qtyCursor.getDouble(1)
				);
			}

			qtyCursor.close();

			// Same reversal, but per variety combo (skipping lines with no
			// combo_id - an item with no variety groups never got one).
			Cursor comboQtyCursor = db.rawQuery(
				"SELECT combo_id, SUM(quantity) FROM " +
				TABLE_PURCHASE_ITEMS +
				" WHERE purchase_id=? AND combo_id IS NOT NULL" +
				" GROUP BY combo_id",
				new String[]{String.valueOf(purchaseId)}
			);

			while (comboQtyCursor.moveToNext()) {

				adjustComboBalance(
					db,
					comboQtyCursor.getInt(0),
					-comboQtyCursor.getDouble(1)
				);
			}

			comboQtyCursor.close();

			db.delete(
                TABLE_PURCHASE_ITEMS,
                "purchase_id=?",
                new String[]{String.valueOf(purchaseId)}
			);

			int rows = db.delete(
                TABLE_PURCHASES,
                "id=?",
                new String[]{String.valueOf(purchaseId)}
			);

			db.setTransactionSuccessful();

			return rows > 0;

		} finally {

			db.endTransaction();
		}
	}
	// =====================
// GET RANDOM PARTY
// =====================

	public HashMap<String, Object> getRandomParty() {

		ArrayList<HashMap<String, Object>> parties = getParties();

		if (parties.size() == 0) {
			return null;
		}

		int index = new java.util.Random().nextInt(parties.size());

		return parties.get(index);
	}

// =====================
// GET RANDOM ITEM
// =====================

	public HashMap<String, Object> getRandomItem() {

		ArrayList<HashMap<String, Object>> items = getItemsForSpinner();

		if (items.size() == 0) {
			return null;
		}

		int index = new java.util.Random().nextInt(items.size());

		return items.get(index);
	}
	// =====================
// UPDATE PURCHASE TOTALS
// =====================

	public boolean updatePurchaseTotals(
        long purchaseId,
        double grandTotal,
        double amountPaid) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("grand_total", grandTotal);
		values.put("amount_paid", amountPaid);

		int rows = db.update(
            TABLE_PURCHASES,
            values,
            "id=?",
            new String[]{String.valueOf(purchaseId)}
		);


		return rows > 0;
	}

	// =====================
// DELETE ALL PURCHASES
// =====================

	public void deleteAllPurchases() {

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			// Every purchase currently in the table shifted its party's
			// balance by -(grand_total - amount_paid) when it was added.
			// Reverse that per party before wiping the rows, or every
			// party's balance would be left stranded with adjustments
			// for purchases that no longer exist.
			Cursor dueCursor = db.rawQuery(
				"SELECT party_id, SUM(grand_total - amount_paid) " +
				"FROM " + TABLE_PURCHASES +
				" GROUP BY party_id",
				null
			);

			while (dueCursor.moveToNext()) {

				adjustPartyBalance(
					db,
					dueCursor.getInt(0),
					dueCursor.getDouble(1)
				);
			}

			dueCursor.close();

			// Same idea as the party balances above, but for stock: every
			// purchase line currently in the table added its quantity to
			// the item's balance when it was inserted.
			Cursor stockCursor = db.rawQuery(
				"SELECT item_id, SUM(quantity) FROM " +
				TABLE_PURCHASE_ITEMS +
				" GROUP BY item_id",
				null
			);

			while (stockCursor.moveToNext()) {

				adjustItemBalance(
					db,
					stockCursor.getInt(0),
					-stockCursor.getDouble(1)
				);
			}

			stockCursor.close();

			// Same idea, but per variety combo.
			Cursor comboStockCursor = db.rawQuery(
				"SELECT combo_id, SUM(quantity) FROM " +
				TABLE_PURCHASE_ITEMS +
				" WHERE combo_id IS NOT NULL GROUP BY combo_id",
				null
			);

			while (comboStockCursor.moveToNext()) {

				adjustComboBalance(
					db,
					comboStockCursor.getInt(0),
					-comboStockCursor.getDouble(1)
				);
			}

			comboStockCursor.close();

			db.delete(TABLE_PURCHASE_ITEMS, null, null);

			db.delete(TABLE_PURCHASES, null, null);

			// See deleteAllItems() - the import log must be cleared too,
			// or a re-import of the same purchases gets skipped as
			// "duplicate".
			db.delete(TABLE_IMPORT_LOG, null, null);

			db.setTransactionSuccessful();

		} finally {

			db.endTransaction();
		}
	}

	// =====================
// GET PURCHASE ITEMS FOR EDIT
// =====================

	public ArrayList<HashMap<String, Object>> getPurchaseItemsForEdit(int purchaseId) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT pi.item_id, i.code, i.name, " +
			"pi.quantity, pi.purchase_price, pi.total, pi.combo_id " +
			"FROM " + TABLE_PURCHASE_ITEMS + " pi " +
			"INNER JOIN " + TABLE_ITEMS + " i " +
			"ON pi.item_id = i.id " +
			"WHERE pi.purchase_id=?",

			new String[]{String.valueOf(purchaseId)}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("item_id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("name", cursor.getString(2));
			map.put("quantity", cursor.getDouble(3));
			map.put("purchase_price", cursor.getDouble(4));
			map.put("total", cursor.getDouble(5));

			if (!cursor.isNull(6)) {
				map.put("combo_id", cursor.getInt(6));
			}

			list.add(map);
		}

		cursor.close();

		return list;
	}

	public void deletePurchaseItems(int purchaseId) {

		SQLiteDatabase db = this.getWritableDatabase();

		// Editing a purchase clears its old line items and re-inserts
		// the new ones, so reverse the stock the old lines added before
		// they're removed - insertPurchaseItem() will add the new
		// quantities back afterward.
		Cursor qtyCursor = db.rawQuery(
			"SELECT item_id, SUM(quantity) FROM " +
			TABLE_PURCHASE_ITEMS +
			" WHERE purchase_id=? GROUP BY item_id",
			new String[]{String.valueOf(purchaseId)}
		);

		while (qtyCursor.moveToNext()) {

			adjustItemBalance(
				db,
				qtyCursor.getInt(0),
				-qtyCursor.getDouble(1)
			);
		}

		qtyCursor.close();

		// Same reversal, but per variety combo.
		Cursor comboQtyCursor = db.rawQuery(
			"SELECT combo_id, SUM(quantity) FROM " +
			TABLE_PURCHASE_ITEMS +
			" WHERE purchase_id=? AND combo_id IS NOT NULL" +
			" GROUP BY combo_id",
			new String[]{String.valueOf(purchaseId)}
		);

		while (comboQtyCursor.moveToNext()) {

			adjustComboBalance(
				db,
				comboQtyCursor.getInt(0),
				-comboQtyCursor.getDouble(1)
			);
		}

		comboQtyCursor.close();

		db.delete(
			TABLE_PURCHASE_ITEMS,
			"purchase_id=?",
			new String[]{
				String.valueOf(purchaseId)
			}
		);

	}
	public boolean updatePurchase(
        int purchaseId,
        int partyId,
        String date,
        String time,
        String invoiceNumber,
        double grandTotal,
        double amountPaid,
        String notes) {

		SQLiteDatabase db = this.getWritableDatabase();

		// Reverse this purchase's previous effect on its old party's
		// balance before applying the new one below - otherwise editing
		// a purchase (amount, paid amount, or even the party itself)
		// would double up or strand the earlier adjustment.
		Cursor oldCursor = db.rawQuery(
			"SELECT party_id, grand_total, amount_paid FROM " +
			TABLE_PURCHASES + " WHERE id=?",
			new String[]{String.valueOf(purchaseId)}
		);

		if (oldCursor.moveToFirst()) {

			int oldPartyId = oldCursor.getInt(0);
			double oldDue = oldCursor.getDouble(1) - oldCursor.getDouble(2);

			adjustPartyBalance(db, oldPartyId, oldDue);
		}

		oldCursor.close();

		ContentValues values = new ContentValues();

		values.put("party_id", partyId);
		values.put("date", date);
		values.put("time", time);
		values.put("invoice_number", invoiceNumber);
		values.put("grand_total", grandTotal);
		values.put("amount_paid", amountPaid);
		values.put("notes", notes);
		values.put("source", "Manual");

		int rows = db.update(
            TABLE_PURCHASES,
            values,
            "id=?",
            new String[]{
				String.valueOf(purchaseId)
            }
		);

		adjustPartyBalance(db, partyId, -(grandTotal - amountPaid));


		return rows > 0;
	}

	// =====================
// GET PURCHASES BY DATE RANGE
// =====================

	public ArrayList<HashMap<String, Object>> getPurchases(
        String fromDate,
        String toDate) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		String sql =
			"SELECT p.id, pa.name, p.date, " +
			"p.invoice_number, p.grand_total " +
			"FROM " + TABLE_PURCHASES + " p " +
			"INNER JOIN " + TABLE_PARTIES + " pa " +
			"ON p.party_id = pa.id ";

		ArrayList<String> args = new ArrayList<>();

		if (fromDate != null && toDate != null) {

			sql += "WHERE p.date BETWEEN ? AND ? ";

			args.add(fromDate);
			args.add(toDate);
		}

		sql += "ORDER BY p.id DESC";

		Cursor cursor = db.rawQuery(
			sql,
			args.toArray(new String[0])
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("party_name", cursor.getString(1));
			map.put("date", cursor.getString(2));
			map.put("invoice_no", cursor.getString(3));
			map.put("grand_total", cursor.getDouble(4));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// GET PARTY BY NAME
	public int getPartyIdByName(String name) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_PARTIES + " WHERE name=?",
			new String[]{name}
		);

		int id = -1;

		if (cursor.moveToFirst()) {
			id = cursor.getInt(0);
		}

		cursor.close();

		return id;
	}

	public long insertSale(HashMap<String, Object> saleData) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("invoice_no", saleData.get("invoice_no").toString());
		values.put("date", saleData.get("date").toString());
		values.put("time", saleData.get("time").toString());
		values.put("party_id", saleData.get("party_id").toString());
		values.put("subtotal", saleData.get("subtotal").toString());
		values.put("discount", saleData.get("discount").toString());
		values.put("other_charges", saleData.get("other_charges").toString());
		values.put("grand_total", saleData.get("grand_total").toString());
		values.put("paid_amount", saleData.get("paid_amount").toString());
		values.put("balance", saleData.get("balance").toString());
		values.put("notes", saleData.get("notes").toString());
		values.put("source", "Manual");

		long id = db.insert("sales", null, values);

		int partyId = Integer.parseInt(saleData.get("party_id").toString());
		double due = Double.parseDouble(saleData.get("balance").toString());

		// Any unpaid portion of this sale is money the party now owes
		// us, so it's added to their balance.
		adjustPartyBalance(db, partyId, due);

		return id;
	}

	public long insertSaleItem(HashMap<String, Object> itemData) {
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("sale_id", itemData.get("sale_id").toString());
		values.put("item_id", itemData.get("item_id").toString());
		values.put("qty", itemData.get("qty").toString());
		values.put("rate", itemData.get("rate").toString());
		values.put("amount", itemData.get("amount").toString());

		// Optional - null for an item with no variety groups, otherwise
		// the specific variety combination this sale line is taking stock
		// from.
		Integer comboId = itemData.get("combo_id") == null
			? null
			: Integer.valueOf(itemData.get("combo_id").toString());

		values.put("combo_id", comboId);

		long id = db.insert("sale_items", null, values);

		// A sale takes stock out.
		int itemId = Integer.parseInt(itemData.get("item_id").toString());
		double qty = Double.parseDouble(itemData.get("qty").toString());

		adjustItemBalance(db, itemId, -qty);
		adjustComboBalance(db, comboId, -qty);

		return id;
	}

	public ArrayList<HashMap<String, Object>> getSales() {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT s.id, s.invoice_no, p.name, s.date, s.grand_total " +
			"FROM sales s " +
			"LEFT JOIN parties p " +
			"ON s.party_id = p.id " +
			"ORDER BY s.id DESC",

			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("party_name", cursor.getString(2));
			map.put("date", cursor.getString(3));
			map.put("grand_total", cursor.getDouble(4));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	public ArrayList<HashMap<String, Object>> getSales(
		String fromDate,
		String toDate) {

		if (fromDate == null || toDate == null) {
			return getSales();
		}

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT s.id, s.invoice_no, p.name, s.date, s.grand_total " +
			"FROM sales s " +
			"LEFT JOIN parties p " +
			"ON s.party_id = p.id " +
			"WHERE s.date BETWEEN ? AND ? " +
			"ORDER BY s.id DESC",

			new String[]{
				fromDate,
				toDate
			}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("party_name", cursor.getString(2));
			map.put("date", cursor.getString(3));
			map.put("grand_total", cursor.getDouble(4));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
	// REPORTS: SALES SUMMARY FOR A DATE RANGE
	// =====================

	public HashMap<String, Object> getSalesSummary(String fromDate, String toDate) {

		HashMap<String, Object> map = new HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT COUNT(*), COALESCE(SUM(grand_total), 0) " +
			"FROM sales " +
			"WHERE date BETWEEN ? AND ?",

			new String[]{
				fromDate,
				toDate
			}
		);

		int count = 0;
		double total = 0;

		if (cursor.moveToFirst()) {

			count = cursor.getInt(0);
			total = cursor.getDouble(1);
		}

		cursor.close();

		map.put("count", count);
		map.put("total", total);

		return map;
	}

	// =====================
	// REPORTS: SALES BY PARTY FOR A DATE RANGE
	// =====================

	public static final int SORT_AMOUNT_DESC = 0;
	public static final int SORT_AMOUNT_ASC = 1;
	public static final int SORT_NAME_ASC = 2;
	public static final int SORT_COUNT_DESC = 3;

	public ArrayList<HashMap<String, Object>> getSalesByParty(
		String fromDate,
		String toDate,
		int sortBy) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		String orderBy;

		switch (sortBy) {

			case SORT_AMOUNT_ASC:
				orderBy = "total ASC";
				break;

			case SORT_NAME_ASC:
				orderBy = "p.name ASC";
				break;

			case SORT_COUNT_DESC:
				orderBy = "cnt DESC";
				break;

			case SORT_AMOUNT_DESC:
			default:
				orderBy = "total DESC";
				break;
		}

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT p.id, p.name, COUNT(s.id) AS cnt, " +
			"COALESCE(SUM(s.grand_total), 0) AS total " +
			"FROM " + TABLE_PARTIES + " p " +
			"INNER JOIN sales s " +
			"ON s.party_id = p.id " +
			"WHERE s.date BETWEEN ? AND ? " +
			"GROUP BY p.id, p.name " +
			"ORDER BY " + orderBy,

			new String[]{
				fromDate,
				toDate
			}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("party_id", cursor.getInt(0));
			map.put("party_name", cursor.getString(1));
			map.put("count", cursor.getInt(2));
			map.put("total", cursor.getDouble(3));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
	// REPORTS: INDEX RANKING (WEEK + MONTH + QUARTER + 6 MONTH +
	// 9 MONTH + YEAR + ALL TIME) FOR ITEMS AND PARTIES
	//
	// For each item/party this first ranks it against every other
	// item/party separately within each of the seven time windows
	// (rank 1 = highest sales total in that window). Those seven
	// per-window ranks are then added together into one "overall
	// index" number, e.g. week(7) + month(13) + quarter(27) +
	// sixMonth(16) + nineMonth(32) + year(24) + allTime(20) = 139.
	// A LOWER index means a party/item ranked well (near the top)
	// across more windows, so the list is sorted with the lowest
	// index first by default.
	// =====================

	public static final int RANK_SORT_COMBINED_DESC = 0; // best (lowest index) first
	public static final int RANK_SORT_COMBINED_ASC = 1;  // worst (highest index) first
	public static final int RANK_SORT_NAME_ASC = 2;

	// weekStart/monthStart/quarterStart/sixMonthStart/nineMonthStart/
	// yearStart are all in "yyyy-MM-dd" format, are rolling windows
	// ending today (e.g. monthStart = one month back from today), and
	// are inclusive lower bounds.
	public ArrayList<HashMap<String, Object>> getItemSalesRanking(
		String weekStart,
		String monthStart,
		String quarterStart,
		String sixMonthStart,
		String nineMonthStart,
		String yearStart,
		int sortBy) {

		return getSalesRanking(
			TABLE_ITEMS,
			"sale_items",
			"item_id",
			"amount",
			"item_name",
			weekStart,
			monthStart,
			quarterStart,
			sixMonthStart,
			nineMonthStart,
			yearStart,
			sortBy
		);
	}

	public ArrayList<HashMap<String, Object>> getPartySalesRanking(
		String weekStart,
		String monthStart,
		String quarterStart,
		String sixMonthStart,
		String nineMonthStart,
		String yearStart,
		int sortBy) {

		return getSalesRanking(
			TABLE_PARTIES,
			"sales",
			"party_id",
			"grand_total",
			"party_name",
			weekStart,
			monthStart,
			quarterStart,
			sixMonthStart,
			nineMonthStart,
			yearStart,
			sortBy
		);
	}

	// The seven period keys used both for the SQL column aliases and
	// for the row map keys (each also gets a "<key>_rank" entry).
	private static final String[] RANK_PERIOD_KEYS = {
		"week", "month", "quarter", "sixmonth", "ninemonth", "year", "alltime"
	};

	// Shared worker behind getItemSalesRanking/getPartySalesRanking:
	// entityTable is "items" or "parties", salesTable is "sale_items"
	// (joined through "sales" for its date) or "sales" itself,
	// fkColumn is the column on salesTable pointing back at the
	// entity, amountColumn is what gets summed, and nameKey is the
	// map key the result row's display name is stored under.
	private ArrayList<HashMap<String, Object>> getSalesRanking(
		String entityTable,
		String salesTable,
		String fkColumn,
		String amountColumn,
		String nameKey,
		String weekStart,
		String monthStart,
		String quarterStart,
		String sixMonthStart,
		String nineMonthStart,
		String yearStart,
		int sortBy) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		// sale_items has no date of its own, so it needs joining
		// through sales; the sales table already has one.
		String dateJoin = salesTable.equals("sale_items") ?
			"INNER JOIN sales sl ON sl.id = st.sale_id " :
			"";

		String dateColumn = salesTable.equals("sale_items") ?
			"sl.date" :
			"st.date";

		String sql =
			"SELECT e.id AS entity_id, e.name AS entity_name, " +
			"COALESCE(SUM(CASE WHEN " + dateColumn + " >= ? THEN st." + amountColumn + " ELSE 0 END), 0) AS week, " +
			"COALESCE(SUM(CASE WHEN " + dateColumn + " >= ? THEN st." + amountColumn + " ELSE 0 END), 0) AS month, " +
			"COALESCE(SUM(CASE WHEN " + dateColumn + " >= ? THEN st." + amountColumn + " ELSE 0 END), 0) AS quarter, " +
			"COALESCE(SUM(CASE WHEN " + dateColumn + " >= ? THEN st." + amountColumn + " ELSE 0 END), 0) AS sixmonth, " +
			"COALESCE(SUM(CASE WHEN " + dateColumn + " >= ? THEN st." + amountColumn + " ELSE 0 END), 0) AS ninemonth, " +
			"COALESCE(SUM(CASE WHEN " + dateColumn + " >= ? THEN st." + amountColumn + " ELSE 0 END), 0) AS year, " +
			"COALESCE(SUM(st." + amountColumn + "), 0) AS alltime " +
			"FROM " + entityTable + " e " +
			"INNER JOIN " + salesTable + " st ON st." + fkColumn + " = e.id " +
			dateJoin +
			"GROUP BY e.id, e.name";

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			sql,
			new String[]{
				weekStart,
				monthStart,
				quarterStart,
				sixMonthStart,
				nineMonthStart,
				yearStart
			}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<>();

			row.put("entity_id", cursor.getInt(0));
			row.put(nameKey, cursor.getString(1));
			row.put("week", cursor.getDouble(2));
			row.put("month", cursor.getDouble(3));
			row.put("quarter", cursor.getDouble(4));
			row.put("sixmonth", cursor.getDouble(5));
			row.put("ninemonth", cursor.getDouble(6));
			row.put("year", cursor.getDouble(7));
			row.put("alltime", cursor.getDouble(8));

			list.add(row);
		}

		cursor.close();

		int n = list.size();

		// Rank every entity within each of the seven windows
		// separately (rank 1 = highest sales in that window), then
		// add the seven per-window ranks into one overall index.
		int[] overallIndex = new int[n];

		for (String key : RANK_PERIOD_KEYS) {

			double[] values = new double[n];

			for (int i = 0; i < n; i++) {
				values[i] = (Double) list.get(i).get(key);
			}

			int[] ranks = computeDescendingRanks(values);

			for (int i = 0; i < n; i++) {
				list.get(i).put(key + "_rank", ranks[i]);
				overallIndex[i] += ranks[i];
			}
		}

		for (int i = 0; i < n; i++) {
			list.get(i).put("overall_index", overallIndex[i]);
		}

		java.util.Comparator<HashMap<String, Object>> comparator;

		switch (sortBy) {

			case RANK_SORT_COMBINED_ASC:
				// Worst (highest index) first.
				comparator = new java.util.Comparator<HashMap<String, Object>>() {
					@Override
					public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
						return ((Integer) b.get("overall_index"))
							.compareTo((Integer) a.get("overall_index"));
					}
				};
				break;

			case RANK_SORT_NAME_ASC:
				final String nameKeyForSort = nameKey;
				comparator = new java.util.Comparator<HashMap<String, Object>>() {
					@Override
					public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
						String nameA = a.get(nameKeyForSort) == null ? "" : a.get(nameKeyForSort).toString();
						String nameB = b.get(nameKeyForSort) == null ? "" : b.get(nameKeyForSort).toString();
						return nameA.compareToIgnoreCase(nameB);
					}
				};
				break;

			case RANK_SORT_COMBINED_DESC:
			default:
				// Best (lowest index) first.
				comparator = new java.util.Comparator<HashMap<String, Object>>() {
					@Override
					public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
						return ((Integer) a.get("overall_index"))
							.compareTo((Integer) b.get("overall_index"));
					}
				};
				break;
		}

		java.util.Collections.sort(list, comparator);

		return list;
	}

	// Standard "competition ranking" (1, 2, 2, 4, ...): entities tied
	// on value share the same rank, and the next distinct value skips
	// ahead by the number of ties. Highest value gets rank 1.
	private int[] computeDescendingRanks(double[] values) {

		int n = values.length;

		int[] order = new int[n];

		for (int i = 0; i < n; i++) {
			order[i] = i;
		}

		// Plain selection sort over primitive ints (report-sized lists,
		// so O(n^2) is fine) - avoids Integer[]/Comparator<Integer>
		// boxing, which this project's build toolchain doesn't handle.
		for (int i = 0; i < n - 1; i++) {

			int bestIndex = i;

			for (int j = i + 1; j < n; j++) {

				if (values[order[j]] > values[order[bestIndex]]) {
					bestIndex = j;
				}
			}

			if (bestIndex != i) {
				int temp = order[i];
				order[i] = order[bestIndex];
				order[bestIndex] = temp;
			}
		}

		int[] ranks = new int[n];
		int currentRank = 0;

		for (int i = 0; i < n; i++) {

			if (i == 0 || values[order[i]] != values[order[i - 1]]) {
				currentRank = i + 1;
			}

			ranks[order[i]] = currentRank;
		}

		return ranks;
	}

	public HashMap<String, Object> getSaleById(String saleId) {

		HashMap<String, Object> map =
			new HashMap<String, Object>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT s.*, p.name AS party_name " +
			"FROM sales s " +
			"LEFT JOIN parties p ON s.party_id = p.id " +
			"WHERE s.id = ?",

			new String[]{saleId}
		);

		if (cursor.moveToFirst()) {

			for (int i = 0; i < cursor.getColumnCount(); i++) {

				map.put(
					cursor.getColumnName(i),
					cursor.getString(i)
				);
			}
		}

		cursor.close();

		return map;
	}

	public ArrayList<HashMap<String, Object>> getSaleItems(String saleId) {
		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT * FROM sale_items WHERE sale_id = ?",
			new String[]{saleId}
		);

		while (cursor.moveToNext()) {
			HashMap<String, Object> map = new HashMap<>();

			for (int i = 0; i < cursor.getColumnCount(); i++) {
				map.put(cursor.getColumnName(i), cursor.getString(i));
			}

			list.add(map);
		}

		cursor.close();

		return list;
	}

	public void deleteSaleItems(String saleId) {
		SQLiteDatabase db = this.getWritableDatabase();

		// This sale's line items took stock out when they were inserted -
		// put it back before the rows disappear (whether the sale itself
		// is being deleted, or is about to be re-saved with new lines).
		Cursor qtyCursor = db.rawQuery(
			"SELECT item_id, SUM(qty) FROM sale_items WHERE sale_id=? " +
			"GROUP BY item_id",
			new String[]{saleId}
		);

		while (qtyCursor.moveToNext()) {

			adjustItemBalance(
				db,
				qtyCursor.getInt(0),
				qtyCursor.getDouble(1)
			);
		}

		qtyCursor.close();

		// Same reversal, but per variety combo.
		Cursor comboQtyCursor = db.rawQuery(
			"SELECT combo_id, SUM(qty) FROM sale_items WHERE sale_id=? " +
			"AND combo_id IS NOT NULL GROUP BY combo_id",
			new String[]{saleId}
		);

		while (comboQtyCursor.moveToNext()) {

			adjustComboBalance(
				db,
				comboQtyCursor.getInt(0),
				comboQtyCursor.getDouble(1)
			);
		}

		comboQtyCursor.close();

		db.delete(
			"sale_items",
			"sale_id=?",
			new String[]{saleId}
		);
	}

	public void deleteSale(String saleId) {
		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(
			"sales",
			"id=?",
			new String[]{saleId}
		);
	}

	public int updateSale(String saleId, HashMap<String, Object> saleData) {
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("invoice_no", saleData.get("invoice_no").toString());
		values.put("date", saleData.get("date").toString());
		values.put("time", saleData.get("time").toString());
		values.put("party_id", saleData.get("party_id").toString());
		values.put("subtotal", saleData.get("subtotal").toString());
		values.put("discount", saleData.get("discount").toString());
		values.put("other_charges", saleData.get("other_charges").toString());
		values.put("grand_total", saleData.get("grand_total").toString());
		values.put("paid_amount", saleData.get("paid_amount").toString());
		values.put("balance", saleData.get("balance").toString());
		values.put("notes", saleData.get("notes").toString());

		return db.update(
			"sales",
			values,
			"id=?",
			new String[]{saleId}
		);
	}


	// Sale invoice numbers auto-generate starting at this value (no
	// prefix) when there are no existing sales yet to count up from.
	public static final int DEFAULT_SALE_INVOICE_NO = 15000;

	public String getNextSaleInvoiceNo() {
		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT MAX(CAST(invoice_no AS INTEGER)) FROM sales",
			null
		);

		int nextNo = DEFAULT_SALE_INVOICE_NO;

		if (cursor.moveToFirst() && !cursor.isNull(0)) {
			nextNo = Math.max(cursor.getInt(0) + 1, DEFAULT_SALE_INVOICE_NO);
		}

		cursor.close();

		return String.valueOf(nextNo);
	}


	public int getSaleCount() {
		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM sales", null);

		int count = 0;

		if (cursor.moveToFirst()) {
			count = cursor.getInt(0);
		}

		cursor.close();

		return count;
	}


	public ArrayList<HashMap<String, Object>> getSaleItemsForEdit(String saleId) {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT " +
			"sale_items.item_id, " +
			"items.code, " +
			"items.name, " +
			"sale_items.qty, " +
			"sale_items.rate, " +
			"sale_items.amount, " +
			"sale_items.combo_id " +
			"FROM sale_items " +
			"INNER JOIN items " +
			"ON sale_items.item_id = items.id " +
			"WHERE sale_items.sale_id = ?",

			new String[]{saleId}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put("item_id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("name", cursor.getString(2));
			map.put("quantity", cursor.getDouble(3));
			map.put("sale_price", cursor.getDouble(4));
			map.put("total", cursor.getDouble(5));

			if (!cursor.isNull(6)) {
				map.put("combo_id", cursor.getInt(6));
			}

			list.add(map);
		}

		cursor.close();

		return list;
	}



	public void deleteAllSales() {

		SQLiteDatabase db = this.getWritableDatabase();

		// Every sale currently in the table shifted its party's balance
		// by +balance (its due amount) when it was added. Reverse that
		// per party before wiping the rows, or every party's balance
		// would be left stranded with adjustments for sales that no
		// longer exist.
		Cursor dueCursor = db.rawQuery(
			"SELECT party_id, SUM(balance) FROM sales GROUP BY party_id",
			null
		);

		while (dueCursor.moveToNext()) {

			adjustPartyBalance(
				db,
				dueCursor.getInt(0),
				-dueCursor.getDouble(1)
			);
		}

		dueCursor.close();

		// Same idea as the party balances above, but for stock: every
		// sale line currently in the table took its quantity off the
		// item's balance when it was inserted.
		Cursor stockCursor = db.rawQuery(
			"SELECT item_id, SUM(qty) FROM sale_items GROUP BY item_id",
			null
		);

		while (stockCursor.moveToNext()) {

			adjustItemBalance(
				db,
				stockCursor.getInt(0),
				stockCursor.getDouble(1)
			);
		}

		stockCursor.close();

		// Same idea, but per variety combo.
		Cursor comboStockCursor = db.rawQuery(
			"SELECT combo_id, SUM(qty) FROM sale_items " +
			"WHERE combo_id IS NOT NULL GROUP BY combo_id",
			null
		);

		while (comboStockCursor.moveToNext()) {

			adjustComboBalance(
				db,
				comboStockCursor.getInt(0),
				comboStockCursor.getDouble(1)
			);
		}

		comboStockCursor.close();

		db.delete("sale_items", null, null);

		db.delete("sales", null, null);

		// See deleteAllItems() - the import log must be cleared too, or
		// a re-import of the same sales gets skipped as "duplicate".
		db.delete(TABLE_IMPORT_LOG, null, null);

	}




	public void deleteSaleItems(int saleId) {

		SQLiteDatabase db = this.getWritableDatabase();

		// Same idea as the String overload above - put back the stock
		// these lines took out before removing them.
		Cursor qtyCursor = db.rawQuery(
			"SELECT item_id, SUM(qty) FROM sale_items WHERE sale_id=? " +
			"GROUP BY item_id",
			new String[]{String.valueOf(saleId)}
		);

		while (qtyCursor.moveToNext()) {

			adjustItemBalance(
				db,
				qtyCursor.getInt(0),
				qtyCursor.getDouble(1)
			);
		}

		qtyCursor.close();

		// Same reversal, but per variety combo.
		Cursor comboQtyCursor = db.rawQuery(
			"SELECT combo_id, SUM(qty) FROM sale_items WHERE sale_id=? " +
			"AND combo_id IS NOT NULL GROUP BY combo_id",
			new String[]{String.valueOf(saleId)}
		);

		while (comboQtyCursor.moveToNext()) {

			adjustComboBalance(
				db,
				comboQtyCursor.getInt(0),
				comboQtyCursor.getDouble(1)
			);
		}

		comboQtyCursor.close();

		db.delete(
			"sale_items",
			"sale_id=?",
			new String[]{String.valueOf(saleId)}
		);

	}


	public boolean updateSale(
		int saleId,
		HashMap<String, Object> saleData) {

		SQLiteDatabase db = this.getWritableDatabase();

		// Reverse this sale's previous effect on its old party's balance
		// before applying the new one below - otherwise editing a sale
		// (amount, paid amount, or even the party itself) would double
		// up or strand the earlier adjustment.
		Cursor oldCursor = db.rawQuery(
			"SELECT party_id, balance FROM sales WHERE id=?",
			new String[]{String.valueOf(saleId)}
		);

		if (oldCursor.moveToFirst()) {

			int oldPartyId = oldCursor.getInt(0);
			double oldDue = oldCursor.getDouble(1);

			adjustPartyBalance(db, oldPartyId, -oldDue);
		}

		oldCursor.close();

		ContentValues values = new ContentValues();

		values.put("invoice_no", saleData.get("invoice_no").toString());
		values.put("date", saleData.get("date").toString());
		values.put("party_id", saleData.get("party_id").toString());
		values.put("subtotal", saleData.get("subtotal").toString());
		values.put("discount", saleData.get("discount").toString());
		values.put("other_charges", saleData.get("other_charges").toString());
		values.put("grand_total", saleData.get("grand_total").toString());
		values.put("paid_amount", saleData.get("paid_amount").toString());
		values.put("balance", saleData.get("balance").toString());
		values.put("notes", saleData.get("notes").toString());
		values.put("source", "Manual");

		int rows = db.update(
			"sales",
			values,
			"id=?",
			new String[]{String.valueOf(saleId)}
		);

		int newPartyId = Integer.parseInt(saleData.get("party_id").toString());
		double newDue = Double.parseDouble(saleData.get("balance").toString());

		adjustPartyBalance(db, newPartyId, newDue);


		return rows > 0;
	}



	// =====================
// GENERATE NEXT PAYMENT CODE
// =====================

	private String generateNextPaymentCode() {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT code FROM " + TABLE_PAYMENTS +
			" ORDER BY id DESC LIMIT 1",
			null
		);

		if (!cursor.moveToFirst()) {

			cursor.close();

			return "PAY00001";
		}

		String lastCode = cursor.getString(0);

		cursor.close();

		int number = Integer.parseInt(lastCode.substring(3));

		number++;

		return String.format("PAY%05d", number);
	}


	// =====================
// INSERT PAYMENT
// =====================

	public long insertPayment(
		int type,
		int partyId,
		String date,
		String time,
		double amount,
		String notes
	) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("code", generateNextPaymentCode());
		values.put("type", type);
		values.put("party_id", partyId);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("source", "Manual");

		long id = db.insert(
			TABLE_PAYMENTS,
			null,
			values
		);

		// A Payment In is money the party paid us, so it comes off what
		// they owe us. A Payment Out is money we paid the party, so it
		// comes off what we owe them.
		adjustPartyBalance(
			db,
			partyId,
			type == PAYMENT_IN ? -amount : amount
		);


		return id;
	}


	// =====================
// GET PAYMENTS
// =====================

	public ArrayList<HashMap<String, Object>> getPayments() {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT py.id, py.code, py.type, p.name, " +
			"py.date, py.amount " +
			"FROM " + TABLE_PAYMENTS + " py " +
			"INNER JOIN " + TABLE_PARTIES + " p " +
			"ON py.party_id = p.id " +
			"ORDER BY py.id DESC",

			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("type", cursor.getInt(2));
			map.put("party_name", cursor.getString(3));
			map.put("date", cursor.getString(4));
			map.put("amount", cursor.getDouble(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}


	// =====================
// GET PAYMENT BY ID
// =====================

	public HashMap<String, Object> getPaymentById(int paymentId) {

		HashMap<String, Object> map =
			new HashMap<String, Object>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT py.id, py.code, py.type, py.party_id, " +
			"p.name, py.date, py.time, py.amount, py.notes " +
			"FROM " + TABLE_PAYMENTS + " py " +
			"INNER JOIN " + TABLE_PARTIES + " p " +
			"ON py.party_id = p.id " +
			"WHERE py.id=?",

			new String[]{
				String.valueOf(paymentId)
			}
		);

		if (cursor.moveToFirst()) {

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("type", cursor.getInt(2));
			map.put("party_id", cursor.getInt(3));
			map.put("party_name", cursor.getString(4));
			map.put("date", cursor.getString(5));
			map.put("time", cursor.getString(6));
			map.put("amount", cursor.getDouble(7));
			map.put("notes", cursor.getString(8));
		}

		cursor.close();

		return map;
	}

	// =====================
// UPDATE PAYMENT
// =====================

	public boolean updatePayment(
		int paymentId,
		int type,
		int partyId,
		String date,
		String time,
		double amount,
		String notes
	) {

		SQLiteDatabase db = this.getWritableDatabase();

		// Reverse this payment's previous effect on its old party's
		// balance before applying the new one below - otherwise editing
		// a payment (amount, type, or even the party itself) would
		// double up or strand the earlier adjustment.
		Cursor oldCursor = db.rawQuery(
			"SELECT type, party_id, amount FROM " + TABLE_PAYMENTS +
			" WHERE id=?",
			new String[]{String.valueOf(paymentId)}
		);

		if (oldCursor.moveToFirst()) {

			int oldType = oldCursor.getInt(0);
			int oldPartyId = oldCursor.getInt(1);
			double oldAmount = oldCursor.getDouble(2);

			adjustPartyBalance(
				db,
				oldPartyId,
				oldType == PAYMENT_IN ? oldAmount : -oldAmount
			);
		}

		oldCursor.close();

		ContentValues values = new ContentValues();

		values.put("type", type);
		values.put("party_id", partyId);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("source", "Manual");

		int rows = db.update(
			TABLE_PAYMENTS,
			values,
			"id=?",
			new String[]{
				String.valueOf(paymentId)
			}
		);

		adjustPartyBalance(
			db,
			partyId,
			type == PAYMENT_IN ? -amount : amount
		);


		return rows > 0;
	}


	// =====================
// DELETE PAYMENT
// =====================

	public boolean deletePayment(int paymentId) {

		SQLiteDatabase db = this.getWritableDatabase();

		// Undo this payment's effect on its party's balance before
		// removing the row, or the balance would be left stranded with
		// an adjustment for a payment that no longer exists.
		Cursor oldCursor = db.rawQuery(
			"SELECT type, party_id, amount FROM " + TABLE_PAYMENTS +
			" WHERE id=?",
			new String[]{String.valueOf(paymentId)}
		);

		if (oldCursor.moveToFirst()) {

			int oldType = oldCursor.getInt(0);
			int oldPartyId = oldCursor.getInt(1);
			double oldAmount = oldCursor.getDouble(2);

			adjustPartyBalance(
				db,
				oldPartyId,
				oldType == PAYMENT_IN ? oldAmount : -oldAmount
			);
		}

		oldCursor.close();

		int rows = db.delete(
			TABLE_PAYMENTS,
			"id=?",
			new String[]{
				String.valueOf(paymentId)
			}
		);


		return rows > 0;
	}


	// =====================
// DELETE ALL PAYMENTS
// =====================

	public void deleteAllPayments() {

		SQLiteDatabase db = this.getWritableDatabase();

		// Every payment currently in the table shifted its party's
		// balance (Payment In by -amount, Payment Out by +amount) when
		// it was added. Reverse that per party before wiping the rows,
		// or every party's balance would be left stranded with
		// adjustments for payments that no longer exist.
		Cursor sumCursor = db.rawQuery(
			"SELECT party_id, " +
			"SUM(CASE WHEN type = " + PAYMENT_IN + " THEN amount ELSE -amount END) " +
			"FROM " + TABLE_PAYMENTS +
			" GROUP BY party_id",
			null
		);

		while (sumCursor.moveToNext()) {

			adjustPartyBalance(
				db,
				sumCursor.getInt(0),
				sumCursor.getDouble(1)
			);
		}

		sumCursor.close();

		db.delete(
			TABLE_PAYMENTS,
			null,
			null
		);

	}


	// =====================
// GET PAYMENTS BY DATE
// =====================

	public ArrayList<HashMap<String, Object>> getPayments(
		String fromDate,
		String toDate
	) {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT py.id, py.code, py.type, p.name, " +
			"py.date, py.amount " +
			"FROM " + TABLE_PAYMENTS + " py " +
			"INNER JOIN " + TABLE_PARTIES + " p " +
			"ON py.party_id = p.id " +
			"WHERE py.date BETWEEN ? AND ? " +
			"ORDER BY py.id DESC",

			new String[]{
				fromDate,
				toDate
			}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("type", cursor.getInt(2));
			map.put("party_name", cursor.getString(3));
			map.put("date", cursor.getString(4));
			map.put("amount", cursor.getDouble(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
// PAYMENT CODE EXISTS
// =====================

	public boolean paymentCodeExists(String code) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_PAYMENTS + " WHERE code=?",
			new String[]{code}
		);

		boolean exists = cursor.moveToFirst();

		cursor.close();

		return exists;
	}

	// =====================
// GET NEXT PAYMENT CODE
// =====================

	public String getNextPaymentCode() {
		return generateNextPaymentCode();
	}


	// =====================
// GET RANDOM PAYMENT
// =====================

	public HashMap<String, Object> getRandomPayment() {

		ArrayList<HashMap<String, Object>> payments =
			getPayments();

		if (payments.size() == 0) {
			return null;
		}

		int index =
			new java.util.Random().nextInt(payments.size());

		return payments.get(index);
	}


	// =====================
// GET PAYMENTS COUNT
// =====================

	public int getPaymentsCount() {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT COUNT(*) FROM " + TABLE_PAYMENTS,
			null
		);

		int count = 0;

		if (cursor.moveToFirst()) {
			count = cursor.getInt(0);
		}

		cursor.close();

		return count;
	}

	// =====================
// SEARCH PAYMENTS
// =====================

	public ArrayList<HashMap<String, Object>> searchPayments(
		String keyword
	) {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		// Split the typed text into words and require each one to
		// appear somewhere across code/party/date - in any order, not
		// necessarily next to each other - the same "every word,
		// anywhere" matching used by the in-memory list filters
		// (see SearchUtils).
		String[] tokens =
			keyword == null || keyword.trim().length() == 0 ?
			new String[0] :
			keyword.trim().split("\\s+");

		StringBuilder whereClause = new StringBuilder("1 = 1");

		ArrayList<String> args = new ArrayList<String>();

		for (String token : tokens) {

			if (token.length() == 0) {
				continue;
			}

			whereClause.append(
				" AND (py.code LIKE ? OR p.name LIKE ? OR py.date LIKE ?)"
			);

			String likeArg = "%" + token + "%";

			args.add(likeArg);
			args.add(likeArg);
			args.add(likeArg);
		}

		Cursor cursor = db.rawQuery(

			"SELECT py.id, py.code, py.type, p.name, " +
			"py.date, py.amount " +
			"FROM " + TABLE_PAYMENTS + " py " +
			"INNER JOIN " + TABLE_PARTIES + " p " +
			"ON py.party_id = p.id " +
			"WHERE " + whereClause.toString() + " " +
			"ORDER BY py.id DESC",

			args.toArray(new String[0])
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("type", cursor.getInt(2));
			map.put("party_name", cursor.getString(3));
			map.put("date", cursor.getString(4));
			map.put("amount", cursor.getDouble(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}



	// =====================
// GET PAYMENTS BY TYPE
// =====================

	public ArrayList<HashMap<String, Object>> getPaymentsByType(
		int type
	) {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db =
			this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT py.id, py.code, py.type, p.name, " +
			"py.date, py.amount " +
			"FROM " + TABLE_PAYMENTS + " py " +
			"INNER JOIN " + TABLE_PARTIES + " p " +
			"ON py.party_id = p.id " +
			"WHERE py.type=? " +
			"ORDER BY py.id DESC",

			new String[]{
				String.valueOf(type)
			}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put("id", cursor.getInt(0));
			map.put("code", cursor.getString(1));
			map.put("type", cursor.getInt(2));
			map.put("party_name", cursor.getString(3));
			map.put("date", cursor.getString(4));
			map.put("amount", cursor.getDouble(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
// INSERT EXPENSE
// =====================

	// Old 6-arg callers (any left) get the pre-cash/credit-toggle
	// behavior unchanged - fully paid, matching what every expense
	// implicitly was before that field existed.
	public long insertExpense(
		String item,
		String date,
		String time,
		double amount,
		String notes,
		Integer partyId
	) {

		return insertExpense(item, date, time, amount, amount, notes, partyId);
	}

	public long insertExpense(
		String item,
		String date,
		String time,
		double amount,
		double paidAmount,
		String notes,
		Integer partyId
	) {

		ContentValues values = new ContentValues();

		values.put("code", generateNextExpenseCode());
		values.put("item", item);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("paid_amount", paidAmount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("source", "Manual");

		SQLiteDatabase db = this.getWritableDatabase();

		long id = db.insert(
			TABLE_EXPENSES,
			null,
			values
		);


		return id;
	}

// =====================
// GET ALL EXPENSES
// =====================

	public ArrayList<HashMap<String, Object>> getExpenses() {

		ArrayList<HashMap<String, Object>> list =
			new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db =
			this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT * FROM " +
			TABLE_EXPENSES +
			" ORDER BY id DESC",

			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map =
				new HashMap<String, Object>();

			map.put(
				"id",
				cursor.getInt(
					cursor.getColumnIndexOrThrow("id")
				)
			);

			map.put(
				"code",
				cursor.getString(
					cursor.getColumnIndexOrThrow("code")
				)
			);

			map.put(
				"item",
				cursor.getString(
					cursor.getColumnIndexOrThrow("item")
				)
			);

			map.put(
				"date",
				cursor.getString(
					cursor.getColumnIndexOrThrow("date")
				)
			);

			map.put(
				"amount",
				cursor.getDouble(
					cursor.getColumnIndexOrThrow("amount")
				)
			);

			map.put(
				"paid_amount",
				cursor.getDouble(
					cursor.getColumnIndexOrThrow("paid_amount")
				)
			);

			int partyIdIndex = cursor.getColumnIndexOrThrow("party_id");

			if (!cursor.isNull(partyIdIndex)) {
				map.put("party_id", cursor.getInt(partyIdIndex));
			}

			list.add(map);
		}

		cursor.close();

		return list;
	}

// =====================
// GET EXPENSE BY ID
// =====================

	public HashMap<String, Object> getExpenseById(
		int expenseId
	) {

		HashMap<String, Object> map =
			new HashMap<String, Object>();

		SQLiteDatabase db =
			this.getReadableDatabase();

		Cursor cursor = db.rawQuery(

			"SELECT * FROM " +
			TABLE_EXPENSES +
			" WHERE id=?",

			new String[]{
				String.valueOf(expenseId)
			}
		);

		if (cursor.moveToFirst()) {

			map.put("id", cursor.getInt(
						cursor.getColumnIndexOrThrow("id")));

			map.put("code", cursor.getString(
						cursor.getColumnIndexOrThrow("code")));

			map.put("item", cursor.getString(
						cursor.getColumnIndexOrThrow("item")));

			map.put("date", cursor.getString(
						cursor.getColumnIndexOrThrow("date")));

			map.put("time", cursor.getString(
						cursor.getColumnIndexOrThrow("time")));

			map.put("amount", cursor.getDouble(
						cursor.getColumnIndexOrThrow("amount")));

			map.put("paid_amount", cursor.getDouble(
						cursor.getColumnIndexOrThrow("paid_amount")));

			map.put("notes", cursor.getString(
						cursor.getColumnIndexOrThrow("notes")));

			int partyIdIndex = cursor.getColumnIndexOrThrow("party_id");

			if (!cursor.isNull(partyIdIndex)) {
				map.put("party_id", cursor.getInt(partyIdIndex));
			}
		}

		cursor.close();

		return map;
	}



	// =====================
// UPDATE EXPENSE
// =====================

	// Old 7-arg callers (any left) keep the pre-cash/credit-toggle
	// behavior - fully paid.
	public boolean updateExpense(
		int expenseId,
		String item,
		String date,
		String time,
		double amount,
		String notes,
		Integer partyId
	) {

		return updateExpense(expenseId, item, date, time, amount, amount, notes, partyId);
	}

	public boolean updateExpense(
		int expenseId,
		String item,
		String date,
		String time,
		double amount,
		double paidAmount,
		String notes,
		Integer partyId
	) {

		ContentValues values = new ContentValues();

		values.put("item", item);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("paid_amount", paidAmount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("source", "Manual");

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.update(
			TABLE_EXPENSES,
			values,
			"id=?",
			new String[]{
				String.valueOf(expenseId)
			}
		);


		return rows > 0;
	}

// =====================
// DELETE EXPENSE
// =====================

	public boolean deleteExpense(
		int expenseId
	) {

		SQLiteDatabase db =
			this.getWritableDatabase();

		int rows = db.delete(
			TABLE_EXPENSES,
			"id=?",
			new String[]{
				String.valueOf(expenseId)
			}
		);


		return rows > 0;
	}

// =====================
// DELETE ALL EXPENSES
// =====================

	public void deleteAllExpenses() {

		SQLiteDatabase db =
			this.getWritableDatabase();

		db.delete(
			TABLE_EXPENSES,
			null,
			null
		);

	}

// =====================
// GENERATE DUMMY EXPENSES
// =====================

	public void generateDummyExpenses() {

		deleteAllExpenses();

		for (int i = 1; i <= 100; i++) {

			insertExpense(

				"Expense " + i,

				"2026-01-" +
				String.format(
					"%02d",
					((i - 1) % 28) + 1
				),

				"10:00",

				i * 100,

				"Dummy expense " + i,

				null
			);
		}
	}

	// =====================
// GENERATE NEXT EXPENSE CODE
// =====================

	private String generateNextExpenseCode() {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT code FROM " + TABLE_EXPENSES +
			" ORDER BY id DESC LIMIT 1",
			null
		);

		if (!cursor.moveToFirst()) {

			cursor.close();

			return "EXP00001";
		}

		String lastCode = cursor.getString(0);

		cursor.close();

		int number = Integer.parseInt(
			lastCode.substring(3)
		);

		number++;

		return String.format(
			"EXP%05d",
			number
		);
	}

// =====================
// GET NEXT EXPENSE CODE
// =====================

	public String getNextExpenseCode() {

		return generateNextExpenseCode();
	}


	public void beginTransaction() {

		transactionDb =
			getWritableDatabase();

		transactionDb.beginTransaction();
	}

	public void endTransaction(
		boolean success) {

		if (transactionDb == null) {

			return;
		}

		if (success) {

			transactionDb.setTransactionSuccessful();
		}

		transactionDb.endTransaction();

		transactionDb = null;
	}

	public SQLiteDatabase getMigrationDatabase() {

		if (transactionDb != null) {

			return transactionDb;
		}

		return getWritableDatabase();
	}


	public long insertPurchaseItemBulk(
		SQLiteDatabase db,
		long purchaseId,
		int itemId,
		double quantity,
		double purchasePrice,
		double total) {

		return insertPurchaseItemBulk(
			db, purchaseId, itemId, quantity, purchasePrice, total, null
		);
	}

	// comboId is null for an item with no variety groups.
	public long insertPurchaseItemBulk(
		SQLiteDatabase db,
		long purchaseId,
		int itemId,
		double quantity,
		double purchasePrice,
		double total,
		Integer comboId) {

		ContentValues values =
			new ContentValues();

		values.put(
			"purchase_id",
			purchaseId
		);

		values.put(
			"item_id",
			itemId
		);

		values.put(
			"quantity",
			quantity
		);

		values.put(
			"purchase_price",
			purchasePrice
		);

		values.put(
			"total",
			total
		);

		values.put(
			"combo_id",
			comboId
		);

		long id = db.insert(
			TABLE_PURCHASE_ITEMS,
			null,
			values
		);

		// A purchase brings stock in.
		adjustItemBalance(db, itemId, quantity);
		adjustComboBalance(db, comboId, quantity);

		return id;
	}

	public long insertSaleItemBulk(
		SQLiteDatabase db,
		HashMap<String, Object> itemData) {

		ContentValues values =
			new ContentValues();

		values.put(
			"sale_id",
			itemData.get("sale_id").toString()
		);

		values.put(
			"item_id",
			itemData.get("item_id").toString()
		);

		values.put(
			"qty",
			itemData.get("qty").toString()
		);

		values.put(
			"rate",
			itemData.get("rate").toString()
		);

		values.put(
			"amount",
			itemData.get("amount").toString()
		);

		// Optional - null for an item with no variety groups, otherwise
		// the specific variety combination this sale line is taking stock
		// from.
		Integer comboId = itemData.get("combo_id") == null
			? null
			: Integer.valueOf(itemData.get("combo_id").toString());

		values.put("combo_id", comboId);

		long id = db.insert(
			"sale_items",
			null,
			values
		);

		// A sale takes stock out.
		int itemId = Integer.parseInt(itemData.get("item_id").toString());
		double qty = Double.parseDouble(itemData.get("qty").toString());

		adjustItemBalance(db, itemId, -qty);
		adjustComboBalance(db, comboId, -qty);

		return id;
	}

	// =====================
	// EXCEL TRANSACTION IMPORT HELPERS
	// (all use a shared SQLiteDatabase from beginTransaction()/
	// getMigrationDatabase() so importing thousands of rows across
	// many files doesn't open/close a connection per row)
	// =====================

	public boolean isImportKeyUsedBulk(SQLiteDatabase db, String importKey) {

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_IMPORT_LOG + " WHERE import_key=?",
			new String[]{importKey}
		);

		boolean used = cursor.moveToFirst();

		cursor.close();

		return used;
	}

	public void markImportKeyUsedBulk(SQLiteDatabase db, String importKey) {

		ContentValues values = new ContentValues();
		values.put("import_key", importKey);

		db.insertWithOnConflict(
			TABLE_IMPORT_LOG,
			null,
			values,
			SQLiteDatabase.CONFLICT_IGNORE
		);
	}

	public int getOrCreatePartyIdBulk(SQLiteDatabase db, String name) {

		if (name == null || name.trim().length() == 0) {
			name = "Cash Sale";
		}

		name = name.trim();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_PARTIES + " WHERE name=?",
			new String[]{name}
		);

		if (cursor.moveToFirst()) {

			int id = cursor.getInt(0);
			cursor.close();
			return id;
		}

		cursor.close();

		ContentValues values = new ContentValues();
		values.put("name", name);

		long id = db.insertWithOnConflict(
			TABLE_PARTIES,
			null,
			values,
			SQLiteDatabase.CONFLICT_IGNORE
		);

		if (id != -1) {
			return (int) id;
		}

		// Someone else created the same party name in this transaction
		// (e.g. an earlier row in the same file) - look it up again.
		return getOrCreatePartyIdBulk(db, name);
	}

	// Finds an existing catalog item by code (preferred) or name, or
	// creates one if neither matches. Used when importing sale/purchase
	// line items from Excel, where the item may or may not already exist
	// in the items table. isPurchase only affects which price field a
	// newly-created item is seeded with.
	public int getOrCreateItemIdBulk(
		SQLiteDatabase db,
		String code,
		String name,
		double price,
		boolean isPurchase) {

		code = code == null ? "" : code.trim();
		name = name == null ? "" : name.trim();

		if (code.length() == 0 && name.length() == 0) {
			name = "Imported Item";
		}

		if (code.length() > 0) {

			Cursor cursor = db.rawQuery(
				"SELECT id FROM " + TABLE_ITEMS + " WHERE code=? LIMIT 1",
				new String[]{code}
			);

			if (cursor.moveToFirst()) {
				int id = cursor.getInt(0);
				cursor.close();
				return id;
			}

			cursor.close();
		}

		if (name.length() > 0) {

			Cursor cursor = db.rawQuery(
				"SELECT id FROM " + TABLE_ITEMS + " WHERE name=? LIMIT 1",
				new String[]{name}
			);

			if (cursor.moveToFirst()) {
				int id = cursor.getInt(0);
				cursor.close();
				return id;
			}

			cursor.close();
		}

		String newCode = code.length() > 0 ? code : generateNextItemCodeBulk(db);

		ContentValues values = new ContentValues();
		values.put("code", newCode);
		values.put("name", name.length() > 0 ? name : newCode);
		values.put("purchase_price", isPurchase ? price : 0.0);
		values.put("sale_price", isPurchase ? 0.0 : price);

		long id = db.insertWithOnConflict(
			TABLE_ITEMS,
			null,
			values,
			SQLiteDatabase.CONFLICT_IGNORE
		);

		if (id != -1) {
			return (int) id;
		}

		// Someone else created a matching code/name earlier in this same
		// bulk import (e.g. an earlier row in the same file) - look it
		// up again instead of failing.
		return getOrCreateItemIdBulk(db, code, name, price, isPurchase);
	}

	private String generateNextItemCodeBulk(SQLiteDatabase db) {

		Cursor cursor = db.rawQuery(
			"SELECT code FROM " + TABLE_ITEMS + " ORDER BY id DESC LIMIT 1",
			null
		);

		char letter = 'C';
		int number = 1;

		if (cursor.moveToFirst()) {

			String lastCode = cursor.getString(0);

			if (lastCode != null &&
				lastCode.length() >= 2 &&
				Character.isLetter(lastCode.charAt(0))) {

				letter = lastCode.charAt(0);

				try {
					number = Integer.parseInt(lastCode.substring(1)) + 1;
				} catch (Exception e) {
					number = 1;
				}
			}
		}

		cursor.close();

		String code = String.format("%c%03d", letter, number);

		while (isItemCodeUsedBulk(db, code)) {

			number++;

			if (number > 999) {
				letter++;
				number = 1;
			}

			code = String.format("%c%03d", letter, number);
		}

		return code;
	}

	private boolean isItemCodeUsedBulk(SQLiteDatabase db, String code) {

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_ITEMS + " WHERE code=? LIMIT 1",
			new String[]{code}
		);

		boolean used = cursor.moveToFirst();
		cursor.close();
		return used;
	}

	// Same numbering scheme as getNextSaleInvoiceNo(), but runs on the
	// shared bulk-transaction connection so it can be called mid-import
	// (once per sale row that has no invoice/ref number of its own in
	// the source file) without opening a second database connection.
	public String getNextSaleInvoiceNoBulk(SQLiteDatabase db) {

		Cursor cursor = db.rawQuery(
			"SELECT MAX(CAST(invoice_no AS INTEGER)) FROM sales",
			null
		);

		int nextNo = DEFAULT_SALE_INVOICE_NO;

		if (cursor.moveToFirst() && !cursor.isNull(0)) {
			nextNo = Math.max(cursor.getInt(0) + 1, DEFAULT_SALE_INVOICE_NO);
		}

		cursor.close();

		return String.valueOf(nextNo);
	}

	public long insertSaleBulk(
		SQLiteDatabase db, HashMap<String, Object> saleData, String source) {

		ContentValues values = new ContentValues();

		values.put("invoice_no", (String) saleData.get("invoice_no"));
		values.put("date", (String) saleData.get("date"));
		values.put("time", (String) saleData.get("time"));
		values.put("party_id", (Integer) saleData.get("party_id"));
		values.put("subtotal", (Double) saleData.get("subtotal"));
		values.put("discount", (Double) saleData.get("discount"));
		values.put("other_charges", (Double) saleData.get("other_charges"));
		values.put("grand_total", (Double) saleData.get("grand_total"));
		values.put("paid_amount", (Double) saleData.get("paid_amount"));
		values.put("balance", (Double) saleData.get("balance"));
		values.put("notes", (String) saleData.get("notes"));
		values.put("source", source);

		long id = db.insert("sales", null, values);

		int partyId = (Integer) saleData.get("party_id");
		double due = (Double) saleData.get("balance");

		adjustPartyBalance(db, partyId, due);

		return id;
	}

	public long insertPurchaseBulk(
		SQLiteDatabase db,
		int partyId,
		String date,
		String time,
		String invoiceNumber,
		double grandTotal,
		double amountPaid,
		String notes,
		String source) {

		// Same fallback as insertPurchase(): a purchase must always carry
		// an invoice number. Imports preserve whatever the source file
		// already has, but when the source row has none (e.g. a Cash
		// Purchase with no supplier ref), auto-assign the next one using
		// the shared bulk-transaction connection so it can be called
		// mid-import.
		if (invoiceNumber == null || invoiceNumber.trim().length() == 0) {
			invoiceNumber = getNextPurchaseInvoiceNoBulk(db);
		}

		ContentValues values = new ContentValues();

		values.put("party_id", partyId);
		values.put("date", date);
		values.put("time", time);
		values.put("invoice_number", invoiceNumber);
		values.put("grand_total", grandTotal);
		values.put("amount_paid", amountPaid);
		values.put("notes", notes);
		values.put("source", source);

		long id = db.insert(TABLE_PURCHASES, null, values);

		double due = grandTotal - amountPaid;
		adjustPartyBalance(db, partyId, -due);

		return id;
	}

	public long insertPaymentBulk(
		SQLiteDatabase db,
		int type,
		int partyId,
		String date,
		String time,
		double amount,
		String notes,
		String source) {

		ContentValues values = new ContentValues();

		values.put("code", generateNextPaymentCode());
		values.put("type", type);
		values.put("party_id", partyId);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("source", source);

		long id = db.insert(TABLE_PAYMENTS, null, values);

		// A Payment In is money the party paid us, so it comes off what
		// they owe us. A Payment Out is money we paid the party, so it
		// comes off what we owe them.
		adjustPartyBalance(
			db,
			partyId,
			type == PAYMENT_IN ? -amount : amount
		);

		return id;
	}

	public long insertExpenseBulk(
		SQLiteDatabase db,
		String item,
		String date,
		String time,
		double amount,
		String notes,
		Integer partyId,
		String source) {

		ContentValues values = new ContentValues();

		values.put("code", generateNextExpenseCode());
		values.put("item", item);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("paid_amount", amount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("source", source);

		return db.insert(TABLE_EXPENSES, null, values);
	}

	// =====================
	// VYAPAR BACKUP IMPORT HELPERS
	// (also run on the shared beginTransaction()/getMigrationDatabase()
	// connection, same as the Excel bulk helpers above)
	// =====================

	// Inserts a new item with both prices set directly (unlike
	// getOrCreateItemIdBulk, which only knows one price at a time because
	// it is called per line item). preferredCode is used as-is if it is
	// non-blank and not already taken; otherwise a code is generated the
	// same way generateNextItemCodeBulk() does for the Excel importer.
	// Stock balance is intentionally left at 0 here - it is built up
	// afterwards from imported purchase/sale line items, the same way the
	// Excel importer computes it, so it stays consistent with the
	// transaction history rather than a separately-trusted snapshot.
	public long insertItemBulk(
		SQLiteDatabase db,
		String preferredCode,
		String name,
		double purchasePrice,
		double salePrice) {

		preferredCode = preferredCode == null ? "" : preferredCode.trim();
		name = name == null || name.trim().length() == 0 ? "Imported Item" : name.trim();

		String code = preferredCode.length() > 0 && !isItemCodeUsedBulk(db, preferredCode)
			? preferredCode
			: generateNextItemCodeBulk(db);

		ContentValues values = new ContentValues();
		values.put("code", code);
		values.put("name", name);
		values.put("purchase_price", purchasePrice);
		values.put("sale_price", salePrice);
		values.put("balance", 0.0);

		return db.insert(TABLE_ITEMS, null, values);
	}

	// =====================
	// Bulk-import counterparts of createVarietyGroup()/addVarietyValue():
	// plain inserts with no grid-generation or balance-migration side
	// effects, since a Vyapar/.vyb import is replaying a backup's own
	// exact groups/values/combos verbatim rather than interactively
	// building them up. insertVarietyComboBulk() always writes balance
	// 0 - like insertItemBulk() above, the real balance is left to
	// accumulate naturally as the backup's purchase/sale line items are
	// replayed through insertPurchaseItemBulk()/insertSaleItemBulk()
	// (each of which calls adjustComboBalance()), not written up front.
	// =====================
	public long insertVarietyGroupBulk(
		SQLiteDatabase db, int itemId, String name, int sortOrder) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("name", name);
		values.put("sort_order", sortOrder);

		return db.insert(TABLE_VARIETY_GROUPS, null, values);
	}

	public long insertVarietyValueBulk(
		SQLiteDatabase db, int groupId, String label, int sortOrder, boolean isDefault) {

		return insertVarietyValueRow(db, groupId, label, sortOrder, isDefault);
	}

	public long insertVarietyComboBulk(SQLiteDatabase db, int itemId) {
		return insertComboRow(db, itemId, 0.0);
	}

	public void linkComboValueBulk(
		SQLiteDatabase db, long comboId, long groupId, long valueId) {

		linkComboValue(db, comboId, groupId, valueId);
	}

	// Records a party-to-party transfer imported from Vyapar. The paying
	// party's balance moves the same direction as a Payment In (money
	// leaving them reduces what they owe us / increases what we owe
	// them), and the receiving party's balance moves the same direction
	// as a Payment Out - mirroring insertPaymentBulk()'s logic for a
	// regular payment. This is a reasonable default interpretation, not
	// a value confirmed from Vyapar's own ledger math, so double check a
	// few of these against Vyapar after import.
	public long insertPartyTransferBulk(
		SQLiteDatabase db,
		int fromPartyId,
		int toPartyId,
		String date,
		String time,
		double amount,
		String notes) {

		ContentValues values = new ContentValues();
		values.put("from_party_id", fromPartyId);
		values.put("to_party_id", toPartyId);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("source", "Vyapar Import");

		long id = db.insert(TABLE_PARTY_TRANSFERS, null, values);

		adjustPartyBalance(db, fromPartyId, -amount);
		adjustPartyBalance(db, toPartyId, amount);

		return id;
	}

	// Looks up the local row id a previously-imported source record
	// created, or null if that source record hasn't been imported yet.
	public Long getVybLocalId(SQLiteDatabase db, String vybType, long vybId) {

		Cursor cursor = db.rawQuery(
			"SELECT local_id FROM " + TABLE_VYB_IMPORT_MAP +
			" WHERE vyb_type=? AND vyb_id=?",
			new String[]{vybType, String.valueOf(vybId)}
		);

		Long result = null;

		if (cursor.moveToFirst()) {
			result = cursor.getLong(0);
		}

		cursor.close();

		return result;
	}

	public void saveVybLocalId(SQLiteDatabase db, String vybType, long vybId, long localId) {

		ContentValues values = new ContentValues();
		values.put("vyb_type", vybType);
		values.put("vyb_id", vybId);
		values.put("local_id", localId);

		db.insertWithOnConflict(
			TABLE_VYB_IMPORT_MAP,
			null,
			values,
			SQLiteDatabase.CONFLICT_REPLACE
		);
	}

	// =====================
	// Wipes every row from every table, for a full "restore from backup"
	// that replaces the database rather than merging into it. Unlike
	// deleteAllPurchases()/deleteAllSales()/deleteAllPayments(), this
	// does not bother reversing each row's party-balance adjustment
	// first - parties.balance is being deleted right along with the
	// party rows themselves in this same pass, so there is nothing left
	// for a stray adjustment to strand.
	//
	// Also clears import_log and vyb_import_map (which the per-table
	// deleteAllX() methods don't all do consistently - see
	// deleteAllItems()'s comment) - once the data they were tracking is
	// gone, keeping their "already imported" keys around would make the
	// very next import of the same backup wrongly skip everything as a
	// duplicate.
	//
	// Runs on the shared beginTransaction()/getMigrationDatabase()
	// connection so the caller can wrap "clear, then import" as one
	// atomic transaction - a failure partway through the import leaves
	// the original data intact instead of an empty database.
	// =====================
	public void clearAllDataBulk(SQLiteDatabase db) {

		db.delete(TABLE_PURCHASE_ITEMS, null, null);
		db.delete(TABLE_PURCHASES, null, null);
		db.delete("sale_items", null, null);
		db.delete("sales", null, null);
		db.delete(TABLE_PAYMENTS, null, null);
		db.delete(TABLE_EXPENSES, null, null);
		db.delete(TABLE_PARTY_TRANSFERS, null, null);
		db.delete(TABLE_VARIETY_COMBO_VALUES, null, null);
		db.delete(TABLE_VARIETY_COMBOS, null, null);
		db.delete(TABLE_VARIETY_VALUES, null, null);
		db.delete(TABLE_VARIETY_GROUPS, null, null);
		db.delete(TABLE_CASH_ADJUSTMENTS, null, null);
		db.delete(TABLE_WANTED_ITEMS, null, null);
		db.delete(TABLE_ITEMS, null, null);
		db.delete(TABLE_PARTIES, null, null);
		db.delete(TABLE_IMPORT_LOG, null, null);
		db.delete(TABLE_VYB_IMPORT_MAP, null, null);
	}

	// =====================
	// BULK PURCHASE IMPORT (Excel -> new Purchase)
	// The *Bulk() methods below use the shared SQLiteDatabase from
	// beginTransaction()/getMigrationDatabase(), same convention as the
	// other *Bulk() helpers above, so a whole run is written - or rolled
	// back - as one single SQLite transaction. The Bulk Purchase Import
	// screen no longer calls these to write a Purchase directly though -
	// it now only validates the file and hands the parsed rows to
	// Transactioneditactivity (purchase add mode) for the user to
	// review before saving. getOrCreateProductForPurchaseImport() below
	// (the non-bulk sibling of getOrCreateProductForPurchaseImportBulk())
	// is what that screen's normal Save now calls, per imported row.
	// =====================

	// True if a purchase with this id still exists. Checked once at the
	// start of a bulk import (on the same shared connection) in case the
	// purchase was deleted from another screen between opening it and
	// finishing the import.
	public boolean purchaseExistsBulk(SQLiteDatabase db, int purchaseId) {

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_PURCHASES + " WHERE id=?",
			new String[]{String.valueOf(purchaseId)}
		);

		boolean exists = cursor.moveToFirst();

		cursor.close();

		return exists;
	}

	// Resolves the catalog item to use for one imported purchase row:
	//  - Code given AND it already exists -> use that existing product
	//    as-is. Its name/prices are never touched and the Excel details
	//    for that row (other than quantity/purchase price on the
	//    purchase item itself) are ignored.
	//  - Code given but NOT found -> create a new product using that
	//    exact code.
	//  - No code given -> create a new product with a freshly generated
	//    code (same numbering scheme as generateNextItemCodeBulk() uses
	//    elsewhere).
	// nameWithoutCode is the product name built by the caller from the
	// Type/Sole/Upper/Design/Color/Gender/Size columns, WITHOUT the code
	// on the end yet - the final code (whichever of the three cases
	// above applies) is appended here, since it isn't known until this
	// point when the Excel row had no code of its own.
	// Returns the item id, or -1 if the product could not be resolved.
	public int getOrCreateProductForPurchaseImportBulk(
		SQLiteDatabase db,
		String code,
		String nameWithoutCode,
		double purchasePrice,
		double salePrice) {

		return resolveOrCreateProductForPurchaseImport(
			db, code, nameWithoutCode, purchasePrice, salePrice);
	}

	// Same resolution as getOrCreateProductForPurchaseImportBulk() above,
	// but for use OUTSIDE the beginTransaction()/getMigrationDatabase()
	// bulk-write path - e.g. Transactioneditactivity.savePurchase()
	// resolving/creating the catalog item for a single purchase-item row
	// (such as one carried over from the Bulk Purchase Import screen,
	// where product creation is deliberately deferred until the user
	// actually taps Save) using the normal getWritableDatabase()
	// connection like every other non-bulk write in this class.
	public int getOrCreateProductForPurchaseImport(
		String code,
		String nameWithoutCode,
		double purchasePrice,
		double salePrice) {

		SQLiteDatabase db = this.getWritableDatabase();

		return resolveOrCreateProductForPurchaseImport(
			db, code, nameWithoutCode, purchasePrice, salePrice);
	}

	private int resolveOrCreateProductForPurchaseImport(
		SQLiteDatabase db,
		String code,
		String nameWithoutCode,
		double purchasePrice,
		double salePrice) {

		code = code == null ? "" : code.trim();
		nameWithoutCode = nameWithoutCode == null ? "" : nameWithoutCode.trim();

		if (code.length() > 0) {

			Cursor cursor = db.rawQuery(
				"SELECT id FROM " + TABLE_ITEMS + " WHERE code=? LIMIT 1",
				new String[]{code}
			);

			if (cursor.moveToFirst()) {

				int id = cursor.getInt(0);
				cursor.close();
				return id;
			}

			cursor.close();
		}

		String finalCode = code.length() > 0 ? code : generateNextItemCodeBulk(db);

		String finalName = nameWithoutCode.length() > 0
			? nameWithoutCode + " " + finalCode
			: finalCode;

		ContentValues values = new ContentValues();
		values.put("code", finalCode);
		values.put("name", finalName);
		values.put("purchase_price", purchasePrice);
		values.put("sale_price", salePrice);
		values.put("balance", 0);

		long id = db.insertWithOnConflict(
			TABLE_ITEMS,
			null,
			values,
			SQLiteDatabase.CONFLICT_IGNORE
		);

		if (id != -1) {
			return (int) id;
		}

		// A product with this exact code already exists after all (a
		// duplicate explicit code earlier in the same file, or a
		// concurrent writer) - use that one instead of failing the
		// whole import.
		Cursor recheck = db.rawQuery(
			"SELECT id FROM " + TABLE_ITEMS + " WHERE code=? LIMIT 1",
			new String[]{finalCode}
		);

		int existingId = -1;

		if (recheck.moveToFirst()) {
			existingId = recheck.getInt(0);
		}

		recheck.close();

		return existingId;
	}

	// Folds the combined total of every purchase item just bulk-inserted
	// into an existing purchase's grand_total, and keeps the supplier's
	// balance in sync - mirrors the balance math in insertPurchase()
	// (amount_paid is untouched, so the whole added total becomes
	// additional amount owed to the supplier).
	public void addToPurchaseTotalBulk(
		SQLiteDatabase db,
		int purchaseId,
		double addedTotal) {

		if (addedTotal == 0) {
			return;
		}

		db.execSQL(
			"UPDATE " + TABLE_PURCHASES +
			" SET grand_total = grand_total + ? WHERE id = ?",
			new Object[]{addedTotal, purchaseId}
		);

		Cursor cursor = db.rawQuery(
			"SELECT party_id FROM " + TABLE_PURCHASES + " WHERE id=?",
			new String[]{String.valueOf(purchaseId)}
		);

		if (cursor.moveToFirst()) {

			int partyId = cursor.getInt(0);
			cursor.close();

			adjustPartyBalance(db, partyId, -addedTotal);

		} else {

			cursor.close();
		}
	}

}

