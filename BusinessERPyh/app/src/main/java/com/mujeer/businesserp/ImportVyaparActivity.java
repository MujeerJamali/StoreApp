package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// =====================
// Restores from a Vyapar (.vyb) backup file - a zip that wraps a real
// SQLite database. This is a full restore, not a merge: confirmAndRunImport()
// warns the user, and runImport() then clears every party, item, purchase,
// sale, payment, expense, and transfer currently in the app (see
// DatabaseHelper.clearAllDataBulk()) before writing the backup's data in
// fresh, all inside one transaction so a failure partway through leaves
// the original data intact instead of an empty database. This is a
// completely separate import path from Importexcelactivity/
// DatabaseHelper's Excel-import helpers - it never touches Excel.
//
// It reuses DatabaseHelper's existing bulk-insert helpers (the same ones
// the Excel importer uses) for every table it writes to, plus a small
// set of Vyapar-specific additions (insertItemBulk, insertPartyTransferBulk,
// getVybLocalId/saveVybLocalId) added alongside them. The per-run
// duplicate tracking those rely on stays useful within a single import
// (e.g. two rows in the backup pointing at the same source id), even
// though the database was just cleared, so nothing in this file's own
// per-table import methods needed to change.
//
// Import order (matches the relationships in Vyapar's own schema):
// Clear -> Parties -> Items -> [Item Varieties] -> Purchases -> Purchase
// Line Items -> Sales -> Sale Line Items -> Payment In -> Payment Out ->
// Party to Party transfers -> Expenses -> [Cash Adjustments] -> [Cost
// Items] -> [Linked Expenses] -> [Recurring Expense Rules] -> [Drafts].
//
// Everything in [brackets] is this app's own extension with no equivalent
// in Vyapar's own schema - see ExportVyaparActivity's businesserp_* tables
// (and kb_items.item_extra_cost_per_unit/kb_lineitems.combo_id/
// kb_transactions.txn_due_date, extra columns on otherwise Vyapar-shaped
// tables). Every one of them is entirely optional: a real Vyapar backup,
// or an export made before this app had that feature, simply won't have
// the table/column, which
// hasVarietyTables/lineItemsHaveComboId/itemsHaveExtraCost/
// salesHaveDueDate/hasCostItemsTable/hasPurchaseExpenseLinksTable/
// hasRecurringExpensesTable/hasDraftsTable below detect up front so that
// whole step is just skipped (or, for the extra columns, defaults to
// 0/null) rather than failing the import. salesHaveDueDate, despite the
// name, gates both kb_transactions.txn_due_date columns - Sale's and
// Purchase's - since it's one shared column on one shared table.
// =====================
public class ImportVyaparActivity extends Activity {

    private static final int REQUEST_PICK_VYB = 2001;

    // Guards against two overlapping runImport() calls writing to the same
    // temp files and the same database transaction at once - e.g. a
    // rotation recreating this Activity mid-import (its background Thread
    // outlives the old instance) followed by a second tap on the new
    // instance's own Pick button. Static/process-wide rather than an
    // instance field, since it has to survive that recreation. A second
    // attempt while true is refused outright rather than queued.
    private static volatile boolean importInProgress = false;

    private Button btn_pick_vyb;
    private Button btn_view_skipped_vyb;
    private TextView tv_vyb_result;

    // =====================
    // One row that was left out of the import - either an unsupported
    // Vyapar transaction type, a row with a missing/unresolvable party or
    // item, or a duplicate of a row already imported previously.
    // =====================
    static class SkippedRow {
        String table;
        String vyaparId;
        String reason;

        String toDisplayString() {
            return table + " (Vyapar id " + vyaparId + ")\nReason: " + reason;
        }
    }

    // In-memory only, for the "View skipped rows" screen right after an
    // import finishes - same pattern as Importexcelactivity.lastSkippedRows.
    static ArrayList<SkippedRow> lastSkippedRows = new ArrayList<SkippedRow>();

    // =====================
    // Running totals for the summary screen.
    // =====================
    private static class Counts {
        int partiesImported, partiesDuplicate;
        int itemsImported, itemsDuplicate;
        int varietyGroupsImported, varietyGroupsDuplicate;
        int varietyValuesImported, varietyValuesDuplicate;
        int varietyCombosImported, varietyCombosDuplicate;
        int purchasesImported, purchasesDuplicate;
        int purchaseItemsImported, purchaseItemsDuplicate;
        int salesImported, salesDuplicate;
        int saleItemsImported, saleItemsDuplicate;
        int paymentInImported, paymentInDuplicate;
        int paymentOutImported, paymentOutDuplicate;
        int transfersImported, transfersDuplicate;
        int expensesImported, expensesDuplicate;
        int cashAdjustmentsImported, cashAdjustmentsDuplicate;
        int costItemsImported, costItemsDuplicate;
        int purchaseExpenseLinksImported, purchaseExpenseLinksDuplicate;
        int recurringExpensesImported, recurringExpensesDuplicate;
        int draftsImported, draftsDuplicate;
        int wantedItemsImported, wantedItemsDuplicate;
        int displayShoesImported, displayShoesDuplicate;
        int sampleShoesImported, sampleShoesDuplicate;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_vyapar);

        btn_pick_vyb = (Button) findViewById(R.id.btn_pick_vyb);
        btn_view_skipped_vyb = (Button) findViewById(R.id.btn_view_skipped_vyb);
        tv_vyb_result = (TextView) findViewById(R.id.tv_vyb_result);

        btn_pick_vyb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btn_view_skipped_vyb.setVisibility(View.GONE);
                    openVybPicker();
                }
            });

        btn_view_skipped_vyb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(
									  ImportVyaparActivity.this,
									  ImportVyaparSkippedRowsActivity.class
								  ));
                }
            });
    }

    private void openVybPicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");

        startActivityForResult(intent, REQUEST_PICK_VYB);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_VYB
            && resultCode == RESULT_OK
            && data != null
            && data.getData() != null) {

            final Uri uri = data.getData();

            confirmAndRunImport(uri);
        }
    }

    // =====================
    // This is a full restore, not a merge - runImport() clears every
    // party/item/purchase/sale/payment/expense/transfer currently in the
    // app before writing the backup's data in fresh. That's destructive
    // and can't be undone, so it needs an explicit confirmation before
    // anything is touched.
    // =====================
    private void confirmAndRunImport(final Uri uri) {

        new AlertDialog.Builder(this)
            .setTitle("Replace all data?")
            .setMessage(
                "This deletes every party, item, purchase, sale, payment, expense, and " +
                "transfer currently in the app, then imports this backup. This cannot be undone."
            )
            .setPositiveButton("Delete and Import", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (importInProgress) {

                            android.widget.Toast.makeText(
                                ImportVyaparActivity.this,
                                "An import is already running - please wait for it to finish.",
                                android.widget.Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        importInProgress = true;

                        new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    runImport(uri);
                                }
                            }).start();
                    }
                })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setStatus(final String text) {

        runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    tv_vyb_result.setText(text);
                }
            });
    }

    // =====================
    // Whole import run: prepare the file, then walk the 9 steps in
    // order inside a single DatabaseHelper transaction so a failure
    // partway through leaves nothing half-written.
    // =====================
    private void runImport(Uri uri) {

        setStatus("Reading backup file...");

        File rawFile = new File(getCacheDir(), "vyb_import_raw.tmp");
        File extractedFile = new File(getCacheDir(), "vyb_import_extracted.tmp");
        SQLiteDatabase vyaparDb = null;
        DatabaseHelper helper = null;
        boolean success = false;

        ArrayList<SkippedRow> skipped = new ArrayList<SkippedRow>();
        Counts counts = new Counts();
        StringBuilder fatalError = new StringBuilder();

        try {

            File dbFile = prepareVyaparDatabaseFile(uri, rawFile, extractedFile);

            vyaparDb = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY
            );

            // Sanity check - make sure this really is a Vyapar backup
            // before writing anything.
            Cursor check = vyaparDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('kb_names','kb_transactions','kb_lineitems')",
                null
            );
            int foundTables = check.getCount();
            check.close();

            if (foundTables < 3) {
                throw new Exception("This file doesn't look like a Vyapar backup (expected tables not found).");
            }

            // This app's own extension (see ExportVyaparActivity) - absent
            // from a real Vyapar backup, or one exported before item
            // varieties existed, in which case variety import is simply
            // skipped below rather than failing the whole restore.
            boolean hasVarietyTables = tableExists(vyaparDb, "businesserp_variety_groups");
            boolean lineItemsHaveComboId = columnExists(vyaparDb, "kb_lineitems", "combo_id");

            // Another of this app's own extensions (see ExportVyaparActivity)
            // - absent from a real Vyapar backup, or one exported before
            // cash adjustments existed, in which case this import step is
            // simply skipped below rather than failing the whole restore.
            boolean hasCashAdjustmentsTable = tableExists(vyaparDb, "businesserp_cash_adjustments");

            // More of this app's own extensions, all following the exact
            // same "detect, then skip the whole step if absent" pattern as
            // hasVarietyTables/hasCashAdjustmentsTable above, for a backup
            // exported before each one existed.
            boolean itemsHaveExtraCost = columnExists(vyaparDb, "kb_items", "item_extra_cost_per_unit");
            boolean itemsHaveActive = columnExists(vyaparDb, "kb_items", "item_active");
            boolean salesHaveDueDate = columnExists(vyaparDb, "kb_transactions", "txn_due_date");
            boolean hasCostItemsTable = tableExists(vyaparDb, "businesserp_cost_items");
            boolean hasPurchaseExpenseLinksTable = tableExists(vyaparDb, "businesserp_purchase_expense_links");
            boolean hasRecurringExpensesTable = tableExists(vyaparDb, "businesserp_recurring_expenses");
            boolean hasWantedItemsTable = tableExists(vyaparDb, "businesserp_wanted_items");
            boolean hasDisplayShoesTable = tableExists(vyaparDb, "businesserp_display_shoes");
            boolean hasSampleShoesTable = tableExists(vyaparDb, "businesserp_sample_shoes");
            boolean hasDraftsTable = tableExists(vyaparDb, "businesserp_drafts");

            helper = new DatabaseHelper(this);
            helper.beginTransaction();
            SQLiteDatabase db = helper.getMigrationDatabase();

            // This is a restore, not a merge: everything currently in the
            // app is wiped before the backup goes in, inside this same
            // transaction, so a failure partway through the import below
            // rolls this back too and leaves the original data intact
            // rather than an empty database.
            setStatus("Clearing existing data...");
            helper.clearAllDataBulk(db);

            HashMap<Long, Integer> partyIdMap = new HashMap<Long, Integer>();
            HashMap<Long, Integer> itemIdMap = new HashMap<Long, Integer>();
            HashMap<Long, Integer> varietyGroupIdMap = new HashMap<Long, Integer>();
            HashMap<Long, Integer> varietyValueIdMap = new HashMap<Long, Integer>();
            HashMap<Long, Integer> varietyComboIdMap = new HashMap<Long, Integer>();
            HashMap<Long, Long> purchaseIdMap = new HashMap<Long, Long>();
            HashMap<Long, Long> saleIdMap = new HashMap<Long, Long>();
            HashMap<Long, Integer> expenseIdMap = new HashMap<Long, Integer>();

            setStatus("Importing parties...");
            importParties(vyaparDb, helper, db, partyIdMap, skipped, counts);

            setStatus("Importing items...");
            importItems(vyaparDb, helper, db, itemIdMap, itemsHaveExtraCost, itemsHaveActive, skipped, counts);

            if (hasVarietyTables) {

                setStatus("Importing item varieties...");
                importVarietyGroups(vyaparDb, helper, db, itemIdMap, varietyGroupIdMap, skipped, counts);
                importVarietyValues(vyaparDb, helper, db, varietyGroupIdMap, varietyValueIdMap, skipped, counts);
                importVarietyCombos(vyaparDb, helper, db, itemIdMap, varietyComboIdMap, skipped, counts);
                importVarietyComboValues(
                    vyaparDb, helper, db, varietyComboIdMap, varietyGroupIdMap, varietyValueIdMap, skipped);

                // A backup can carry duplicate combo rows for the exact
                // same item+value combination (e.g. from an older buggy
                // export, or a raw data fix that created a fresh combo
                // instead of reusing the existing one) - collapse those
                // now, before line items get their combo_id repointed
                // below, so a duplicate is never chosen over the original.
                helper.mergeDuplicateVarietyCombos(db);
            }

            setStatus("Importing purchases...");
            importPurchases(
                vyaparDb, helper, db, partyIdMap, purchaseIdMap, salesHaveDueDate, skipped, counts);

            setStatus("Importing purchase line items...");
            importPurchaseLineItems(
                vyaparDb, helper, db, purchaseIdMap, itemIdMap, varietyComboIdMap,
                lineItemsHaveComboId, skipped, counts);

            setStatus("Importing sales...");
            importSales(vyaparDb, helper, db, partyIdMap, saleIdMap, salesHaveDueDate, skipped, counts);

            setStatus("Importing sale line items...");
            importSaleLineItems(
                vyaparDb, helper, db, saleIdMap, itemIdMap, varietyComboIdMap,
                lineItemsHaveComboId, skipped, counts);

            setStatus("Importing payments in...");
            importPayments(vyaparDb, helper, db, partyIdMap, skipped, counts, 3, true);

            setStatus("Importing payments out...");
            importPayments(vyaparDb, helper, db, partyIdMap, skipped, counts, 4, false);

            setStatus("Importing party to party transfers...");
            importPartyTransfers(vyaparDb, helper, db, partyIdMap, skipped, counts);

            setStatus("Importing expenses...");
            importExpenses(vyaparDb, helper, db, partyIdMap, expenseIdMap, skipped, counts);

            if (hasCashAdjustmentsTable) {

                setStatus("Importing cash adjustments...");
                importCashAdjustments(vyaparDb, helper, db, skipped, counts);
            }

            if (hasCostItemsTable) {

                setStatus("Importing cost items...");
                importCostItems(vyaparDb, helper, db, skipped, counts);
            }

            if (hasPurchaseExpenseLinksTable) {

                setStatus("Importing linked expenses...");
                importPurchaseExpenseLinks(
                    vyaparDb, helper, db, purchaseIdMap, expenseIdMap, skipped, counts);
            }

            if (hasRecurringExpensesTable) {

                setStatus("Importing recurring expenses...");
                importRecurringExpenses(vyaparDb, helper, db, partyIdMap, skipped, counts);
            }

            if (hasDraftsTable) {

                setStatus("Importing drafts...");
                importDrafts(
                    vyaparDb, helper, db, partyIdMap, itemIdMap, varietyComboIdMap,
                    expenseIdMap, skipped, counts);
            }

            if (hasWantedItemsTable) {

                setStatus("Importing wanted items...");
                importWantedItems(vyaparDb, helper, db, partyIdMap, itemIdMap, skipped, counts);
            }

            if (hasDisplayShoesTable) {

                setStatus("Importing display shoes...");
                importDisplayShoes(vyaparDb, helper, db, itemIdMap, varietyComboIdMap, skipped, counts);
            }

            if (hasSampleShoesTable) {

                setStatus("Importing sample shoes...");
                importSampleShoes(vyaparDb, helper, db, itemIdMap, varietyComboIdMap, skipped, counts);
            }

            setStatus("Logging unsupported transaction types...");
            logUnsupportedTypes(vyaparDb, skipped);

            success = true;

        } catch (Exception e) {

            fatalError.append(e.toString());

        } finally {

            if (helper != null) {
                helper.endTransaction(success);
            }

            if (vyaparDb != null) {
                vyaparDb.close();
            }

            if (extractedFile.exists()) {
                extractedFile.delete();
            }

            if (rawFile.exists()) {
                rawFile.delete();
            }

            importInProgress = false;
        }

        ImportVyaparActivity.lastSkippedRows = skipped;

        final boolean finalSuccess = success;
        final String finalError = fatalError.toString();
        final Counts finalCounts = counts;
        final int finalSkippedCount = skipped.size();

        runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (!finalSuccess) {

                        tv_vyb_result.setText(
                            "Import failed - nothing was written.\n\n" + finalError
                        );

                        return;
                    }

                    StringBuilder summary = new StringBuilder();

                    summary.append("Parties: " + finalCounts.partiesImported
								   + " imported, " + finalCounts.partiesDuplicate + " already imported\n");
                    summary.append("Items: " + finalCounts.itemsImported
								   + " imported, " + finalCounts.itemsDuplicate + " already imported\n");
                    if (finalCounts.varietyGroupsImported > 0 || finalCounts.varietyGroupsDuplicate > 0) {
                        summary.append("Variety groups: " + finalCounts.varietyGroupsImported
									   + " imported, " + finalCounts.varietyGroupsDuplicate + " already imported\n");
                        summary.append("Variety values: " + finalCounts.varietyValuesImported
									   + " imported, " + finalCounts.varietyValuesDuplicate + " already imported\n");
                    }
                    summary.append("Purchases: " + finalCounts.purchasesImported
								   + " imported, " + finalCounts.purchasesDuplicate + " already imported\n");
                    summary.append("Purchase line items: " + finalCounts.purchaseItemsImported
								   + " imported, " + finalCounts.purchaseItemsDuplicate + " already imported\n");
                    summary.append("Sales: " + finalCounts.salesImported
								   + " imported, " + finalCounts.salesDuplicate + " already imported\n");
                    summary.append("Sale line items: " + finalCounts.saleItemsImported
								   + " imported, " + finalCounts.saleItemsDuplicate + " already imported\n");
                    summary.append("Payment In: " + finalCounts.paymentInImported
								   + " imported, " + finalCounts.paymentInDuplicate + " already imported\n");
                    summary.append("Payment Out: " + finalCounts.paymentOutImported
								   + " imported, " + finalCounts.paymentOutDuplicate + " already imported\n");
                    summary.append("Party to party transfers: " + finalCounts.transfersImported
								   + " imported, " + finalCounts.transfersDuplicate + " already imported\n");
                    summary.append("Expenses: " + finalCounts.expensesImported
								   + " imported, " + finalCounts.expensesDuplicate + " already imported\n");
                    if (finalCounts.cashAdjustmentsImported > 0 || finalCounts.cashAdjustmentsDuplicate > 0) {
                        summary.append("Cash adjustments: " + finalCounts.cashAdjustmentsImported
									   + " imported, " + finalCounts.cashAdjustmentsDuplicate + " already imported\n");
                    }
                    if (finalCounts.costItemsImported > 0 || finalCounts.costItemsDuplicate > 0) {
                        summary.append("Cost items: " + finalCounts.costItemsImported
									   + " imported, " + finalCounts.costItemsDuplicate + " already imported\n");
                    }
                    if (finalCounts.purchaseExpenseLinksImported > 0 || finalCounts.purchaseExpenseLinksDuplicate > 0) {
                        summary.append("Linked expenses: " + finalCounts.purchaseExpenseLinksImported
									   + " imported, " + finalCounts.purchaseExpenseLinksDuplicate + " already imported\n");
                    }
                    if (finalCounts.recurringExpensesImported > 0 || finalCounts.recurringExpensesDuplicate > 0) {
                        summary.append("Recurring expense rules: " + finalCounts.recurringExpensesImported
									   + " imported, " + finalCounts.recurringExpensesDuplicate + " already imported\n");
                    }
                    if (finalCounts.draftsImported > 0 || finalCounts.draftsDuplicate > 0) {
                        summary.append("Drafts: " + finalCounts.draftsImported
									   + " imported, " + finalCounts.draftsDuplicate + " already imported\n");
                    }
                    if (finalCounts.wantedItemsImported > 0 || finalCounts.wantedItemsDuplicate > 0) {
                        summary.append("Wanted items: " + finalCounts.wantedItemsImported
									   + " imported, " + finalCounts.wantedItemsDuplicate + " already imported\n");
                    }
                    if (finalCounts.displayShoesImported > 0 || finalCounts.displayShoesDuplicate > 0) {
                        summary.append("Display shoes: " + finalCounts.displayShoesImported
									   + " imported, " + finalCounts.displayShoesDuplicate + " already imported\n");
                    }
                    if (finalCounts.sampleShoesImported > 0 || finalCounts.sampleShoesDuplicate > 0) {
                        summary.append("Sample shoes: " + finalCounts.sampleShoesImported
									   + " imported, " + finalCounts.sampleShoesDuplicate + " already imported\n");
                    }

                    summary.append("\nRows not imported: " + finalSkippedCount);

                    tv_vyb_result.setText(summary.toString());

                    if (finalSkippedCount > 0) {
                        btn_view_skipped_vyb.setText(
                            "View rows not imported (" + finalSkippedCount + ")");
                        btn_view_skipped_vyb.setVisibility(View.VISIBLE);
                    } else {
                        btn_view_skipped_vyb.setVisibility(View.GONE);
                    }
                }
            });
    }

    // =====================
    // FILE PREPARATION
    // A .vyb file is a zip wrapping a real SQLite database. Copies the
    // picked content:// URI to local storage, then unzips it if needed.
    // Streams both the copy and the unzip in fixed-size chunks, so this
    // stays memory-efficient regardless of backup size.
    // =====================
    private File prepareVyaparDatabaseFile(Uri uri, File rawFile, File extractedFile) throws Exception {

        ContentResolver resolver = getContentResolver();
        InputStream in = resolver.openInputStream(uri);

        if (in == null) {
            throw new Exception("Could not open the selected file.");
        }

        OutputStream out = new FileOutputStream(rawFile);
        byte[] buffer = new byte[8192];
        int len;

        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }

        out.close();
        in.close();

        // Detect zip (PK..) vs an already-raw SQLite file ("SQLite format 3").
        boolean isZip = false;

        InputStream headerCheck = new FileInputStream(rawFile);
        byte[] header = new byte[2];
        int read = headerCheck.read(header);
        headerCheck.close();

        if (read == 2 && header[0] == 'P' && header[1] == 'K') {
            isZip = true;
        }

        if (!isZip) {
            return rawFile;
        }

        ZipInputStream zis = new ZipInputStream(new FileInputStream(rawFile));
        ZipEntry entry;
        boolean found = false;

        while ((entry = zis.getNextEntry()) != null) {

            if (entry.isDirectory()) {
                continue;
            }

            OutputStream fos = new FileOutputStream(extractedFile);

            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }

            fos.close();
            found = true;
            break;
        }

        zis.close();
        rawFile.delete();

        if (!found) {
            throw new Exception("The selected .vyb file did not contain a database.");
        }

        return extractedFile;
    }

    // =====================
    // Converts Vyapar's txn_time (integer seconds since midnight) to the
    // app's "HH:mm" time format.
    // =====================
    private String formatTime(int secondsSinceMidnight) {

        if (secondsSinceMidnight < 0) {
            secondsSinceMidnight = 0;
        }

        int hours = (secondsSinceMidnight / 3600) % 24;
        int minutes = (secondsSinceMidnight % 3600) / 60;

        return String.format("%02d:%02d", hours, minutes);
    }

    // Vyapar stores dates as "yyyy-MM-dd 00:00:00" - the app's date
    // columns just want the "yyyy-MM-dd" part.
    private String formatDate(String vyaparDate) {

        if (vyaparDate == null || vyaparDate.length() < 10) {
            return "";
        }

        return vyaparDate.substring(0, 10);
    }

    private boolean tableExists(SQLiteDatabase db, String table) {

        Cursor c = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            new String[]{table}
        );

        boolean exists = c.getCount() > 0;

        c.close();

        return exists;
    }

    private boolean columnExists(SQLiteDatabase db, String table, String column) {

        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);

        int nameIndex = c.getColumnIndex("name");
        boolean exists = false;

        while (c.moveToNext()) {

            if (column.equalsIgnoreCase(c.getString(nameIndex))) {
                exists = true;
                break;
            }
        }

        c.close();

        return exists;
    }

    private void addSkipped(ArrayList<SkippedRow> skipped, String table, long vyaparId, String reason) {

        SkippedRow row = new SkippedRow();
        row.table = table;
        row.vyaparId = String.valueOf(vyaparId);
        row.reason = reason;

        skipped.add(row);
    }

    // =====================
    // STEP 1 - PARTIES (kb_names, name_type=1)
    // name_type=2 is Vyapar's own expense/category labels, not parties,
    // and is intentionally excluded.
    // =====================
    private void importParties(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT name_id, full_name FROM kb_names WHERE name_type=1", null);

        while (c.moveToNext()) {

            long nameId = c.getLong(0);
            String fullName = c.getString(1);
            String importKey = "vyb_party_" + nameId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "party", nameId);

                if (localId != null) {
                    partyIdMap.put(nameId, localId.intValue());
                }

                counts.partiesDuplicate++;
                continue;
            }

            if (fullName == null || fullName.trim().length() == 0) {
                addSkipped(skipped, "party", nameId, "Blank party name");
                continue;
            }

            int localId = (int) helper.getOrCreatePartyIdBulk(db, fullName.trim());

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "party", nameId, localId);

            partyIdMap.put(nameId, localId);
            counts.partiesImported++;
        }

        c.close();
    }

    // =====================
    // STEP 2 - ITEMS (kb_items)
    // Item names are imported exactly as they appear in the Vyapar
    // backup - no reformatting is applied. This is the only place a raw
    // Vyapar product name enters the ERP - purchase and sale line items
    // only ever carry an item_id (resolved through itemIdMap/
    // getVybLocalId back to this same items row), so leaving the name
    // untouched here is enough for it to show up consistently everywhere
    // in the imported database.
    //
    // kb_items is NOT exclusively real inventory. Vyapar also auto-creates
    // an item_type=2 ("Service") row for every distinct expense
    // name/description a user has ever typed when logging an Expense
    // (txn_type=7) - e.g. "milk", "haircut", "father's pocket money" -
    // purely so the expense transaction can carry it as a kb_lineitems
    // row the same way a real purchase/sale carries its line items.
    // Confirmed against this importer's own data: every item_id used by
    // a txn_type=7 line item is item_type=2, and no item_type=2 item is
    // ever used by a real purchase/sale (txn_type 1/2) line item - the
    // two sets are completely disjoint. Those rows are expense line
    // items, not inventory, so they must never be inserted into
    // TABLE_ITEMS (see importExpenses() below for where their text
    // actually belongs). The OR clause is a defensive fallback only: if
    // some other backup ever did legitimately sell/purchase an
    // item_type=2 row, it stays included.
    // =====================
    private void importItems(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> itemIdMap,
        boolean itemsHaveExtraCost,
        boolean itemsHaveActive,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        // item_extra_cost_per_unit/item_active are this app's own
        // extensions (see itemsHaveExtraCost/itemsHaveActive in
        // runImport()) - only selected when the backup's kb_items
        // actually has that column, since a real Vyapar backup or an
        // export made before either existed won't; item_active defaults
        // to active (see below) rather than failing in that case.
        Cursor c = vyaparDb.rawQuery(
            "SELECT item_id, item_code, item_name, item_purchase_unit_price, item_sale_unit_price" +
            (itemsHaveExtraCost ? ", item_extra_cost_per_unit" : "") +
            (itemsHaveActive ? ", item_active" : "") + " " +
            "FROM kb_items " +
            "WHERE item_type != 2 " +
            "   OR item_id IN (" +
            "       SELECT DISTINCT li.item_id FROM kb_lineitems li " +
            "       JOIN kb_transactions t ON li.lineitem_txn_id = t.txn_id " +
            "       WHERE t.txn_type IN (1,2)" +
            "   )",
            null);

        int extraCostColumn = 5;
        int activeColumn = itemsHaveExtraCost ? 6 : 5;

        while (c.moveToNext()) {

            long itemId = c.getLong(0);
            String code = c.getString(1);
            String name = c.getString(2);
            double purchasePrice = c.getDouble(3);
            double salePrice = c.getDouble(4);

            double extraCostPerUnit = (itemsHaveExtraCost && !c.isNull(extraCostColumn)) ?
                c.getDouble(extraCostColumn) : 0.0;

            boolean active = (!itemsHaveActive || c.isNull(activeColumn)) ?
                true : c.getInt(activeColumn) != 0;

            String importKey = "vyb_item_" + itemId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "item", itemId);

                if (localId != null) {
                    itemIdMap.put(itemId, localId.intValue());
                }

                counts.itemsDuplicate++;
                continue;
            }

            long localId = helper.insertItemBulk(
                db, code, name, purchasePrice, salePrice, extraCostPerUnit, active);

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "item", itemId, localId);

            itemIdMap.put(itemId, (int) localId);
            counts.itemsImported++;
        }

        c.close();
    }

    // Resolves a Vyapar name_id to a local party id, checking the
    // in-memory map first (fast path for this run) and falling back to
    // the persistent vyb_import_map (for a party imported in an earlier
    // run of this same importer).
    private Integer resolveParty(
        DatabaseHelper helper, SQLiteDatabase db, HashMap<Long, Integer> partyIdMap, Long nameId) {

        if (nameId == null) {
            return null;
        }

        Integer id = partyIdMap.get(nameId);

        if (id != null) {
            return id;
        }

        Long fromMap = helper.getVybLocalId(db, "party", nameId);

        if (fromMap != null) {
            partyIdMap.put(nameId, fromMap.intValue());
            return fromMap.intValue();
        }

        return null;
    }

    private Integer resolveItem(
        DatabaseHelper helper, SQLiteDatabase db, HashMap<Long, Integer> itemIdMap, long itemId) {

        Integer id = itemIdMap.get(itemId);

        if (id != null) {
            return id;
        }

        Long fromMap = helper.getVybLocalId(db, "item", itemId);

        if (fromMap != null) {
            itemIdMap.put(itemId, fromMap.intValue());
            return fromMap.intValue();
        }

        return null;
    }

    private Integer resolveVarietyGroup(
        DatabaseHelper helper, SQLiteDatabase db, HashMap<Long, Integer> groupIdMap, long groupId) {

        Integer id = groupIdMap.get(groupId);

        if (id != null) {
            return id;
        }

        Long fromMap = helper.getVybLocalId(db, "variety_group", groupId);

        if (fromMap != null) {
            groupIdMap.put(groupId, fromMap.intValue());
            return fromMap.intValue();
        }

        return null;
    }

    private Integer resolveVarietyValue(
        DatabaseHelper helper, SQLiteDatabase db, HashMap<Long, Integer> valueIdMap, long valueId) {

        Integer id = valueIdMap.get(valueId);

        if (id != null) {
            return id;
        }

        Long fromMap = helper.getVybLocalId(db, "variety_value", valueId);

        if (fromMap != null) {
            valueIdMap.put(valueId, fromMap.intValue());
            return fromMap.intValue();
        }

        return null;
    }

    private Integer resolveVarietyCombo(
        DatabaseHelper helper, SQLiteDatabase db, HashMap<Long, Integer> comboIdMap, long comboId) {

        Integer id = comboIdMap.get(comboId);

        if (id != null) {
            return id;
        }

        Long fromMap = helper.getVybLocalId(db, "variety_combo", comboId);

        if (fromMap != null) {
            comboIdMap.put(comboId, fromMap.intValue());
            return fromMap.intValue();
        }

        return null;
    }

    // vybPurchaseId is the same OFFSET_PURCHASE_TXN-based value already
    // used as kb_transactions.txn_id for a purchase (see importPurchases())
    // - callers with a raw, un-offset local purchase id (none currently)
    // would need to add ExportVyaparActivity.OFFSET_PURCHASE_TXN first.
    private Long resolvePurchase(
        DatabaseHelper helper, SQLiteDatabase db, HashMap<Long, Long> purchaseIdMap, long vybPurchaseId) {

        Long id = purchaseIdMap.get(vybPurchaseId);

        if (id != null) {
            return id;
        }

        Long fromMap = helper.getVybLocalId(db, "purchase", vybPurchaseId);

        if (fromMap != null) {
            purchaseIdMap.put(vybPurchaseId, fromMap);
            return fromMap;
        }

        return null;
    }

    // vybExpenseId is the same OFFSET_EXPENSE_TXN-based value already used
    // as kb_transactions.txn_id for an expense (see importExpenses()) - a
    // caller with a raw, un-offset local expense id (a Purchase draft's
    // pending_linked_expenses - see importDrafts()) must add
    // ExportVyaparActivity.OFFSET_EXPENSE_TXN to it first.
    private Integer resolveExpense(
        DatabaseHelper helper, SQLiteDatabase db, HashMap<Long, Integer> expenseIdMap, long vybExpenseId) {

        Integer id = expenseIdMap.get(vybExpenseId);

        if (id != null) {
            return id;
        }

        Long fromMap = helper.getVybLocalId(db, "expense", vybExpenseId);

        if (fromMap != null) {
            expenseIdMap.put(vybExpenseId, fromMap.intValue());
            return fromMap.intValue();
        }

        return null;
    }

    // =====================
    // STEP 2b - ITEM VARIETIES (businesserp_variety_groups/values/combos/
    // combo_values - this app's own extension, only present when
    // hasVarietyTables was true). Order matters: groups before values
    // before combos before combo_values, since each references the one
    // before it.
    // =====================
    private void importVarietyGroups(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> groupIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT group_id, item_id, name, sort_order FROM businesserp_variety_groups", null);

        while (c.moveToNext()) {

            long groupId = c.getLong(0);
            long itemId = c.getLong(1);
            String name = c.getString(2);
            int sortOrder = c.getInt(3);

            String importKey = "vyb_variety_group_" + groupId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "variety_group", groupId);

                if (localId != null) {
                    groupIdMap.put(groupId, localId.intValue());
                }

                counts.varietyGroupsDuplicate++;
                continue;
            }

            Integer localItemId = resolveItem(helper, db, itemIdMap, itemId);

            if (localItemId == null) {
                addSkipped(skipped, "variety_group", groupId, "Item for this variety group was not found/imported");
                continue;
            }

            long localId = helper.insertVarietyGroupBulk(
                db, localItemId, name == null ? "" : name, sortOrder);

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "variety_group", groupId, localId);

            groupIdMap.put(groupId, (int) localId);
            counts.varietyGroupsImported++;
        }

        c.close();
    }

    private void importVarietyValues(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> groupIdMap,
        HashMap<Long, Integer> valueIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT value_id, group_id, label, sort_order, is_default FROM businesserp_variety_values", null);

        while (c.moveToNext()) {

            long valueId = c.getLong(0);
            long groupId = c.getLong(1);
            String label = c.getString(2);
            int sortOrder = c.getInt(3);
            boolean isDefault = c.getInt(4) != 0;

            String importKey = "vyb_variety_value_" + valueId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "variety_value", valueId);

                if (localId != null) {
                    valueIdMap.put(valueId, localId.intValue());
                }

                counts.varietyValuesDuplicate++;
                continue;
            }

            Integer localGroupId = resolveVarietyGroup(helper, db, groupIdMap, groupId);

            if (localGroupId == null) {
                addSkipped(skipped, "variety_value", valueId, "Variety group for this value was not found/imported");
                continue;
            }

            long localId = helper.insertVarietyValueBulk(
                db, localGroupId, label == null ? "" : label, sortOrder, isDefault);

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "variety_value", valueId, localId);

            valueIdMap.put(valueId, (int) localId);
            counts.varietyValuesImported++;
        }

        c.close();
    }

    // Combos are always created at 0 balance regardless of anything the
    // backup might carry - see insertVarietyComboBulk()'s comment. The
    // real balance builds back up naturally as this same import replays
    // the backup's purchase/sale line items below.
    private void importVarietyCombos(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> comboIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT combo_id, item_id FROM businesserp_variety_combos", null);

        while (c.moveToNext()) {

            long comboId = c.getLong(0);
            long itemId = c.getLong(1);

            String importKey = "vyb_variety_combo_" + comboId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "variety_combo", comboId);

                if (localId != null) {
                    comboIdMap.put(comboId, localId.intValue());
                }

                counts.varietyCombosDuplicate++;
                continue;
            }

            Integer localItemId = resolveItem(helper, db, itemIdMap, itemId);

            if (localItemId == null) {
                addSkipped(skipped, "variety_combo", comboId, "Item for this variety combo was not found/imported");
                continue;
            }

            long localId = helper.insertVarietyComboBulk(db, localItemId);

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "variety_combo", comboId, localId);

            comboIdMap.put(comboId, (int) localId);
            counts.varietyCombosImported++;
        }

        c.close();
    }

    // No per-row dedup tracking needed - linkComboValueBulk() upserts
    // (combo_id, group_id) is its own primary key, so replaying the same
    // link twice is naturally idempotent.
    private void importVarietyComboValues(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> comboIdMap,
        HashMap<Long, Integer> groupIdMap,
        HashMap<Long, Integer> valueIdMap,
        ArrayList<SkippedRow> skipped) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT combo_id, group_id, value_id FROM businesserp_variety_combo_values", null);

        while (c.moveToNext()) {

            long comboId = c.getLong(0);
            long groupId = c.getLong(1);
            long valueId = c.getLong(2);

            Integer localComboId = resolveVarietyCombo(helper, db, comboIdMap, comboId);
            Integer localGroupId = resolveVarietyGroup(helper, db, groupIdMap, groupId);
            Integer localValueId = resolveVarietyValue(helper, db, valueIdMap, valueId);

            if (localComboId == null || localGroupId == null || localValueId == null) {
                addSkipped(
                    skipped, "variety_combo_value", comboId,
                    "Combo, group, or value for this link was not found/imported");
                continue;
            }

            helper.linkComboValueBulk(db, localComboId, localGroupId, localValueId);
        }

        c.close();
    }

    // =====================
    // STEP 3 - PURCHASES (kb_transactions, txn_type=2)
    // grand_total = txn_cash_amount + txn_balance_amount and
    // amount_paid = txn_cash_amount, confirmed against
    // SUM(kb_lineitems.total_amount) for this backup's own data.
    // =====================
    private void importPurchases(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        HashMap<Long, Long> purchaseIdMap,
        boolean purchasesHaveDueDate,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        // NOTE: txn_type=2 is the real Purchase type in this backup (stock
        // line items, priced per unit, always tied to a supplier party).
        // txn_type=7 is Expense (see importExpenses below) - every row has
        // a txn_category_id pointing at an expense label in kb_names
        // (name_type=2) and never a party, which is why it was wrongly
        // showing up as "party not found" when this was misread as Purchase.
        Cursor c = vyaparDb.rawQuery(
            "SELECT txn_id, txn_name_id, txn_date, txn_time, txn_cash_amount, txn_balance_amount, " +
            "txn_invoice_prefix, txn_ref_number_char, txn_description" +
            (purchasesHaveDueDate ? ", txn_due_date" : "") + " " +
            "FROM kb_transactions WHERE txn_type=2", null);

        while (c.moveToNext()) {

            long txnId = c.getLong(0);
            Long nameId = c.isNull(1) ? null : c.getLong(1);
            String txnDate = c.getString(2);
            int txnTime = c.getInt(3);
            double cash = c.getDouble(4);
            double balance = c.getDouble(5);
            String prefix = c.getString(6);
            String ref = c.getString(7);
            String description = c.getString(8);

            String dueDate = (purchasesHaveDueDate && !c.isNull(9)) ? c.getString(9) : null;

            String importKey = "vyb_purchase_" + txnId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "purchase", txnId);

                if (localId != null) {
                    purchaseIdMap.put(txnId, localId);
                }

                counts.purchasesDuplicate++;
                continue;
            }

            Integer partyId;

            if (nameId == null) {
                partyId = (int) helper.getOrCreatePartyIdBulk(db, "Cash Purchase");
            } else {
                partyId = resolveParty(helper, db, partyIdMap, nameId);
            }

            if (partyId == null) {
                addSkipped(skipped, "purchase", txnId, "Party for this purchase was not found/imported");
                continue;
            }

            String invoiceNumber = ((prefix == null ? "" : prefix) + (ref == null ? "" : ref)).trim();

            double grandTotal = cash + balance;

            long localId = helper.insertPurchaseBulk(
                db,
                partyId,
                formatDate(txnDate),
                formatTime(txnTime),
                invoiceNumber,
                grandTotal,
                cash,
                description == null ? "" : description,
                "Vyapar Import",
                dueDate
            );

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "purchase", txnId, localId);

            purchaseIdMap.put(txnId, localId);
            counts.purchasesImported++;
        }

        c.close();
    }

    // =====================
    // STEP 4 - PURCHASE LINE ITEMS
    // Joined straight from kb_lineitems to kb_transactions on
    // lineitem_txn_id = txn_id, exactly the relationship in the task
    // requirements, rather than matching on invoice numbers.
    // =====================
    private void importPurchaseLineItems(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Long> purchaseIdMap,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> comboIdMap,
        boolean lineItemsHaveComboId,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        // combo_id is this app's own extension (see hasVarietyTables in
        // runImport()) - only selected when the backup's kb_lineitems
        // actually has that column, since a real Vyapar backup won't.
        Cursor c = vyaparDb.rawQuery(
            "SELECT li.lineitem_id, li.lineitem_txn_id, li.item_id, li.quantity, li.priceperunit, li.total_amount" +
            (lineItemsHaveComboId ? ", li.combo_id" : "") + " " +
            "FROM kb_lineitems li JOIN kb_transactions t ON li.lineitem_txn_id = t.txn_id " +
            "WHERE t.txn_type=2", null);

        while (c.moveToNext()) {

            long lineItemId = c.getLong(0);
            long txnId = c.getLong(1);
            long itemId = c.getLong(2);
            double quantity = c.getDouble(3);
            double pricePerUnit = c.getDouble(4);
            double totalAmount = c.getDouble(5);

            Integer localComboId = null;

            if (lineItemsHaveComboId && !c.isNull(6)) {
                localComboId = resolveVarietyCombo(helper, db, comboIdMap, c.getLong(6));
            }

            String importKey = "vyb_purchase_item_" + lineItemId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.purchaseItemsDuplicate++;
                continue;
            }

            Long localPurchaseId = purchaseIdMap.get(txnId);

            if (localPurchaseId == null) {
                localPurchaseId = helper.getVybLocalId(db, "purchase", txnId);
            }

            if (localPurchaseId == null) {
                addSkipped(skipped, "purchase_line_item", lineItemId, "Parent purchase was not imported");
                continue;
            }

            Integer localItemId = resolveItem(helper, db, itemIdMap, itemId);

            if (localItemId == null) {
                addSkipped(skipped, "purchase_line_item", lineItemId, "Item was not found/imported");
                continue;
            }

            // The item has a Size (or other) variety group, but this row
            // came in with no combo to attach it to - writing it in with
            // combo_id=null would silently pile stock up in a phantom
            // "no size" bucket instead of a real one, and the same
            // bucket can then go negative on a later sale that hits this
            // same gap. Skip it and surface it in the Rows Not Imported
            // report instead, so it gets added back in manually with the
            // correct size chosen.
            if (localComboId == null && helper.itemHasVarietyGroupsBulk(db, localItemId)) {
                addSkipped(skipped, "purchase_line_item", lineItemId,
                    "Item requires a size and the backup didn't specify one - add this line manually with the correct size");
                continue;
            }

            helper.insertPurchaseItemBulk(
                db, localPurchaseId, localItemId, quantity, pricePerUnit, totalAmount, localComboId);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.purchaseItemsImported++;
        }

        c.close();
    }

    // =====================
    // STEP 5 - SALES (kb_transactions, txn_type=1)
    // txn_type=2 is not a credit note/sale return as originally assumed -
    // it's Purchase (see STEP 3 above), based on the actual data: item
    // line items priced per unit and always tied to a supplier party.
    // =====================
    private void importSales(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        HashMap<Long, Long> saleIdMap,
        boolean salesHaveDueDate,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT txn_id, txn_name_id, txn_date, txn_time, txn_cash_amount, txn_balance_amount, " +
            "txn_invoice_prefix, txn_ref_number_char, txn_description, txn_discount_amount, txn_tax_amount" +
            (salesHaveDueDate ? ", txn_due_date" : "") + " " +
            "FROM kb_transactions WHERE txn_type=1", null);

        while (c.moveToNext()) {

            long txnId = c.getLong(0);
            Long nameId = c.isNull(1) ? null : c.getLong(1);
            String txnDate = c.getString(2);
            int txnTime = c.getInt(3);
            double cash = c.getDouble(4);
            double balance = c.getDouble(5);
            String prefix = c.getString(6);
            String ref = c.getString(7);
            String description = c.getString(8);
            double discount = c.getDouble(9);
            double tax = c.getDouble(10);

            String dueDate = (salesHaveDueDate && !c.isNull(11)) ? c.getString(11) : null;

            String importKey = "vyb_sale_" + txnId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "sale", txnId);

                if (localId != null) {
                    saleIdMap.put(txnId, localId);
                }

                counts.salesDuplicate++;
                continue;
            }

            Integer partyId;

            if (nameId == null) {
                partyId = (int) helper.getOrCreatePartyIdBulk(db, "Cash Sale");
            } else {
                partyId = resolveParty(helper, db, partyIdMap, nameId);
            }

            if (partyId == null) {
                addSkipped(skipped, "sale", txnId, "Party for this sale was not found/imported");
                continue;
            }

            String invoiceNumber = ((prefix == null ? "" : prefix) + (ref == null ? "" : ref)).trim();

            double grandTotal = cash + balance;

            HashMap<String, Object> saleData = new HashMap<String, Object>();
            saleData.put("invoice_no", invoiceNumber);
            saleData.put("date", formatDate(txnDate));
            saleData.put("time", formatTime(txnTime));
            saleData.put("party_id", partyId);
            // subtotal isn't stored separately in Vyapar's schema in a way
            // that's cleanly re-derivable, so it mirrors grand_total here -
            // discount/tax are still preserved in their own fields.
            saleData.put("subtotal", grandTotal);
            saleData.put("discount", discount);
            saleData.put("other_charges", tax);
            saleData.put("grand_total", grandTotal);
            saleData.put("paid_amount", cash);
            saleData.put("balance", balance);
            saleData.put("notes", description == null ? "" : description);
            saleData.put("due_date", dueDate);

            long localId = helper.insertSaleBulk(db, saleData, "Vyapar Import");

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "sale", txnId, localId);

            saleIdMap.put(txnId, localId);
            counts.salesImported++;
        }

        c.close();
    }

    // =====================
    // STEP 6 - SALE LINE ITEMS
    // =====================
    private void importSaleLineItems(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Long> saleIdMap,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> comboIdMap,
        boolean lineItemsHaveComboId,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT li.lineitem_id, li.lineitem_txn_id, li.item_id, li.quantity, li.priceperunit, li.total_amount" +
            (lineItemsHaveComboId ? ", li.combo_id" : "") + " " +
            "FROM kb_lineitems li JOIN kb_transactions t ON li.lineitem_txn_id = t.txn_id " +
            "WHERE t.txn_type=1", null);

        while (c.moveToNext()) {

            long lineItemId = c.getLong(0);
            long txnId = c.getLong(1);
            long itemId = c.getLong(2);
            double quantity = c.getDouble(3);
            double pricePerUnit = c.getDouble(4);
            double totalAmount = c.getDouble(5);

            Integer localComboId = null;

            if (lineItemsHaveComboId && !c.isNull(6)) {
                localComboId = resolveVarietyCombo(helper, db, comboIdMap, c.getLong(6));
            }

            String importKey = "vyb_sale_item_" + lineItemId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.saleItemsDuplicate++;
                continue;
            }

            Long localSaleId = saleIdMap.get(txnId);

            if (localSaleId == null) {
                localSaleId = helper.getVybLocalId(db, "sale", txnId);
            }

            if (localSaleId == null) {
                addSkipped(skipped, "sale_line_item", lineItemId, "Parent sale was not imported");
                continue;
            }

            Integer localItemId = resolveItem(helper, db, itemIdMap, itemId);

            if (localItemId == null) {
                addSkipped(skipped, "sale_line_item", lineItemId, "Item was not found/imported");
                continue;
            }

            // See the matching guard in importPurchaseLineItems() - a
            // sizeless line for a sized item must not be written in with
            // combo_id=null, since that phantom "no size" bucket has no
            // stock of its own and would go negative even though the
            // item's real sizes have stock on hand.
            if (localComboId == null && helper.itemHasVarietyGroupsBulk(db, localItemId)) {
                addSkipped(skipped, "sale_line_item", lineItemId,
                    "Item requires a size and the backup didn't specify one - add this line manually with the correct size");
                continue;
            }

            HashMap<String, Object> itemData = new HashMap<String, Object>();
            itemData.put("sale_id", localSaleId);
            itemData.put("item_id", localItemId);
            itemData.put("qty", quantity);
            itemData.put("rate", pricePerUnit);
            itemData.put("amount", totalAmount);
            itemData.put("combo_id", localComboId);

            helper.insertSaleItemBulk(db, itemData);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.saleItemsImported++;
        }

        c.close();
    }

    // =====================
    // STEPS 7 & 8 - PAYMENT IN (txn_type=3) / PAYMENT OUT (txn_type=4)
    // One method handles both directions - txn_id is unique across all
    // Vyapar transaction types, so the import key stays unique too.
    // =====================
    private void importPayments(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts,
        int vyaparTxnType,
        boolean isPaymentIn) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT txn_id, txn_name_id, txn_date, txn_time, txn_cash_amount, txn_description " +
            "FROM kb_transactions WHERE txn_type=?",
            new String[]{String.valueOf(vyaparTxnType)});

        while (c.moveToNext()) {

            long txnId = c.getLong(0);
            Long nameId = c.isNull(1) ? null : c.getLong(1);
            String txnDate = c.getString(2);
            int txnTime = c.getInt(3);
            double amount = c.getDouble(4);
            String description = c.getString(5);

            String importKey = "vyb_payment_" + txnId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                if (isPaymentIn) {
                    counts.paymentInDuplicate++;
                } else {
                    counts.paymentOutDuplicate++;
                }

                continue;
            }

            Integer partyId = resolveParty(helper, db, partyIdMap, nameId);

            if (partyId == null) {
                addSkipped(skipped, isPaymentIn ? "payment_in" : "payment_out", txnId,
						   "Party for this payment was not found/imported");
                continue;
            }

            helper.insertPaymentBulk(
                db,
                isPaymentIn ? DatabaseHelper.PAYMENT_IN : DatabaseHelper.PAYMENT_OUT,
                partyId,
                formatDate(txnDate),
                formatTime(txnTime),
                amount,
                description == null ? "" : description,
                "Vyapar Import"
            );

            helper.markImportKeyUsedBulk(db, importKey);

            if (isPaymentIn) {
                counts.paymentInImported++;
            } else {
                counts.paymentOutImported++;
            }
        }

        c.close();
    }

    // =====================
    // STEP 9 - PARTY TO PARTY TRANSFERS (party_to_party_transfer)
    // The paying side's balance moves the same direction as a Payment In
    // and the receiving side's the same as a Payment Out - see the
    // comment on DatabaseHelper.insertPartyTransferBulk() for why, and
    // verify a few of these against Vyapar's own party ledgers.
    // =====================
    private void importPartyTransfers(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT p_txn_id, p_amount, p_received_txn_id, p_paid_txn_id, p_txn_date, p_txn_description " +
            "FROM party_to_party_transfer", null);

        while (c.moveToNext()) {

            long pTxnId = c.getLong(0);
            double amount = c.getDouble(1);
            long receivedTxnId = c.getLong(2);
            long paidTxnId = c.getLong(3);
            String txnDate = c.getString(4);
            String description = c.getString(5);

            String importKey = "vyb_p2p_" + pTxnId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.transfersDuplicate++;
                continue;
            }

            Long fromNameId = lookupTxnNameId(vyaparDb, paidTxnId);
            Long toNameId = lookupTxnNameId(vyaparDb, receivedTxnId);

            Integer fromPartyId = resolveParty(helper, db, partyIdMap, fromNameId);
            Integer toPartyId = resolveParty(helper, db, partyIdMap, toNameId);

            if (fromPartyId == null || toPartyId == null) {
                addSkipped(skipped, "party_transfer", pTxnId, "One or both parties for this transfer were not found/imported");
                continue;
            }

            int time = lookupTxnTime(vyaparDb, paidTxnId);

            helper.insertPartyTransferBulk(
                db,
                fromPartyId,
                toPartyId,
                formatDate(txnDate),
                formatTime(time),
                amount,
                description == null ? "" : description
            );

            helper.markImportKeyUsedBulk(db, importKey);
            counts.transfersImported++;
        }

        c.close();
    }

    private Long lookupTxnNameId(SQLiteDatabase vyaparDb, long txnId) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT txn_name_id FROM kb_transactions WHERE txn_id=?",
            new String[]{String.valueOf(txnId)});

        Long result = null;

        if (c.moveToFirst() && !c.isNull(0)) {
            result = c.getLong(0);
        }

        c.close();

        return result;
    }

    private int lookupTxnTime(SQLiteDatabase vyaparDb, long txnId) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT txn_time FROM kb_transactions WHERE txn_id=?",
            new String[]{String.valueOf(txnId)});

        int result = 0;

        if (c.moveToFirst()) {
            result = c.getInt(0);
        }

        c.close();

        return result;
    }

    // =====================
    // Logs any Vyapar transaction outside the types this importer
    // handles - 1 (Sale), 2 (Purchase), 3 (Payment In), 4 (Payment Out),
    // 7 (Expense), 50/51 (party transfer legs) - as skipped, so nothing
    // silently disappears without a trace in the summary.
    // =====================
    private void logUnsupportedTypes(SQLiteDatabase vyaparDb, ArrayList<SkippedRow> skipped) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT txn_id, txn_type FROM kb_transactions " +
            "WHERE txn_type NOT IN (1,2,3,4,7,50,51)", null);

        while (c.moveToNext()) {
            addSkipped(skipped, "transaction", c.getLong(0),
					   "Unsupported Vyapar transaction type (" + c.getInt(1) + ")");
        }

        c.close();
    }

    // =====================
    // STEP 10 - EXPENSES (kb_transactions, txn_type=7)
    // Every txn_type=7 row's txn_category_id points at an expense label
    // in kb_names (name_type=2) - "food", "pocket money", "medical", etc
    // - and none of them carry a party. That's expected: these are
    // personal/business expenses, not supplier purchases, which is why
    // treating them as purchases used to fail on "party not found".
    //
    // An expense transaction can also carry its own kb_lineitems, each
    // pointing at an item_type=2 row in kb_items whose item_name is the
    // actual expense name/description the user typed (e.g. "milk",
    // "haircut") - distinct from and more specific than the broader
    // category above (e.g. "food"). Per the importItems() note above,
    // those item_type=2 rows are never imported into TABLE_ITEMS and no
    // item_id is ever assigned to an expense - the line-item text is
    // read directly out of Vyapar's kb_items here and written straight
    // into expenses.item as a plain String, exactly as it appears in the
    // backup. A transaction with more than one line item (e.g. several
    // things paid for in one go) has all of its names joined with ", "
    // so nothing is dropped; a transaction with no line items at all
    // falls back to the category name, and then "Uncategorized", exactly
    // as before.
    // =====================
    private void importExpenses(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        HashMap<Long, Integer> expenseIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        // amount = txn_cash_amount + txn_balance_amount and paid_amount =
        // txn_cash_amount, exactly mirroring how importPurchases()/
        // importSales() reconstruct grand_total/amount_paid - see
        // ExportVyaparActivity.exportExpenses()'s comment for why.
        Cursor c = vyaparDb.rawQuery(
            "SELECT t.txn_id, t.txn_date, t.txn_time, t.txn_cash_amount, t.txn_balance_amount, " +
            "  t.txn_description, n.full_name, " +
            "  (SELECT GROUP_CONCAT(item_name, ', ') FROM (" +
            "      SELECT ki.item_name AS item_name " +
            "      FROM kb_lineitems li JOIN kb_items ki ON li.item_id = ki.item_id " +
            "      WHERE li.lineitem_txn_id = t.txn_id " +
            "      ORDER BY li.lineitem_id" +
            "  )) AS line_item_names, " +
            "  t.txn_name_id " +
            "FROM kb_transactions t LEFT JOIN kb_names n ON t.txn_category_id = n.name_id " +
            "WHERE t.txn_type=7", null);

        while (c.moveToNext()) {

            long txnId = c.getLong(0);
            String txnDate = c.getString(1);
            int txnTime = c.getInt(2);
            double cash = c.getDouble(3);
            double balance = c.getDouble(4);
            String description = c.getString(5);
            String categoryName = c.getString(6);
            String lineItemNames = c.getString(7);

            // Optional - most expenses have no party. When present, it's
            // resolved the same way a purchase/sale/payment's party is;
            // unlike those, though, an unresolvable party here just means
            // the expense imports without one rather than being skipped,
            // since a party was never required for an expense in the
            // first place (see Expenseeditactivity.saveExpense()).
            Integer partyId = c.isNull(8)
                ? null
                : resolveParty(helper, db, partyIdMap, c.getLong(8));

            String importKey = "vyb_expense_" + txnId;

            if (helper.isImportKeyUsedBulk(db, importKey)) {

                Long localId = helper.getVybLocalId(db, "expense", txnId);

                if (localId != null) {
                    expenseIdMap.put(txnId, localId.intValue());
                }

                counts.expensesDuplicate++;
                continue;
            }

            // Prefer the expense's own line-item name(s) - the specific
            // "what was this for" text - over the broader category, and
            // only fall back when a transaction has no line items at all.
            String item;

            if (lineItemNames != null && lineItemNames.trim().length() > 0) {
                item = lineItemNames.trim();
            } else if (categoryName != null && categoryName.trim().length() > 0) {
                item = categoryName.trim();
            } else {
                item = "Uncategorized";
            }

            double amount = cash + balance;
            double paidAmount = cash;

            long localId = helper.insertExpenseBulk(
                db,
                item,
                formatDate(txnDate),
                formatTime(txnTime),
                amount,
                paidAmount,
                description == null ? "" : description,
                partyId,
                "Vyapar Import"
            );

            helper.markImportKeyUsedBulk(db, importKey);
            helper.saveVybLocalId(db, "expense", txnId, localId);
            expenseIdMap.put(txnId, (int) localId);

            counts.expensesImported++;
        }

        c.close();
    }

    // =====================
    // STEP 11 - CASH ADJUSTMENTS (businesserp_cash_adjustments)
    // This app's own extension - only present when hasCashAdjustmentsTable
    // was true. date/time are already in this app's own format here
    // (unlike kb_transactions' txn_date/txn_time), since
    // ExportVyaparActivity copies them straight across with no Vyapar-
    // format conversion - so no formatDate()/formatTime() call is needed
    // on the way back in either.
    // =====================
    private void importCashAdjustments(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT id, date, time, amount, notes, source FROM businesserp_cash_adjustments", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            String date = c.getString(1);
            String time = c.getString(2);
            double amount = c.getDouble(3);
            String notes = c.getString(4);
            String source = c.isNull(5) ? "Vyapar Import" : c.getString(5);

            String importKey = "vyb_cash_adjustment_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.cashAdjustmentsDuplicate++;
                continue;
            }

            helper.insertCashAdjustmentBulk(
                db, date, time, amount, notes == null ? "" : notes, source);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.cashAdjustmentsImported++;
        }

        c.close();
    }

    // =====================
    // STEP 12 - COST ITEMS (businesserp_cost_items) - this app's own
    // extension, only present when hasCostItemsTable was true. Nothing
    // references a cost item by id, so this is a get-or-create-by-name
    // (see insertCostItemBulk()) rather than a proper id-remapped import -
    // there is no id to remap anything else through.
    // =====================
    private void importCostItems(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery("SELECT id, name FROM businesserp_cost_items", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            String name = c.getString(1);

            String importKey = "vyb_cost_item_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.costItemsDuplicate++;
                continue;
            }

            if (name == null || name.trim().length() == 0) {
                continue;
            }

            helper.insertCostItemBulk(db, name);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.costItemsImported++;
        }

        c.close();
    }

    // =====================
    // STEP 13 - LINKED EXPENSES (businesserp_purchase_expense_links) -
    // this app's own extension, only present when
    // hasPurchaseExpenseLinksTable was true. Run after both importPurchases
    // and importExpenses, since it needs both maps those steps build.
    // Restoring each item's extra_cost_per_unit verbatim via importItems()
    // already restores the actual landed-cost EFFECT of every past link -
    // this step only restores the link ROW itself, so the linked expense
    // stays excluded from plain expense totals and the Purchase screen
    // still shows what it's linked to.
    // =====================
    private void importPurchaseExpenseLinks(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Long> purchaseIdMap,
        HashMap<Long, Integer> expenseIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT id, expense_id, purchase_id, share_percent, allocated_amount " +
            "FROM businesserp_purchase_expense_links", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            long vybExpenseId = c.getLong(1);
            long vybPurchaseId = c.getLong(2);
            double sharePercent = c.getDouble(3);
            double allocatedAmount = c.getDouble(4);

            String importKey = "vyb_purchase_expense_link_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.purchaseExpenseLinksDuplicate++;
                continue;
            }

            Integer localExpenseId = resolveExpense(helper, db, expenseIdMap, vybExpenseId);
            Long localPurchaseId = resolvePurchase(helper, db, purchaseIdMap, vybPurchaseId);

            if (localExpenseId == null || localPurchaseId == null) {
                addSkipped(skipped, "purchase_expense_link", id,
                    "Expense or purchase for this landed-cost link was not found/imported");
                continue;
            }

            helper.insertPurchaseExpenseLinkBulk(
                db, localExpenseId, localPurchaseId.intValue(), sharePercent, allocatedAmount);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.purchaseExpenseLinksImported++;
        }

        c.close();
    }

    // =====================
    // STEP 14 - RECURRING EXPENSE RULES (businesserp_recurring_expenses) -
    // this app's own extension, only present when hasRecurringExpensesTable
    // was true. Only the rule itself; the concrete expense rows it has
    // already generated came back separately as ordinary expenses via
    // importExpenses() above.
    // =====================
    private void importRecurringExpenses(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT id, item, amount, notes, party_id, frequency, day_of_week, day_of_month, " +
            "specific_dates, start_date, last_generated_date, active " +
            "FROM businesserp_recurring_expenses", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            String item = c.getString(1);
            double amount = c.getDouble(2);
            String notes = c.getString(3);
            Long vybPartyId = c.isNull(4) ? null : c.getLong(4);
            int frequency = c.getInt(5);
            Integer dayOfWeek = c.isNull(6) ? null : c.getInt(6);
            Integer dayOfMonth = c.isNull(7) ? null : c.getInt(7);
            String specificDates = c.getString(8);
            String startDate = c.getString(9);
            String lastGeneratedDate = c.getString(10);
            boolean active = c.getInt(11) != 0;

            String importKey = "vyb_recurring_expense_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.recurringExpensesDuplicate++;
                continue;
            }

            Integer partyId = resolveParty(helper, db, partyIdMap, vybPartyId);

            helper.insertRecurringExpenseBulk(
                db, item == null ? "" : item, amount, notes, partyId, frequency,
                dayOfWeek, dayOfMonth, specificDates, startDate, lastGeneratedDate, active);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.recurringExpensesImported++;
        }

        c.close();
    }

    // =====================
    // STEP 15 - DRAFTS (businesserp_drafts) - this app's own extension,
    // only present when hasDraftsTable was true, and run last, since a
    // Purchase/Sale draft's remap below needs every map every earlier step
    // built. Payment and Expense drafts carry no ids at all (see
    // Paymenteditactivity/Expenseeditactivity saveDraft(), which store
    // party_name/item as plain text) so they're copied straight through
    // unchanged; only a Purchase/Sale draft's party_id, each line's item_id/
    // combo_id, and a Purchase draft's pending_linked_expenses' expense_id
    // need remapping - see remapPurchaseOrSaleDraftIds().
    // =====================
    private void importDrafts(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> comboIdMap,
        HashMap<Long, Integer> expenseIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT id, type, label, data, date, time FROM businesserp_drafts", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            String type = c.getString(1);
            String label = c.getString(2);
            String rawData = c.getString(3);
            String date = c.getString(4);
            String time = c.getString(5);

            String importKey = "vyb_draft_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.draftsDuplicate++;
                continue;
            }

            HashMap<String, Object> data = DraftCodec.decode(rawData);

            if (DatabaseHelper.DRAFT_TYPE_PURCHASE.equals(type)
                || DatabaseHelper.DRAFT_TYPE_SALE.equals(type)) {

                remapPurchaseOrSaleDraftIds(
                    helper, db, data, partyIdMap, itemIdMap, comboIdMap, expenseIdMap);
            }

            String encoded = DraftCodec.encode(data);

            if (encoded == null) {
                addSkipped(skipped, "draft", id, "Could not re-encode this draft's data after import");
                continue;
            }

            helper.insertDraftBulk(
                db, type, label == null ? "" : label, encoded,
                date == null ? "" : date, time == null ? "" : time);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.draftsImported++;
        }

        c.close();
    }

    // =====================
    // STEP 16 - WANTED ITEMS (businesserp_wanted_items) - this app's own
    // extension, only present when hasWantedItemsTable was true. A
    // customer request not tied to any transaction, so unlike a Purchase/
    // Sale draft's items there's just the one item_id/party_id pair to
    // remap, both nullable - a reference that can't be resolved (item or
    // party not present in this backup) is simply dropped, keeping the
    // plain-text item_name/notes rather than skipping the whole row, same
    // as how a Sale/Purchase's own party_id degrades to "no party" rather
    // than failing the row.
    // =====================
    private void importWantedItems(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> partyIdMap,
        HashMap<Long, Integer> itemIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT id, item_id, item_name, date, time, party_id, notes, fulfilled " +
            "FROM businesserp_wanted_items", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            Long vybItemId = c.isNull(1) ? null : c.getLong(1);
            String itemName = c.getString(2);
            String date = c.getString(3);
            String time = c.getString(4);
            Long vybPartyId = c.isNull(5) ? null : c.getLong(5);
            String notes = c.getString(6);
            boolean fulfilled = c.getInt(7) != 0;

            String importKey = "vyb_wanted_item_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.wantedItemsDuplicate++;
                continue;
            }

            Integer itemId = vybItemId == null ? null : resolveItem(helper, db, itemIdMap, vybItemId);
            Integer partyId = resolveParty(helper, db, partyIdMap, vybPartyId);

            helper.insertWantedItemBulk(
                db, itemId, itemName == null ? "" : itemName,
                date == null ? "" : date, time == null ? "" : time,
                partyId, notes, fulfilled);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.wantedItemsImported++;
        }

        c.close();
    }

    // =====================
    // DISPLAY SHOES (businesserp_display_shoes) - only present when
    // hasDisplayShoesTable was true. item_id/combo_id both need
    // remapping (unlike Wanted Items' nullable ones, both are NOT NULL
    // here - see TABLE_DISPLAY_SHOES) - a row whose item or combo isn't
    // in this backup is dropped rather than left dangling.
    // =====================
    private void importDisplayShoes(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> comboIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT id, item_id, combo_id, row_pos, col_pos FROM businesserp_display_shoes", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            long vybItemId = c.getLong(1);
            long vybComboId = c.getLong(2);
            int rowPos = c.getInt(3);
            int colPos = c.getInt(4);

            String importKey = "vyb_display_shoe_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.displayShoesDuplicate++;
                continue;
            }

            Integer itemId = resolveItem(helper, db, itemIdMap, vybItemId);
            Integer comboId = resolveVarietyCombo(helper, db, comboIdMap, vybComboId);

            if (itemId == null || comboId == null) {

                addSkipped(
                    skipped, "display_shoe", id,
                    "Its item or size no longer exists in this backup"
                );

                continue;
            }

            helper.insertDisplayShoeBulk(db, itemId, comboId, rowPos, colPos);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.displayShoesImported++;
        }

        c.close();
    }

    // =====================
    // SAMPLE SHOES (businesserp_sample_shoes) - only present when
    // hasSampleShoesTable was true. Same remap/drop reasoning as
    // Display Shoes above, minus the grid position.
    // =====================
    private void importSampleShoes(
        SQLiteDatabase vyaparDb,
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> comboIdMap,
        ArrayList<SkippedRow> skipped,
        Counts counts) {

        Cursor c = vyaparDb.rawQuery(
            "SELECT id, item_id, combo_id FROM businesserp_sample_shoes", null);

        while (c.moveToNext()) {

            long id = c.getLong(0);
            long vybItemId = c.getLong(1);
            long vybComboId = c.getLong(2);

            String importKey = "vyb_sample_shoe_" + id;

            if (helper.isImportKeyUsedBulk(db, importKey)) {
                counts.sampleShoesDuplicate++;
                continue;
            }

            Integer itemId = resolveItem(helper, db, itemIdMap, vybItemId);
            Integer comboId = resolveVarietyCombo(helper, db, comboIdMap, vybComboId);

            if (itemId == null || comboId == null) {

                addSkipped(
                    skipped, "sample_shoe", id,
                    "Its item or size no longer exists in this backup"
                );

                continue;
            }

            helper.insertSampleShoeBulk(db, itemId, comboId);

            helper.markImportKeyUsedBulk(db, importKey);
            counts.sampleShoesImported++;
        }

        c.close();
    }

    // Rewrites a Purchase/Sale draft's embedded ids (party_id, each line's
    // item_id/combo_id, and a Purchase draft's pending_linked_expenses'
    // expense_id) from the original device's local ids to this restore's
    // newly-assigned ones, using the exact same maps already built for the
    // real (committed) rows above. A line/party/pending-expense whose id
    // can't be resolved (e.g. it referenced something not present in this
    // backup) is dropped rather than left pointing at the wrong thing -
    // consistent with a draft being allowed to stay incomplete.
    @SuppressWarnings("unchecked")
    private void remapPurchaseOrSaleDraftIds(
        DatabaseHelper helper,
        SQLiteDatabase db,
        HashMap<String, Object> data,
        HashMap<Long, Integer> partyIdMap,
        HashMap<Long, Integer> itemIdMap,
        HashMap<Long, Integer> comboIdMap,
        HashMap<Long, Integer> expenseIdMap) {

        Object oldPartyIdObj = data.get("party_id");

        if (oldPartyIdObj != null) {

            Integer newPartyId = resolveParty(
                helper, db, partyIdMap, ((Integer) oldPartyIdObj).longValue());

            if (newPartyId != null) {
                data.put("party_id", newPartyId);
            } else {
                data.remove("party_id");
            }
        }

        ArrayList<HashMap<String, Object>> items =
            (ArrayList<HashMap<String, Object>>) data.get("items");

        if (items != null) {

            ArrayList<HashMap<String, Object>> remapped = new ArrayList<HashMap<String, Object>>();

            for (HashMap<String, Object> line : items) {

                Object oldItemIdObj = line.get("item_id");

                if (oldItemIdObj == null) {
                    remapped.add(line);
                    continue;
                }

                Integer newItemId = resolveItem(
                    helper, db, itemIdMap, ((Integer) oldItemIdObj).longValue());

                if (newItemId == null) {
                    continue;
                }

                line.put("item_id", newItemId);

                Object oldComboIdObj = line.get("combo_id");

                if (oldComboIdObj != null) {

                    Integer newComboId = resolveVarietyCombo(
                        helper, db, comboIdMap, ((Integer) oldComboIdObj).longValue());

                    line.put("combo_id", newComboId);
                }

                remapped.add(line);
            }

            data.put("items", remapped);
        }

        ArrayList<HashMap<String, Object>> pending =
            (ArrayList<HashMap<String, Object>>) data.get("pending_linked_expenses");

        if (pending != null) {

            ArrayList<HashMap<String, Object>> remappedPending = new ArrayList<HashMap<String, Object>>();

            for (HashMap<String, Object> entry : pending) {

                Object oldExpenseIdObj = entry.get("expense_id");

                if (oldExpenseIdObj == null) {
                    continue;
                }

                Integer newExpenseId = resolveExpense(
                    helper, db, expenseIdMap,
                    ExportVyaparActivity.OFFSET_EXPENSE_TXN + ((Integer) oldExpenseIdObj).longValue());

                if (newExpenseId == null) {
                    continue;
                }

                entry.put("expense_id", newExpenseId);
                remappedPending.add(entry);
            }

            data.put("pending_linked_expenses", remappedPending);
        }
    }
}
