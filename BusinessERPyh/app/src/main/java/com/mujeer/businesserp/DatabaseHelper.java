package com.mujeer.businesserp;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
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
    // Bumped 15 -> 16 to add recurring_expenses (standing rules that
    // auto-generate expense entries) - onUpgrade() only runs onCreate()
    // again when this number goes up, so without the bump an existing
    // install never gets the new table and generateDueRecurringExpenses()
    // (called every app open) crashes with "no such table".
    // Bumped 16 -> 17 to add "drafts" (a Sale/Purchase/Payment/Expense
    // saved mid-entry, to be finished later from the Drafts screen).
    // Bumped 17 -> 18 to add landed-cost tracking: items.extra_cost_
    // per_unit (a weighted-average transport/shipping cost per unit,
    // blended in from purchases.other_charges each time a Purchase
    // carrying one is saved) and purchases.other_charges_to_party
    // (whether that charge was added to the supplier's owed amount, or
    // tracked purely for cost purposes).
    // Bumped 18 -> 19 to replace the single per-purchase "Other Charges"
    // field (purchases.other_charges is left in place, just unused by
    // new entries) with landed costs sourced from Expenses themselves:
    // cost_items (a reusable category list - Petrol, Shipping,
    // Packaging, ... - used by Expenses' Item field instead of free
    // text) and purchase_expense_links (how one Expense's amount splits
    // across the purchase(s) it's linked to as their landed cost - see
    // Transactioneditactivity's "Select Expenses"). A linked expense's
    // own cash/party-balance effect stays entirely its own (paid_amount/
    // party_id, unchanged) - only its landed-cost share is new, and it
    // drops out of the plain Expenses list/totals so it isn't double
    // counted.
    // Bumped 19 -> 20 to add: items.active (a simple label filterable
    // everywhere an item list/picker appears app-wide, not a behavior
    // change on its own), and two brand-new tables, display_shoes/
    // sample_shoes (one physical shoe each - Display=right, Sample=left
    // - placed as a reference overlay that never reserves/removes
    // stock; see Transactioneditactivity's sale-save flow for how a
    // matching sale offers to remove a row).
    // Bumped 20 -> 21 to add sales.due_date (a credit sale's due date -
    // this got missed when the column was first added, so onUpgrade()
    // never ran on any install already at 20 and every getSaleById()/
    // getCreditDueSales()/export call hit "no such column: due_date".
    // Bumped 21 -> 22 to add purchases.due_date, mirroring sales.due_date -
    // a credit Purchase (not fully paid) now gets the same Due Date field/
    // default as a credit Sale. Not yet exercised in practice (every
    // Purchase is currently paid in full), but wired the same way so it
    // works the moment it is.
    // Bumped 22 -> 23 to add items.reorder_threshold (0 = no alert) -
    // backs the Low Stock report/notification.
    // Bumped 23 -> 24 to add the recently_deleted table - backs the
    // Recently Deleted/undo screen for Purchases/Sales.
    // Bumped 24 -> 25 to add reorder_suggestion_log - backs the Reorder
    // List's learning loop (see getReorderSuggestions()/
    // recordReorderDecision()). Deliberately NOT included in the Vyapar
    // export/import round-trip, same reasoning as recently_deleted
    // below: it's the automation's own operational memory, not a
    // business record - a restore starts the learning loop fresh
    // rather than needing to carry it across.
    // Bumped 25 -> 26 to add item_clearance - backs the Dead Stock
    // Aging report's clearance workflow (see startClearance()/
    // endClearance()/getActiveClearances()). Unlike reorder_suggestion_
    // log above, this IS real standing user data (an active clearance
    // decision), so it's included in the Vyapar round-trip.
    // Bumped 26 -> 27 to add loyalty_points_ledger - backs the Loyalty
    // Points feature (see earnLoyaltyPointsForSale()/
    // getLoyaltyPointsBalance()/adjustLoyaltyPoints()). Real standing
    // user data (a customer's earned rewards), so it's included in the
    // Vyapar round-trip, same reasoning as item_clearance above.
    public static final int DATABASE_VERSION = 27;

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

	// A standing rule that auto-generates a real Expense row (fully paid,
	// same as a plain insertExpense()) for each date it's due - see
	// generateDueRecurringExpenses(), run once per app open from
	// MainActivity. last_generated_date is the rule's own bookmark of how
	// far it's already caught up to, so a rule never double-generates
	// even across many days the app wasn't opened.
	public static final String TABLE_RECURRING_EXPENSES = "recurring_expenses";

	// A Sale/Purchase/Payment/Expense saved mid-entry instead of being
	// committed as a real transaction - "type" is which editor it came
	// from and "data" is that screen's fields (and item list, for a
	// Sale/Purchase) serialized as JSON. Nothing here ever affects cash,
	// stock, or party balances until it's opened from the Drafts screen
	// and actually saved for real, at which point the draft row is
	// deleted.
	public static final String TABLE_DRAFTS = "drafts";

	// A deleted Purchase/Sale's full row (plus its line items) as one
	// JSON snapshot, kept until the user permanently removes it or it
	// ages out - backs the Recently Deleted/undo screen. See
	// buildSaleSnapshot()/buildPurchaseSnapshot() and
	// restoreSaleFromSnapshot()/restorePurchaseFromSnapshot().
	public static final String TABLE_RECENTLY_DELETED = "recently_deleted";

	// See DATABASE_VERSION's bump comment above for what this tracks and
	// why it's excluded from the Vyapar round-trip.
	public static final String TABLE_REORDER_SUGGESTION_LOG = "reorder_suggestion_log";

	// An item currently marked down for clearance - one row per item
	// (PRIMARY KEY item_id, not AUTOINCREMENT, since an item can only be
	// in clearance once at a time; starting a new clearance on an item
	// already in one replaces its row). starting_balance is the stock
	// level when clearance began, so progress can be shown as how much
	// of that has sold since.
	public static final String TABLE_ITEM_CLEARANCE = "item_clearance";

	// One row per loyalty-points event (earn from a Sale, or a manual
	// adjustment) for one party - an append-only ledger, not a running
	// total; a party's current balance is SUM(points) over their rows
	// (see getLoyaltyPointsBalance()). AUTOINCREMENT since a party can
	// have many entries over time.
	public static final String TABLE_LOYALTY_POINTS = "loyalty_points_ledger";

	public static final String DRAFT_TYPE_SALE = "sale";
	public static final String DRAFT_TYPE_PURCHASE = "purchase";
	public static final String DRAFT_TYPE_PAYMENT = "payment";
	public static final String DRAFT_TYPE_EXPENSE = "expense";

	// A reusable expense category (Petrol, Shipping, Packaging, food,
	// ...) - Expenses' Item field picks from this list the same way
	// every screen that picks a party shares one Parties list.
	public static final String TABLE_COST_ITEMS = "cost_items";

	// How one Expense's amount is attributed across the purchase(s) its
	// cost belongs to (see Transactioneditactivity's "Select Expenses" -
	// replaces the old separate Purchase Costs entity: the "cost event"
	// is just the expenses row itself, already carrying its own item/
	// amount/date/party/paid_amount) - share_percent is that purchase's
	// proportional slice (by purchase value) of the expense's total,
	// allocated_amount is the resulting rupee amount for this link.
	// Deliberately carries no cash/party-balance effect of its own -
	// the expense's own paid_amount/party_id already fully own that (see
	// getCashBalance()); this table only decides how much of the expense
	// blends into each linked purchase's line items' extra_cost_per_unit
	// (see applyExtraCostToItem()) - and, since it's now double-counted
	// as a purchase's landed cost, excludes that expense from
	// getExpenses()/getNetProfitSummary()'s/getExpenseTotalForRange()'s
	// plain-expense totals (see their own comments).
	public static final String TABLE_PURCHASE_EXPENSE_LINKS = "purchase_expense_links";

	// One physical shoe (not a pair) placed on the shop's physical
	// display or kept as a sample - Display = right shoe, Sample = left
	// shoe. Multiple rows can share the same item_id+combo_id (you can
	// have more than one physical unit of the same model+size out at
	// once) - they're interchangeable, so nothing here tracks which
	// specific row corresponds to which physical unit beyond that.
	// Placing a shoe here does NOT reserve/remove it from sellable stock
	// - it's a reference overlay only; see Transactioneditactivity's
	// sale-save flow for how a matching sale removes a row here.
	// Display additionally carries a free-form grid position (row_pos/
	// col_pos) the user arranges manually, mirroring their physical
	// shelf layout - Sample has no such position, it's a plain list.
	public static final String TABLE_DISPLAY_SHOES = "display_shoes";
	public static final String TABLE_SAMPLE_SHOES = "sample_shoes";

	public static final int RECURRING_DAILY = 1;
	public static final int RECURRING_WEEKLY = 2;
	public static final int RECURRING_MONTHLY = 3;
	public static final int RECURRING_SPECIFIC_DATES = 4;



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
			"balance REAL NOT NULL DEFAULT 0, " +
			"extra_cost_per_unit REAL NOT NULL DEFAULT 0, " +
			"active INTEGER NOT NULL DEFAULT 1, " +
			"reorder_threshold REAL NOT NULL DEFAULT 0" +
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
			"notes TEXT, " +
			"source TEXT, " +
			"other_charges REAL NOT NULL DEFAULT 0, " +
			"other_charges_to_party INTEGER NOT NULL DEFAULT 1, " +
			"due_date TEXT" +
			");"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_PURCHASE_ITEMS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"purchase_id INTEGER NOT NULL, " +
			"item_id INTEGER NOT NULL, " +
			"quantity REAL NOT NULL, " +
			"purchase_price REAL NOT NULL, " +
			"total REAL NOT NULL, " +
			"combo_id INTEGER" +
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
			"notes TEXT, " +
			"source TEXT, " +
			"due_date TEXT" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS sale_items (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"sale_id INTEGER, " +
			"item_id INTEGER, " +
			"qty REAL DEFAULT 0, " +
			"rate REAL DEFAULT 0, " +
			"amount REAL DEFAULT 0, " +
			"combo_id INTEGER" +
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
			"notes TEXT, " +
			"source TEXT" +
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
			"party_id INTEGER, " +
			"source TEXT, " +
			"paid_amount REAL NOT NULL DEFAULT 0" +
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
			"notes TEXT, " +
			"source TEXT" +
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

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_RECURRING_EXPENSES + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"item TEXT NOT NULL, " +
			"amount REAL NOT NULL DEFAULT 0, " +
			"notes TEXT, " +
			"party_id INTEGER, " +
			"frequency INTEGER NOT NULL, " +
			"day_of_week INTEGER, " +
			"day_of_month INTEGER, " +
			"specific_dates TEXT, " +
			"start_date TEXT NOT NULL, " +
			"last_generated_date TEXT, " +
			"active INTEGER NOT NULL DEFAULT 1" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_DRAFTS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"type TEXT NOT NULL, " +
			"label TEXT NOT NULL, " +
			"data TEXT NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_COST_ITEMS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"name TEXT NOT NULL UNIQUE" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_RECENTLY_DELETED + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"type TEXT NOT NULL, " +
			"label TEXT NOT NULL, " +
			"data TEXT NOT NULL, " +
			"deleted_date TEXT NOT NULL, " +
			"deleted_time TEXT NOT NULL" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_PURCHASE_EXPENSE_LINKS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"expense_id INTEGER NOT NULL, " +
			"purchase_id INTEGER NOT NULL, " +
			"share_percent REAL NOT NULL DEFAULT 0, " +
			"allocated_amount REAL NOT NULL DEFAULT 0" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_DISPLAY_SHOES + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"item_id INTEGER NOT NULL, " +
			"combo_id INTEGER NOT NULL, " +
			"row_pos INTEGER NOT NULL, " +
			"col_pos INTEGER NOT NULL" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_SAMPLE_SHOES + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"item_id INTEGER NOT NULL, " +
			"combo_id INTEGER NOT NULL" +
			")"
		);

		// One row per reorder suggestion ever shown, and what happened to
		// it - backs the learning loop in getReorderSuggestions(). outcome
		// is one of "accepted" (converted to a draft purchase), "ignored"
		// (left unconverted past its own suggested_date), "overstocked"
		// (set later, if the item is still sitting on stock well above
		// its threshold a while after this suggestion), or
		// "ran_out_before_restock" (set later, if the item hit zero stock
		// before a following purchase arrived). null outcome = not yet
		// resolved either way.
		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_REORDER_SUGGESTION_LOG + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"item_id INTEGER NOT NULL, " +
			"combo_id INTEGER, " +
			"suggested_date TEXT NOT NULL, " +
			"suggested_qty REAL NOT NULL, " +
			"outcome TEXT, " +
			"outcome_date TEXT" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_ITEM_CLEARANCE + " (" +
			"item_id INTEGER PRIMARY KEY, " +
			"started_date TEXT NOT NULL, " +
			"discount_percent REAL NOT NULL, " +
			"starting_balance REAL NOT NULL" +
			")"
		);

		db.execSQL(
			"CREATE TABLE IF NOT EXISTS " + TABLE_LOYALTY_POINTS + " (" +
			"id INTEGER PRIMARY KEY AUTOINCREMENT, " +
			"party_id INTEGER NOT NULL, " +
			"points INTEGER NOT NULL, " +
			"date TEXT NOT NULL, " +
			"time TEXT NOT NULL, " +
			"reason TEXT NOT NULL" +
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

		addColumnIfMissing(db, TABLE_ITEMS, "extra_cost_per_unit", "REAL NOT NULL DEFAULT 0");
		addColumnIfMissing(db, TABLE_ITEMS, "active", "INTEGER NOT NULL DEFAULT 1");
		addColumnIfMissing(db, TABLE_PURCHASES, "other_charges", "REAL NOT NULL DEFAULT 0");
		addColumnIfMissing(db, TABLE_PURCHASES, "other_charges_to_party", "INTEGER NOT NULL DEFAULT 1");

		// A credit sale's due date - a Sale only ever had an implicit,
		// unenforced "whenever" before this existed, so an existing
		// unpaid/partial sale from before this column was added simply
		// has no due date rather than one getting invented for it.
		addColumnIfMissing(db, "sales", "due_date", "TEXT");

		// Same, mirrored onto Purchases - see DATABASE_VERSION's comment.
		addColumnIfMissing(db, TABLE_PURCHASES, "due_date", "TEXT");

		// Per-item reorder threshold (0 = no alert) - backs the Low Stock
		// report/notification.
		addColumnIfMissing(db, TABLE_ITEMS, "reorder_threshold", "REAL NOT NULL DEFAULT 0");

		// cost_items (created above by onCreate(db)) is brand new as of
		// this version - every expense/recurring-expense rule recorded
		// before it existed has its "item" as plain free text with no
		// Cost Item behind it. Seed one in for each distinct value
		// already in use so upgrading doesn't lose that history - the
		// Expense screen's Item field autocomplete (see
		// Expenseeditactivity) then already offers everything already in
		// use, instead of starting empty. Gated to oldVersion < 19 (when
		// cost_items was introduced) so it only ever runs the one time.
		if (oldVersion < 19) {
			backfillCostItemsFromExistingItemText(db);
		}

		dropPurchaseCodeColumnIfPresent(db);
	}

	// =====================
	// See its call site in onUpgrade() above. Reads TABLE_EXPENSES.item
	// and TABLE_RECURRING_EXPENSES.item directly off the db parameter
	// (never through a public getXxx()/getOrCreateCostItemId() helper,
	// which would call back into getWritableDatabase() while the
	// database isn't finished opening yet) and inserts one cost_items
	// row per distinct value, ignoring an already-seeded duplicate.
	// =====================
	private void backfillCostItemsFromExistingItemText(SQLiteDatabase db) {

		String[] sourceQueries = {
			"SELECT DISTINCT item FROM " + TABLE_EXPENSES +
				" WHERE item IS NOT NULL AND TRIM(item) != ''",
			"SELECT DISTINCT item FROM " + TABLE_RECURRING_EXPENSES +
				" WHERE item IS NOT NULL AND TRIM(item) != ''"
		};

		for (String sql : sourceQueries) {

			Cursor cursor = db.rawQuery(sql, null);

			while (cursor.moveToNext()) {

				String item = cursor.getString(0).trim();

				if (item.length() == 0) {
					continue;
				}

				ContentValues values = new ContentValues();
				values.put("name", item);

				db.insertWithOnConflict(
					TABLE_COST_ITEMS, null, values, SQLiteDatabase.CONFLICT_IGNORE
				);
			}

			cursor.close();
		}
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

	// Shared by Partiesactivity's own sort controls and the new Party
	// Report screen (Partyreportactivity) - "latest transaction" pools
	// every kind of party-facing money movement (Sale, Purchase,
	// Payment, an Expense billed to a party, and either side of a Party
	// Transfer) and takes the most recent date across all of them. A
	// party with no activity at all sorts as "longest ago"/"last" under
	// PARTY_SORT_LATEST_TXN regardless of direction, since there's no
	// real date to compare - not first under ascending.
	public static final int PARTY_SORT_LATEST_TXN = 0;
	public static final int PARTY_SORT_BALANCE = 1;
	public static final int PARTY_SORT_NAME = 2;

	public static final int PARTY_BALANCE_FILTER_ALL = 0;
	public static final int PARTY_BALANCE_FILTER_ZERO = 1;
	public static final int PARTY_BALANCE_FILTER_NONZERO = 2;

	public ArrayList<HashMap<String, Object>> getPartiesWithActivity(
		int sortBy, boolean ascending, int balanceFilter) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		String sql =
			"SELECT p.id, p.name, p.balance, MAX(t.date) AS last_date " +
			"FROM " + TABLE_PARTIES + " p " +
			"LEFT JOIN (" +
			"SELECT party_id AS pid, date FROM sales " +
			"UNION ALL SELECT party_id AS pid, date FROM purchases " +
			"UNION ALL SELECT party_id AS pid, date FROM payments " +
			"UNION ALL SELECT party_id AS pid, date FROM " + TABLE_EXPENSES +
			" WHERE party_id IS NOT NULL " +
			"UNION ALL SELECT from_party_id AS pid, date FROM " + TABLE_PARTY_TRANSFERS + " " +
			"UNION ALL SELECT to_party_id AS pid, date FROM " + TABLE_PARTY_TRANSFERS +
			") t ON t.pid = p.id " +
			"GROUP BY p.id";

		Cursor cursor = db.rawQuery(sql, null);

		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

		while (cursor.moveToNext()) {

			double balance = cursor.getDouble(2);

			if (balanceFilter == PARTY_BALANCE_FILTER_ZERO && balance != 0) {
				continue;
			}

			if (balanceFilter == PARTY_BALANCE_FILTER_NONZERO && balance == 0) {
				continue;
			}

			String lastDate = cursor.isNull(3) ? null : cursor.getString(3);
			int daysSince = lastDate == null ? -1 : daysBetweenDates(lastDate, today);

			HashMap<String, Object> row = new HashMap<>();

			row.put("id", cursor.getInt(0));
			row.put("name", cursor.getString(1));
			row.put("balance", balance);
			row.put("last_date", lastDate);
			row.put("days_since", daysSince);

			list.add(row);
		}

		cursor.close();

		final int sortBy_forCompare = sortBy;
		final boolean ascending_forCompare = ascending;

		Collections.sort(list, new Comparator<HashMap<String, Object>>() {
			@Override
			public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {

				int result;

				switch (sortBy_forCompare) {

					case PARTY_SORT_BALANCE:
						result = Double.compare((Double) a.get("balance"), (Double) b.get("balance"));
						break;

					case PARTY_SORT_NAME:
						result = ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
						break;

					case PARTY_SORT_LATEST_TXN:
					default:
						int daysA = (Integer) a.get("days_since");
						int daysB = (Integer) b.get("days_since");
						int effA = daysA == -1 ? Integer.MAX_VALUE : daysA;
						int effB = daysB == -1 ? Integer.MAX_VALUE : daysB;
						result = Integer.compare(effA, effB);
						break;
				}

				return ascending_forCompare ? result : -result;
			}
		});

		return list;
	}

	private int daysBetweenDates(String fromIso, String toIso) {

		try {

			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

			long diffMillis = format.parse(toIso).getTime() - format.parse(fromIso).getTime();

			return (int) (diffMillis / (24L * 60 * 60 * 1000));

		} catch (Exception e) {
			return -1;
		}
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

		return insertItem(null, name, purchasePrice, salePrice, balance);
	}

	// code is the auto-assigned value the Add Item screen pre-fills and
	// the user can now override (see isItemCodeUsedBulk-style check
	// below) - null or blank falls back to the same auto-generated code
	// as before, for every other caller that never had a code to pass.
	public long insertItem(String code,
						   String name,
						   double purchasePrice,
						   double salePrice,
						   double balance) {

		SQLiteDatabase db = this.getWritableDatabase();

		String finalCode = (code == null || code.trim().length() == 0)
			? generateNextItemCode()
			: code.trim();

		ContentValues values = new ContentValues();

		values.put("code", finalCode);
		values.put("name", name);
		values.put("purchase_price", purchasePrice);
		values.put("sale_price", salePrice);
		values.put("balance", balance);

		long id = db.insert(TABLE_ITEMS, null, values);


		return id;
	}

	// Lets the Add Item screen show (and the user edit) the code that
	// would be auto-assigned, before actually saving - a plain preview,
	// same value insertItem() would fall back to if left unchanged.
	public String peekNextItemCode() {
		return generateNextItemCode();
	}

	// Quick duplicate for creating a similar item (see Itemsactivity's
	// long-press menu) - copies name (with " (Copy)" appended), purchase
	// price, sale price and Reorder Threshold with a fresh auto-generated
	// code and zero stock. Deliberately does NOT copy variety groups/
	// combos - those are specific stock-keeping structure the new item
	// should set up fresh for whatever it actually turns out to be, not
	// an exact clone of the original's. Returns the new item's id.
	public long copyItem(int itemId) {

		HashMap<String, Object> original = getItemById(itemId);

		String originalName = (String) original.get("name");
		double purchasePrice = (Double) original.get("purchase_price");
		double salePrice = (Double) original.get("sale_price");
		double reorderThreshold =
			original.get("reorder_threshold") == null ? 0 : (Double) original.get("reorder_threshold");

		long newId = insertItem(originalName + " (Copy)", purchasePrice, salePrice, 0);

		if (newId != -1 && reorderThreshold > 0) {

			SQLiteDatabase db = this.getWritableDatabase();

			ContentValues values = new ContentValues();
			values.put("reorder_threshold", reorderThreshold);

			db.update(TABLE_ITEMS, values, "id=?", new String[]{String.valueOf(newId)});
		}

		return newId;
	}

	public boolean isItemCodeTaken(String code) {

		if (code == null || code.trim().length() == 0) {
			return false;
		}

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT 1 FROM " + TABLE_ITEMS + " WHERE code=? LIMIT 1",
			new String[]{code.trim()}
		);

		boolean taken = cursor.moveToFirst();

		cursor.close();

		return taken;
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

					try {
						purchasePrice = Double.parseDouble(
							map.get("purchase_price").toString().trim());
					} catch (Exception e) {
					}
				}

				if (map.get("sale_price") != null &&
					map.get("sale_price").toString().trim().length() > 0) {

					try {
						salePrice = Double.parseDouble(
							map.get("sale_price").toString().trim());
					} catch (Exception e) {
					}
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

	public static final int ITEM_ACTIVE_FILTER_ALL = 0;
	public static final int ITEM_ACTIVE_FILTER_ACTIVE_ONLY = 1;
	public static final int ITEM_ACTIVE_FILTER_INACTIVE_ONLY = 2;

	public ArrayList<java.util.HashMap<String, Object>> getItems() {
		return getItems(ITEM_ACTIVE_FILTER_ALL);
	}

	public ArrayList<java.util.HashMap<String, Object>> getItems(int activeFilter) {

		ArrayList<java.util.HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		String where = "";

		if (activeFilter == ITEM_ACTIVE_FILTER_ACTIVE_ONLY) {
			where = " WHERE active = 1";
		} else if (activeFilter == ITEM_ACTIVE_FILTER_INACTIVE_ONLY) {
			where = " WHERE active = 0";
		}

		Cursor cursor = db.rawQuery(
			"SELECT id, code, name, purchase_price, sale_price, balance, active FROM " +
			TABLE_ITEMS + where +
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
			map.put("active", cursor.getInt(6) != 0);

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
			"SELECT code, name, purchase_price, sale_price, balance, extra_cost_per_unit, active, " +
			"reorder_threshold FROM " +
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
			map.put("extra_cost_per_unit", cursor.getDouble(5));
			map.put("active", cursor.getInt(6) != 0);
			map.put("reorder_threshold", cursor.getDouble(7));
		}

		cursor.close();

		return map;
	}

	// Wherever an item's active/inactive state alone needs updating,
	// separate from its name/price/balance (see Itemseditactivity's
	// "Active" checkbox) - active=false is the "discontinued, stop
	// offering this in item pickers" flag; see getItemsForSpinner(),
	// which is the one place that filters on it.
	public boolean setItemActive(int id, boolean active) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put("active", active ? 1 : 0);

		int rows = db.update(
			TABLE_ITEMS, values, "id=?", new String[]{String.valueOf(id)}
		);

		return rows > 0;
	}

	// purchase_price + extra_cost_per_unit for one item - the same cost
	// basis Net Profit/Item Monthly Rank/Profit Split already use, in a
	// single-value form for a live per-line profit preview while
	// building a Sale (see Transactioneditactivity#updateGrandTotal()).
	public double getItemCostBasis(int itemId) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT purchase_price + extra_cost_per_unit FROM " + TABLE_ITEMS + " WHERE id=?",
			new String[]{String.valueOf(itemId)}
		);

		double result = 0;

		if (cursor.moveToFirst()) {
			result = cursor.getDouble(0);
		}

		cursor.close();

		return result;
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

		return updateItem(id, name, purchasePrice, salePrice, balance, null);
	}

	// reorderThreshold == null leaves it untouched, same convention as
	// balance above - used by Itemseditactivity, which always passes its
	// own (possibly-0, never-null) field value explicitly.
	public boolean updateItem(int id,
							  String name,
							  double purchasePrice,
							  double salePrice,
							  Double balance,
							  Double reorderThreshold) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();

		values.put("name", name);
		values.put("purchase_price", purchasePrice);
		values.put("sale_price", salePrice);

		if (balance != null) {
			values.put("balance", balance);
		}

		if (reorderThreshold != null) {
			values.put("reorder_threshold", reorderThreshold);
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
// BULK ITEM UPDATES - operates on every active item whose name or code
// contains the given filter text (case-insensitive substring match) -
// there's no formal "category" field on items, so this substring match
// is the closest thing to one. Used by BulkItemUpdateActivity's
// Preview/Apply flow so the user always sees the affected count before
// committing to either change.
// =====================

	public ArrayList<java.util.HashMap<String, Object>> getItemsMatchingFilter(String filterText) {

		ArrayList<java.util.HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		String like = "%" + filterText.trim() + "%";

		Cursor cursor = db.rawQuery(
			"SELECT id, code, name, purchase_price, sale_price, reorder_threshold FROM " +
			TABLE_ITEMS +
			" WHERE active = 1 AND (name LIKE ? COLLATE NOCASE OR code LIKE ? COLLATE NOCASE) " +
			"ORDER BY code",
			new String[]{like, like}
		);

		while (cursor.moveToNext()) {

			java.util.HashMap<String, Object> row = new java.util.HashMap<>();

			row.put("id", cursor.getInt(0));
			row.put("code", cursor.getString(1));
			row.put("name", cursor.getString(2));
			row.put("purchase_price", cursor.getDouble(3));
			row.put("sale_price", cursor.getDouble(4));
			row.put("reorder_threshold", cursor.getDouble(5));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// Applies a percentage change (10 = +10%, -10 = -10%) to purchase
	// and/or sale price across every item matching the filter, floored
	// at 0 so a large negative percentage can't push a price negative.
	// Returns how many items were affected.
	public int bulkAdjustPrice(
		String filterText, boolean applyToPurchase, boolean applyToSale, double percentChange) {

		ArrayList<java.util.HashMap<String, Object>> items = getItemsMatchingFilter(filterText);

		SQLiteDatabase db = this.getWritableDatabase();

		double multiplier = 1 + (percentChange / 100.0);

		for (java.util.HashMap<String, Object> item : items) {

			ContentValues values = new ContentValues();

			if (applyToPurchase) {

				double newPurchase = (Double) item.get("purchase_price") * multiplier;
				values.put("purchase_price", Math.max(0, newPurchase));
			}

			if (applyToSale) {

				double newSale = (Double) item.get("sale_price") * multiplier;
				values.put("sale_price", Math.max(0, newSale));
			}

			if (values.size() > 0) {

				db.update(
					TABLE_ITEMS, values, "id=?", new String[]{String.valueOf(item.get("id"))}
				);
			}
		}

		return items.size();
	}

	// Sets every matching item's reorder_threshold to the same value.
	// Returns how many items were affected.
	public int bulkSetReorderThreshold(String filterText, double threshold) {

		ArrayList<java.util.HashMap<String, Object>> items = getItemsMatchingFilter(filterText);

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put("reorder_threshold", threshold);

		for (java.util.HashMap<String, Object> item : items) {

			db.update(TABLE_ITEMS, values, "id=?", new String[]{String.valueOf(item.get("id"))});
		}

		return items.size();
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

	// Same rows as getVarietyValues(), each with its own "stock" figure -
	// the summed balance across every combo that pairs this exact
	// group/value with any selection of the item's other groups. Used to
	// filter a Sale's size dropdown down to values that can actually be
	// sold - see Transactioneditactivity.
	public ArrayList<HashMap<String, Object>> getVarietyValuesWithStock(int groupId) {

		SQLiteDatabase db = this.getReadableDatabase();

		ArrayList<HashMap<String, Object>> values = getVarietyValues(db, groupId);

		for (HashMap<String, Object> value : values) {

			int valueId = (Integer) value.get("id");

			Cursor cursor = db.rawQuery(
				"SELECT COALESCE(SUM(c.balance), 0) FROM " +
				TABLE_VARIETY_COMBO_VALUES + " cv " +
				"INNER JOIN " + TABLE_VARIETY_COMBOS + " c ON c.id = cv.combo_id " +
				"WHERE cv.group_id=? AND cv.value_id=?",
				new String[]{String.valueOf(groupId), String.valueOf(valueId)}
			);

			double stock = 0;

			if (cursor.moveToFirst()) {
				stock = cursor.getDouble(0);
			}

			cursor.close();

			value.put("stock", stock);
		}

		return values;
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

	public String getComboLabel(int comboId) {
		return getComboLabel(this.getReadableDatabase(), comboId);
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

	// =====================
	// REPORT: COMBO/VARIETY STOCK (shoes) - single-screen design: one row
	// per shoe item that has variety combos, each carrying its full
	// combos list (every size it comes in, via getVarietyCombos()) so
	// the screen can build its Gender+Size filter dropdown and the
	// selected-size-first item list entirely client-side in Java. Only
	// items matching the shop's shoe-naming convention are included
	// (ShoeIdentity.isShoe()) - Gender isn't a variety group in this
	// schema, it only exists inside the item name, so a non-shoe item
	// with variety combos has no gender to filter by and is correctly
	// left out, same as every other shoes-only report in the app.
	// =====================
	public ArrayList<HashMap<String, Object>> getShoeComboItems() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT DISTINCT i.id, i.code, i.name FROM " + TABLE_ITEMS + " i " +
			"INNER JOIN " + TABLE_VARIETY_COMBOS + " c ON c.item_id = i.id " +
			"ORDER BY i.name",
			null
		);

		while (cursor.moveToNext()) {

			int itemId = cursor.getInt(0);
			String name = cursor.getString(2);

			if (!ShoeIdentity.isShoe(name)) {
				continue;
			}

			HashMap<String, Object> row = new HashMap<>();

			row.put("item_id", itemId);
			row.put("code", cursor.getString(1));
			row.put("name", name);
			row.put("combos", getVarietyCombos(itemId));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// Every item that has at least one variety combo (Size, ...) -
	// feeds the item picker on the Display/Sample Shoes "Add" dialog,
	// since a board entry always needs a specific combo to point at
	// (TABLE_DISPLAY_SHOES/TABLE_SAMPLE_SHOES.combo_id is NOT NULL).
	public ArrayList<HashMap<String, Object>> getItemsWithVarietyCombos() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT DISTINCT i.id, i.code, i.name FROM " + TABLE_ITEMS + " i " +
			"INNER JOIN " + TABLE_VARIETY_COMBOS + " c ON c.item_id = i.id " +
			"ORDER BY i.name",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<>();
			row.put("id", cursor.getInt(0));
			row.put("code", cursor.getString(1));
			row.put("name", cursor.getString(2));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// Current stock for one combo - used by the Display/Sample Shoes
	// sale-time hook to decide whether a just-sold combo hit 0 (auto-
	// remove silently) or still has stock left (ask which physical unit
	// it was).
	public double getComboBalance(int comboId) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT balance FROM " + TABLE_VARIETY_COMBOS + " WHERE id=?",
			new String[]{String.valueOf(comboId)}
		);

		double balance = 0;

		if (cursor.moveToFirst()) {
			balance = cursor.getDouble(0);
		}

		cursor.close();

		return balance;
	}

	// =====================
	// DISPLAY SHOES - a free-form grid the user manually arranges to
	// mirror the physical shelf, one row per shared prefix comment on
	// TABLE_DISPLAY_SHOES. Placing a shoe here is a pure reference
	// overlay - it never reserves/removes stock (see
	// Transactioneditactivity's post-sale hook for the only place a row
	// here is ever removed automatically).
	// =====================
	public ArrayList<HashMap<String, Object>> getDisplayShoes() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT d.id, d.item_id, i.code, d.combo_id, d.row_pos, d.col_pos " +
			"FROM " + TABLE_DISPLAY_SHOES + " d " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = d.item_id " +
			"ORDER BY d.row_pos, d.col_pos",
			null
		);

		while (cursor.moveToNext()) {

			int comboId = cursor.getInt(3);

			HashMap<String, Object> row = new HashMap<>();
			row.put("id", cursor.getInt(0));
			row.put("item_id", cursor.getInt(1));
			row.put("code", cursor.getString(2));
			row.put("combo_id", comboId);
			row.put("combo_label", getComboLabel(db, comboId));
			row.put("row_pos", cursor.getInt(4));
			row.put("col_pos", cursor.getInt(5));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	public long addDisplayShoe(int itemId, int comboId, int rowPos, int colPos) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("combo_id", comboId);
		values.put("row_pos", rowPos);
		values.put("col_pos", colPos);

		return db.insert(TABLE_DISPLAY_SHOES, null, values);
	}

	// Bulk-import counterpart of addDisplayShoe() - operates on the given
	// db instead of getWritableDatabase().
	public long insertDisplayShoeBulk(SQLiteDatabase db, int itemId, int comboId, int rowPos, int colPos) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("combo_id", comboId);
		values.put("row_pos", rowPos);
		values.put("col_pos", colPos);

		return db.insert(TABLE_DISPLAY_SHOES, null, values);
	}

	public void removeDisplayShoeById(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_DISPLAY_SHOES, "id=?", new String[]{String.valueOf(id)});
	}

	// Removes any ONE entry matching this item+combo - the entries are
	// interchangeable (same model, same size, same physical role), so
	// which specific row is removed doesn't matter; see the sale-time
	// hook that calls this. Returns false if there was nothing to remove.
	public boolean removeOneDisplayShoeForCombo(int itemId, int comboId) {

		SQLiteDatabase db = this.getWritableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_DISPLAY_SHOES + " WHERE item_id=? AND combo_id=? LIMIT 1",
			new String[]{String.valueOf(itemId), String.valueOf(comboId)}
		);

		Integer id = cursor.moveToFirst() ? cursor.getInt(0) : null;
		cursor.close();

		if (id == null) {
			return false;
		}

		db.delete(TABLE_DISPLAY_SHOES, "id=?", new String[]{String.valueOf(id)});

		return true;
	}

	public boolean hasDisplayShoeForCombo(int itemId, int comboId) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT 1 FROM " + TABLE_DISPLAY_SHOES + " WHERE item_id=? AND combo_id=? LIMIT 1",
			new String[]{String.valueOf(itemId), String.valueOf(comboId)}
		);

		boolean has = cursor.moveToFirst();
		cursor.close();

		return has;
	}

	// Removing an entire row/column shifts every entry beyond it back by
	// one, so the grid stays contiguous - same reasoning as removing a
	// row/column in a spreadsheet. Adding a row/column is always done at
	// the end (no shifting needed) - see DisplayShoesActivity.
	public void removeDisplayGridRow(int rowPos) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_DISPLAY_SHOES, "row_pos=?", new String[]{String.valueOf(rowPos)});

		db.execSQL(
			"UPDATE " + TABLE_DISPLAY_SHOES + " SET row_pos = row_pos - 1 WHERE row_pos > ?",
			new Object[]{rowPos}
		);
	}

	public void removeDisplayGridColumn(int colPos) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_DISPLAY_SHOES, "col_pos=?", new String[]{String.valueOf(colPos)});

		db.execSQL(
			"UPDATE " + TABLE_DISPLAY_SHOES + " SET col_pos = col_pos - 1 WHERE col_pos > ?",
			new Object[]{colPos}
		);
	}

	// =====================
	// SAMPLE SHOES - same data model and sale-time logic as Display
	// Shoes above, just a plain list instead of a positioned grid (no
	// row_pos/col_pos - see TABLE_SAMPLE_SHOES).
	// =====================
	public ArrayList<HashMap<String, Object>> getSampleShoes() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT s.id, s.item_id, i.code, i.name, s.combo_id " +
			"FROM " + TABLE_SAMPLE_SHOES + " s " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = s.item_id " +
			"ORDER BY i.code",
			null
		);

		while (cursor.moveToNext()) {

			int comboId = cursor.getInt(4);

			HashMap<String, Object> row = new HashMap<>();
			row.put("id", cursor.getInt(0));
			row.put("item_id", cursor.getInt(1));
			row.put("code", cursor.getString(2));
			row.put("name", cursor.getString(3));
			row.put("combo_id", comboId);
			row.put("combo_label", getComboLabel(db, comboId));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	public long addSampleShoe(int itemId, int comboId) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("combo_id", comboId);

		return db.insert(TABLE_SAMPLE_SHOES, null, values);
	}

	// Bulk-import counterpart of addSampleShoe() - operates on the given
	// db instead of getWritableDatabase().
	public long insertSampleShoeBulk(SQLiteDatabase db, int itemId, int comboId) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("combo_id", comboId);

		return db.insert(TABLE_SAMPLE_SHOES, null, values);
	}

	// Bulk-import counterpart of startClearance() - operates on the
	// given db and takes the exact started_date/starting_balance from
	// the backup instead of computing them fresh, so a restore recreates
	// the clearance exactly as it was, not as a brand-new one starting
	// today.
	public long insertItemClearanceBulk(
		SQLiteDatabase db, int itemId, String startedDate, double discountPercent,
		double startingBalance) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("started_date", startedDate);
		values.put("discount_percent", discountPercent);
		values.put("starting_balance", startingBalance);

		return db.insertWithOnConflict(
			TABLE_ITEM_CLEARANCE, null, values, SQLiteDatabase.CONFLICT_REPLACE
		);
	}

	public long insertLoyaltyPointsBulk(
		SQLiteDatabase db, int partyId, int points, String date, String time, String reason) {

		ContentValues values = new ContentValues();
		values.put("party_id", partyId);
		values.put("points", points);
		values.put("date", date);
		values.put("time", time);
		values.put("reason", reason);

		return db.insert(TABLE_LOYALTY_POINTS, null, values);
	}

	public void removeSampleShoeById(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_SAMPLE_SHOES, "id=?", new String[]{String.valueOf(id)});
	}

	public boolean removeOneSampleShoeForCombo(int itemId, int comboId) {

		SQLiteDatabase db = this.getWritableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_SAMPLE_SHOES + " WHERE item_id=? AND combo_id=? LIMIT 1",
			new String[]{String.valueOf(itemId), String.valueOf(comboId)}
		);

		Integer id = cursor.moveToFirst() ? cursor.getInt(0) : null;
		cursor.close();

		if (id == null) {
			return false;
		}

		db.delete(TABLE_SAMPLE_SHOES, "id=?", new String[]{String.valueOf(id)});

		return true;
	}

	public boolean hasSampleShoeForCombo(int itemId, int comboId) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT 1 FROM " + TABLE_SAMPLE_SHOES + " WHERE item_id=? AND combo_id=? LIMIT 1",
			new String[]{String.valueOf(itemId), String.valueOf(comboId)}
		);

		boolean has = cursor.moveToFirst();
		cursor.close();

		return has;
	}

	// Given the value selected for every group of an item, finds the
	// combo row that matches all of them. Returns null if there are no
	// selections (the item has no variety groups - nothing to resolve).
	public Integer resolveComboId(Map<Integer, Integer> groupIdToValueId) {

		if (groupIdToValueId == null || groupIdToValueId.isEmpty()) {
			return null;
		}

		SQLiteDatabase db = this.getWritableDatabase();

		Integer comboId = findComboId(db, groupIdToValueId);

		if (comboId != null) {
			return comboId;
		}

		// No combo exists yet for this exact combination of values - can
		// happen if a value's own combo row never got created (e.g. an
		// older/partial import, or a value added outside the normal
		// "add value" flow that grid-generates one). Rather than handing
		// the caller a null and letting a real, user-picked size
		// silently sell/purchase as untracked stock, create the missing
		// combo on the spot (0 balance - it's genuinely new to the
		// system, there's no history to carry over) the same way
		// addVarietyValue()'s auto-grid does, so this can never happen
		// twice for the same combination.
		db.beginTransaction();

		try {

			int itemId = getItemIdForGroup(
				db, groupIdToValueId.keySet().iterator().next());

			long newComboId = insertComboRow(db, itemId, 0);

			for (Map.Entry<Integer, Integer> entry : groupIdToValueId.entrySet()) {
				linkComboValue(db, newComboId, entry.getKey(), entry.getValue());
			}

			db.setTransactionSuccessful();

			comboId = (int) newComboId;

		} finally {

			db.endTransaction();
		}

		return comboId;
	}

	private Integer findComboId(SQLiteDatabase db, Map<Integer, Integer> groupIdToValueId) {

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

		// THE root cause of "every single Add Sale/Purchase creates a new
		// combo": db.rawQuery() binds every arg in the String[] as TEXT,
		// and SQLite does NOT coerce types when comparing a computed
		// value like COUNT(*) (INTEGER) against a bound TEXT parameter
		// the way it does for a plain column comparison (group_id=? works
		// because "group_id" has INTEGER affinity) - INTEGER 1 vs TEXT
		// '1' are simply never equal. HAVING COUNT(*)=? has therefore
		// always evaluated false, for every call, since this method was
		// written: findComboId() has never once found an existing combo,
		// only ever self-healed a fresh one. CAST(? AS INTEGER) forces
		// the comparison to actually work.
		//
		// More than one combo can match the same value-set if duplicates
		// ever slipped in regardless (e.g. an older buggy export, or a
		// raw data fix that inserted a fresh combo instead of reusing
		// the existing one) - without an explicit order, GROUP BY has no
		// guaranteed row order, so a plain "first match" could silently
		// return an empty duplicate over the real, stocked combo. Always
		// prefer whichever match actually has stock, then the oldest
		// (lowest id) as a deterministic tiebreak.
		Cursor cursor = db.rawQuery(
			"SELECT cv.combo_id FROM " + TABLE_VARIETY_COMBO_VALUES + " cv " +
			"INNER JOIN " + TABLE_VARIETY_COMBOS + " c ON c.id = cv.combo_id " +
			"WHERE " + where.toString() +
			" GROUP BY cv.combo_id HAVING COUNT(*)=CAST(? AS INTEGER)" +
			" ORDER BY c.balance DESC, cv.combo_id ASC",
			args.toArray(new String[0])
		);

		Integer comboId = null;

		if (cursor.moveToFirst()) {
			comboId = cursor.getInt(0);
		}

		cursor.close();

		return comboId;
	}

	// Collapses combos that map to the exact same item + value selections
	// down to one - can happen from an older/buggy export, or a raw data
	// fix that inserted a fresh combo instead of reusing the existing
	// one. Called once after a Vyapar import's variety tables are in
	// place, before line items get their combo_id resolved, so a
	// duplicate can never be the one a line item ends up pointing at.
	// Keeps the lowest (oldest) combo id per group as canonical, moves
	// the others' balance and any line-item references onto it, then
	// deletes the duplicates.
	public void mergeDuplicateVarietyCombos(SQLiteDatabase db) {

		HashMap<Integer, Integer> comboItem = new HashMap<Integer, Integer>();

		Cursor comboCursor = db.rawQuery(
			"SELECT id, item_id FROM " + TABLE_VARIETY_COMBOS, null);

		while (comboCursor.moveToNext()) {
			comboItem.put(comboCursor.getInt(0), comboCursor.getInt(1));
		}

		comboCursor.close();

		HashMap<Integer, String> comboSignature = new HashMap<Integer, String>();

		Cursor cvCursor = db.rawQuery(
			"SELECT combo_id, group_id, value_id FROM " +
			TABLE_VARIETY_COMBO_VALUES + " ORDER BY combo_id, group_id", null);

		while (cvCursor.moveToNext()) {

			int comboId = cvCursor.getInt(0);
			String pair = cvCursor.getInt(1) + ":" + cvCursor.getInt(2);
			String existing = comboSignature.get(comboId);

			comboSignature.put(
				comboId, existing == null ? pair : existing + "," + pair);
		}

		cvCursor.close();

		// item_id -> signature -> combo ids sharing it, in ascending order.
		HashMap<Integer, HashMap<String, ArrayList<Integer>>> groups =
			new HashMap<Integer, HashMap<String, ArrayList<Integer>>>();

		ArrayList<Integer> comboIdsAscending = new ArrayList<Integer>(comboItem.keySet());
		java.util.Collections.sort(comboIdsAscending);

		for (int comboId : comboIdsAscending) {

			int itemId = comboItem.get(comboId);
			String signature = comboSignature.get(comboId);

			if (signature == null) {
				continue;
			}

			if (!groups.containsKey(itemId)) {
				groups.put(itemId, new HashMap<String, ArrayList<Integer>>());
			}

			HashMap<String, ArrayList<Integer>> bySignature = groups.get(itemId);

			if (!bySignature.containsKey(signature)) {
				bySignature.put(signature, new ArrayList<Integer>());
			}

			bySignature.get(signature).add(comboId);
		}

		for (HashMap<String, ArrayList<Integer>> bySignature : groups.values()) {

			for (ArrayList<Integer> comboIds : bySignature.values()) {

				if (comboIds.size() < 2) {
					continue;
				}

				int canonical = comboIds.get(0);

				for (int i = 1; i < comboIds.size(); i++) {

					int dupe = comboIds.get(i);

					db.execSQL(
						"UPDATE " + TABLE_VARIETY_COMBOS +
						" SET balance = balance + (SELECT balance FROM " +
						TABLE_VARIETY_COMBOS + " WHERE id=?) WHERE id=?",
						new Object[]{dupe, canonical}
					);

					db.execSQL(
						"UPDATE " + TABLE_PURCHASE_ITEMS +
						" SET combo_id=? WHERE combo_id=?",
						new Object[]{canonical, dupe}
					);

					db.execSQL(
						"UPDATE sale_items SET combo_id=? WHERE combo_id=?",
						new Object[]{canonical, dupe}
					);

					db.execSQL(
						"DELETE FROM " + TABLE_VARIETY_COMBO_VALUES + " WHERE combo_id=?",
						new Object[]{dupe}
					);

					db.execSQL(
						"DELETE FROM " + TABLE_VARIETY_COMBOS + " WHERE id=?",
						new Object[]{dupe}
					);
				}
			}
		}
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

		return insertPurchase(
			partyId, date, time, invoiceNumber, grandTotal, amountPaid, notes, null
		);
	}

	public long insertPurchase(
        int partyId,
        String date,
        String time,
        String invoiceNumber,
        double grandTotal,
        double amountPaid,
        String notes,
        String dueDate
	) {

		return insertPurchase(
			partyId, date, time, invoiceNumber, grandTotal, amountPaid, notes, 0, true, dueDate
		);
	}

	public long insertPurchase(
        int partyId,
        String date,
        String time,
        String invoiceNumber,
        double grandTotal,
        double amountPaid,
        String notes,
        double otherCharges,
        boolean otherChargesToParty
	) {

		return insertPurchase(
			partyId, date, time, invoiceNumber, grandTotal, amountPaid, notes,
			otherCharges, otherChargesToParty, null
		);
	}

	public long insertPurchase(
        int partyId,
        String date,
        String time,
        String invoiceNumber,
        double grandTotal,
        double amountPaid,
        String notes,
        double otherCharges,
        boolean otherChargesToParty,
        String dueDate
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
		values.put("other_charges", otherCharges);
		values.put("other_charges_to_party", otherChargesToParty ? 1 : 0);
		values.put("due_date", dueDate);

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
	public static final int TRANSACTION_TYPE_EXPENSE = 4;

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
			"UNION ALL " +
			"SELECT e.id, e.code, pa5.name, e.date, e.amount, " +
			TRANSACTION_TYPE_EXPENSE + " AS transaction_type " +
			"FROM " + TABLE_EXPENSES + " e " +
			"LEFT JOIN " + TABLE_PARTIES + " pa5 " +
			"ON e.party_id = pa5.id " +
			"WHERE e.party_id = ? " +
			"ORDER BY date DESC",

			new String[]{
				String.valueOf(partyId),
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
			"ORDER BY p.date DESC, p.time DESC",

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

	// Used by every item picker (Sale/Purchase's item dialog, Wanted
	// Items) - active=0 items are discontinued and shouldn't be offered
	// for a new entry, so this is the one place items.active is
	// actually filtered on (see CLAUDE.md's "universal filter" note;
	// the plain Items list itself still shows everything, with its own
	// All/Active/Inactive filter for management).
	public ArrayList<HashMap<String, Object>> getItemsForSpinner() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, code, name, purchase_price, sale_price, balance " +
			"FROM " + TABLE_ITEMS +
			" WHERE active = 1" +
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
			"p.amount_paid, p.notes, p.other_charges, p.other_charges_to_party, " +
			"p.due_date " +
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
			map.put("other_charges", cursor.getDouble(8));
			map.put("other_charges_to_party", cursor.getInt(9) != 0);
			map.put("due_date", cursor.getString(10));
		}

		cursor.close();

		return map;
	}

	// =====================
	// Blends a newly-arrived batch's per-unit extra cost (its allocated
	// share of that purchase's other_charges - transport/shipping/etc.,
	// split across the purchase's line items by value) into the item's
	// running extra_cost_per_unit by weighted average against its
	// current stock. This is the same "single current cost" approach
	// the rest of the app already takes with purchase_price - not a
	// per-batch/lot cost, since stock itself isn't lot-tracked.
	//
	// Must be called BEFORE this item's balance is adjusted for the
	// incoming quantity (i.e. before insertPurchaseItem()), so the
	// weighting reflects stock as it stood immediately before this
	// purchase. Only ever called for a brand-new purchase - editing or
	// deleting one doesn't try to reverse an already-blended average,
	// same as purchase_price is never retroactively corrected either.
	// =====================
	public void applyExtraCostToItem(
		int itemId, double incomingQty, double incomingExtraCostTotal) {

		if (incomingQty <= 0) {
			return;
		}

		SQLiteDatabase db = this.getWritableDatabase();

		double oldBalance = 0;
		double oldExtraCostPerUnit = 0;

		Cursor cursor = db.rawQuery(
			"SELECT balance, extra_cost_per_unit FROM " + TABLE_ITEMS + " WHERE id=?",
			new String[]{String.valueOf(itemId)}
		);

		if (cursor.moveToFirst()) {
			oldBalance = cursor.getDouble(0);
			oldExtraCostPerUnit = cursor.getDouble(1);
		}

		cursor.close();

		// A zero/negative existing balance contributes no weight - the
		// incoming batch is effectively starting the average fresh.
		double oldWeight = Math.max(oldBalance, 0);

		double incomingExtraCostPerUnit = incomingExtraCostTotal / incomingQty;

		double newExtraCostPerUnit =
			(oldExtraCostPerUnit * oldWeight + incomingExtraCostPerUnit * incomingQty) /
			(oldWeight + incomingQty);

		ContentValues values = new ContentValues();
		values.put("extra_cost_per_unit", newExtraCostPerUnit);

		db.update(TABLE_ITEMS, values, "id=?", new String[]{String.valueOf(itemId)});
	}

	// =====================
	// Every existing Expense not already linked to any purchase (see
	// TABLE_PURCHASE_EXPENSE_LINKS) - the candidate list for
	// Transactioneditactivity's "Select Expenses" picker. Once an expense
	// has been linked (to one or more purchases, decided together in one
	// sitting - see applyExpensePurchaseLinks()), it drops out of this
	// list for good; there's no re-opening it later to add more
	// purchases, same "not retroactively corrected" simplification as
	// the rest of this landed-cost machinery.
	// =====================
	public ArrayList<HashMap<String, Object>> getUnlinkedExpenses(String search) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		String sql =
			"SELECT id, item, amount, date, time, notes FROM " + TABLE_EXPENSES +
			" WHERE id NOT IN (SELECT expense_id FROM " + TABLE_PURCHASE_EXPENSE_LINKS + ")";

		ArrayList<String> args = new ArrayList<>();

		if (search != null && search.trim().length() > 0) {
			sql += " AND item LIKE ?";
			args.add("%" + search.trim() + "%");
		}

		sql += " ORDER BY date DESC, time DESC";

		Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]));

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("id", cursor.getInt(0));
			map.put("item", cursor.getString(1));
			map.put("amount", cursor.getDouble(2));
			map.put("date", cursor.getString(3));
			map.put("time", cursor.getString(4));
			map.put("notes", cursor.getString(5));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
	// Splits one Expense's amount across the purchase(s) it's being
	// linked to, proportionally by each purchase's own grand_total
	// (rounded to the nearest rupee; the last selection absorbs whatever
	// rounding remainder is left so the parts sum exactly to totalAmount -
	// same approach the old Purchase Costs design used). selections is
	// one map per selected purchase: "purchase_id" -> Integer.
	//
	// Deliberately does NOT touch cash or any party balance - the expense
	// row itself already owns that (its own paid_amount/party_id, exactly
	// as an ordinary standalone expense would). This only decides how
	// much of it blends into each linked purchase's line items'
	// extra_cost_per_unit.
	// =====================
	public void applyExpensePurchaseLinks(
		int expenseId, double totalAmount, ArrayList<HashMap<String, Object>> selections) {

		if (selections == null || selections.isEmpty()) {
			return;
		}

		SQLiteDatabase db = this.getWritableDatabase();

		double[] purchaseTotals = new double[selections.size()];
		double totalsSum = 0;

		for (int i = 0; i < selections.size(); i++) {

			int purchaseId = (Integer) selections.get(i).get("purchase_id");

			double purchaseTotal = sumColumn(
				db,
				"SELECT grand_total FROM " + TABLE_PURCHASES + " WHERE id=?",
				new String[]{String.valueOf(purchaseId)}
			);

			purchaseTotals[i] = purchaseTotal;
			totalsSum += purchaseTotal;
		}

		double allocatedSoFar = 0;

		for (int i = 0; i < selections.size(); i++) {

			int purchaseId = (Integer) selections.get(i).get("purchase_id");

			double sharePercent;
			double allocatedAmount;

			boolean isLast = (i == selections.size() - 1);

			if (isLast) {
				// Absorb rounding remainder so the parts sum exactly.
				allocatedAmount = totalAmount - allocatedSoFar;
				sharePercent = totalsSum > 0 ? (purchaseTotals[i] / totalsSum) * 100 : 0;
			} else if (totalsSum > 0) {
				sharePercent = (purchaseTotals[i] / totalsSum) * 100;
				allocatedAmount = Math.round(totalAmount * (purchaseTotals[i] / totalsSum));
			} else {
				// No purchase values to weight by (e.g. all zero) - split evenly.
				sharePercent = 100.0 / selections.size();
				allocatedAmount = Math.round(totalAmount / selections.size());
			}

			allocatedSoFar += allocatedAmount;

			recordExpensePurchaseLinkRow(db, expenseId, purchaseId, sharePercent, allocatedAmount);

			blendCostIntoPurchaseItems(db, purchaseId, allocatedAmount);
		}
	}

	// =====================
	// Inserts one purchase_expense_links row - the metadata half of
	// applying a link, factored out so applySingleExpensePurchaseLink()
	// (which must NOT also blend - see its own comment) can share it
	// with applyExpensePurchaseLinks().
	// =====================
	private void recordExpensePurchaseLinkRow(
		SQLiteDatabase db, int expenseId, int purchaseId,
		double sharePercent, double allocatedAmount) {

		ContentValues values = new ContentValues();
		values.put("expense_id", expenseId);
		values.put("purchase_id", purchaseId);
		values.put("share_percent", sharePercent);
		values.put("allocated_amount", allocatedAmount);

		db.insert(TABLE_PURCHASE_EXPENSE_LINKS, null, values);
	}

	// =====================
	// Records a 100%-share purchase_expense_links row for a purchase
	// being saved for the very first time, WITHOUT blending
	// allocatedAmount into that purchase's line items - unlike
	// applyExpensePurchaseLinks() (used for already-saved purchases,
	// whose purchase_items rows already exist), a brand-new purchase's
	// items haven't been written yet when its pending linked expense is
	// applied, and blending must happen per-line BEFORE
	// insertPurchaseItem() runs (see applyExtraCostToItem()'s own
	// ordering note) - so the caller (Transactioneditactivity#
	// savePurchase()) does that part manually, ahead of calling this.
	// =====================
	public void applySingleExpensePurchaseLink(
		int expenseId, int purchaseId, double allocatedAmount) {

		SQLiteDatabase db = this.getWritableDatabase();

		recordExpensePurchaseLinkRow(db, expenseId, purchaseId, 100.0, allocatedAmount);
	}

	// =====================
	// Every purchase_expense_links row applied to one purchase, with the
	// linked expense's item/date - for Transactioneditactivity's "Linked
	// Expenses" section when editing an already-saved purchase (a
	// brand-new one still being entered shows its own in-memory pending
	// list instead).
	// =====================
	public ArrayList<HashMap<String, Object>> getLinkedExpensesForPurchase(int purchaseId) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT pel.id, pel.expense_id, e.item, pel.allocated_amount, e.date " +
			"FROM " + TABLE_PURCHASE_EXPENSE_LINKS + " pel " +
			"INNER JOIN " + TABLE_EXPENSES + " e ON e.id = pel.expense_id " +
			"WHERE pel.purchase_id=?",
			new String[]{String.valueOf(purchaseId)}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<>();

			map.put("link_id", cursor.getInt(0));
			map.put("expense_id", cursor.getInt(1));
			map.put("item", cursor.getString(2));
			map.put("allocated_amount", cursor.getDouble(3));
			map.put("date", cursor.getString(4));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
	// Removes one purchase_expense_links row (e.g. "Unlink" on an
	// already-saved purchase's Linked Expenses list). Same simplification
	// as the rest of this feature: the extra_cost_per_unit blend this
	// link already applied is NOT reversed, only the record itself is
	// removed - and since the expense is no longer linked to anything,
	// it becomes selectable again from "Select Expenses" (see
	// getUnlinkedExpenses()) and reappears in the plain Expenses list.
	// =====================
	public boolean deletePurchaseExpenseLink(int linkId) {

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.delete(
			TABLE_PURCHASE_EXPENSE_LINKS, "id=?", new String[]{String.valueOf(linkId)}
		);

		return rows > 0;
	}

	// =====================
	// Blends one linked expense's allocatedAmount (its share for THIS
	// purchase) into that purchase's own line items' extra_cost_per_unit,
	// splitting it across those lines proportionally by each line's own
	// total (same "by value" approach applyExpensePurchaseLinks() uses to
	// split across purchases).
	// =====================
	private void blendCostIntoPurchaseItems(
		SQLiteDatabase db, int purchaseId, double allocatedAmount) {

		if (allocatedAmount == 0) {
			return;
		}

		Cursor cursor = db.rawQuery(
			"SELECT item_id, quantity, total FROM " + TABLE_PURCHASE_ITEMS + " WHERE purchase_id=?",
			new String[]{String.valueOf(purchaseId)}
		);

		ArrayList<Integer> itemIds = new ArrayList<>();
		ArrayList<Double> quantities = new ArrayList<>();
		ArrayList<Double> totals = new ArrayList<>();
		double subtotal = 0;

		while (cursor.moveToNext()) {
			itemIds.add(cursor.getInt(0));
			quantities.add(cursor.getDouble(1));
			double lineTotal = cursor.getDouble(2);
			totals.add(lineTotal);
			subtotal += lineTotal;
		}
		cursor.close();

		if (subtotal <= 0) {
			return;
		}

		for (int i = 0; i < itemIds.size(); i++) {

			double lineShare = totals.get(i) / subtotal;
			applyExtraCostToItem(itemIds.get(i), quantities.get(i), allocatedAmount * lineShare);
		}
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

		// Keep the item's own Purchase Price current with what was actually
		// paid on the most recent purchase - this used to be a manual-only
		// field (only ever set from Add/Edit Item), which is how items
		// bought for real through this exact screen could still end up
		// sitting at a purchase price of 0 forever. Only for a real,
		// positive price - a $0 line (a freebie/correction) shouldn't wipe
		// out the item's known cost.
		if (purchasePrice > 0) {

			ContentValues itemValues = new ContentValues();
			itemValues.put("purchase_price", purchasePrice);

			db.update(TABLE_ITEMS, itemValues, "id=?", new String[]{String.valueOf(itemId)});
		}

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

			// This purchase's unpaid portion (if any) shifted its
			// party's balance when it was saved - reverse that before
			// the row disappears, same reversal updatePurchase() already
			// does on edit, or the party's balance would be left
			// stranded with an adjustment for a purchase that no longer
			// exists.
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

		return updatePurchase(
			purchaseId, partyId, date, time, invoiceNumber, grandTotal,
			amountPaid, notes, 0, true, null
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
        String notes,
        double otherCharges,
        boolean otherChargesToParty) {

		return updatePurchase(
			purchaseId, partyId, date, time, invoiceNumber, grandTotal,
			amountPaid, notes, otherCharges, otherChargesToParty, null
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
        String notes,
        double otherCharges,
        boolean otherChargesToParty,
        String dueDate) {

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
		values.put("other_charges", otherCharges);
		values.put("other_charges_to_party", otherChargesToParty ? 1 : 0);
		values.put("due_date", dueDate);

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

		sql += "ORDER BY p.date DESC, p.time DESC";

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

		values.put(
			"due_date",
			saleData.get("due_date") == null ? null : saleData.get("due_date").toString()
		);

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
			"ORDER BY s.date DESC, s.time DESC",

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
			"ORDER BY s.date DESC, s.time DESC",

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
	// REPORT: AVERAGE CART SIZE/AMOUNT FOR A DATE RANGE
	// "Cart size" is the count of DISTINCT items on a sale (not total
	// quantity) - 2 units of item B716 plus 3 units of item B719 is a
	// cart size of 2, and 2+3 units of the SAME item across two size/
	// combo lines is still 1. COUNT(DISTINCT si.item_id) already reads
	// 0 for a sale with no line items (COUNT DISTINCT ignores the NULL
	// a LEFT JOIN with no matches produces), so that edge case still
	// pulls the average down like it should with no extra handling.
	// =====================
	public HashMap<String, Object> getCartStatsSummary(String fromDate, String toDate) {

		HashMap<String, Object> map = new HashMap<String, Object>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT COUNT(*), COALESCE(AVG(item_count), 0), COALESCE(AVG(grand_total), 0) " +
			"FROM (" +
			"SELECT s.id, s.grand_total, COUNT(DISTINCT si.item_id) AS item_count " +
			"FROM sales s LEFT JOIN sale_items si ON si.sale_id = s.id " +
			"WHERE s.date BETWEEN ? AND ? " +
			"GROUP BY s.id" +
			")",
			new String[]{fromDate, toDate}
		);

		int count = 0;
		double avgCartSize = 0;
		double avgCartAmount = 0;

		if (cursor.moveToFirst()) {

			count = cursor.getInt(0);
			avgCartSize = cursor.getDouble(1);
			avgCartAmount = cursor.getDouble(2);
		}

		cursor.close();

		map.put("count", count);
		map.put("avg_cart_size", avgCartSize);
		map.put("avg_cart_amount", avgCartAmount);

		return map;
	}

	// =====================
	// REPORT: PROFIT SPLIT - CASH SALE VS NAMED PARTY, FOR A DATE RANGE
	// A sale counts as "Cash Sale" if it has no party at all, or its
	// party is literally named "Cash Sale" (the synthetic party this app
	// and a Vyapar import both use for a walk-in/unnamed customer) -
	// every other party is a real, named customer. Profit per line item
	// mirrors Net Profit's own cost basis (amount - qty * current
	// landed cost, i.e. purchase_price + extra_cost_per_unit).
	// =====================
	public HashMap<String, Object> getProfitSplitByPartyType(String fromDate, String toDate) {

		HashMap<String, Object> map = new HashMap<String, Object>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT " +
			"CASE WHEN pa.id IS NULL OR pa.name = 'Cash Sale' THEN 'cash' ELSE 'party' END AS bucket, " +
			"COALESCE(SUM(si.amount - si.qty * (i.purchase_price + i.extra_cost_per_unit)), 0) AS profit " +
			"FROM sales s " +
			"LEFT JOIN " + TABLE_PARTIES + " pa ON pa.id = s.party_id " +
			"INNER JOIN sale_items si ON si.sale_id = s.id " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = si.item_id " +
			"WHERE s.date BETWEEN ? AND ? " +
			"GROUP BY bucket",
			new String[]{fromDate, toDate}
		);

		double cashProfit = 0;
		double partyProfit = 0;

		while (cursor.moveToNext()) {

			String bucket = cursor.getString(0);
			double profit = cursor.getDouble(1);

			if ("cash".equals(bucket)) {
				cashProfit = profit;
			} else {
				partyProfit = profit;
			}
		}

		cursor.close();

		double totalProfit = cashProfit + partyProfit;

		double cashPercent = totalProfit == 0 ? 0 : (cashProfit / totalProfit) * 100.0;
		double partyPercent = totalProfit == 0 ? 0 : (partyProfit / totalProfit) * 100.0;

		map.put("cash_profit", cashProfit);
		map.put("party_profit", partyProfit);
		map.put("total_profit", totalProfit);
		map.put("cash_percent", cashPercent);
		map.put("party_percent", partyPercent);

		return map;
	}

	// =====================
	// REPORTS: SALES BY PARTY FOR A DATE RANGE
	// =====================

	public static final int SORT_AMOUNT_DESC = 0;
	public static final int SORT_AMOUNT_ASC = 1;
	public static final int SORT_NAME_ASC = 2;
	public static final int SORT_COUNT_DESC = 3;

	// Top Customers ranking - by profit contributed, not just amount
	// spent (a party that haggles hard on a big-ticket item can spend
	// more than one who doesn't, while contributing less profit).
	public static final int SORT_PROFIT_DESC = 4;
	public static final int SORT_PROFIT_ASC = 5;

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

			case SORT_PROFIT_ASC:
				orderBy = "profit ASC";
				break;

			case SORT_PROFIT_DESC:
				orderBy = "profit DESC";
				break;

			case SORT_AMOUNT_DESC:
			default:
				orderBy = "total DESC";
				break;
		}

		SQLiteDatabase db = this.getReadableDatabase();

		// profit is always computed (not only when sorting by it) so a
		// Top Customers view of this same list can show it alongside
		// amount without a second query - same item-cost-minus-sales-
		// amount formula as getNetProfitByItem(), just grouped by party
		// instead of by item.
		Cursor cursor = db.rawQuery(

			"SELECT p.id, p.name, COUNT(s.id) AS cnt, " +
			"COALESCE(SUM(s.grand_total), 0) AS total, " +
			"COALESCE((" +
			"SELECT SUM(si.amount) - SUM(si.qty * (i.purchase_price + i.extra_cost_per_unit)) " +
			"FROM sale_items si " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = si.item_id " +
			"WHERE si.sale_id IN (" +
			"SELECT id FROM sales WHERE party_id = p.id AND date BETWEEN ? AND ?" +
			")" +
			"), 0) AS profit " +
			"FROM " + TABLE_PARTIES + " p " +
			"INNER JOIN sales s " +
			"ON s.party_id = p.id " +
			"WHERE s.date BETWEEN ? AND ? " +
			"GROUP BY p.id, p.name " +
			"ORDER BY " + orderBy,

			new String[]{
				fromDate,
				toDate,
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
			map.put("profit", cursor.getDouble(4));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	// =====================
	// REPORT: CREDIT DUE - every not-fully-paid Sale whose Due Date
	// (see Transactioneditactivity's Due Date field) falls within the
	// given period - only a Sale carries a due date in this app, so
	// Purchases/Payments/Expenses have nothing to show here. A sale
	// saved before this column existed has no due date at all and is
	// left out, since there's no date to filter it by.
	// =====================
	public ArrayList<HashMap<String, Object>> getCreditDueSales(String fromDate, String toDate) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT s.id, s.invoice_no, p.name, s.due_date, s.balance " +
			"FROM sales s " +
			"LEFT JOIN parties p ON s.party_id = p.id " +
			"WHERE s.balance > 0.01 AND s.due_date IS NOT NULL " +
			"AND s.due_date BETWEEN ? AND ? " +
			"ORDER BY s.due_date ASC",
			new String[]{fromDate, toDate}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<>();

			row.put("sale_id", cursor.getInt(0));
			row.put("invoice_no", cursor.getString(1));
			row.put("party_name", cursor.getString(2));
			row.put("due_date", cursor.getString(3));
			row.put("balance", cursor.getDouble(4));

			list.add(row);
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
	// "An item starts with 'Shoe' is a shoe item" - the whole app's
	// shoes/non-shoes split (item reports, Stock Worth) is this one
	// name-prefix rule; SQLite's LIKE is case-insensitive for ASCII, so
	// this also matches "shoe...", "SHOE...", etc.
	public static final int SHOES_FILTER_ALL = 0;
	public static final int SHOES_FILTER_SHOES_ONLY = 1;
	public static final int SHOES_FILTER_NON_SHOES_ONLY = 2;

	public ArrayList<HashMap<String, Object>> getItemSalesRanking(
		String weekStart,
		String monthStart,
		String quarterStart,
		String sixMonthStart,
		String nineMonthStart,
		String yearStart,
		int sortBy,
		int shoesFilter) {

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
			sortBy,
			shoesFilter
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
			sortBy,
			SHOES_FILTER_ALL
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
		int sortBy,
		int shoesFilter) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		// sale_items has no date of its own, so it needs joining
		// through sales; the sales table already has one.
		String dateJoin = salesTable.equals("sale_items") ?
			"INNER JOIN sales sl ON sl.id = st.sale_id " :
			"";

		String dateColumn = salesTable.equals("sale_items") ?
			"sl.date" :
			"st.date";

		// Only meaningful for the items entity - parties have no shoes
		// concept, and this whole clause is skipped for them.
		String shoesWhere = "";

		if (entityTable.equals(TABLE_ITEMS) && shoesFilter == SHOES_FILTER_SHOES_ONLY) {
			shoesWhere = "WHERE e.name LIKE 'Shoe%' ";
		} else if (entityTable.equals(TABLE_ITEMS) && shoesFilter == SHOES_FILTER_NON_SHOES_ONLY) {
			shoesWhere = "WHERE e.name NOT LIKE 'Shoe%' ";
		}

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
			shoesWhere +
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

	// Every "yyyy-MM" month from startMonth to endMonth, inclusive.
	private ArrayList<String> enumerateMonthRange(String startMonth, String endMonth) {

		ArrayList<String> months = new ArrayList<String>();

		SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM", Locale.US);

		Calendar cal = Calendar.getInstance();

		try {
			cal.setTime(monthFormat.parse(startMonth));
		} catch (Exception e) {
			months.add(startMonth);
			return months;
		}

		cal.set(Calendar.DAY_OF_MONTH, 1);

		for (int i = 0; i < 1200; i++) {

			String month = monthFormat.format(cal.getTime());

			months.add(month);

			if (month.equals(endMonth)) {
				break;
			}

			cal.add(Calendar.MONTH, 1);
		}

		return months;
	}

	// =====================
	// REPORT: NET PROFIT (sale total - item cost - expenses) FOR A DATE
	// RANGE. fromDate/toDate null means All Time (no date bound). Item
	// cost is each sale line's quantity times its item's CURRENT landed
	// cost (purchase_price + extra_cost_per_unit, the latter a running
	// weighted-average transport/shipping cost - see
	// applyExtraCostToItem()) - this app doesn't keep a historical cost
	// snapshot per sale, so a cost change today also reshapes past
	// periods' profit, same simplification the rest of the app already
	// makes by treating an item's cost as a single current figure.
	// =====================
	public HashMap<String, Object> getNetProfitSummary(String fromDate, String toDate) {

		HashMap<String, Object> map = new HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();

		boolean allTime = fromDate == null || toDate == null;

		String[] args = allTime ? null : new String[]{fromDate, toDate};

		double salesTotal = sumColumn(
			db,
			"SELECT COALESCE(SUM(grand_total), 0) FROM sales" +
			(allTime ? "" : " WHERE date BETWEEN ? AND ?"),
			args
		);

		double itemCost = sumColumn(
			db,
			"SELECT COALESCE(SUM(si.qty * (i.purchase_price + i.extra_cost_per_unit)), 0) " +
			"FROM sale_items si " +
			"INNER JOIN sales s ON s.id = si.sale_id " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = si.item_id" +
			(allTime ? "" : " WHERE s.date BETWEEN ? AND ?"),
			args
		);

		// Excludes an expense already linked to a purchase (see
		// TABLE_PURCHASE_EXPENSE_LINKS) - its cost is already inside
		// itemCost above via that purchase's items' extra_cost_per_unit,
		// so counting it again here would subtract it from profit twice.
		double expensesTotal = sumColumn(
			db,
			"SELECT COALESCE(SUM(amount), 0) FROM " + TABLE_EXPENSES +
			" WHERE id NOT IN (SELECT expense_id FROM " + TABLE_PURCHASE_EXPENSE_LINKS + ")" +
			(allTime ? "" : " AND date BETWEEN ? AND ?"),
			args
		);

		map.put("sales_total", salesTotal);
		map.put("item_cost", itemCost);
		map.put("expenses_total", expensesTotal);
		map.put("net_profit", salesTotal - itemCost - expensesTotal);

		return map;
	}

	// =====================
	// SALES BY CATEGORY FOR RANGE - "category" is the first word of each
	// sold item's name, the same proxy Stock Value's by-category
	// breakdown uses (there's no formal category field). Used by
	// Month-over-Month's category comparison, called once per period.
	// =====================
	public LinkedHashMap<String, Double> getSalesByCategoryForRange(String fromDate, String toDate) {

		LinkedHashMap<String, Double> result = new LinkedHashMap<String, Double>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT i.name, SUM(si.amount) FROM sale_items si " +
			"INNER JOIN sales s ON s.id = si.sale_id " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = si.item_id " +
			"WHERE s.date BETWEEN ? AND ? " +
			"GROUP BY i.id",
			new String[]{fromDate, toDate}
		);

		while (cursor.moveToNext()) {

			String name = cursor.getString(0);
			double amount = cursor.getDouble(1);

			String category =
				(name == null || name.trim().isEmpty()) ? "Other" : name.trim().split("\\s+")[0];

			Double existing = result.get(category);
			result.put(category, (existing == null ? 0.0 : existing) + amount);
		}

		cursor.close();

		return result;
	}

	// =====================
	// CROSS-SELL INSIGHT - for a given item, which other items most often
	// appear in the same Sale (co-occurrence count, across every sale
	// that included this item) - "frequently_together", only items that
	// co-occurred at least once, highest count first; and the reverse
	// direction, "rarely_together": every other active item that has
	// sold at all, sorted by that same co-occurrence count ascending (0
	// first) - a possible missed cross-sell opportunity, since an item
	// with zero sales ever isn't a meaningful candidate to compare
	// against. Both lists exclude the item itself.
	// =====================
	public HashMap<String, Object> getCrossSellInsight(int itemId) {

		HashMap<String, Object> result = new HashMap<String, Object>();

		SQLiteDatabase db = this.getReadableDatabase();

		ArrayList<Integer> saleIds = new ArrayList<Integer>();

		Cursor saleCursor = db.rawQuery(
			"SELECT DISTINCT sale_id FROM sale_items WHERE item_id=?",
			new String[]{String.valueOf(itemId)}
		);

		while (saleCursor.moveToNext()) {
			saleIds.add(saleCursor.getInt(0));
		}

		saleCursor.close();

		HashMap<Integer, Integer> coOccurrence = new HashMap<Integer, Integer>();

		if (!saleIds.isEmpty()) {

			StringBuilder idList = new StringBuilder();

			for (int i = 0; i < saleIds.size(); i++) {

				if (i > 0) {
					idList.append(",");
				}

				idList.append(saleIds.get(i));
			}

			Cursor coCursor = db.rawQuery(
				"SELECT item_id, COUNT(DISTINCT sale_id) FROM sale_items " +
				"WHERE sale_id IN (" + idList + ") AND item_id != ? " +
				"GROUP BY item_id",
				new String[]{String.valueOf(itemId)}
			);

			while (coCursor.moveToNext()) {
				coOccurrence.put(coCursor.getInt(0), coCursor.getInt(1));
			}

			coCursor.close();
		}

		ArrayList<HashMap<String, Object>> allSoldItems = new ArrayList<HashMap<String, Object>>();

		Cursor allCursor = db.rawQuery(
			"SELECT DISTINCT i.id, i.code, i.name FROM " + TABLE_ITEMS + " i " +
			"INNER JOIN sale_items si ON si.item_id = i.id " +
			"WHERE i.id != ? AND i.active = 1",
			new String[]{String.valueOf(itemId)}
		);

		while (allCursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();

			int otherItemId = allCursor.getInt(0);

			row.put("item_id", otherItemId);
			row.put("code", allCursor.getString(1));
			row.put("name", allCursor.getString(2));

			Integer count = coOccurrence.get(otherItemId);
			row.put("co_occurrence_count", count == null ? 0 : count);

			allSoldItems.add(row);
		}

		allCursor.close();

		ArrayList<HashMap<String, Object>> frequentlyTogether =
			new ArrayList<HashMap<String, Object>>();

		for (HashMap<String, Object> row : allSoldItems) {

			if ((Integer) row.get("co_occurrence_count") > 0) {
				frequentlyTogether.add(row);
			}
		}

		Collections.sort(
			frequentlyTogether,
			new Comparator<HashMap<String, Object>>() {
				@Override
				public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
					return (Integer) b.get("co_occurrence_count") - (Integer) a.get("co_occurrence_count");
				}
			}
		);

		ArrayList<HashMap<String, Object>> rarelyTogether =
			new ArrayList<HashMap<String, Object>>(allSoldItems);

		Collections.sort(
			rarelyTogether,
			new Comparator<HashMap<String, Object>>() {
				@Override
				public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
					return (Integer) a.get("co_occurrence_count") - (Integer) b.get("co_occurrence_count");
				}
			}
		);

		result.put("frequently_together", frequentlyTogether);
		result.put("rarely_together", rarelyTogether);

		return result;
	}

	// =====================
	// REPORT: NET PROFIT BY ITEM - the same Net Profit period, broken
	// down per item (only items with at least one sale in the period -
	// the INNER JOINs below drop everything else on their own). Same
	// cost-basis simplification as getNetProfitSummary() (current
	// purchase_price + extra_cost_per_unit, not a historical snapshot),
	// and the same "expenses aren't attributable to one item" reasoning
	// - this is sales minus item cost only, with no expense share
	// subtracted per item.
	// =====================
	public ArrayList<HashMap<String, Object>> getNetProfitByItem(
		String fromDate, String toDate, boolean ascending, int shoesFilter) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		boolean allTime = fromDate == null || toDate == null;

		ArrayList<String> conditions = new ArrayList<>();
		ArrayList<String> argList = new ArrayList<>();

		if (!allTime) {
			conditions.add("s.date BETWEEN ? AND ?");
			argList.add(fromDate);
			argList.add(toDate);
		}

		if (shoesFilter == SHOES_FILTER_SHOES_ONLY) {
			conditions.add("i.name LIKE 'Shoe%'");
		} else if (shoesFilter == SHOES_FILTER_NON_SHOES_ONLY) {
			conditions.add("i.name NOT LIKE 'Shoe%'");
		}

		StringBuilder whereClause = new StringBuilder();

		for (int i = 0; i < conditions.size(); i++) {
			whereClause.append(i == 0 ? "WHERE " : " AND ").append(conditions.get(i));
		}

		if (whereClause.length() > 0) {
			whereClause.append(" ");
		}

		String sql =
			"SELECT i.id, i.code, i.name, SUM(si.qty) AS qty, " +
			"SUM(si.amount) AS sales_amount, " +
			"SUM(si.qty * (i.purchase_price + i.extra_cost_per_unit)) AS item_cost, " +
			"SUM(si.amount) - SUM(si.qty * (i.purchase_price + i.extra_cost_per_unit)) AS profit " +
			"FROM sale_items si " +
			"INNER JOIN sales s ON s.id = si.sale_id " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = si.item_id " +
			whereClause +
			"GROUP BY i.id " +
			"ORDER BY profit " + (ascending ? "ASC" : "DESC");

		Cursor cursor = db.rawQuery(sql, argList.toArray(new String[0]));

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<>();

			row.put("item_id", cursor.getInt(0));
			row.put("item_code", cursor.getString(1));
			row.put("item_name", cursor.getString(2));
			row.put("qty", cursor.getDouble(3));
			row.put("sales_amount", cursor.getDouble(4));
			row.put("item_cost", cursor.getDouble(5));
			row.put("profit", cursor.getDouble(6));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// =====================
	// STANDING MARGIN BY ITEM - the other half of the merged Item
	// Profitability report alongside getNetProfitByItem()'s "This
	// Period" actuals: every active item's own current margin per unit
	// (sale_price - purchase_price - extra_cost_per_unit) and margin %,
	// straight from the item master - period-independent, and includes
	// an item with zero sales ever (unlike getNetProfitByItem(), which
	// only lists items that actually sold in the period).
	// =====================
	public ArrayList<HashMap<String, Object>> getStandingMarginByItem(
		boolean ascending, int shoesFilter) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		ArrayList<String> conditions = new ArrayList<>();
		conditions.add("active = 1");

		if (shoesFilter == SHOES_FILTER_SHOES_ONLY) {
			conditions.add("name LIKE 'Shoe%'");
		} else if (shoesFilter == SHOES_FILTER_NON_SHOES_ONLY) {
			conditions.add("name NOT LIKE 'Shoe%'");
		}

		StringBuilder whereClause = new StringBuilder("WHERE ");

		for (int i = 0; i < conditions.size(); i++) {
			whereClause.append(i == 0 ? "" : " AND ").append(conditions.get(i));
		}

		String sql =
			"SELECT id, code, name, sale_price, " +
			"(sale_price - purchase_price - extra_cost_per_unit) AS margin, " +
			"CASE WHEN sale_price > 0 " +
			"THEN (sale_price - purchase_price - extra_cost_per_unit) / sale_price * 100 " +
			"ELSE 0 END AS margin_percent " +
			"FROM " + TABLE_ITEMS + " " + whereClause + " " +
			"ORDER BY margin " + (ascending ? "ASC" : "DESC");

		Cursor cursor = db.rawQuery(sql, null);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<>();

			row.put("item_id", cursor.getInt(0));
			row.put("item_code", cursor.getString(1));
			row.put("item_name", cursor.getString(2));
			row.put("sale_price", cursor.getDouble(3));
			row.put("margin", cursor.getDouble(4));
			row.put("margin_percent", cursor.getDouble(5));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// =====================
	// MARGIN EROSION - items that sold in both This Month (1st-today)
	// and the full previous month, whose margin % dropped by at least
	// MARGIN_EROSION_THRESHOLD_POINTS percentage points between the
	// two. Reuses getNetProfitByItem() for both periods and merges by
	// item_id, same pattern Month-over-Month's category comparison
	// uses at item granularity instead. Powers both the Margin &
	// Profit Alerts report and MarginErosionNotifier's daily check.
	// =====================
	public static final double MARGIN_EROSION_THRESHOLD_POINTS = 5.0;

	public ArrayList<HashMap<String, Object>> getMarginErosionAlerts(
		String thisFrom, String thisTo, String lastFrom, String lastTo, int shoesFilter) {

		ArrayList<HashMap<String, Object>> merged =
			getMergedItemProfit(thisFrom, thisTo, lastFrom, lastTo, shoesFilter);

		ArrayList<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();

		for (HashMap<String, Object> row : merged) {

			double salesThis = (Double) row.get("sales_this");
			double salesLast = (Double) row.get("sales_last");

			// Both periods need an actual sale to compare margins
			// meaningfully - a brand-new or stopped-selling item would
			// otherwise show a misleading 100% "erosion".
			if (salesThis < 0.01 || salesLast < 0.01) {
				continue;
			}

			double profitThis = (Double) row.get("profit_this");
			double profitLast = (Double) row.get("profit_last");

			double marginThis = profitThis / salesThis * 100;
			double marginLast = profitLast / salesLast * 100;
			double erosion = marginLast - marginThis;

			if (erosion < MARGIN_EROSION_THRESHOLD_POINTS) {
				continue;
			}

			row.put("margin_this", marginThis);
			row.put("margin_last", marginLast);
			row.put("erosion_points", erosion);

			result.add(row);
		}

		Collections.sort(result, new Comparator<HashMap<String, Object>>() {
				@Override
				public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
					return Double.compare(
						(Double) b.get("erosion_points"), (Double) a.get("erosion_points")
					);
				}
			}
		);

		return result;
	}

	// Every item that sold in either period, ranked by the size of its
	// profit change (up or down) - a big increase is just as worth
	// surfacing as a big drop. Not filtered by a threshold like margin
	// erosion is, since "biggest" is inherently relative; capped to
	// maxResults so a shop with many items doesn't get an unbounded
	// list for what's meant to be an at-a-glance alert.
	public ArrayList<HashMap<String, Object>> getBiggestProfitSwings(
		String thisFrom, String thisTo, String lastFrom, String lastTo,
		int shoesFilter, int maxResults) {

		ArrayList<HashMap<String, Object>> merged =
			getMergedItemProfit(thisFrom, thisTo, lastFrom, lastTo, shoesFilter);

		for (HashMap<String, Object> row : merged) {

			double profitThis = (Double) row.get("profit_this");
			double profitLast = (Double) row.get("profit_last");

			row.put("profit_swing", profitThis - profitLast);
		}

		Collections.sort(merged, new Comparator<HashMap<String, Object>>() {
				@Override
				public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
					return Double.compare(
						Math.abs((Double) b.get("profit_swing")),
						Math.abs((Double) a.get("profit_swing"))
					);
				}
			}
		);

		if (merged.size() > maxResults) {
			merged = new ArrayList<HashMap<String, Object>>(merged.subList(0, maxResults));
		}

		return merged;
	}

	// Shared merge step for the two methods above: every item that
	// sold in either period, by item_id, with this/last sales_amount
	// and profit defaulted to 0 for whichever period it's absent from.
	private ArrayList<HashMap<String, Object>> getMergedItemProfit(
		String thisFrom, String thisTo, String lastFrom, String lastTo, int shoesFilter) {

		ArrayList<HashMap<String, Object>> thisList =
			getNetProfitByItem(thisFrom, thisTo, false, shoesFilter);

		ArrayList<HashMap<String, Object>> lastList =
			getNetProfitByItem(lastFrom, lastTo, false, shoesFilter);

		LinkedHashMap<Integer, HashMap<String, Object>> merged =
			new LinkedHashMap<Integer, HashMap<String, Object>>();

		for (HashMap<String, Object> row : thisList) {

			int itemId = (Integer) row.get("item_id");

			HashMap<String, Object> mergedRow = new HashMap<String, Object>();
			mergedRow.put("item_id", itemId);
			mergedRow.put("item_name", row.get("item_name"));
			mergedRow.put("sales_this", (Double) row.get("sales_amount"));
			mergedRow.put("profit_this", (Double) row.get("profit"));
			mergedRow.put("sales_last", 0.0);
			mergedRow.put("profit_last", 0.0);

			merged.put(itemId, mergedRow);
		}

		for (HashMap<String, Object> row : lastList) {

			int itemId = (Integer) row.get("item_id");

			HashMap<String, Object> mergedRow = merged.get(itemId);

			if (mergedRow == null) {

				mergedRow = new HashMap<String, Object>();
				mergedRow.put("item_id", itemId);
				mergedRow.put("item_name", row.get("item_name"));
				mergedRow.put("sales_this", 0.0);
				mergedRow.put("profit_this", 0.0);

				merged.put(itemId, mergedRow);
			}

			mergedRow.put("sales_last", (Double) row.get("sales_amount"));
			mergedRow.put("profit_last", (Double) row.get("profit"));
		}

		return new ArrayList<HashMap<String, Object>>(merged.values());
	}

	// =====================
	// SIZE-CURVE ANALYSIS (shoes) - how this shop's shoe sales split
	// across sizes vs how its current stock splits across those same
	// sizes, so a size selling a disproportionate share of volume but
	// carrying a thin slice of stock (a stockout risk) - or the
	// reverse, cash tied up on a size that barely moves - stands out.
	// Size isn't a variety group for this shop's real catalog - it's
	// parsed straight out of the item name via ShoeIdentity (see that
	// class), since every real shoe item here is its own exact size,
	// not a parent item with size combos. Lifetime totals, not a
	// period - this is a structural "where should the next buy lean"
	// question, not a trend.
	// =====================
	public ArrayList<HashMap<String, Object>> getSizeCurveAnalysis() {

		SQLiteDatabase db = this.getReadableDatabase();

		HashMap<String, Double> soldBySize = new HashMap<String, Double>();
		HashMap<String, Double> stockBySize = new HashMap<String, Double>();

		Cursor cursor = db.rawQuery(
			"SELECT i.name, i.balance, COALESCE(SUM(si.qty), 0) AS qty_sold " +
			"FROM " + TABLE_ITEMS + " i " +
			"LEFT JOIN sale_items si ON si.item_id = i.id " +
			"WHERE i.name LIKE 'Shoe%' " +
			"GROUP BY i.id",
			null
		);

		while (cursor.moveToNext()) {

			String name = cursor.getString(0);
			double balance = cursor.getDouble(1);
			double qtySold = cursor.getDouble(2);

			ShoeIdentity identity = ShoeIdentity.parse(name);

			if (identity == null) {
				continue;
			}

			String size = identity.size;

			soldBySize.put(size, (soldBySize.containsKey(size) ? soldBySize.get(size) : 0.0) + qtySold);
			stockBySize.put(size, (stockBySize.containsKey(size) ? stockBySize.get(size) : 0.0) + balance);
		}

		cursor.close();

		TreeSet<String> allSizes = new TreeSet<String>(soldBySize.keySet());
		allSizes.addAll(stockBySize.keySet());

		double totalSold = 0;
		double totalStock = 0;

		for (String size : allSizes) {
			totalSold += soldBySize.containsKey(size) ? soldBySize.get(size) : 0.0;
			totalStock += stockBySize.containsKey(size) ? stockBySize.get(size) : 0.0;
		}

		ArrayList<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();

		for (String size : allSizes) {

			double sold = soldBySize.containsKey(size) ? soldBySize.get(size) : 0.0;
			double stock = stockBySize.containsKey(size) ? stockBySize.get(size) : 0.0;

			double soldPercent = totalSold > 0.01 ? sold / totalSold * 100 : 0;
			double stockPercent = totalStock > 0.01 ? stock / totalStock * 100 : 0;

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("size", size);
			row.put("qty_sold", sold);
			row.put("stock", stock);
			row.put("sold_percent", soldPercent);
			row.put("stock_percent", stockPercent);
			row.put("mismatch_points", soldPercent - stockPercent);

			result.add(row);
		}

		Collections.sort(result, new Comparator<HashMap<String, Object>>() {
				@Override
				public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
					return compareSizeLabels((String) a.get("size"), (String) b.get("size"));
				}
			}
		);

		return result;
	}

	// Sorts size labels numerically when both parse as a number (the
	// common case - "7", "8.5", "10") so the curve reads smallest to
	// largest, falling back to plain alphabetical for anything that
	// doesn't (e.g. a non-numeric size label).
	private int compareSizeLabels(String a, String b) {

		try {

			double numA = Double.parseDouble(a);
			double numB = Double.parseDouble(b);

			return Double.compare(numA, numB);

		} catch (NumberFormatException e) {

			return a.compareTo(b);
		}
	}

	// =====================
	// REPORT: SHOES VS NON-SHOES - sales and profit for a period, split
	// by the same "item name starts with 'Shoe'" rule as everywhere else
	// (Item Ranking's shoes filter, Stock Worth). Both metrics are
	// computed together so the UI can flip between Sale/Profit without a
	// second query; percentages are computed separately per metric since
	// a sales split and a profit split over the same period can differ.
	// =====================
	public HashMap<String, Object> getShoesVsNonShoesSummary(String fromDate, String toDate) {

		HashMap<String, Object> map = new HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();

		boolean allTime = fromDate == null || toDate == null;

		String[] args = allTime ? null : new String[]{fromDate, toDate};

		String sql =
			"SELECT " +
			"COALESCE(SUM(CASE WHEN i.name LIKE 'Shoe%' THEN si.amount ELSE 0 END), 0) AS shoes_sales, " +
			"COALESCE(SUM(CASE WHEN i.name NOT LIKE 'Shoe%' THEN si.amount ELSE 0 END), 0) AS non_shoes_sales, " +
			"COALESCE(SUM(CASE WHEN i.name LIKE 'Shoe%' " +
			"THEN si.qty * (i.purchase_price + i.extra_cost_per_unit) ELSE 0 END), 0) AS shoes_cost, " +
			"COALESCE(SUM(CASE WHEN i.name NOT LIKE 'Shoe%' " +
			"THEN si.qty * (i.purchase_price + i.extra_cost_per_unit) ELSE 0 END), 0) AS non_shoes_cost " +
			"FROM sale_items si " +
			"INNER JOIN sales s ON s.id = si.sale_id " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = si.item_id" +
			(allTime ? "" : " WHERE s.date BETWEEN ? AND ?");

		Cursor cursor = db.rawQuery(sql, args);

		double shoesSales = 0;
		double nonShoesSales = 0;
		double shoesCost = 0;
		double nonShoesCost = 0;

		if (cursor.moveToFirst()) {
			shoesSales = cursor.getDouble(0);
			nonShoesSales = cursor.getDouble(1);
			shoesCost = cursor.getDouble(2);
			nonShoesCost = cursor.getDouble(3);
		}

		cursor.close();

		double shoesProfit = shoesSales - shoesCost;
		double nonShoesProfit = nonShoesSales - nonShoesCost;

		double totalSales = shoesSales + nonShoesSales;
		double totalProfit = shoesProfit + nonShoesProfit;

		map.put("shoes_sales", shoesSales);
		map.put("non_shoes_sales", nonShoesSales);
		map.put("shoes_profit", shoesProfit);
		map.put("non_shoes_profit", nonShoesProfit);

		map.put("shoes_sales_percent", totalSales == 0 ? 0 : (shoesSales / totalSales) * 100.0);
		map.put("non_shoes_sales_percent", totalSales == 0 ? 0 : (nonShoesSales / totalSales) * 100.0);
		map.put("shoes_profit_percent", totalProfit == 0 ? 0 : (shoesProfit / totalProfit) * 100.0);
		map.put("non_shoes_profit_percent", totalProfit == 0 ? 0 : (nonShoesProfit / totalProfit) * 100.0);

		return map;
	}

	// =====================
	// REPORT: LOW STOCK - every active item with a reorder_threshold
	// above 0 (0 means "no alert configured") whose current total
	// balance (summed across variety sizes already, same figure
	// Itemseditactivity's Current Stock shows) is at or below it.
	// Lowest stock first, so the most urgent ones lead. An inactive
	// item is excluded, same as everywhere else an item picker/alert
	// filters on active - a discontinued item being "low" isn't
	// actionable.
	// =====================
	public ArrayList<HashMap<String, Object>> getLowStockItems() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, code, name, balance, reorder_threshold FROM " + TABLE_ITEMS +
			" WHERE active = 1 AND reorder_threshold > 0 AND balance <= reorder_threshold" +
			" ORDER BY balance ASC, name ASC",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<>();

			row.put("item_id", cursor.getInt(0));
			row.put("code", cursor.getString(1));
			row.put("name", cursor.getString(2));
			row.put("balance", cursor.getDouble(3));
			row.put("reorder_threshold", cursor.getDouble(4));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// =====================
	// REORDER SUGGESTIONS - see ReorderSettings for every formula input
	// this uses, and that class's own comment for why they're
	// SharedPreferences rather than a database table.
	//
	// Operates per sellable unit, not per parent item: a shoe-style item
	// with variety combos (Gender/Type/Sole/Upper/Design/Color/Size all
	// bundled into one combo - see TABLE_VARIETY_COMBOS) gets one
	// suggestion per combo it actually carries, since a size 8 and a
	// size 9 of the same model are different things to reorder. An item
	// with no variety groups at all is its own single unit.
	//
	// For each unit:
	//   velocity = units sold in the last velocityWindowDays / that many days
	//   leadTimeDemand = velocity * leadTimeDays
	//   reorderPoint = leadTimeDemand * (1 + safetyStockPercent/100)
	//   suggestedQty = (reorderPoint + leadTimeDemand) - currentStock,
	//     i.e. replenish back up to covering one full lead time beyond
	//     the reorder point, then clamped to [minOrderQty, maxOrderQty]
	//     (maxOrderQty 0 = uncapped)
	//
	// A unit also qualifies with zero sales history if its *item*-level
	// manual reorder_threshold (Itemseditactivity) is set and its own
	// stock is at or below it - the same signal the Low Stock report
	// already uses above, kept as a floor so a unit with too little
	// history to compute a velocity still gets suggested once flagged.
	// =====================
	public ArrayList<HashMap<String, Object>> getReorderSuggestions(Context context) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		int velocityWindowDays = ReorderSettings.getVelocityWindowDays(context);
		double safetyStockPercent = ReorderSettings.getSafetyStockPercent(context);
		int leadTimeDays = ReorderSettings.getDefaultLeadTimeDays(context);
		double minOrderQty = ReorderSettings.getMinOrderQty(context);
		double maxOrderQty = ReorderSettings.getMaxOrderQty(context);

		Calendar windowStart = Calendar.getInstance();
		windowStart.add(Calendar.DAY_OF_MONTH, -velocityWindowDays);

		String windowStartDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
			.format(windowStart.getTime());

		Cursor itemCursor = db.rawQuery(
			"SELECT id, code, name, balance, purchase_price, reorder_threshold FROM " +
			TABLE_ITEMS + " WHERE active = 1",
			null
		);

		while (itemCursor.moveToNext()) {

			int itemId = itemCursor.getInt(0);
			String code = itemCursor.getString(1);
			String name = itemCursor.getString(2);
			double itemBalance = itemCursor.getDouble(3);
			double purchasePrice = itemCursor.getDouble(4);
			double manualThreshold = itemCursor.getDouble(5);

			ArrayList<HashMap<String, Object>> combos = getVarietyCombos(itemId);

			if (combos.isEmpty()) {

				HashMap<String, Object> suggestion = buildReorderSuggestion(
					db, itemId, 0, null, code, name, itemBalance, purchasePrice,
					manualThreshold, windowStartDate, velocityWindowDays, leadTimeDays,
					safetyStockPercent, minOrderQty, maxOrderQty
				);

				if (suggestion != null) {
					list.add(suggestion);
				}

			} else {

				for (HashMap<String, Object> combo : combos) {

					int comboId = (Integer) combo.get("id");
					double comboBalance = (Double) combo.get("balance");
					String comboLabel = (String) combo.get("label");

					HashMap<String, Object> suggestion = buildReorderSuggestion(
						db, itemId, comboId, comboLabel, code, name, comboBalance,
						purchasePrice, manualThreshold, windowStartDate,
						velocityWindowDays, leadTimeDays, safetyStockPercent,
						minOrderQty, maxOrderQty
					);

					if (suggestion != null) {
						list.add(suggestion);
					}
				}
			}
		}

		itemCursor.close();

		Collections.sort(list, new Comparator<HashMap<String, Object>>() {
			@Override
			public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
				return String.valueOf(a.get("name"))
					.compareToIgnoreCase(String.valueOf(b.get("name")));
			}
		});

		return list;
	}

	private HashMap<String, Object> buildReorderSuggestion(
		SQLiteDatabase db, int itemId, int comboId, String comboLabel, String code,
		String name, double currentStock, double purchasePrice, double manualThreshold,
		String windowStartDate, int velocityWindowDays, int leadTimeDays,
		double safetyStockPercent, double minOrderQty, double maxOrderQty) {

		double unitsSold;

		if (comboId > 0) {

			unitsSold = sumColumn(
				db,
				"SELECT COALESCE(SUM(si.qty), 0) FROM sale_items si " +
				"INNER JOIN sales s ON s.id = si.sale_id " +
				"WHERE si.combo_id = ? AND s.date >= ?",
				new String[]{String.valueOf(comboId), windowStartDate}
			);

		} else {

			unitsSold = sumColumn(
				db,
				"SELECT COALESCE(SUM(si.qty), 0) FROM sale_items si " +
				"INNER JOIN sales s ON s.id = si.sale_id " +
				"WHERE si.item_id = ? AND (si.combo_id IS NULL OR si.combo_id = 0) " +
				"AND s.date >= ?",
				new String[]{String.valueOf(itemId), windowStartDate}
			);
		}

		double velocity = unitsSold / velocityWindowDays;
		double leadTimeDemand = velocity * leadTimeDays;
		double reorderPoint = leadTimeDemand * (1 + safetyStockPercent / 100.0);

		boolean belowVelocityReorderPoint = velocity > 0 && currentStock <= reorderPoint;
		boolean belowManualThreshold =
			manualThreshold > 0 && currentStock <= manualThreshold;

		if (!belowVelocityReorderPoint && !belowManualThreshold) {
			return null;
		}

		double targetStock = Math.max(reorderPoint + leadTimeDemand, manualThreshold);
		double suggestedQty = targetStock - currentStock;

		if (suggestedQty <= 0) {
			return null;
		}

		double seasonalMultiplier =
			getSeasonalMultiplier(db, itemId, comboId > 0 ? comboId : null, velocity);

		suggestedQty = suggestedQty * seasonalMultiplier;

		if (suggestedQty < minOrderQty) {
			suggestedQty = minOrderQty;
		}

		if (maxOrderQty > 0 && suggestedQty > maxOrderQty) {
			suggestedQty = maxOrderQty;
		}

		HashMap<String, Object> supplier = getLastSupplier(db, itemId);

		HashMap<String, Object> row = new HashMap<String, Object>();
		row.put("item_id", itemId);
		row.put("combo_id", comboId > 0 ? comboId : null);
		row.put("combo_label", comboLabel);
		row.put("code", code);
		row.put("name", name);
		row.put("current_stock", currentStock);
		row.put("velocity_per_day", velocity);
		row.put("suggested_qty", suggestedQty);
		row.put("estimated_cost", suggestedQty * purchasePrice);
		row.put("supplier_party_id", supplier.get("party_id"));
		row.put("supplier_party_name", supplier.get("party_name"));
		row.put("seasonal_multiplier", seasonalMultiplier);

		if (velocity > 0) {

			int daysOfStockLeft = (int) Math.floor(currentStock / velocity);

			Calendar runOutDate = Calendar.getInstance();
			runOutDate.add(Calendar.DAY_OF_MONTH, daysOfStockLeft);

			row.put(
				"runs_out_date",
				new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(runOutDate.getTime())
			);

		} else {

			row.put("runs_out_date", null);
		}

		return row;
	}

	// Compares this calendar month's average total sales in past years
	// (any year but the current one) against what the recent velocity
	// would project for a month, to catch a recurring seasonal spike
	// (e.g. this item always sells faster in a certain month) before the
	// plain recent-velocity formula above would react to it. Returns 1.0
	// (no adjustment) whenever there isn't at least one past year's worth
	// of data for this exact month, or the recent velocity is already
	// zero - this is a bonus signal on top of the velocity formula, not a
	// replacement for it, and a brand-new shop with no history yet simply
	// gets no seasonal adjustment rather than a divide-by-zero. Capped at
	// 2x so one unusually large past month can't blow out the suggestion.
	private double getSeasonalMultiplier(
		SQLiteDatabase db, int itemId, Integer comboId, double currentVelocity) {

		if (currentVelocity <= 0) {
			return 1.0;
		}

		String currentMonth = new SimpleDateFormat("MM", Locale.getDefault()).format(new Date());
		String currentYearMonth =
			new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());

		String whereClause;
		String[] args;

		if (comboId != null && comboId > 0) {

			whereClause = "si.combo_id = ?";
			args = new String[]{String.valueOf(comboId), currentMonth, currentYearMonth};

		} else {

			whereClause = "si.item_id = ? AND (si.combo_id IS NULL OR si.combo_id = 0)";
			args = new String[]{String.valueOf(itemId), currentMonth, currentYearMonth};
		}

		Cursor cursor = db.rawQuery(
			"SELECT COALESCE(SUM(si.qty), 0), COUNT(DISTINCT strftime('%Y', s.date)) " +
			"FROM sale_items si INNER JOIN sales s ON s.id = si.sale_id " +
			"WHERE " + whereClause + " AND strftime('%m', s.date) = ? " +
			"AND strftime('%Y-%m', s.date) != ?",
			args
		);

		double pastMonthTotal = 0;
		int yearCount = 0;

		if (cursor.moveToFirst()) {
			pastMonthTotal = cursor.getDouble(0);
			yearCount = cursor.getInt(1);
		}

		cursor.close();

		if (yearCount == 0 || pastMonthTotal <= 0) {
			return 1.0;
		}

		double avgPastMonthTotal = pastMonthTotal / yearCount;
		int daysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH);
		double avgPastMonthVelocity = avgPastMonthTotal / daysInMonth;

		double ratio = avgPastMonthVelocity / currentVelocity;

		return ratio > 1.3 ? Math.min(ratio, 2.0) : 1.0;
	}

	// The supplier (party) of this item's most recent purchase, if any -
	// used to batch the Reorder List by who you'd actually order from. An
	// item never purchased yet (e.g. opening stock entered directly) has
	// no supplier to infer, so it falls into its own "No Supplier" group
	// on that screen.
	private HashMap<String, Object> getLastSupplier(SQLiteDatabase db, int itemId) {

		Cursor cursor = db.rawQuery(
			"SELECT p.party_id, pt.name FROM " + TABLE_PURCHASE_ITEMS + " pi " +
			"INNER JOIN " + TABLE_PURCHASES + " p ON p.id = pi.purchase_id " +
			"INNER JOIN " + TABLE_PARTIES + " pt ON pt.id = p.party_id " +
			"WHERE pi.item_id = ? ORDER BY p.date DESC, p.time DESC LIMIT 1",
			new String[]{String.valueOf(itemId)}
		);

		HashMap<String, Object> result = new HashMap<String, Object>();

		if (cursor.moveToFirst()) {
			result.put("party_id", cursor.getInt(0));
			result.put("party_name", cursor.getString(1));
		} else {
			result.put("party_id", null);
			result.put("party_name", "No Supplier");
		}

		cursor.close();

		return result;
	}

	// Records what happened to a shown suggestion - "accepted" when the
	// user converts it to a draft Purchase, "ignored" when they dismiss it
	// outright. The outcome/outcome_date columns double as the learning
	// signal a later pass reads back (see reorder_suggestion_log's own
	// comment) to eventually also detect "overstocked"/
	// "ran_out_before_restock" once enough time has passed to judge those.
	public void recordReorderDecision(
		int itemId, Integer comboId, double suggestedQty, String outcome) {

		SQLiteDatabase db = this.getWritableDatabase();

		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
			.format(new Date());

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("combo_id", comboId);
		values.put("suggested_date", today);
		values.put("suggested_qty", suggestedQty);
		values.put("outcome", outcome);
		values.put("outcome_date", today);

		db.insert(TABLE_REORDER_SUGGESTION_LOG, null, values);
	}

	// =====================
	// TRENDING FLAGS - compares each item's last 7 days of sales against
	// the 7 days before that, at the whole-item level (summed across any
	// variety combos) since this is an early-warning signal for the
	// item's own list row, not a per-size reordering decision the way
	// getReorderSuggestions() is. "up" is an early stock-out warning -
	// selling meaningfully faster than before, maybe before it's even
	// hit its reorder point yet. "down" is an early overbuy warning -
	// slowing down, worth knowing before committing to a big reorder.
	// An item with no sales in the earlier 7-day window has nothing to
	// compare against and is left unflagged either way. Computed in one
	// pass across every item with any sales history, so a list screen
	// can show the flag without a query per row.
	// =====================
	public HashMap<Integer, String> getItemTrends() {

		HashMap<Integer, String> trends = new HashMap<Integer, String>();

		SQLiteDatabase db = this.getReadableDatabase();

		Calendar last7Start = Calendar.getInstance();
		last7Start.add(Calendar.DAY_OF_MONTH, -7);

		Calendar previous7Start = Calendar.getInstance();
		previous7Start.add(Calendar.DAY_OF_MONTH, -14);

		String last7StartDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
			.format(last7Start.getTime());

		String previous7StartDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
			.format(previous7Start.getTime());

		Cursor cursor = db.rawQuery(
			"SELECT si.item_id, " +
			"SUM(CASE WHEN s.date >= ? THEN si.qty ELSE 0 END) AS last7, " +
			"SUM(CASE WHEN s.date >= ? AND s.date < ? THEN si.qty ELSE 0 END) AS previous7 " +
			"FROM sale_items si INNER JOIN sales s ON s.id = si.sale_id " +
			"GROUP BY si.item_id",
			new String[]{last7StartDate, previous7StartDate, last7StartDate}
		);

		while (cursor.moveToNext()) {

			int itemId = cursor.getInt(0);
			double last7 = cursor.getDouble(1);
			double previous7 = cursor.getDouble(2);

			if (previous7 <= 0) {
				continue;
			}

			if (last7 > previous7 * 1.3) {
				trends.put(itemId, "up");
			} else if (last7 < previous7 * 0.7) {
				trends.put(itemId, "down");
			}
		}

		cursor.close();

		return trends;
	}

	// =====================
	// SLOW-MOVING STOCK - every active item still carrying stock whose
	// most recent Sale (if it has ever had one) falls before the cutoff,
	// oldest/never-sold first. An item with stock that's never sold at
	// all (last_sale_date IS NULL) always qualifies regardless of how
	// young the item is - there's no "too new to judge" grace period,
	// since a never-sold item sitting on stock is exactly what this
	// report exists to surface.
	//
	// A non-shoe item's days-since-sale is divided by
	// ReorderSettings.getNonShoeTurnoverMultiplier() (default 3x) before
	// being compared against days or bucketed by a caller (Discount This
	// Week/Dead Stock Aging) - this shop's general merchandise (Clothes/
	// Toys/Home/Tools) naturally turns over slower than its shoes, so a
	// tool sitting 90 days is only as "slow" as a shoe sitting 30. Both
	// the raw days ("days_since_sale", for truthful display - "last sold
	// X days ago" should never lie) and the adjusted figure
	// ("effective_days_since_sale", for every threshold/bucket decision)
	// are returned.
	// =====================
	public ArrayList<HashMap<String, Object>> getSlowMovingStock(Context context, int days) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		double nonShoeMultiplier = ReorderSettings.getNonShoeTurnoverMultiplier(context);

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
		long todayMillis = new Date().getTime();

		// No date cutoff at the SQL level any more - a non-shoe item's
		// cutoff is a multiple of a shoe's, which isn't expressible as a
		// single WHERE clause, so every active in-stock item is fetched
		// and the category-adjusted cutoff is applied in Java instead.
		Cursor cursor = db.rawQuery(
			"SELECT i.id, i.code, i.name, i.balance, MAX(s.date) AS last_sale_date " +
			"FROM " + TABLE_ITEMS + " i " +
			"LEFT JOIN sale_items si ON si.item_id = i.id " +
			"LEFT JOIN sales s ON s.id = si.sale_id " +
			"WHERE i.active = 1 AND i.balance > 0 " +
			"GROUP BY i.id",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();

			String name = cursor.getString(2);
			String lastSaleDate = cursor.isNull(4) ? null : cursor.getString(4);

			int daysSinceSale;

			if (lastSaleDate == null) {

				daysSinceSale = -1;

			} else {

				try {

					long diffMillis = todayMillis - dateFormat.parse(lastSaleDate).getTime();
					daysSinceSale = (int) (diffMillis / (1000L * 60 * 60 * 24));

				} catch (Exception e) {

					daysSinceSale = 0;
				}
			}

			boolean isShoe = name != null && name.startsWith("Shoe");

			int effectiveDaysSinceSale =
				daysSinceSale < 0 || isShoe
				? daysSinceSale
				: (int) (daysSinceSale / nonShoeMultiplier);

			if (effectiveDaysSinceSale >= 0 && effectiveDaysSinceSale < days) {
				continue;
			}

			row.put("item_id", cursor.getInt(0));
			row.put("code", cursor.getString(1));
			row.put("name", name);
			row.put("balance", cursor.getDouble(3));
			row.put("last_sale_date", lastSaleDate);
			row.put("days_since_sale", daysSinceSale);
			row.put("effective_days_since_sale", effectiveDaysSinceSale);

			list.add(row);
		}

		cursor.close();

		Collections.sort(list, new Comparator<HashMap<String, Object>>() {
				@Override
				public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {

					int effectiveA = (Integer) a.get("effective_days_since_sale");
					int effectiveB = (Integer) b.get("effective_days_since_sale");

					// Never-sold (-1) is the most urgent, so it sorts first -
					// same as it sorting first under the old "last_sale_date
					// ASC" (NULL first) ordering.
					if (effectiveA < 0 && effectiveB < 0) {
						return 0;
					}

					if (effectiveA < 0) {
						return -1;
					}

					if (effectiveB < 0) {
						return 1;
					}

					return Integer.compare(effectiveB, effectiveA);
				}
			}
		);

		return list;
	}

	// =====================
	// REPORT: DISCOUNT THIS WEEK - reuses getSlowMovingStock(context, 30)
	// (every active item with stock and no sale in 30+ effective days)
	// and annotates each with a suggested discount tier based on its
	// effective_days_since_sale (category-adjusted - see
	// getSlowMovingStock()): 30-59 = 10%, 60-89 = 20%, 90+ (or never sold
	// at all) = 30%. These tiers are a starting suggestion, not a rule
	// the app enforces anywhere - the user still sets the actual sale
	// price.
	// =====================
	public ArrayList<HashMap<String, Object>> getDiscountCandidates(Context context) {

		ArrayList<HashMap<String, Object>> list = getSlowMovingStock(context, 30);

		for (HashMap<String, Object> row : list) {

			int effectiveDays = (Integer) row.get("effective_days_since_sale");

			int suggestedDiscountPercent;

			if (effectiveDays < 0 || effectiveDays >= 90) {
				suggestedDiscountPercent = 30;
			} else if (effectiveDays >= 60) {
				suggestedDiscountPercent = 20;
			} else {
				suggestedDiscountPercent = 10;
			}

			row.put("suggested_discount_percent", suggestedDiscountPercent);
		}

		return list;
	}

	// =====================
	// REPORT: STOP RESTOCKING - every active item whose Sale Price is at
	// or below its own cost basis (purchase_price + extra_cost_per_unit,
	// the same basis Net Profit/Item Monthly Rank use) right now - i.e.
	// restocking it at today's prices would be selling at a loss or
	// break-even. A snapshot of right now, same as Low Stock; it doesn't
	// look at sales history the way Discount This Week does.
	// =====================
	public ArrayList<HashMap<String, Object>> getStopRestockingCandidates() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, code, name, purchase_price, extra_cost_per_unit, sale_price, balance FROM " +
			TABLE_ITEMS +
			" WHERE active = 1 AND sale_price <= (purchase_price + extra_cost_per_unit) " +
			"ORDER BY name ASC",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();

			double costBasis = cursor.getDouble(3) + cursor.getDouble(4);
			double salePrice = cursor.getDouble(5);

			row.put("item_id", cursor.getInt(0));
			row.put("code", cursor.getString(1));
			row.put("name", cursor.getString(2));
			row.put("cost_basis", costBasis);
			row.put("sale_price", salePrice);
			row.put("margin", salePrice - costBasis);
			row.put("balance", cursor.getDouble(6));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// =====================
	// REPORT: DEAD STOCK AGING - every active item with stock that
	// hasn't sold in 60+ effective days (a stricter cutoff than Slow-
	// Moving Stock's 30-day default), bucketed into 60-89/90-119/120+
	// days, or Never Sold - all against effective_days_since_sale, same
	// category-adjusted figure Discount This Week buckets on.
	// =====================
	public ArrayList<HashMap<String, Object>> getDeadStockAging(Context context) {

		ArrayList<HashMap<String, Object>> list = getSlowMovingStock(context, 60);

		for (HashMap<String, Object> row : list) {

			int effectiveDays = (Integer) row.get("effective_days_since_sale");

			String ageBucket;

			if (effectiveDays < 0) {
				ageBucket = "Never Sold";
			} else if (effectiveDays < 90) {
				ageBucket = "60-89 Days";
			} else if (effectiveDays < 120) {
				ageBucket = "90-119 Days";
			} else {
				ageBucket = "120+ Days";
			}

			row.put("age_bucket", ageBucket);

			HashMap<String, Object> clearance = getClearanceForItem((Integer) row.get("item_id"));
			row.put("clearance", clearance);
		}

		return list;
	}

	// Starts (or replaces, if already in one) clearance on an item at the
	// given discount %, snapshotting its current stock as the baseline
	// clearance progress is measured against.
	public void startClearance(int itemId, double discountPercent) {

		SQLiteDatabase db = this.getWritableDatabase();

		double currentBalance = getItemBalance(db, itemId);

		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
			.format(new Date());

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("started_date", today);
		values.put("discount_percent", discountPercent);
		values.put("starting_balance", currentBalance);

		db.insertWithOnConflict(
			TABLE_ITEM_CLEARANCE, null, values, SQLiteDatabase.CONFLICT_REPLACE
		);
	}

	public void endClearance(int itemId) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_ITEM_CLEARANCE, "item_id=?", new String[]{String.valueOf(itemId)});
	}

	private HashMap<String, Object> getClearanceForItem(int itemId) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT started_date, discount_percent, starting_balance FROM " +
			TABLE_ITEM_CLEARANCE + " WHERE item_id=?",
			new String[]{String.valueOf(itemId)}
		);

		HashMap<String, Object> result = null;

		if (cursor.moveToFirst()) {

			result = new HashMap<String, Object>();
			result.put("started_date", cursor.getString(0));
			result.put("discount_percent", cursor.getDouble(1));
			result.put("starting_balance", cursor.getDouble(2));
		}

		cursor.close();

		return result;
	}

	// Every item currently in clearance, with its progress - how much of
	// the starting balance has sold since clearance began (current
	// balance is read fresh each time, not stored).
	public ArrayList<HashMap<String, Object>> getActiveClearances() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT ic.item_id, i.code, i.name, i.balance, ic.started_date, " +
			"ic.discount_percent, ic.starting_balance FROM " + TABLE_ITEM_CLEARANCE + " ic " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = ic.item_id " +
			"ORDER BY ic.started_date ASC",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();

			double currentBalance = cursor.getDouble(3);
			double startingBalance = cursor.getDouble(6);
			double sold = startingBalance - currentBalance;

			row.put("item_id", cursor.getInt(0));
			row.put("code", cursor.getString(1));
			row.put("name", cursor.getString(2));
			row.put("current_balance", currentBalance);
			row.put("started_date", cursor.getString(4));
			row.put("discount_percent", cursor.getDouble(5));
			row.put("starting_balance", startingBalance);

			row.put(
				"percent_cleared",
				startingBalance > 0 ? Math.max(0, Math.min(100, sold / startingBalance * 100)) : 0
			);

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// =====================
	// LOYALTY POINTS - 1 point per LOYALTY_POINTS_PER_RUPEES spent on a
	// Sale (floored), skipped for the "Cash Sale" placeholder party
	// since it isn't a trackable customer. Deliberately an append-only
	// ledger (one row per earn/adjustment), not a running total column
	// on parties - a party's current balance is always SUM(points)
	// over their own rows (getLoyaltyPointsBalance()). Points earned
	// are NOT clawed back if the originating Sale is later deleted or
	// edited - once earned, a reward stays earned, same as most real
	// loyalty programs; a genuine correction goes through
	// adjustLoyaltyPoints() instead. The rate and milestone thresholds
	// aren't yet exposed as settings - reasonable defaults for now,
	// same as DailyDigestScheduler's fixed 9 PM.
	// =====================
	public static final int LOYALTY_POINTS_PER_RUPEES = 100;

	private static final int[] LOYALTY_MILESTONES = {100, 250, 500, 1000, 2500, 5000, 10000};

	// Called right after a new Sale is saved (Transactioneditactivity) -
	// NOT from insertSale() itself, so an edit/undo/restore never
	// re-triggers it. Returns the milestone just crossed (e.g. 500), or
	// null if the party didn't cross one (including when no points were
	// earned at all, or the party is "Cash Sale").
	public Integer earnLoyaltyPointsForSale(int partyId, double grandTotal, long saleId) {

		SQLiteDatabase db = this.getWritableDatabase();

		Cursor nameCursor = db.rawQuery(
			"SELECT name FROM " + TABLE_PARTIES + " WHERE id=?",
			new String[]{String.valueOf(partyId)}
		);

		String partyName = null;

		if (nameCursor.moveToFirst()) {
			partyName = nameCursor.getString(0);
		}

		nameCursor.close();

		if ("Cash Sale".equalsIgnoreCase(partyName)) {
			return null;
		}

		int points = (int) Math.floor(grandTotal / LOYALTY_POINTS_PER_RUPEES);

		if (points <= 0) {
			return null;
		}

		int before = getLoyaltyPointsBalance(partyId);

		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
		String now = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

		insertLoyaltyLedgerEntry(db, partyId, points, today, now, "Sale #" + saleId);

		int after = before + points;

		for (int i = LOYALTY_MILESTONES.length - 1; i >= 0; i--) {

			int milestone = LOYALTY_MILESTONES[i];

			if (before < milestone && after >= milestone) {
				return milestone;
			}
		}

		return null;
	}

	// A manual correction (redemption, goodwill credit, fixing an
	// error) - pointsDelta can be negative. Never rejected for going
	// negative; a party's balance is just allowed to read negative
	// until it's earned back, same as a party balance can.
	public void adjustLoyaltyPoints(int partyId, int pointsDelta, String reason) {

		SQLiteDatabase db = this.getWritableDatabase();

		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
		String now = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

		insertLoyaltyLedgerEntry(db, partyId, pointsDelta, today, now, reason);
	}

	private void insertLoyaltyLedgerEntry(
		SQLiteDatabase db, int partyId, int points, String date, String time, String reason) {

		ContentValues values = new ContentValues();
		values.put("party_id", partyId);
		values.put("points", points);
		values.put("date", date);
		values.put("time", time);
		values.put("reason", reason);

		db.insert(TABLE_LOYALTY_POINTS, null, values);
	}

	public int getLoyaltyPointsBalance(int partyId) {

		SQLiteDatabase db = this.getReadableDatabase();

		return (int) sumColumn(
			db,
			"SELECT SUM(points) FROM " + TABLE_LOYALTY_POINTS + " WHERE party_id=?",
			new String[]{String.valueOf(partyId)}
		);
	}

	public ArrayList<HashMap<String, Object>> getLoyaltyLedgerForParty(int partyId) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT points, date, time, reason FROM " + TABLE_LOYALTY_POINTS +
			" WHERE party_id=? ORDER BY date DESC, time DESC, id DESC",
			new String[]{String.valueOf(partyId)}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("points", cursor.getInt(0));
			row.put("date", cursor.getString(1));
			row.put("time", cursor.getString(2));
			row.put("reason", cursor.getString(3));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// Every party with a non-zero points balance, highest first - backs
	// LoyaltyPointsActivity.
	public ArrayList<HashMap<String, Object>> getLoyaltyRanking() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT p.id, p.name, SUM(l.points) AS total_points FROM " +
			TABLE_LOYALTY_POINTS + " l " +
			"INNER JOIN " + TABLE_PARTIES + " p ON p.id = l.party_id " +
			"GROUP BY l.party_id " +
			"HAVING total_points != 0 " +
			"ORDER BY total_points DESC",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("party_id", cursor.getInt(0));
			row.put("name", cursor.getString(1));
			row.put("points", cursor.getInt(2));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// =====================
	// REPORT: STOCK WORTH - current stock quantity times purchase_price,
	// split into shoes/non-shoes by the same name-prefix rule as
	// everywhere else. This is always a snapshot of right now: the app
	// doesn't keep historical stock-level snapshots, so there's no way
	// to reconstruct "stock worth as of a past date" without replaying
	// every transaction back to that point - out of scope here.
	// =====================
	public HashMap<String, Object> getStockWorthSummary() {

		HashMap<String, Object> map = new HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();

		double shoesWorth = sumColumn(
			db,
			"SELECT COALESCE(SUM(balance * purchase_price), 0) " +
			"FROM " + TABLE_ITEMS + " WHERE name LIKE 'Shoe%'",
			null
		);

		double nonShoesWorth = sumColumn(
			db,
			"SELECT COALESCE(SUM(balance * purchase_price), 0) " +
			"FROM " + TABLE_ITEMS + " WHERE name NOT LIKE 'Shoe%'",
			null
		);

		int shoesCount = (int) sumColumn(
			db,
			"SELECT COUNT(*) FROM " + TABLE_ITEMS + " WHERE name LIKE 'Shoe%'",
			null
		);

		int nonShoesCount = (int) sumColumn(
			db,
			"SELECT COUNT(*) FROM " + TABLE_ITEMS + " WHERE name NOT LIKE 'Shoe%'",
			null
		);

		map.put("shoes_worth", shoesWorth);
		map.put("non_shoes_worth", nonShoesWorth);
		map.put("total_worth", shoesWorth + nonShoesWorth);
		map.put("shoes_count", shoesCount);
		map.put("non_shoes_count", nonShoesCount);

		return map;
	}

	// =====================
	// REPORT: STOCK VALUE BY CATEGORY/AGE - "where's my money tied up".
	// Category here is the first word of the item's name - there's no
	// formal category field, but this shop's naming convention already
	// groups every shoe item under "Shoes ..." the same way Stock Worth
	// above does, and buckets everything else by whatever word the user
	// starts each item's name with. Age is how long since the item's
	// last sale (same signal as Slow-Moving Stock) - 0-30/31-60/61-90/
	// 90+ days, or Never Sold - a rough proxy for how long that stock
	// has realistically been sitting, since nothing tracks purchase-
	// batch-level aging. Both breakdowns slice the exact same total
	// (current stock value = balance * purchase_price, every active
	// item with stock > 0) two different ways.
	// =====================
	public HashMap<String, Object> getStockValueByCategoryAndAge() {

		HashMap<String, Object> result = new HashMap<String, Object>();

		LinkedHashMap<String, Double> byCategory = new LinkedHashMap<String, Double>();

		LinkedHashMap<String, Double> byAge = new LinkedHashMap<String, Double>();
		byAge.put("0-30 Days", 0.0);
		byAge.put("31-60 Days", 0.0);
		byAge.put("61-90 Days", 0.0);
		byAge.put("90+ Days", 0.0);
		byAge.put("Never Sold", 0.0);

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT i.name, i.balance, i.purchase_price, MAX(s.date) AS last_sale_date " +
			"FROM " + TABLE_ITEMS + " i " +
			"LEFT JOIN sale_items si ON si.item_id = i.id " +
			"LEFT JOIN sales s ON s.id = si.sale_id " +
			"WHERE i.active = 1 AND i.balance > 0 " +
			"GROUP BY i.id",
			null
		);

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
		long todayMillis = new Date().getTime();

		double totalValue = 0;

		while (cursor.moveToNext()) {

			String name = cursor.getString(0);
			double balance = cursor.getDouble(1);
			double purchasePrice = cursor.getDouble(2);
			String lastSaleDate = cursor.isNull(3) ? null : cursor.getString(3);

			double value = balance * purchasePrice;
			totalValue += value;

			String category =
				(name == null || name.trim().isEmpty()) ? "Other" : name.trim().split("\\s+")[0];

			Double existingCategoryValue = byCategory.get(category);
			byCategory.put(category, (existingCategoryValue == null ? 0.0 : existingCategoryValue) + value);

			String ageBucket;

			if (lastSaleDate == null) {

				ageBucket = "Never Sold";

			} else {

				int daysSince;

				try {

					long diffMillis = todayMillis - dateFormat.parse(lastSaleDate).getTime();
					daysSince = (int) (diffMillis / (1000L * 60 * 60 * 24));

				} catch (Exception e) {

					daysSince = 0;
				}

				if (daysSince <= 30) {
					ageBucket = "0-30 Days";
				} else if (daysSince <= 60) {
					ageBucket = "31-60 Days";
				} else if (daysSince <= 90) {
					ageBucket = "61-90 Days";
				} else {
					ageBucket = "90+ Days";
				}
			}

			byAge.put(ageBucket, byAge.get(ageBucket) + value);
		}

		cursor.close();

		result.put("total_value", totalValue);
		result.put("by_category", byCategory);
		result.put("by_age", byAge);

		return result;
	}

	// =====================
	// REPORT: ITEM MONTHLY RANK-OF-RANKS
	//
	// For each calendar month touched by the date range, every item
	// that sold that month is ranked against every other item that
	// sold that month (rank 1 = highest sales, or highest profit when
	// byProfit is true) using the same computeDescendingRanks() used by
	// the Week/Month/.../All Time index ranking above. An item's
	// per-month ranks are then summed across every month it appears in
	// - a LOWER sum means it ranked well (near the top) more
	// consistently, so the result is sorted with the lowest sum first.
	// Months an item didn't sell in simply don't contribute a rank
	// (neither a bonus nor a penalty) - this is what the feature was
	// asked for as specified, not a claim that it's bias-free.
	// =====================
	public ArrayList<HashMap<String, Object>> getItemMonthlyRankReport(
		String fromDate, String toDate, boolean byProfit, int shoesFilter) {

		SQLiteDatabase db = this.getReadableDatabase();

		String shoesWhere = "";

		if (shoesFilter == SHOES_FILTER_SHOES_ONLY) {
			shoesWhere = "AND i.name LIKE 'Shoe%' ";
		} else if (shoesFilter == SHOES_FILTER_NON_SHOES_ONLY) {
			shoesWhere = "AND i.name NOT LIKE 'Shoe%' ";
		}

		Cursor cursor = db.rawQuery(
			"SELECT i.id, i.name, strftime('%Y-%m', s.date) AS ym, " +
			"SUM(si.amount) AS sales_total, " +
			"SUM(si.qty * (i.purchase_price + i.extra_cost_per_unit)) AS cost_total " +
			"FROM sale_items si " +
			"INNER JOIN sales s ON s.id = si.sale_id " +
			"INNER JOIN " + TABLE_ITEMS + " i ON i.id = si.item_id " +
			"WHERE s.date BETWEEN ? AND ? " +
			shoesWhere +
			"GROUP BY i.id, i.name, ym",
			new String[]{fromDate, toDate}
		);

		// month -> list of {itemId, itemName, value}
		java.util.LinkedHashMap<String, ArrayList<Object[]>> byMonth =
			new java.util.LinkedHashMap<String, ArrayList<Object[]>>();

		// Summed across the WHOLE range (not per-month like the rank
		// calculation below needs) - the list row's own profit/sale
		// figures, independent of which metric is being ranked by.
		HashMap<Integer, Double> totalSalesById = new HashMap<Integer, Double>();
		HashMap<Integer, Double> totalProfitById = new HashMap<Integer, Double>();

		while (cursor.moveToNext()) {

			int itemId = cursor.getInt(0);
			String itemName = cursor.getString(1);
			String month = cursor.getString(2);
			double salesTotal = cursor.getDouble(3);
			double costTotal = cursor.getDouble(4);

			double value = byProfit ? (salesTotal - costTotal) : salesTotal;

			ArrayList<Object[]> monthRows = byMonth.get(month);

			if (monthRows == null) {
				monthRows = new ArrayList<Object[]>();
				byMonth.put(month, monthRows);
			}

			monthRows.add(new Object[]{itemId, itemName, value});

			totalSalesById.put(
				itemId, (totalSalesById.containsKey(itemId) ? totalSalesById.get(itemId) : 0) + salesTotal);
			totalProfitById.put(
				itemId,
				(totalProfitById.containsKey(itemId) ? totalProfitById.get(itemId) : 0)
					+ (salesTotal - costTotal));
		}

		cursor.close();

		if (byMonth.isEmpty()) {
			return new ArrayList<HashMap<String, Object>>();
		}

		// The item universe is everyone who sold at least once somewhere
		// in the range - the same set the old code implicitly used. The
		// month span is every calendar month between the first and last
		// month that had ANY sale in range (not the raw fromDate/toDate,
		// which for "All Time" starts at year 0000 and would enumerate
		// millennia of empty months for nothing).
		HashMap<Integer, String> nameById = new HashMap<Integer, String>();

		for (ArrayList<Object[]> monthRows : byMonth.values()) {
			for (Object[] row : monthRows) {
				nameById.put((Integer) row[0], (String) row[1]);
			}
		}

		ArrayList<String> months = new ArrayList<String>(byMonth.keySet());
		java.util.Collections.sort(months);

		ArrayList<String> allMonths = enumerateMonthRange(
			months.get(0), months.get(months.size() - 1));

		HashMap<Integer, Integer> rankSumById = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> monthsCountedById = new HashMap<Integer, Integer>();

		for (String month : allMonths) {

			ArrayList<Object[]> monthRows = byMonth.get(month);

			if (monthRows == null) {
				monthRows = new ArrayList<Object[]>();
			}

			double[] values = new double[monthRows.size()];

			for (int i = 0; i < monthRows.size(); i++) {
				values[i] = (Double) monthRows.get(i)[2];
			}

			int[] ranks = computeDescendingRanks(values);

			// A product absent this month, or with zero sales, ranks
			// worse than every seller that did have one - one past the
			// lowest (worst) rank actually handed out.
			int lastPlaceRank = monthRows.size() + 1;

			java.util.HashSet<Integer> soldThisMonth = new java.util.HashSet<Integer>();

			for (int i = 0; i < monthRows.size(); i++) {

				int itemId = (Integer) monthRows.get(i)[0];
				soldThisMonth.add(itemId);

				Integer existingSum = rankSumById.get(itemId);
				rankSumById.put(itemId, (existingSum == null ? 0 : existingSum) + ranks[i]);
			}

			for (Integer itemId : nameById.keySet()) {

				if (!soldThisMonth.contains(itemId)) {

					Integer existingSum = rankSumById.get(itemId);
					rankSumById.put(itemId, (existingSum == null ? 0 : existingSum) + lastPlaceRank);
				}

				Integer existingCount = monthsCountedById.get(itemId);
				monthsCountedById.put(itemId, (existingCount == null ? 0 : existingCount) + 1);
			}
		}

		ArrayList<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();

		for (Integer itemId : rankSumById.keySet()) {

			HashMap<String, Object> row = new HashMap<String, Object>();

			row.put("item_id", itemId);
			row.put("item_name", nameById.get(itemId));
			row.put("rank_sum", rankSumById.get(itemId));
			row.put("months_counted", monthsCountedById.get(itemId));
			row.put("total_sales", totalSalesById.containsKey(itemId) ? totalSalesById.get(itemId) : 0.0);
			row.put("total_profit", totalProfitById.containsKey(itemId) ? totalProfitById.get(itemId) : 0.0);

			result.add(row);
		}

		java.util.Collections.sort(
			result,
			new java.util.Comparator<HashMap<String, Object>>() {

				@Override
				public int compare(HashMap<String, Object> a, HashMap<String, Object> b) {
					return ((Integer) a.get("rank_sum")).compareTo((Integer) b.get("rank_sum"));
				}
			}
		);

		return result;
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

		// This sale's unpaid portion (if any) shifted its party's
		// balance when it was saved - reverse that before the row
		// disappears, same reversal updateSale() already does on edit,
		// or the party's balance would be left stranded with an
		// adjustment for a sale that no longer exists.
		Cursor oldCursor = db.rawQuery(
			"SELECT party_id, balance FROM sales WHERE id=?",
			new String[]{saleId}
		);

		if (oldCursor.moveToFirst()) {

			int oldPartyId = oldCursor.getInt(0);
			double oldDue = oldCursor.getDouble(1);

			adjustPartyBalance(db, oldPartyId, -oldDue);
		}

		oldCursor.close();

		db.delete(
			"sales",
			"id=?",
			new String[]{saleId}
		);
	}

	// =====================
	// RECENTLY DELETED / UNDO - a deleted Purchase/Sale's full row plus
	// its line items, snapshotted as one JSON blob immediately before
	// the delete that Transactionactivity's single/bulk delete paths
	// now take, and restorable from that same blob. Deliberately a raw
	// row dump (every column, by name, via the cursor's own column
	// list) rather than hand-picking fields - the snapshot then can't
	// drift out of sync with the schema the way a hardcoded field list
	// eventually would.
	//
	// Restoring re-inserts the row(s) with their ORIGINAL id (SQLite
	// allows an explicit id on an INSERT into an AUTOINCREMENT column
	// as long as it's not currently taken, which it never is right
	// after that same id was deleted) - this is what lets sale_items/
	// purchase_items snapshot their original sale_id/purchase_id
	// straight through with no remapping. Restoring then re-applies the
	// exact mirror-image of whatever deletePurchase()/deleteSale() +
	// deleteSaleItems() reversed, so every balance-reversal side effect
	// of the original delete is itself reversed, the same way an edit
	// un-does and re-does those effects.
	// =====================

	private JSONObject rowToJson(Cursor cursor) throws JSONException {

		JSONObject obj = new JSONObject();

		for (int i = 0; i < cursor.getColumnCount(); i++) {

			String name = cursor.getColumnName(i);

			switch (cursor.getType(i)) {

				case Cursor.FIELD_TYPE_INTEGER:
					obj.put(name, cursor.getLong(i));
					break;

				case Cursor.FIELD_TYPE_FLOAT:
					obj.put(name, cursor.getDouble(i));
					break;

				case Cursor.FIELD_TYPE_NULL:
					obj.put(name, JSONObject.NULL);
					break;

				default:
					obj.put(name, cursor.getString(i));
					break;
			}
		}

		return obj;
	}

	private ContentValues jsonToContentValues(JSONObject obj) throws JSONException {

		ContentValues values = new ContentValues();

		Iterator<String> keys = obj.keys();

		while (keys.hasNext()) {

			String key = keys.next();
			Object value = obj.get(key);

			if (value == JSONObject.NULL) {
				values.putNull(key);
			} else if (value instanceof Integer) {
				values.put(key, (Integer) value);
			} else if (value instanceof Long) {
				values.put(key, (Long) value);
			} else if (value instanceof Double) {
				values.put(key, (Double) value);
			} else {
				values.put(key, value.toString());
			}
		}

		return values;
	}

	// Snapshots the sale and its sale_items, writes it to
	// TABLE_RECENTLY_DELETED, then deletes the sale exactly as
	// deleteSaleItems()+deleteSale() already did - callers replace
	// those two calls with this one to get undo for free.
	public void snapshotAndDeleteSale(String saleId, String label, String date, String time) {

		SQLiteDatabase db = this.getWritableDatabase();

		try {

			JSONObject root = new JSONObject();

			Cursor saleCursor = db.rawQuery("SELECT * FROM sales WHERE id=?", new String[]{saleId});

			if (saleCursor.moveToFirst()) {
				root.put("sale", rowToJson(saleCursor));
			}

			saleCursor.close();

			JSONArray items = new JSONArray();

			Cursor itemsCursor = db.rawQuery(
				"SELECT * FROM sale_items WHERE sale_id=?", new String[]{saleId}
			);

			while (itemsCursor.moveToNext()) {
				items.put(rowToJson(itemsCursor));
			}

			itemsCursor.close();

			root.put("items", items);

			insertRecentlyDeleted("sale", label, root.toString(), date, time);

		} catch (JSONException e) {
			// Never blocks the delete itself - losing undo for this one
			// row is far better than refusing to let it be deleted.
		}

		deleteSaleItems(saleId);
		deleteSale(saleId);
	}

	// Same idea for a Purchase - mirrors deletePurchase()'s own body
	// instead of calling it, since deletePurchase() does the item-row
	// delete and the balance reversal in one transaction that this
	// needs to snapshot the middle of.
	public void snapshotAndDeletePurchase(
		int purchaseId, String label, String date, String time) {

		SQLiteDatabase db = this.getWritableDatabase();

		try {

			JSONObject root = new JSONObject();

			Cursor purchaseCursor = db.rawQuery(
				"SELECT * FROM " + TABLE_PURCHASES + " WHERE id=?",
				new String[]{String.valueOf(purchaseId)}
			);

			if (purchaseCursor.moveToFirst()) {
				root.put("purchase", rowToJson(purchaseCursor));
			}

			purchaseCursor.close();

			JSONArray items = new JSONArray();

			Cursor itemsCursor = db.rawQuery(
				"SELECT * FROM " + TABLE_PURCHASE_ITEMS + " WHERE purchase_id=?",
				new String[]{String.valueOf(purchaseId)}
			);

			while (itemsCursor.moveToNext()) {
				items.put(rowToJson(itemsCursor));
			}

			itemsCursor.close();

			root.put("items", items);

			insertRecentlyDeleted("purchase", label, root.toString(), date, time);

		} catch (JSONException e) {
			// Never blocks the delete itself.
		}

		deletePurchase(purchaseId);
	}

	public long insertRecentlyDeleted(
		String type, String label, String data, String date, String time) {

		ContentValues values = new ContentValues();
		values.put("type", type);
		values.put("label", label);
		values.put("data", data);
		values.put("deleted_date", date);
		values.put("deleted_time", time);

		SQLiteDatabase db = this.getWritableDatabase();

		return db.insert(TABLE_RECENTLY_DELETED, null, values);
	}

	public ArrayList<HashMap<String, Object>> getRecentlyDeleted() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, type, label, deleted_date, deleted_time FROM " +
			TABLE_RECENTLY_DELETED + " ORDER BY deleted_date DESC, deleted_time DESC",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("id", cursor.getInt(0));
			row.put("type", cursor.getString(1));
			row.put("label", cursor.getString(2));
			row.put("deleted_date", cursor.getString(3));
			row.put("deleted_time", cursor.getString(4));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	public void deleteRecentlyDeletedPermanently(int trashId) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(
			TABLE_RECENTLY_DELETED, "id=?", new String[]{String.valueOf(trashId)}
		);
	}

	// Purges every entry older than the given number of days - called
	// opportunistically (BusinessERPApplication.onCreate()) so Recently
	// Deleted doesn't grow forever; a row this old was never going to
	// be restored.
	public void purgeOldRecentlyDeleted(int days) {

		Calendar cutoff = Calendar.getInstance();
		cutoff.add(Calendar.DAY_OF_MONTH, -days);

		String cutoffDate =
			new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cutoff.getTime());

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(
			TABLE_RECENTLY_DELETED, "deleted_date < ?", new String[]{cutoffDate}
		);
	}

	// Returns the raw snapshot JSON for one trash entry, or null if it
	// was already restored/purged by the time this runs.
	public String getRecentlyDeletedSnapshot(int trashId) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT data FROM " + TABLE_RECENTLY_DELETED + " WHERE id=?",
			new String[]{String.valueOf(trashId)}
		);

		String data = null;

		if (cursor.moveToFirst()) {
			data = cursor.getString(0);
		}

		cursor.close();

		return data;
	}

	// Re-inserts the sale + its sale_items with their original ids,
	// then re-applies the exact mirror of what deleteSaleItems()/
	// deleteSale() reversed: sale_items took stock out, so restoring
	// takes it out again; the sale's due amount shifted the party's
	// balance, so restoring shifts it again.
	public boolean restoreSaleFromTrash(int trashId) {

		String json = getRecentlyDeletedSnapshot(trashId);

		if (json == null) {
			return false;
		}

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			reinsertSaleSnapshot(db, new JSONObject(json));

			deleteRecentlyDeletedPermanently(trashId);

			db.setTransactionSuccessful();

			return true;

		} catch (JSONException e) {

			return false;

		} finally {

			db.endTransaction();
		}
	}

	// Mirror of restoreSaleFromTrash() for a Purchase - re-applies
	// what deletePurchase() reversed: purchase_items added stock, so
	// restoring adds it back; the purchase's due amount shifted the
	// party's balance, so restoring shifts it back.
	public boolean restorePurchaseFromTrash(int trashId) {

		String json = getRecentlyDeletedSnapshot(trashId);

		if (json == null) {
			return false;
		}

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			reinsertPurchaseSnapshot(db, new JSONObject(json));

			deleteRecentlyDeletedPermanently(trashId);

			db.setTransactionSuccessful();

			return true;

		} catch (JSONException e) {

			return false;

		} finally {

			db.endTransaction();
		}
	}

	// Shared re-insertion step for restoreSaleFromTrash() and
	// undoSaleEdit() - inserts the snapshotted sale/sale_items back
	// with their original ids and re-applies the balance effects a
	// fresh save of them would have made.
	private void reinsertSaleSnapshot(SQLiteDatabase db, JSONObject root) throws JSONException {

		JSONObject saleJson = root.getJSONObject("sale");

		db.insertOrThrow("sales", null, jsonToContentValues(saleJson));

		JSONArray items = root.getJSONArray("items");

		for (int i = 0; i < items.length(); i++) {

			JSONObject itemJson = items.getJSONObject(i);

			db.insertOrThrow("sale_items", null, jsonToContentValues(itemJson));

			int itemId = itemJson.getInt("item_id");
			double qty = itemJson.getDouble("qty");

			adjustItemBalance(db, itemId, -qty);

			if (!itemJson.isNull("combo_id")) {
				adjustComboBalance(db, itemJson.getInt("combo_id"), -qty);
			}
		}

		int partyId = saleJson.getInt("party_id");
		double oldDue = saleJson.getDouble("balance");

		adjustPartyBalance(db, partyId, oldDue);
	}

	// Shared re-insertion step for restorePurchaseFromTrash() and
	// undoPurchaseEdit().
	private void reinsertPurchaseSnapshot(SQLiteDatabase db, JSONObject root) throws JSONException {

		JSONObject purchaseJson = root.getJSONObject("purchase");

		db.insertOrThrow(TABLE_PURCHASES, null, jsonToContentValues(purchaseJson));

		JSONArray items = root.getJSONArray("items");

		for (int i = 0; i < items.length(); i++) {

			JSONObject itemJson = items.getJSONObject(i);

			db.insertOrThrow(TABLE_PURCHASE_ITEMS, null, jsonToContentValues(itemJson));

			int itemId = itemJson.getInt("item_id");
			double qty = itemJson.getDouble("quantity");

			adjustItemBalance(db, itemId, qty);

			if (!itemJson.isNull("combo_id")) {
				adjustComboBalance(db, itemJson.getInt("combo_id"), qty);
			}
		}

		int partyId = purchaseJson.getInt("party_id");
		double oldDue = purchaseJson.getDouble("grand_total") - purchaseJson.getDouble("amount_paid");

		adjustPartyBalance(db, partyId, -oldDue);
	}

	// =====================
	// UNDO-BEYOND-DELETE - the same Recently Deleted trash also holds a
	// "before edit" snapshot (type "sale_edit"/"purchase_edit", taken by
	// snapshotSaleBeforeEdit()/snapshotPurchaseBeforeEdit() right before
	// Transactioneditactivity's Update button applies the new values),
	// so an accidental edit is just as undoable as a delete, not only a
	// delete. Undoing one deletes the CURRENT (post-edit) row exactly as
	// a normal delete would - deleteSaleItems()/deleteSale() already
	// reverse its balance effects - then re-inserts the pre-edit
	// snapshot via the same reinsertSaleSnapshot()/
	// reinsertPurchaseSnapshot() restoreXFromTrash() itself uses, so the
	// old values' balance effects come back exactly as they were.
	// =====================
	public boolean undoSaleEdit(int trashId) {

		String json = getRecentlyDeletedSnapshot(trashId);

		if (json == null) {
			return false;
		}

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			JSONObject root = new JSONObject(json);
			String saleId = String.valueOf(root.getJSONObject("sale").getInt("id"));

			deleteSaleItems(saleId);
			deleteSale(saleId);

			reinsertSaleSnapshot(db, root);

			deleteRecentlyDeletedPermanently(trashId);

			db.setTransactionSuccessful();

			return true;

		} catch (JSONException e) {

			return false;

		} finally {

			db.endTransaction();
		}
	}

	public boolean undoPurchaseEdit(int trashId) {

		String json = getRecentlyDeletedSnapshot(trashId);

		if (json == null) {
			return false;
		}

		SQLiteDatabase db = this.getWritableDatabase();

		db.beginTransaction();

		try {

			JSONObject root = new JSONObject(json);
			int purchaseId = root.getJSONObject("purchase").getInt("id");

			deletePurchase(purchaseId);

			reinsertPurchaseSnapshot(db, root);

			deleteRecentlyDeletedPermanently(trashId);

			db.setTransactionSuccessful();

			return true;

		} catch (JSONException e) {

			return false;

		} finally {

			db.endTransaction();
		}
	}

	// Snapshots a Sale's current row + line items (type "sale_edit")
	// immediately before Transactioneditactivity applies an edit to it
	// - see undoSaleEdit(). Label mirrors Transactionactivity's own
	// labelForTransaction() format ("Sale #<invoice> - <party>").
	public void snapshotSaleBeforeEdit(int saleId) {

		SQLiteDatabase db = this.getWritableDatabase();

		try {

			JSONObject root = new JSONObject();

			Cursor saleCursor = db.rawQuery(
				"SELECT * FROM sales WHERE id=?", new String[]{String.valueOf(saleId)}
			);

			if (saleCursor.moveToFirst()) {
				root.put("sale", rowToJson(saleCursor));
			}

			saleCursor.close();

			JSONArray items = new JSONArray();

			Cursor itemsCursor = db.rawQuery(
				"SELECT * FROM sale_items WHERE sale_id=?", new String[]{String.valueOf(saleId)}
			);

			while (itemsCursor.moveToNext()) {
				items.put(rowToJson(itemsCursor));
			}

			itemsCursor.close();

			root.put("items", items);

			String label = "Sale";

			Cursor labelCursor = db.rawQuery(
				"SELECT s.invoice_no, p.name FROM sales s LEFT JOIN " + TABLE_PARTIES +
				" p ON p.id = s.party_id WHERE s.id=?",
				new String[]{String.valueOf(saleId)}
			);

			if (labelCursor.moveToFirst()) {

				String invoice = labelCursor.getString(0);
				String partyName = labelCursor.getString(1);

				label = "Sale #" + invoice +
					(partyName != null && partyName.length() > 0 ? " - " + partyName : "");
			}

			labelCursor.close();

			String nowDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
			String nowTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

			insertRecentlyDeleted("sale_edit", label, root.toString(), nowDate, nowTime);

		} catch (JSONException e) {
			// Never blocks the edit itself - losing undo for this one
			// edit is far better than refusing to let it save.
		}
	}

	// Mirror of snapshotSaleBeforeEdit() for a Purchase.
	public void snapshotPurchaseBeforeEdit(int purchaseId) {

		SQLiteDatabase db = this.getWritableDatabase();

		try {

			JSONObject root = new JSONObject();

			Cursor purchaseCursor = db.rawQuery(
				"SELECT * FROM " + TABLE_PURCHASES + " WHERE id=?",
				new String[]{String.valueOf(purchaseId)}
			);

			if (purchaseCursor.moveToFirst()) {
				root.put("purchase", rowToJson(purchaseCursor));
			}

			purchaseCursor.close();

			JSONArray items = new JSONArray();

			Cursor itemsCursor = db.rawQuery(
				"SELECT * FROM " + TABLE_PURCHASE_ITEMS + " WHERE purchase_id=?",
				new String[]{String.valueOf(purchaseId)}
			);

			while (itemsCursor.moveToNext()) {
				items.put(rowToJson(itemsCursor));
			}

			itemsCursor.close();

			root.put("items", items);

			String label = "Purchase";

			Cursor labelCursor = db.rawQuery(
				"SELECT pu.invoice_number, p.name FROM " + TABLE_PURCHASES + " pu LEFT JOIN " +
				TABLE_PARTIES + " p ON p.id = pu.party_id WHERE pu.id=?",
				new String[]{String.valueOf(purchaseId)}
			);

			if (labelCursor.moveToFirst()) {

				String code = labelCursor.getString(0);
				String partyName = labelCursor.getString(1);

				label = "Purchase #" + code +
					(partyName != null && partyName.length() > 0 ? " - " + partyName : "");
			}

			labelCursor.close();

			String nowDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
			String nowTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

			insertRecentlyDeleted("purchase_edit", label, root.toString(), nowDate, nowTime);

		} catch (JSONException e) {
			// Never blocks the edit itself.
		}
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

		values.put(
			"due_date",
			saleData.get("due_date") == null ? null : saleData.get("due_date").toString()
		);

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
			"ORDER BY py.date DESC, py.time DESC",

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
			"ORDER BY py.date DESC, py.time DESC",

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
			"ORDER BY py.date DESC, py.time DESC",

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
			"ORDER BY py.date DESC, py.time DESC",

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

	// Type + date-range combined - DayCloseReportActivity's Payments In/
	// Out cards open here scoped to just the one day they're showing.
	public ArrayList<HashMap<String, Object>> getPaymentsByType(
		int type, String fromDate, String toDate
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
			"WHERE py.type=? AND py.date BETWEEN ? AND ? " +
			"ORDER BY py.date DESC, py.time DESC",

			new String[]{
				String.valueOf(type),
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

		// Any unpaid portion of this expense is money we now owe the
		// party (petrol station, landlord, ...) it was billed to - same
		// convention as a Purchase. "Cash Expenses" and any other party
		// still gets this call; it's just always a no-op for them since
		// they're required to be paid in full.
		if (partyId != null) {

			double due = amount - paidAmount;
			adjustPartyBalance(db, partyId, -due);
		}

		return id;
	}

// =====================
// LAST EXPENSE AMOUNT FOR AN ITEM
// =====================

	// The N most frequently logged expense items in the last 7 days (by
	// how many times each distinct item text has been used in that
	// rolling window, not by total amount spent) - each paired with its
	// own most recent amount (from any time, not just the window), for
	// the Expenses screen's quick-add boxes. A window this short can
	// come back with fewer than `limit` items (or none) for a
	// low-activity week - callers just show however many boxes that is,
	// rather than backfilling from all-time history.
	public ArrayList<HashMap<String, Object>> getTopExpenseItems(int limit) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<>();

		SQLiteDatabase db = this.getReadableDatabase();

		Calendar weekAgo = Calendar.getInstance();
		weekAgo.add(Calendar.DAY_OF_YEAR, -7);

		String weekAgoDate =
			new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(weekAgo.getTime());

		Cursor cursor = db.rawQuery(
			"SELECT item, COUNT(*) AS uses FROM " + TABLE_EXPENSES +
			" WHERE date >= ? GROUP BY item ORDER BY uses DESC, item ASC LIMIT ?",
			new String[]{weekAgoDate, String.valueOf(limit)}
		);

		while (cursor.moveToNext()) {

			String item = cursor.getString(0);
			Double amount = getLastExpenseAmountForItem(item);

			HashMap<String, Object> row = new HashMap<>();
			row.put("item", item);
			row.put("amount", amount == null ? 0.0 : amount);

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// Most recent amount an Expense with this exact item text was
	// recorded for - used to pre-fill the Amount field the moment that
	// item is picked from the dropdown (Expenseeditactivity), and to
	// label the Expenses screen's "top items" quick-add boxes. Null when
	// this item has never been used before.
	public Double getLastExpenseAmountForItem(String item) {

		if (item == null || item.trim().length() == 0) {
			return null;
		}

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT amount FROM " + TABLE_EXPENSES +
			" WHERE item=? ORDER BY date DESC, time DESC, id DESC LIMIT 1",
			new String[]{item.trim()}
		);

		Double result = null;

		if (cursor.moveToFirst()) {
			result = cursor.getDouble(0);
		}

		cursor.close();

		return result;
	}

	// Average amount + how many prior expenses this exact item text has,
	// excluding excludeExpenseId (the one being edited, if any) - used
	// to flag a new/edited expense that's unusually high for its own
	// category (Expenseeditactivity.saveExpense()). count is 0 and
	// average is 0 when this item has never been used before (or only
	// by the entry being edited).
	public HashMap<String, Object> getExpenseAmountStatsForItem(String item, int excludeExpenseId) {

		HashMap<String, Object> stats = new HashMap<String, Object>();

		if (item == null || item.trim().length() == 0) {

			stats.put("average", 0.0);
			stats.put("count", 0);

			return stats;
		}

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT AVG(amount), COUNT(*) FROM " + TABLE_EXPENSES +
			" WHERE item=? AND id != ?",
			new String[]{item.trim(), String.valueOf(excludeExpenseId)}
		);

		double average = 0;
		int count = 0;

		if (cursor.moveToFirst() && !cursor.isNull(0)) {
			average = cursor.getDouble(0);
			count = cursor.getInt(1);
		}

		cursor.close();

		stats.put("average", average);
		stats.put("count", count);

		return stats;
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

			// Excludes an expense already linked to a purchase (see
			// TABLE_PURCHASE_EXPENSE_LINKS) - it's now that purchase's
			// landed cost instead of a standalone operating expense, and
			// showing it here too would double-count it.
			"SELECT * FROM " +
			TABLE_EXPENSES +
			" WHERE id NOT IN (SELECT expense_id FROM " + TABLE_PURCHASE_EXPENSE_LINKS + ")" +
			" ORDER BY date DESC, time DESC",

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

		SQLiteDatabase db = this.getWritableDatabase();

		// Reverse this expense's previous effect on its old party's
		// balance before applying the new one below - same
		// "reverse-old-then-apply-new" pattern as updatePurchase(), so
		// editing an expense (amount, paid amount, or even the party
		// itself) doesn't double up or strand the earlier adjustment.
		Cursor oldCursor = db.rawQuery(
			"SELECT party_id, amount, paid_amount FROM " +
			TABLE_EXPENSES + " WHERE id=?",
			new String[]{String.valueOf(expenseId)}
		);

		if (oldCursor.moveToFirst() && !oldCursor.isNull(0)) {

			int oldPartyId = oldCursor.getInt(0);
			double oldDue = oldCursor.getDouble(1) - oldCursor.getDouble(2);

			adjustPartyBalance(db, oldPartyId, oldDue);
		}

		oldCursor.close();

		ContentValues values = new ContentValues();

		values.put("item", item);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("paid_amount", paidAmount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("source", "Manual");

		int rows = db.update(
			TABLE_EXPENSES,
			values,
			"id=?",
			new String[]{
				String.valueOf(expenseId)
			}
		);

		if (partyId != null) {
			adjustPartyBalance(db, partyId, -(amount - paidAmount));
		}

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

		// This expense's unpaid portion (if any) shifted its party's
		// balance when it was saved - reverse that before the row
		// disappears, same as updateExpense()'s reversal, or the
		// party's balance would be left stranded.
		Cursor oldCursor = db.rawQuery(
			"SELECT party_id, amount, paid_amount FROM " +
			TABLE_EXPENSES + " WHERE id=?",
			new String[]{String.valueOf(expenseId)}
		);

		if (oldCursor.moveToFirst() && !oldCursor.isNull(0)) {

			int oldPartyId = oldCursor.getInt(0);
			double oldDue = oldCursor.getDouble(1) - oldCursor.getDouble(2);

			adjustPartyBalance(db, oldPartyId, oldDue);
		}

		oldCursor.close();

		// Cleans up a dangling link if this expense was linked to a
		// purchase - same "not retroactively corrected" simplification
		// as elsewhere: the extra_cost_per_unit blend that link already
		// applied stays as-is, only the now-orphaned link row is removed.
		db.delete(
			TABLE_PURCHASE_EXPENSE_LINKS,
			"expense_id=?",
			new String[]{String.valueOf(expenseId)}
		);

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

	// Non-bulk convenience wrapper for a single call outside an existing
	// import transaction - e.g. defaulting a new Sale/Expense's party to
	// a synthetic "Cash Sale"/"Cash Expenses" party, creating it the
	// first time it's needed on an install with no such party yet.
	public int getOrCreatePartyId(String name) {

		SQLiteDatabase db = this.getWritableDatabase();
		return getOrCreatePartyIdBulk(db, name);
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

	// =====================
	// COST ITEMS - the reusable category list (Petrol, Shipping,
	// Packaging, food, ...) used by Expenses' Item field (an expense in
	// one of these categories can then be linked to a purchase as its
	// landed cost - see TABLE_PURCHASE_EXPENSE_LINKS).
	// =====================
	public int getOrCreateCostItemId(String name) {

		if (name == null || name.trim().length() == 0) {
			return -1;
		}

		name = name.trim();

		SQLiteDatabase db = this.getWritableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_COST_ITEMS + " WHERE name=?",
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
			TABLE_COST_ITEMS,
			null,
			values,
			SQLiteDatabase.CONFLICT_IGNORE
		);

		if (id != -1) {
			return (int) id;
		}

		Cursor again = db.rawQuery(
			"SELECT id FROM " + TABLE_COST_ITEMS + " WHERE name=?",
			new String[]{name}
		);

		int existingId = again.moveToFirst() ? again.getInt(0) : -1;
		again.close();

		return existingId;
	}

	public ArrayList<HashMap<String, Object>> getCostItems() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, name FROM " + TABLE_COST_ITEMS + " ORDER BY name",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("id", cursor.getInt(0));
			row.put("name", cursor.getString(1));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	public HashMap<String, Object> getCostItemById(int id) {

		HashMap<String, Object> map = new HashMap<String, Object>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, name FROM " + TABLE_COST_ITEMS + " WHERE id=?",
			new String[]{String.valueOf(id)}
		);

		if (cursor.moveToFirst()) {

			map.put("id", cursor.getInt(0));
			map.put("name", cursor.getString(1));
		}

		cursor.close();

		return map;
	}

	public boolean updateCostItem(int id, String name) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put("name", name.trim());

		int rows = db.update(
			TABLE_COST_ITEMS, values, "id=?", new String[]{String.valueOf(id)}
		);

		return rows > 0;
	}

	public void deleteCostItem(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_COST_ITEMS, "id=?", new String[]{String.valueOf(id)});
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
		values.put("due_date", (String) saleData.get("due_date"));

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

		return insertPurchaseBulk(
			db, partyId, date, time, invoiceNumber, grandTotal, amountPaid, notes, source, null
		);
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
		String source,
		String dueDate) {

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
		values.put("due_date", dueDate);

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

		return insertExpenseBulk(db, item, date, time, amount, amount, notes, partyId, source);
	}

	// Vyapar import needs to preserve the historical paid/credit split
	// (TABLE_EXPENSES.paid_amount) instead of always treating an imported
	// expense as fully paid - the plain 7-arg overload above still does
	// that for its one other caller (GenerateEntriesActivity, which has no
	// paid/credit concept of its own).
	public long insertExpenseBulk(
		SQLiteDatabase db,
		String item,
		String date,
		String time,
		double amount,
		double paidAmount,
		String notes,
		Integer partyId,
		String source) {

		ContentValues values = new ContentValues();

		values.put("code", generateNextExpenseCode());
		values.put("item", item);
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("paid_amount", paidAmount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("source", source);

		long id = db.insert(TABLE_EXPENSES, null, values);

		// Same balance effect as insertExpense() - a bulk/import source
		// expense with a party still owes that party for its unpaid
		// portion, same as a manually-entered one.
		if (partyId != null) {

			double due = amount - paidAmount;
			adjustPartyBalance(db, partyId, -due);
		}

		return id;
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
		double salePrice,
		double extraCostPerUnit) {

		return insertItemBulk(db, preferredCode, name, purchasePrice, salePrice, extraCostPerUnit, true);
	}

	public long insertItemBulk(
		SQLiteDatabase db,
		String preferredCode,
		String name,
		double purchasePrice,
		double salePrice,
		double extraCostPerUnit,
		boolean active) {

		return insertItemBulk(
			db, preferredCode, name, purchasePrice, salePrice, extraCostPerUnit, active, 0
		);
	}

	public long insertItemBulk(
		SQLiteDatabase db,
		String preferredCode,
		String name,
		double purchasePrice,
		double salePrice,
		double extraCostPerUnit,
		boolean active,
		double reorderThreshold) {

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
		values.put("extra_cost_per_unit", extraCostPerUnit);
		values.put("active", active ? 1 : 0);
		values.put("reorder_threshold", reorderThreshold);

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

	// True if itemId has at least one variety group (e.g. "Size") - used
	// by the Vyapar importer to tell a genuinely combo-less item from one
	// that needs a combo_id the source row didn't provide (see the
	// sizeless-line guard in ImportVyaparActivity).
	public boolean itemHasVarietyGroupsBulk(SQLiteDatabase db, int itemId) {

		Cursor cursor = db.rawQuery(
			"SELECT 1 FROM " + TABLE_VARIETY_GROUPS + " WHERE item_id=? LIMIT 1",
			new String[]{String.valueOf(itemId)}
		);

		boolean has = cursor.moveToFirst();

		cursor.close();

		return has;
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

	// Bulk-import counterpart of getOrCreateCostItemId() - operates on the
	// given db (the shared migration connection) instead of
	// getWritableDatabase(), and is get-or-create by name exactly like the
	// interactive version, since cost_items.name is UNIQUE and nothing
	// downstream references a cost item by id (Expenses.item is plain
	// text), so there's no id to preserve or remap here - only the name
	// needs to exist again.
	public long insertCostItemBulk(SQLiteDatabase db, String name) {

		if (name == null || name.trim().length() == 0) {
			return -1;
		}

		name = name.trim();

		Cursor cursor = db.rawQuery(
			"SELECT id FROM " + TABLE_COST_ITEMS + " WHERE name=?",
			new String[]{name}
		);

		if (cursor.moveToFirst()) {

			long id = cursor.getLong(0);
			cursor.close();
			return id;
		}

		cursor.close();

		ContentValues values = new ContentValues();
		values.put("name", name);

		return db.insertWithOnConflict(
			TABLE_COST_ITEMS, null, values, SQLiteDatabase.CONFLICT_IGNORE
		);
	}

	// Bulk-import counterpart used only by the Vyapar importer - a raw
	// insert with none of applySingleExpensePurchaseLink()'s side effects
	// (it never blends into extra_cost_per_unit or touches cash), since a
	// Vyapar restore already gets each item's current extra_cost_per_unit
	// back verbatim via insertItemBulk() above. This only needs to restore
	// the link record itself, so the linked expense stays excluded from
	// plain expense totals and the Purchase screen still shows it.
	public long insertPurchaseExpenseLinkBulk(
		SQLiteDatabase db, int expenseId, int purchaseId, double sharePercent, double allocatedAmount) {

		ContentValues values = new ContentValues();
		values.put("expense_id", expenseId);
		values.put("purchase_id", purchaseId);
		values.put("share_percent", sharePercent);
		values.put("allocated_amount", allocatedAmount);

		return db.insert(TABLE_PURCHASE_EXPENSE_LINKS, null, values);
	}

	// Bulk-import counterpart of insertRecurringExpense() - operates on the
	// given db and additionally preserves last_generated_date/active
	// exactly as the backup had them, unlike the interactive version
	// (always called for a brand-new rule that hasn't generated anything
	// yet, so it has no last_generated_date and is always active).
	public long insertRecurringExpenseBulk(
		SQLiteDatabase db,
		String item,
		double amount,
		String notes,
		Integer partyId,
		int frequency,
		Integer dayOfWeek,
		Integer dayOfMonth,
		String specificDates,
		String startDate,
		String lastGeneratedDate,
		boolean active) {

		ContentValues values = new ContentValues();
		values.put("item", item);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("frequency", frequency);
		values.put("day_of_week", dayOfWeek);
		values.put("day_of_month", dayOfMonth);
		values.put("specific_dates", specificDates);
		values.put("start_date", startDate);
		values.put("last_generated_date", lastGeneratedDate);
		values.put("active", active ? 1 : 0);

		return db.insert(TABLE_RECURRING_EXPENSES, null, values);
	}

	// Bulk-import counterpart of insertDraft() - operates on the given db
	// instead of getWritableDatabase(). The "data" blob's own embedded ids
	// (party/item/combo/expense) are remapped by the caller (see
	// ImportVyaparActivity.importDrafts()) before this is called - this
	// method itself is just a plain insert.
	public long insertDraftBulk(
		SQLiteDatabase db, String type, String label, String data, String date, String time) {

		ContentValues values = new ContentValues();
		values.put("type", type);
		values.put("label", label);
		values.put("data", data);
		values.put("date", date);
		values.put("time", time);

		return db.insert(TABLE_DRAFTS, null, values);
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

		db.delete(TABLE_PURCHASE_EXPENSE_LINKS, null, null);
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
		db.delete(TABLE_COST_ITEMS, null, null);
		db.delete(TABLE_RECURRING_EXPENSES, null, null);
		db.delete(TABLE_DRAFTS, null, null);
		db.delete(TABLE_DISPLAY_SHOES, null, null);
		db.delete(TABLE_SAMPLE_SHOES, null, null);
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

	// =====================
	// CASH IN HAND
	// =====================
	// "Cash" here means money that has actually changed hands right now -
	// the paid portion of a sale/purchase/expense (not its full total),
	// a payment (always fully cash, no partial concept), and manual
	// adjustments. Computed live from the ledger each time, not stored/
	// maintained as a running balance - this app's data volume makes a
	// handful of SUM queries cheap, and it avoids having to hook a
	// balance-adjustment call into dozens of existing insert/delete/
	// update call sites the way items.balance/parties.balance already
	// are. Party-to-party transfers are deliberately excluded - they
	// move balances between two parties' accounts, not necessarily this
	// business's own cash in hand.
	// =====================

	private double sumColumn(SQLiteDatabase db, String sql, String[] args) {

		Cursor cursor = db.rawQuery(sql, args);

		double result = 0;

		if (cursor.moveToFirst() && !cursor.isNull(0)) {
			result = cursor.getDouble(0);
		}

		cursor.close();

		return result;
	}

	public double getCashBalance() {

		SQLiteDatabase db = this.getReadableDatabase();

		double balance = 0;

		balance += sumColumn(db, "SELECT SUM(paid_amount) FROM sales", null);
		balance += sumColumn(db,
			"SELECT SUM(amount) FROM " + TABLE_PAYMENTS + " WHERE type=?",
			new String[]{String.valueOf(PAYMENT_IN)});
		balance += sumColumn(db, "SELECT SUM(amount) FROM " + TABLE_CASH_ADJUSTMENTS, null);

		balance -= sumColumn(db, "SELECT SUM(amount_paid) FROM " + TABLE_PURCHASES, null);
		balance -= sumColumn(db,
			"SELECT SUM(amount) FROM " + TABLE_PAYMENTS + " WHERE type=?",
			new String[]{String.valueOf(PAYMENT_OUT)});
		balance -= sumColumn(db, "SELECT SUM(paid_amount) FROM " + TABLE_EXPENSES, null);

		return balance;
	}

	// One row per cash-affecting transaction - amount is the signed cash
	// effect (positive = cash in, negative = cash out), so the Cash
	// screen can show a plain running list without exposing full
	// transaction detail. Rows with a zero cash effect (e.g. a fully
	// credit purchase) are left out - there's nothing to show for them
	// here.
	public ArrayList<HashMap<String, Object>> getCashLedger() {
		return getCashLedger(null);
	}

	// One date's slice of the same ledger - DayCloseReportActivity's
	// "net cash movement" card opens here with today's date so the user
	// can see exactly what moved the running balance that day, without
	// losing the plain, unfiltered ledger the Cash screen normally shows.
	public ArrayList<HashMap<String, Object>> getCashLedger(String date) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		String dateFilter = date != null ? "AND date=? " : "";

		Cursor cursor = db.rawQuery(
			"SELECT date, time, paid_amount AS amount, " +
			"('Sale - ' || COALESCE(pa.name, 'Cash Sale')) AS label, source, 0, NULL, 'sale', s.id " +
			"FROM sales s LEFT JOIN " + TABLE_PARTIES + " pa ON s.party_id = pa.id " +
			"WHERE paid_amount != 0 " + dateFilter +

			"UNION ALL " +

			"SELECT date, time, -amount_paid, " +
			"('Purchase - ' || pa.name), source, 0, NULL, 'purchase', p.id " +
			"FROM " + TABLE_PURCHASES + " p " +
			"INNER JOIN " + TABLE_PARTIES + " pa ON p.party_id = pa.id " +
			"WHERE amount_paid != 0 " + dateFilter +

			"UNION ALL " +

			"SELECT date, time, amount, ('Payment In - ' || pa.name), source, 0, NULL, 'payment', pm.id " +
			"FROM " + TABLE_PAYMENTS + " pm " +
			"INNER JOIN " + TABLE_PARTIES + " pa ON pm.party_id = pa.id " +
			"WHERE type=" + PAYMENT_IN + " " + dateFilter +

			"UNION ALL " +

			"SELECT date, time, -amount, ('Payment Out - ' || pa.name), source, 0, NULL, 'payment', pm.id " +
			"FROM " + TABLE_PAYMENTS + " pm " +
			"INNER JOIN " + TABLE_PARTIES + " pa ON pm.party_id = pa.id " +
			"WHERE type=" + PAYMENT_OUT + " " + dateFilter +

			"UNION ALL " +

			"SELECT date, time, -paid_amount, ('Expense - ' || item), source, 0, NULL, 'expense', id " +
			"FROM " + TABLE_EXPENSES + " WHERE paid_amount != 0 " + dateFilter +

			"UNION ALL " +

			"SELECT date, time, amount, " +
			"('Adjustment' || CASE WHEN notes IS NOT NULL AND notes != '' " +
			"THEN ' - ' || notes ELSE '' END), source, id, notes, 'adjustment', id " +
			"FROM " + TABLE_CASH_ADJUSTMENTS +
			(date != null ? " WHERE date=? " : " ") +

			" ORDER BY date DESC, time DESC",

			date != null ? new String[]{date, date, date, date, date, date} : null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<String, Object>();

			map.put("date", cursor.getString(0));
			map.put("time", cursor.getString(1));
			map.put("amount", cursor.getDouble(2));
			map.put("label", cursor.getString(3));
			map.put("source", cursor.isNull(4) ? "Manual" : cursor.getString(4));
			map.put("adjustment_id", cursor.getInt(5));
			map.put("notes", cursor.isNull(6) ? "" : cursor.getString(6));
			map.put("record_type", cursor.getString(7));
			map.put("record_id", cursor.getInt(8));

			list.add(map);
		}

		cursor.close();

		return list;
	}

	public long insertCashAdjustment(
		String date, String time, double amount, String notes) {

		ContentValues values = new ContentValues();
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("source", "Manual");

		SQLiteDatabase db = this.getWritableDatabase();

		return db.insert(TABLE_CASH_ADJUSTMENTS, null, values);
	}

	public long insertCashAdjustmentBulk(
		SQLiteDatabase db, String date, String time, double amount, String notes, String source) {

		ContentValues values = new ContentValues();
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("source", source);

		return db.insert(TABLE_CASH_ADJUSTMENTS, null, values);
	}

	public void updateCashAdjustment(
		int id, String date, String time, double amount, String notes) {

		ContentValues values = new ContentValues();
		values.put("date", date);
		values.put("time", time);
		values.put("amount", amount);
		values.put("notes", notes);

		SQLiteDatabase db = this.getWritableDatabase();

		db.update(
			TABLE_CASH_ADJUSTMENTS, values, "id=?", new String[]{String.valueOf(id)}
		);
	}

	public void deleteCashAdjustment(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_CASH_ADJUSTMENTS, "id=?", new String[]{String.valueOf(id)});
	}

	// =====================
	// DRAFTS - a Sale/Purchase/Payment/Expense saved mid-entry instead of
	// committed for real. "data" is that screen's own JSON, opaque to
	// everything except the editor that wrote it.
	// =====================
	public long insertDraft(String type, String label, String data, String date, String time) {

		ContentValues values = new ContentValues();
		values.put("type", type);
		values.put("label", label);
		values.put("data", data);
		values.put("date", date);
		values.put("time", time);

		SQLiteDatabase db = this.getWritableDatabase();

		return db.insert(TABLE_DRAFTS, null, values);
	}

	public ArrayList<HashMap<String, Object>> getAllDrafts() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, type, label, date, time FROM " + TABLE_DRAFTS +
			" ORDER BY date DESC, time DESC, id DESC",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("id", cursor.getInt(0));
			row.put("type", cursor.getString(1));
			row.put("label", cursor.getString(2));
			row.put("date", cursor.getString(3));
			row.put("time", cursor.getString(4));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	public HashMap<String, Object> getDraftById(int id) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, type, label, data, date, time FROM " + TABLE_DRAFTS + " WHERE id=?",
			new String[]{String.valueOf(id)}
		);

		HashMap<String, Object> row = null;

		if (cursor.moveToFirst()) {

			row = new HashMap<String, Object>();
			row.put("id", cursor.getInt(0));
			row.put("type", cursor.getString(1));
			row.put("label", cursor.getString(2));
			row.put("data", cursor.getString(3));
			row.put("date", cursor.getString(4));
			row.put("time", cursor.getString(5));
		}

		cursor.close();

		return row;
	}

	public void deleteDraft(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(TABLE_DRAFTS, "id=?", new String[]{String.valueOf(id)});
	}

	// =====================
	// TODAY / THIS WEEK / THIS MONTH EXPENSE TOTALS (dashboard)
	// =====================
	public double getExpenseTotalForRange(String fromDate, String toDate) {

		SQLiteDatabase db = this.getReadableDatabase();

		// Excludes a purchase-linked expense - see getExpenses()'s own
		// comment.
		return sumColumn(
			db,
			"SELECT SUM(amount) FROM " + TABLE_EXPENSES +
			" WHERE date BETWEEN ? AND ?" +
			" AND id NOT IN (SELECT expense_id FROM " + TABLE_PURCHASE_EXPENSE_LINKS + ")",
			new String[]{fromDate, toDate}
		);
	}

	// =====================
	// EXPENSE RATIO TREND - Expenses as a % of Sales for each of the
	// last `months` calendar months, oldest first (the current month is
	// included even though it's partial, same "1st through today"
	// convention Month-over-Month uses elsewhere). A rising ratio means
	// expenses are growing faster than sales - worth watching even when
	// both totals are individually growing.
	// =====================
	public ArrayList<HashMap<String, Object>> getExpenseRatioTrend(int months) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
		SimpleDateFormat monthFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

		Calendar monthStart = Calendar.getInstance();
		monthStart.set(Calendar.DAY_OF_MONTH, 1);
		monthStart.set(Calendar.HOUR_OF_DAY, 0);
		monthStart.set(Calendar.MINUTE, 0);
		monthStart.set(Calendar.SECOND, 0);
		monthStart.set(Calendar.MILLISECOND, 0);

		// Walk back so the oldest month in the window is first.
		monthStart.add(Calendar.MONTH, -(months - 1));

		for (int i = 0; i < months; i++) {

			Calendar monthEnd = (Calendar) monthStart.clone();
			monthEnd.add(Calendar.MONTH, 1);
			monthEnd.add(Calendar.DAY_OF_MONTH, -1);

			Calendar today = Calendar.getInstance();

			if (monthEnd.after(today)) {
				monthEnd = today;
			}

			String fromDate = dateFormat.format(monthStart.getTime());
			String toDate = dateFormat.format(monthEnd.getTime());

			double salesTotal = sumColumn(
				db, "SELECT SUM(grand_total) FROM sales WHERE date BETWEEN ? AND ?",
				new String[]{fromDate, toDate}
			);

			double expensesTotal = getExpenseTotalForRange(fromDate, toDate);

			double ratioPercent = salesTotal > 0.01 ? expensesTotal / salesTotal * 100 : 0;

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("month_label", monthFormat.format(monthStart.getTime()));
			row.put("sales_total", salesTotal);
			row.put("expenses_total", expensesTotal);
			row.put("ratio_percent", ratioPercent);

			list.add(row);

			monthStart.add(Calendar.MONTH, 1);
		}

		return list;
	}

	// =====================
	// DAY CLOSE SUMMARY - one date's Sales/Purchases/Expenses/Payments
	// totals plus how much the cash balance actually moved that day, for
	// DayCloseReportActivity's end-of-day review screen.
	//
	// "Expenses" here matches getExpenseTotalForRange()'s own convention
	// (SUM(amount), purchase-linked expenses excluded) so it agrees with
	// the same date's Expenses list total rather than introducing a
	// second, differently-scoped expense figure.
	//
	// net_cash_movement instead mirrors getCashBalance()'s formula
	// exactly (paid_amount/amount_paid, nothing excluded) but scoped to
	// this one date, so it reads as "how much today moved the running
	// cash balance" - a purchase-linked expense's cash still left the
	// till today whether or not it's shown separately as an "expense".
	// =====================
	public HashMap<String, Object> getDayCloseSummary(String date) {

		SQLiteDatabase db = this.getReadableDatabase();

		HashMap<String, Object> summary = new HashMap<String, Object>();

		String[] dateArg = new String[]{date};

		Cursor salesCursor = db.rawQuery(
			"SELECT COUNT(*), COALESCE(SUM(grand_total), 0), COALESCE(SUM(paid_amount), 0) " +
			"FROM sales WHERE date=?",
			dateArg
		);
		salesCursor.moveToFirst();
		summary.put("sales_count", salesCursor.getInt(0));
		summary.put("sales_total", salesCursor.getDouble(1));
		double salesPaid = salesCursor.getDouble(2);
		salesCursor.close();

		Cursor purchasesCursor = db.rawQuery(
			"SELECT COUNT(*), COALESCE(SUM(grand_total), 0), COALESCE(SUM(amount_paid), 0) " +
			"FROM " + TABLE_PURCHASES + " WHERE date=?",
			dateArg
		);
		purchasesCursor.moveToFirst();
		summary.put("purchases_count", purchasesCursor.getInt(0));
		summary.put("purchases_total", purchasesCursor.getDouble(1));
		double purchasesPaid = purchasesCursor.getDouble(2);
		purchasesCursor.close();

		double expensesTotal = sumColumn(
			db,
			"SELECT SUM(amount) FROM " + TABLE_EXPENSES +
			" WHERE date=?" +
			" AND id NOT IN (SELECT expense_id FROM " + TABLE_PURCHASE_EXPENSE_LINKS + ")",
			dateArg
		);
		summary.put("expenses_total", expensesTotal);

		double expensesPaid = sumColumn(
			db, "SELECT SUM(paid_amount) FROM " + TABLE_EXPENSES + " WHERE date=?", dateArg
		);

		double paymentsIn = sumColumn(
			db,
			"SELECT SUM(amount) FROM " + TABLE_PAYMENTS + " WHERE date=? AND type=?",
			new String[]{date, String.valueOf(PAYMENT_IN)}
		);
		summary.put("payments_in_total", paymentsIn);

		double paymentsOut = sumColumn(
			db,
			"SELECT SUM(amount) FROM " + TABLE_PAYMENTS + " WHERE date=? AND type=?",
			new String[]{date, String.valueOf(PAYMENT_OUT)}
		);
		summary.put("payments_out_total", paymentsOut);

		double netCashMovement =
			salesPaid - purchasesPaid + paymentsIn - paymentsOut - expensesPaid;
		summary.put("net_cash_movement", netCashMovement);

		return summary;
	}

	// =====================
	// CASH FLOW FORECAST - today's real cash balance (getCashBalance())
	// projected forward day by day using only what's already committed
	// and dated: each not-fully-paid Sale's Due Date as money expected
	// IN that day, each not-fully-paid Purchase's Due Date as money
	// expected OUT that day. Nothing here is predicted or averaged from
	// history - a day with no due Sale/Purchase simply carries the
	// running balance forward unchanged, which is why this can only
	// look as far ahead as the furthest Due Date actually entered.
	//
	// Purchases have no stored "balance" column the way sales.balance
	// is kept in sync (see getCreditDueSales()'s own comment on it), so
	// its due amount is computed here as grand_total - amount_paid
	// instead of selecting a column.
	// =====================
	public ArrayList<HashMap<String, Object>> getCashFlowForecast(int daysAhead) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		double runningBalance = getCashBalance();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
		Calendar cal = Calendar.getInstance();

		for (int i = 0; i < daysAhead; i++) {

			String date = sdf.format(cal.getTime());

			String[] dateArg = new String[]{date};

			double expectedIn = sumColumn(
				db,
				"SELECT SUM(balance) FROM sales WHERE balance > 0.01 AND due_date=?",
				dateArg
			);

			double expectedOut = sumColumn(
				db,
				"SELECT SUM(grand_total - amount_paid) FROM " + TABLE_PURCHASES +
				" WHERE (grand_total - amount_paid) > 0.01 AND due_date=?",
				dateArg
			);

			runningBalance += expectedIn - expectedOut;

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("date", date);
			row.put("expected_in", expectedIn);
			row.put("expected_out", expectedOut);
			row.put("running_balance", runningBalance);

			list.add(row);

			cal.add(Calendar.DAY_OF_MONTH, 1);
		}

		return list;
	}

	// =====================
	// CASH PROJECTION BY SELECTED PERIOD - like Cash Flow Forecast
	// above, but a single from/to summary instead of a day-by-day list,
	// and it also factors in recurring expenses expected in that window
	// (simulated day by day with the exact same due-check logic
	// generateDueRecurringExpenses() uses, just without actually
	// inserting anything) and, separately, what restocking everything
	// currently on the Reorder List would cost - shown as its own
	// what-if line since nothing there is committed yet. See
	// ReorderListActivity for why that cost needs a Context (reads
	// ReorderSettings).
	// =====================
	public HashMap<String, Object> getCashProjection(
		android.content.Context context, String fromDate, String toDate) {

		HashMap<String, Object> result = new HashMap<String, Object>();

		SQLiteDatabase db = this.getReadableDatabase();

		double startingCash = getCashBalance();

		double duesIn = sumColumn(
			db,
			"SELECT SUM(balance) FROM sales WHERE balance > 0.01 AND due_date BETWEEN ? AND ?",
			new String[]{fromDate, toDate}
		);

		double billsOut = sumColumn(
			db,
			"SELECT SUM(grand_total - amount_paid) FROM " + TABLE_PURCHASES +
			" WHERE (grand_total - amount_paid) > 0.01 AND due_date BETWEEN ? AND ?",
			new String[]{fromDate, toDate}
		);

		double recurringExpensesOut = getProjectedRecurringExpenseTotal(fromDate, toDate);

		double reorderCostEstimate = 0;

		ArrayList<HashMap<String, Object>> reorderSuggestions = getReorderSuggestions(context);

		for (HashMap<String, Object> suggestion : reorderSuggestions) {
			reorderCostEstimate += (Double) suggestion.get("estimated_cost");
		}

		double projectedCash = startingCash + duesIn - billsOut - recurringExpensesOut;

		result.put("starting_cash", startingCash);
		result.put("dues_in", duesIn);
		result.put("bills_out", billsOut);
		result.put("recurring_expenses_out", recurringExpensesOut);
		result.put("projected_cash", projectedCash);
		result.put("reorder_cost_estimate", reorderCostEstimate);
		result.put("projected_cash_after_reorder", projectedCash - reorderCostEstimate);

		return result;
	}

	// Simulates every active recurring_expenses rule day by day across
	// [fromDate, toDate] (inclusive, capped at 366 days) using the same
	// due-check switch generateDueRecurringExpenses() uses, summing what
	// would be generated - a projection only, nothing is inserted.
	private double getProjectedRecurringExpenseTotal(String fromDate, String toDate) {

		ArrayList<HashMap<String, Object>> rules = getRecurringExpenses();

		SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

		double total = 0;

		for (HashMap<String, Object> rule : rules) {

			if (!(Boolean) rule.get("active")) {
				continue;
			}

			int frequency = (Integer) rule.get("frequency");
			double amount = (Double) rule.get("amount");
			Integer dayOfWeek = (Integer) rule.get("day_of_week");
			Integer dayOfMonth = (Integer) rule.get("day_of_month");

			HashSet<String> specificDates = new HashSet<String>();
			String specificDatesRaw = (String) rule.get("specific_dates");

			if (specificDatesRaw != null && specificDatesRaw.trim().length() > 0) {
				for (String oneDate : specificDatesRaw.split(",")) {
					specificDates.add(oneDate.trim());
				}
			}

			Calendar cal = Calendar.getInstance();

			try {
				cal.setTime(isoFormat.parse(fromDate));
			} catch (Exception e) {
				continue;
			}

			for (int i = 0; i < RECURRING_CATCHUP_LIMIT; i++) {

				String dateStr = isoFormat.format(cal.getTime());

				if (dateStr.compareTo(toDate) > 0) {
					break;
				}

				boolean due;

				switch (frequency) {

					case RECURRING_DAILY:
						due = true;
						break;

					case RECURRING_WEEKLY:
						due = dayOfWeek != null && cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek;
						break;

					case RECURRING_MONTHLY:
						due = dayOfMonth != null && isMonthlyDue(cal, dayOfMonth);
						break;

					case RECURRING_SPECIFIC_DATES:
						due = specificDates.contains(dateStr);
						break;

					default:
						due = false;
						break;
				}

				if (due) {
					total += amount;
				}

				cal.add(Calendar.DAY_OF_MONTH, 1);
			}
		}

		return total;
	}

	// =====================
	// DAILY SALES TREND - one row per day for the last N days (oldest
	// first), including a day with zero sales, for the Dashboard's
	// trend sparkline. Deliberately a plain day-by-day total (not
	// profit) - a quick "is the shop busy lately" glance, not another
	// profit report.
	// =====================
	public ArrayList<HashMap<String, Object>> getDailySalesTrend(int days) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, -(days - 1));

		for (int i = 0; i < days; i++) {

			String date = sdf.format(cal.getTime());

			double total = sumColumn(
				db, "SELECT SUM(grand_total) FROM sales WHERE date=?", new String[]{date}
			);

			HashMap<String, Object> row = new HashMap<String, Object>();
			row.put("date", date);
			row.put("total", total);

			list.add(row);

			cal.add(Calendar.DAY_OF_MONTH, 1);
		}

		return list;
	}

	// =====================
	// GLOBAL SEARCH - invoice numbers only (Parties/Items reuse their
	// own existing full-list methods, filtered client-side the same way
	// every other list screen's search box already does via
	// SearchUtils - there's no separate "search" query for those).
	// Invoices get their own query instead of a client-side filter
	// because they live across two tables (sales/purchases) with no
	// single existing full-list method to reuse.
	// =====================
	public ArrayList<HashMap<String, Object>> searchTransactionsByInvoice(String query) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		String likeQuery = "%" + query + "%";

		Cursor cursor = db.rawQuery(
			"SELECT 'sale', s.id, s.invoice_no, COALESCE(pa.name, 'Cash Sale'), s.date, s.grand_total " +
			"FROM sales s LEFT JOIN " + TABLE_PARTIES + " pa ON s.party_id = pa.id " +
			"WHERE s.invoice_no LIKE ? " +

			"UNION ALL " +

			"SELECT 'purchase', p.id, p.invoice_number, pa2.name, p.date, p.grand_total " +
			"FROM " + TABLE_PURCHASES + " p " +
			"INNER JOIN " + TABLE_PARTIES + " pa2 ON p.party_id = pa2.id " +
			"WHERE p.invoice_number LIKE ? " +

			"ORDER BY date DESC",

			new String[]{likeQuery, likeQuery}
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> row = new HashMap<String, Object>();

			row.put("transaction_type", cursor.getString(0));
			row.put("transaction_id", cursor.getInt(1));
			row.put("invoice", cursor.isNull(2) ? "" : cursor.getString(2));
			row.put("party_name", cursor.getString(3));
			row.put("date", cursor.getString(4));
			row.put("grand_total", cursor.getDouble(5));

			list.add(row);
		}

		cursor.close();

		return list;
	}

	// =====================
	// WANTED ITEMS - a customer asked for something not currently in
	// stock (an existing catalog item that's out, or something not in
	// the catalog at all).
	// =====================
	public long insertWantedItem(
		Integer itemId, String itemName, String date, String time,
		Integer partyId, String notes) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("item_name", itemName);
		values.put("date", date);
		values.put("time", time);
		values.put("party_id", partyId);
		values.put("notes", notes);
		values.put("fulfilled", 0);

		SQLiteDatabase db = this.getWritableDatabase();

		return db.insert(TABLE_WANTED_ITEMS, null, values);
	}

	public boolean updateWantedItem(
		int id, Integer itemId, String itemName, Integer partyId, String notes) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("item_name", itemName);
		values.put("party_id", partyId);
		values.put("notes", notes);

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.update(
			TABLE_WANTED_ITEMS, values, "id=?", new String[]{String.valueOf(id)}
		);

		return rows > 0;
	}

	public ArrayList<HashMap<String, Object>> getWantedItems(boolean includeFulfilled) {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT w.id, w.item_id, w.item_name, w.date, w.time, " +
			"w.party_id, pa.name, w.notes, w.fulfilled " +
			"FROM " + TABLE_WANTED_ITEMS + " w " +
			"LEFT JOIN " + TABLE_PARTIES + " pa ON w.party_id = pa.id " +
			(includeFulfilled ? "" : "WHERE w.fulfilled = 0 ") +
			"ORDER BY w.date DESC, w.time DESC",
			null
		);

		while (cursor.moveToNext()) {

			HashMap<String, Object> map = new HashMap<String, Object>();

			map.put("id", cursor.getInt(0));

			if (!cursor.isNull(1)) {
				map.put("item_id", cursor.getInt(1));
			}

			map.put("item_name", cursor.getString(2));
			map.put("date", cursor.getString(3));
			map.put("time", cursor.getString(4));

			if (!cursor.isNull(5)) {
				map.put("party_id", cursor.getInt(5));
				map.put("party_name", cursor.getString(6));
			}

			map.put("notes", cursor.getString(7));
			map.put("fulfilled", cursor.getInt(8) != 0);

			list.add(map);
		}

		cursor.close();

		return list;
	}

	public boolean setWantedItemFulfilled(int wantedItemId, boolean fulfilled) {

		ContentValues values = new ContentValues();
		values.put("fulfilled", fulfilled ? 1 : 0);

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.update(
			TABLE_WANTED_ITEMS, values, "id=?",
			new String[]{String.valueOf(wantedItemId)}
		);

		return rows > 0;
	}

	public boolean deleteWantedItem(int wantedItemId) {

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.delete(
			TABLE_WANTED_ITEMS, "id=?",
			new String[]{String.valueOf(wantedItemId)}
		);

		return rows > 0;
	}

	// Bulk-import counterpart of insertWantedItem() - operates on the given
	// db instead of getWritableDatabase(), and takes fulfilled directly
	// (a restored backup keeps whatever fulfilled state it was exported
	// with, rather than always starting unfulfilled like a fresh add).
	public long insertWantedItemBulk(
		SQLiteDatabase db, Integer itemId, String itemName, String date, String time,
		Integer partyId, String notes, boolean fulfilled) {

		ContentValues values = new ContentValues();
		values.put("item_id", itemId);
		values.put("item_name", itemName);
		values.put("date", date);
		values.put("time", time);
		values.put("party_id", partyId);
		values.put("notes", notes);
		values.put("fulfilled", fulfilled ? 1 : 0);

		return db.insert(TABLE_WANTED_ITEMS, null, values);
	}

	// =====================
	// RECURRING EXPENSES
	// =====================

	public long insertRecurringExpense(
		String item, double amount, String notes, Integer partyId,
		int frequency, Integer dayOfWeek, Integer dayOfMonth,
		String specificDates, String startDate) {

		ContentValues values = new ContentValues();
		values.put("item", item);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("frequency", frequency);
		values.put("day_of_week", dayOfWeek);
		values.put("day_of_month", dayOfMonth);
		values.put("specific_dates", specificDates);
		values.put("start_date", startDate);
		values.put("active", 1);

		SQLiteDatabase db = this.getWritableDatabase();

		return db.insert(TABLE_RECURRING_EXPENSES, null, values);
	}

	public boolean updateRecurringExpense(
		int id, String item, double amount, String notes, Integer partyId,
		int frequency, Integer dayOfWeek, Integer dayOfMonth,
		String specificDates, String startDate) {

		ContentValues values = new ContentValues();
		values.put("item", item);
		values.put("amount", amount);
		values.put("notes", notes);
		values.put("party_id", partyId);
		values.put("frequency", frequency);
		values.put("day_of_week", dayOfWeek);
		values.put("day_of_month", dayOfMonth);
		values.put("specific_dates", specificDates);
		values.put("start_date", startDate);

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.update(
			TABLE_RECURRING_EXPENSES, values, "id=?", new String[]{String.valueOf(id)}
		);

		return rows > 0;
	}

	public boolean setRecurringExpenseActive(int id, boolean active) {

		ContentValues values = new ContentValues();
		values.put("active", active ? 1 : 0);

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.update(
			TABLE_RECURRING_EXPENSES, values, "id=?", new String[]{String.valueOf(id)}
		);

		return rows > 0;
	}

	public boolean deleteRecurringExpense(int id) {

		SQLiteDatabase db = this.getWritableDatabase();

		int rows = db.delete(
			TABLE_RECURRING_EXPENSES, "id=?", new String[]{String.valueOf(id)}
		);

		return rows > 0;
	}

	public HashMap<String, Object> getRecurringExpenseById(int id) {

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, item, amount, notes, party_id, frequency, day_of_week, " +
			"day_of_month, specific_dates, start_date, last_generated_date, active " +
			"FROM " + TABLE_RECURRING_EXPENSES + " WHERE id=?",
			new String[]{String.valueOf(id)}
		);

		HashMap<String, Object> map = null;

		if (cursor.moveToFirst()) {
			map = recurringExpenseFromCursor(cursor);
		}

		cursor.close();

		return map;
	}

	public ArrayList<HashMap<String, Object>> getRecurringExpenses() {

		ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();

		SQLiteDatabase db = this.getReadableDatabase();

		Cursor cursor = db.rawQuery(
			"SELECT id, item, amount, notes, party_id, frequency, day_of_week, " +
			"day_of_month, specific_dates, start_date, last_generated_date, active " +
			"FROM " + TABLE_RECURRING_EXPENSES + " ORDER BY item",
			null
		);

		while (cursor.moveToNext()) {
			list.add(recurringExpenseFromCursor(cursor));
		}

		cursor.close();

		return list;
	}

	private HashMap<String, Object> recurringExpenseFromCursor(Cursor cursor) {

		HashMap<String, Object> map = new HashMap<String, Object>();

		map.put("id", cursor.getInt(0));
		map.put("item", cursor.getString(1));
		map.put("amount", cursor.getDouble(2));
		map.put("notes", cursor.isNull(3) ? "" : cursor.getString(3));
		map.put("party_id", cursor.isNull(4) ? null : cursor.getInt(4));
		map.put("frequency", cursor.getInt(5));
		map.put("day_of_week", cursor.isNull(6) ? null : cursor.getInt(6));
		map.put("day_of_month", cursor.isNull(7) ? null : cursor.getInt(7));
		map.put("specific_dates", cursor.isNull(8) ? "" : cursor.getString(8));
		map.put("start_date", cursor.getString(9));
		map.put("last_generated_date", cursor.isNull(10) ? null : cursor.getString(10));
		map.put("active", cursor.getInt(11) != 0);

		return map;
	}

	// A long-dormant rule catches up gradually across a few app opens
	// rather than flooding the Expenses list with a year of backdated
	// entries in one go.
	private static final int RECURRING_CATCHUP_LIMIT = 366;

	// Catches every active rule up to today, inserting one real,
	// fully-paid Expense (same as insertExpense()) per date it's due
	// since it was last generated (or since its start date, if never
	// generated before). Meant to be called once per app open, on a
	// background thread - see MainActivity. Returns how many expenses
	// were generated, so the caller can tell the user.
	public int generateDueRecurringExpenses() {

		SQLiteDatabase db = this.getWritableDatabase();

		ArrayList<HashMap<String, Object>> rules = getRecurringExpenses();

		SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

		String today = isoFormat.format(new Date());

		int generatedCount = 0;

		for (HashMap<String, Object> rule : rules) {

			if (!(Boolean) rule.get("active")) {
				continue;
			}

			int ruleId = (Integer) rule.get("id");
			int frequency = (Integer) rule.get("frequency");
			String startDate = (String) rule.get("start_date");
			String lastGenerated = (String) rule.get("last_generated_date");

			String cursorDateStr = lastGenerated != null ?
				addDays(isoFormat, lastGenerated, 1) : startDate;

			if (cursorDateStr.compareTo(today) > 0) {
				continue;
			}

			Calendar cal = Calendar.getInstance();

			try {
				cal.setTime(isoFormat.parse(cursorDateStr));
			} catch (Exception e) {
				continue;
			}

			Integer dayOfWeek = (Integer) rule.get("day_of_week");
			Integer dayOfMonth = (Integer) rule.get("day_of_month");

			HashSet<String> specificDates = new HashSet<String>();
			String specificDatesRaw = (String) rule.get("specific_dates");

			if (specificDatesRaw != null && specificDatesRaw.trim().length() > 0) {
				for (String oneDate : specificDatesRaw.split(",")) {
					specificDates.add(oneDate.trim());
				}
			}

			String lastDateCheckedThisRun = lastGenerated;

			for (int i = 0; i < RECURRING_CATCHUP_LIMIT; i++) {

				String dateStr = isoFormat.format(cal.getTime());

				if (dateStr.compareTo(today) > 0) {
					break;
				}

				boolean due;

				switch (frequency) {

					case RECURRING_DAILY:
						due = true;
						break;

					case RECURRING_WEEKLY:
						due = dayOfWeek != null &&
							cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek;
						break;

					case RECURRING_MONTHLY:
						due = dayOfMonth != null && isMonthlyDue(cal, dayOfMonth);
						break;

					case RECURRING_SPECIFIC_DATES:
						due = specificDates.contains(dateStr);
						break;

					default:
						due = false;
						break;
				}

				if (due) {

					insertExpense(
						(String) rule.get("item"),
						dateStr,
						"09:00",
						(Double) rule.get("amount"),
						(String) rule.get("notes"),
						(Integer) rule.get("party_id")
					);

					generatedCount++;
				}

				lastDateCheckedThisRun = dateStr;

				cal.add(Calendar.DAY_OF_YEAR, 1);
			}

			if (lastDateCheckedThisRun != null &&
				!lastDateCheckedThisRun.equals(lastGenerated)) {

				ContentValues values = new ContentValues();
				values.put("last_generated_date", lastDateCheckedThisRun);

				db.update(
					TABLE_RECURRING_EXPENSES, values, "id=?",
					new String[]{String.valueOf(ruleId)}
				);
			}
		}

		return generatedCount;
	}

	// dayOfMonth clamped to the shorter month (e.g. a "31st" rule falls
	// on the 30th in a 30-day month, the 28th/29th in February).
	private boolean isMonthlyDue(Calendar cal, int dayOfMonth) {

		int actualDay = cal.get(Calendar.DAY_OF_MONTH);
		int maxDayThisMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

		int effectiveDay = Math.min(dayOfMonth, maxDayThisMonth);

		return actualDay == effectiveDay;
	}

	private String addDays(SimpleDateFormat fmt, String dateStr, int days) {

		try {

			Calendar cal = Calendar.getInstance();
			cal.setTime(fmt.parse(dateStr));
			cal.add(Calendar.DAY_OF_YEAR, days);

			return fmt.format(cal.getTime());

		} catch (Exception e) {

			return dateStr;
		}
	}

}

