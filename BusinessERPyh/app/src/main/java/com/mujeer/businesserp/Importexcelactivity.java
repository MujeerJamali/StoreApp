package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

import jxl.Cell;
import jxl.CellType;
import jxl.DateCell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

public class Importexcelactivity extends Activity {

    private static final int REQUEST_PICK_EXCEL = 1001;
    private static final int REQUEST_PICK_TRANSACTION_FILES = 1002;

    private Button btn_import_items;
    private Button btn_import_transactions;
    private Button btn_view_skipped;
    private Button btn_import_vyapar;
    private Button btn_export_vyapar;
    private Button btn_bulk_purchase_import;
    private TextView tv_result;

    // =====================
    // One "not imported" row from the last import run - either skipped
    // (unsupported transaction type, or missing date) or a duplicate of
    // a row already imported previously. Kept in memory only, for the
    // "View skipped rows" screen right after an import finishes.
    // =====================
    static class SkippedRow {
        String file;
        String rowNumber;
        String type;
        String date;
        String party;
        String amount;
        String reason;

        String toDisplayString() {
            return (file + " - row " + rowNumber
                + (type.length() > 0 ? " - " + type : "")
                + (date.length() > 0 ? " - " + date : "")
                + (party.length() > 0 ? " - " + party : "")
                + (amount.length() > 0 ? " - " + amount : "")
                + "\nReason: " + reason);
        }
    }

    // Holds the previous import run's not-imported rows so
    // ImportSkippedRowsActivity can display them. In-memory only -
    // intentionally not persisted, this is just a "what just happened"
    // report for right after an import.
    static ArrayList<SkippedRow> lastSkippedRows = new ArrayList<SkippedRow>();

    // =====================
    // One line from the "Item Details" sheet of an "All Transactions"
    // export - one purchase/sale line item, before it has been matched
    // up to its parent transaction header.
    // =====================
    private static class ItemRow {
        String code;
        String name;
        double quantity;
        double price;
        double amount;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_excel);

        btn_import_items = (Button) findViewById(R.id.btn_import_items);
        btn_import_transactions = (Button) findViewById(R.id.btn_import_transactions);
        btn_view_skipped = (Button) findViewById(R.id.btn_view_skipped);
        btn_import_vyapar = (Button) findViewById(R.id.btn_import_vyapar);
        btn_export_vyapar = (Button) findViewById(R.id.btn_export_vyapar);
        btn_bulk_purchase_import = (Button) findViewById(R.id.btn_bulk_purchase_import);
        tv_result = (TextView) findViewById(R.id.tv_result);

        btn_import_items.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openExcelPicker();
				}
			});

        btn_import_transactions.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					btn_view_skipped.setVisibility(View.GONE);
					openTransactionFilesPicker();
				}
			});

        btn_view_skipped.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						Importexcelactivity.this,
						ImportSkippedRowsActivity.class
					));
				}
			});

        // Opens the new, completely separate Vyapar backup importer.
        // Nothing above this line changes - the Excel importer keeps
        // working exactly as before.
        btn_import_vyapar.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						Importexcelactivity.this,
						ImportVyaparActivity.class
					));
				}
			});

        // Exports this app's own data into a .vyb file, in the same
        // format the importer above reads.
        btn_export_vyapar.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						Importexcelactivity.this,
						ExportVyaparActivity.class
					));
				}
			});

        // Opens Bulk Purchase Import with no purchase pre-selected - the
        // user picks one from the list on that screen. (When reached
        // instead from a purchase's own "Bulk Import" button, that
        // purchase comes pre-selected there.)
        btn_bulk_purchase_import.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						Importexcelactivity.this,
						BulkPurchaseImportActivity.class
					));
				}
			});
    }

    private void openExcelPicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");

        intent.putExtra(
			Intent.EXTRA_MIME_TYPES,
			new String[]{
				"application/vnd.ms-excel"
			});

        startActivityForResult(intent, REQUEST_PICK_EXCEL);
    }

    private void openTransactionFilesPicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");

        intent.putExtra(
			Intent.EXTRA_MIME_TYPES,
			new String[]{
				"application/vnd.ms-excel"
			});

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        startActivityForResult(intent, REQUEST_PICK_TRANSACTION_FILES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_PICK_TRANSACTION_FILES
			&& resultCode == RESULT_OK
			&& data != null) {

			final ArrayList<Uri> uris = new ArrayList<Uri>();

			if (data.getClipData() != null) {

				int fileCount = data.getClipData().getItemCount();

				for (int i = 0; i < fileCount; i++) {
					uris.add(data.getClipData().getItemAt(i).getUri());
				}

			} else if (data.getData() != null) {

				uris.add(data.getData());
			}

			new Thread(new Runnable() {
					@Override
					public void run() {

						final int totalFiles = uris.size();

						int totalImported = 0;
						int totalSkipped = 0;
						int totalDuplicates = 0;
						int totalItems = 0;

						final ArrayList<SkippedRow> allSkipped = new ArrayList<SkippedRow>();

						StringBuilder fileErrors = new StringBuilder();

						for (int i = 0; i < uris.size(); i++) {

							final int current = i + 1;

							runOnUiThread(new Runnable() {
									@Override
									public void run() {
										tv_result.setText(
											"Reading file " + current + "/" + totalFiles + "..."
										);
									}
								});

							try {

								HashMap<String, Integer> result =
									readTransactionWorkbook(
										uris.get(i), "File " + current, allSkipped);

								totalImported += result.get("imported");
								totalSkipped += result.get("skipped");
								totalDuplicates += result.get("duplicates");

								Integer itemsForFile = result.get("items");
								totalItems += itemsForFile != null ? itemsForFile : 0;

							} catch (Exception e) {

								fileErrors.append("File " + current + ": " + e.toString() + "\n");
							}
						}

						Importexcelactivity.lastSkippedRows = allSkipped;

						final int finalImported = totalImported;
						final int finalSkipped = totalSkipped;
						final int finalDuplicates = totalDuplicates;
						final int finalItems = totalItems;
						final int finalNotImportedCount = allSkipped.size();
						final String finalErrors = fileErrors.toString();

						runOnUiThread(new Runnable() {
								@Override
								public void run() {

									StringBuilder summary = new StringBuilder();

									summary.append("Files processed: " + totalFiles + "\n");
									summary.append("Imported: " + finalImported + "\n");
									summary.append("Item lines imported: " + finalItems + "\n");
									summary.append("Skipped (unsupported row type): " + finalSkipped + "\n");
									summary.append("Duplicates skipped: " + finalDuplicates + "\n");

									if (finalErrors.length() > 0) {
										summary.append("\nErrors:\n" + finalErrors);
									}

									tv_result.setText(summary.toString());

									if (finalNotImportedCount > 0) {
										btn_view_skipped.setText(
											"View rows not imported (" + finalNotImportedCount + ")");
										btn_view_skipped.setVisibility(View.VISIBLE);
									} else {
										btn_view_skipped.setVisibility(View.GONE);
									}
								}
							});
					}
				}).start();

			return;
		}

        if (requestCode == REQUEST_PICK_EXCEL
			&& resultCode == RESULT_OK
			&& data != null) {

            final Uri uri = data.getData();

            new Thread(new Runnable() {
					@Override
					public void run() {

						try {

							InputStream inputStream =
                                getContentResolver().openInputStream(uri);

							if (inputStream == null) {
								throw new Exception(
									"Could not open the selected file " +
									"(no permission, or the file was moved/deleted).");
							}

							Workbook workbook = null;

							try {

								workbook = openWorkbook(inputStream);

								if (workbook.getNumberOfSheets() == 0) {
									throw new Exception("This Excel file has no sheets.");
								}

								Sheet sheet = workbook.getSheet(0);

								final ArrayList<HashMap<String, Object>> listMap =
	                                new ArrayList<HashMap<String, Object>>();

								final int rows = sheet.getRows();
								int columns = sheet.getColumns();

								if (rows == 0 || columns == 0) {
									throw new Exception("This Excel file's first sheet is empty.");
								}

								for (int row = 1; row < rows; row++) {

									HashMap<String, Object> map =
	                                    new HashMap<String, Object>();

									boolean emptyRow = true;

									for (int col = 0; col < columns; col++) {

										String key = sheet.getCell(col, 0).getContents().trim();

										if (key.equalsIgnoreCase("Item Code")) {
											key = "code";
										} else if (key.equalsIgnoreCase("Item Name")) {
											key = "name";
										} else if (key.equalsIgnoreCase("Purchase Price")) {
											key = "purchase_price";
										} else if (key.equalsIgnoreCase("Sale Price")) {
											key = "sale_price";
										}

										String value = sheet.getCell(col, row).getContents().trim();

										if (value.length() > 0) {
											emptyRow = false;
										}

										if (key.equals("code") && value.length() == 0) {
											value = "";
										}

										map.put(key, value);
									}

									if (!emptyRow) {
										listMap.add(map);
									}

									final int currentRow = row;

									runOnUiThread(new Runnable() {
											@Override
											public void run() {
												tv_result.setText(currentRow + "/" + (rows - 1) + " rows read");
											}
										});
								}

								final DatabaseHelper db =
	                                new DatabaseHelper(Importexcelactivity.this);

								final HashMap<String, Integer> result =
	                                db.insertItems(listMap);

								runOnUiThread(new Runnable() {
										@Override
										public void run() {

											tv_result.setText(
												"Imported: " + result.get("imported") +
												"\nSkipped: " + result.get("skipped") +
												"\nTotal: " + listMap.size()
											);

										}
									});

							} finally {

								if (workbook != null) {
									workbook.close();
								}

								try {
									inputStream.close();
								} catch (Exception ignoreClose) {
									// already reading/parsing failed or
									// finished - nothing useful to do here
								}
							}

						} catch (final Exception e) {

							runOnUiThread(new Runnable() {
									@Override
									public void run() {

										StringBuilder error = new StringBuilder();

										error.append(e.toString()).append("\n\n");

										for (StackTraceElement s : e.getStackTrace()) {
											error.append(s.toString()).append("\n");
										}

										tv_result.setText(error.toString());

									}
								});

						}

					}
				}).start();
        }
    }

	// =====================
	// Reads one "All Transactions" export (Sale / Purchase / Payment-in /
	// Payment-out / Expense rows) and writes each row straight into the
	// matching table. Returns a summary map with keys:
	// "imported", "skipped" (unsupported row types), "duplicates".
	// Every row that does NOT end up imported (skipped, duplicate, or
	// missing a date) is also appended to skippedOut so the user can
	// review exactly which rows were left out and why.
	// =====================
	private HashMap<String, Integer> readTransactionWorkbook(
		Uri uri,
		String fileLabel,
		ArrayList<SkippedRow> skippedOut) throws Exception {

		InputStream inputStream = getContentResolver().openInputStream(uri);

		if (inputStream == null) {
			throw new Exception(
				"Could not open the file " +
				"(no permission, or the file was moved/deleted).");
		}

		Workbook workbook = null;
		DatabaseHelper dbHelper = null;
		boolean transactionStarted = false;

		try {

			workbook = openWorkbook(inputStream);

			if (workbook.getNumberOfSheets() == 0) {
				throw new Exception("This Excel file has no sheets.");
			}

			Sheet sheet = workbook.getSheet(0);

			int rows = sheet.getRows();
			int columns = sheet.getColumns();

			if (rows == 0 || columns == 0) {
				throw new Exception("This Excel file's first sheet is empty.");
			}

			// The file starts with a few "From Date:/To Date:/..." summary rows
			// before the real header row, so find the header row by looking
			// for the one starting with "Date" then "Party Name".
			int headerRow = -1;

			for (int r = 0; r < rows; r++) {

				String c0 = sheet.getCell(0, r).getContents().trim();
				String c1 = columns > 1 ? sheet.getCell(1, r).getContents().trim() : "";

				if (c0.equalsIgnoreCase("Date") && c1.equalsIgnoreCase("Party Name")) {
					headerRow = r;
					break;
				}
			}

			if (headerRow == -1) {
				throw new Exception("Could not find the header row (Date, Party Name...) in this file.");
			}

			HashMap<String, Integer> colIndex = new HashMap<String, Integer>();

			for (int c = 0; c < columns; c++) {

				String name = sheet.getCell(c, headerRow).getContents().trim();

				if (name.length() > 0) {
					colIndex.put(name, c);
				}
			}

			String[] required = {"Date", "Party Name", "Transaction Type", "Amount"};

			for (String req : required) {
				if (!colIndex.containsKey(req)) {
					throw new Exception("Missing required column: " + req);
				}
			}

			// Sale item lines are keyed by date+party+invoice number, since
			// sales carry a Ref No. that matches the "Invoice No." column
			// in the Item Details sheet. Purchase item lines have no such
			// reference, so they are grouped only by date+party and handed
			// out to each purchase transaction, in sheet order, by
			// accumulating item amounts until they add up to that
			// transaction's total (see pickPurchaseItems()).
			HashMap<String, ArrayList<ItemRow>> salesItemsByKey =
				new HashMap<String, ArrayList<ItemRow>>();
			HashMap<String, ArrayDeque<ItemRow>> purchaseItemQueueByKey =
				new HashMap<String, ArrayDeque<ItemRow>>();

			loadItemDetails(workbook, salesItemsByKey, purchaseItemQueueByKey);

			dbHelper = new DatabaseHelper(this);
			dbHelper.beginTransaction();
			transactionStarted = true;
			SQLiteDatabase db = dbHelper.getMigrationDatabase();

			int imported = 0;
			int skipped = 0;
			int duplicates = 0;
			int itemsImported = 0;

			// Counts how many times a given type+date+party+ref+amount
			// combination has been seen SO FAR in this file. Purchases in
			// particular often repeat the exact same date/party/amount as
			// separate real transactions (e.g. several single-item
			// purchases from the same supplier on the same day) - without
			// this, the second one would be wrongly treated as a
			// duplicate of the first. Re-running the same file always
			// produces the same sequence of counts, so true re-imports
			// are still caught.
			HashMap<String, Integer> occurrenceCounter = new HashMap<String, Integer>();

			for (int row = headerRow + 1; row < rows; row++) {

				Integer dateCol = colIndex.get("Date");
				String date = dateCol != null ? getIsoDate(sheet, dateCol, row) : "";

				if (date.length() == 0) {

					SkippedRow sr = new SkippedRow();
					sr.file = fileLabel;
					sr.rowNumber = String.valueOf(row + 1);
					sr.type = getCell(sheet, colIndex, "Transaction Type", row);
					sr.date = "";
					sr.party = getCell(sheet, colIndex, "Party Name", row);
					sr.amount = getCell(sheet, colIndex, "Amount", row);
					sr.reason = "Missing or unreadable date";
					skippedOut.add(sr);

					continue;
				}

				String partyName = getCell(sheet, colIndex, "Party Name", row);
				String category = getCell(sheet, colIndex, "Category", row);
				String type = getCell(sheet, colIndex, "Transaction Type", row);
				String refNo = getCell(sheet, colIndex, "Ref No.", row);
				String description = getCell(sheet, colIndex, "Description", row);
				String amountStr = getCell(sheet, colIndex, "Amount", row);
				String receivedStr = getCell(sheet, colIndex, "Received Amount", row);
				String paidStr = getCell(sheet, colIndex, "Paid Amount", row);
				String balanceStr = getCell(sheet, colIndex, "Balance Amount", row);

				double amount = parseAmount(amountStr);
				double received = parseAmount(receivedStr);
				double paid = parseAmount(paidStr);
				double balance = parseAmount(balanceStr);

				String time = "00:00:00";

				// De-dupe key: same type/date/party/ref/amount = same row,
				// so re-importing a file (or an overlapping file) is safe.
				// The "#occurrence" suffix lets genuinely repeated
				// transactions (same type/date/party/amount, no ref
				// number to tell them apart) through instead of treating
				// the 2nd, 3rd... one as a duplicate of the 1st.
				String baseKey = type + "|" + date + "|" + partyName + "|" + refNo + "|" + amountStr;

				Integer occurrence = occurrenceCounter.get(baseKey);
				occurrence = occurrence == null ? 1 : occurrence + 1;
				occurrenceCounter.put(baseKey, occurrence);

				String importKey = baseKey + "|#" + occurrence;

				if (dbHelper.isImportKeyUsedBulk(db, importKey)) {

					duplicates++;

					SkippedRow sr = new SkippedRow();
					sr.file = fileLabel;
					sr.rowNumber = String.valueOf(row + 1);
					sr.type = type;
					sr.date = date;
					sr.party = partyName;
					sr.amount = amountStr;
					sr.reason = "Duplicate - already imported previously";
					skippedOut.add(sr);

					continue;
				}

				if (type.equalsIgnoreCase("Sale")) {

					int partyId = dbHelper.getOrCreatePartyIdBulk(db, partyName);

					// Use the sale's own invoice/ref number from the
					// source file as-is. If the row has none (e.g. some
					// Cash Sale rows), the sale is imported with a blank
					// invoice number rather than inventing one - invoice
					// numbers are only auto-generated for transactions
					// entered manually in the app.
					String invoiceNo = refNo;

					HashMap<String, Object> saleData = new HashMap<String, Object>();
					saleData.put("invoice_no", invoiceNo);
					saleData.put("date", date);
					saleData.put("time", time);
					saleData.put("party_id", partyId);
					saleData.put("subtotal", amount);
					saleData.put("discount", 0.0);
					saleData.put("other_charges", 0.0);
					saleData.put("grand_total", amount);
					saleData.put("paid_amount", received);
					saleData.put("balance", balance);
					saleData.put("notes", description);

					long saleId = dbHelper.insertSaleBulk(db, saleData);

					String saleItemKey = date + "|" + partyName + "|" + refNo;
					ArrayList<ItemRow> saleItems = salesItemsByKey.get(saleItemKey);

					if (saleItems != null) {

						for (ItemRow item : saleItems) {

							int itemId = dbHelper.getOrCreateItemIdBulk(
								db, item.code, item.name, item.price, false);

							HashMap<String, Object> itemData = new HashMap<String, Object>();
							itemData.put("sale_id", saleId);
							itemData.put("item_id", itemId);
							itemData.put("qty", item.quantity);
							itemData.put("rate", item.price);
							itemData.put("amount", item.amount);

							dbHelper.insertSaleItemBulk(db, itemData);
							itemsImported++;
						}
					}

					imported++;

				} else if (type.equalsIgnoreCase("Purchase")) {

					int partyId = dbHelper.getOrCreatePartyIdBulk(db, partyName);

					// Use the purchase's own invoice/ref number from the
					// source file as-is. If the row has none, insertPurchaseBulk()
					// auto-assigns the next purchase invoice number (same
					// fallback used for manual entry), so every purchase
					// ends up with a valid invoice number either way.
					long purchaseId = dbHelper.insertPurchaseBulk(
						db, partyId, date, time, refNo, amount, paid, description);

					String purchaseGroupKey = date + "|" + partyName;
					ArrayDeque<ItemRow> purchaseQueue =
						purchaseItemQueueByKey.get(purchaseGroupKey);

					ArrayList<ItemRow> purchaseItems =
						pickPurchaseItems(purchaseQueue, amount);

					for (ItemRow item : purchaseItems) {

						int itemId = dbHelper.getOrCreateItemIdBulk(
							db, item.code, item.name, item.price, true);

						dbHelper.insertPurchaseItemBulk(
							db, purchaseId, itemId, item.quantity, item.price, item.amount);

						itemsImported++;
					}

					imported++;

				} else if (type.equalsIgnoreCase("Payment-in")) {

					int partyId = dbHelper.getOrCreatePartyIdBulk(db, partyName);
					double payAmount = received > 0 ? received : amount;

					dbHelper.insertPaymentBulk(
						db, DatabaseHelper.PAYMENT_IN, partyId, date, time, payAmount, description);

					imported++;

				} else if (type.equalsIgnoreCase("Payment-out")) {

					int partyId = dbHelper.getOrCreatePartyIdBulk(db, partyName);
					double payAmount = paid > 0 ? paid : amount;

					dbHelper.insertPaymentBulk(
						db, DatabaseHelper.PAYMENT_OUT, partyId, date, time, payAmount, description);

					imported++;

				} else if (type.equalsIgnoreCase("Expense")) {

					String expenseItem = category.length() > 0 ? category
						: (description.length() > 0 ? description : "Imported Expense");

					Integer expensePartyId = partyName != null && partyName.trim().length() > 0
						? dbHelper.getOrCreatePartyIdBulk(db, partyName)
						: null;

					dbHelper.insertExpenseBulk(db, expenseItem, date, time, amount, description, expensePartyId);

					imported++;

				} else if (type.equalsIgnoreCase("Party To Party [Paid]")) {

					// A party-to-party transfer paid out is, from this
					// party's side, just a Payment Out.
					int partyId = dbHelper.getOrCreatePartyIdBulk(db, partyName);
					double payAmount = paid > 0 ? paid : amount;

					dbHelper.insertPaymentBulk(
						db, DatabaseHelper.PAYMENT_OUT, partyId, date, time, payAmount, description);

					imported++;

				} else if (type.equalsIgnoreCase("Party To Party [Rcvd]")) {

					// A party-to-party transfer received is, from this
					// party's side, just a Payment In.
					int partyId = dbHelper.getOrCreatePartyIdBulk(db, partyName);
					double payAmount = received > 0 ? received : amount;

					dbHelper.insertPaymentBulk(
						db, DatabaseHelper.PAYMENT_IN, partyId, date, time, payAmount, description);

					imported++;

				} else {

					skipped++;

					SkippedRow sr = new SkippedRow();
					sr.file = fileLabel;
					sr.rowNumber = String.valueOf(row + 1);
					sr.type = type;
					sr.date = date;
					sr.party = partyName;
					sr.amount = amountStr;
					sr.reason = "Unsupported transaction type: "
						+ (type.length() > 0 ? type : "(blank)");
					skippedOut.add(sr);

					continue;
				}

				dbHelper.markImportKeyUsedBulk(db, importKey);
			}

			dbHelper.endTransaction(true);
			transactionStarted = false;

			HashMap<String, Integer> result = new HashMap<String, Integer>();
			result.put("imported", imported);
			result.put("skipped", skipped);
			result.put("duplicates", duplicates);
			result.put("items", itemsImported);

			return result;

		} catch (Exception e) {

			if (transactionStarted && dbHelper != null) {
				dbHelper.endTransaction(false);
				transactionStarted = false;
			}

			throw e;

		} finally {

			if (workbook != null) {
				workbook.close();
			}

			try {
				inputStream.close();
			} catch (Exception ignoreClose) {
				// already reading/parsing failed or finished -
				// nothing useful to do here
			}
		}
	}

	// =====================
	// Reads the "Item Details" sheet of an "All Transactions" export (if
	// the workbook has one - older exports may not) and buckets each line
	// into either salesItemsByKey or purchaseItemQueueByKey:
	//
	//  - Sale lines carry an "Invoice No." that matches the Ref No. of
	//    their Sale transaction, so they're keyed by date+party+invoice
	//    and grouped into a list (a sale can have several item lines).
	//
	//  - Purchase lines have no such reference. They're keyed by just
	//    date+party and kept in a queue, in the order they appear in the
	//    sheet, so pickPurchaseItems() can hand them out to each purchase
	//    transaction for that date+party in turn.
	// =====================
	private void loadItemDetails(
		Workbook workbook,
		HashMap<String, ArrayList<ItemRow>> salesItemsByKey,
		HashMap<String, ArrayDeque<ItemRow>> purchaseItemQueueByKey) {

		Sheet sheet = workbook.getSheet("Item Details");

		if (sheet == null) {
			// No item-level breakdown in this file - transactions will
			// still import fine, just without line items.
			return;
		}

		int rows = sheet.getRows();
		int columns = sheet.getColumns();

		if (rows == 0 || columns == 0) {
			return;
		}

		int headerRow = -1;

		for (int r = 0; r < rows; r++) {

			String c0 = sheet.getCell(0, r).getContents().trim();
			String c1 = columns > 1 ? sheet.getCell(1, r).getContents().trim() : "";

			if (c0.equalsIgnoreCase("Date") && c1.equalsIgnoreCase("Party Name")) {
				headerRow = r;
				break;
			}
		}

		if (headerRow == -1) {
			return;
		}

		HashMap<String, Integer> colIndex = new HashMap<String, Integer>();

		for (int c = 0; c < columns; c++) {

			String name = sheet.getCell(c, headerRow).getContents().trim();

			if (name.length() > 0) {
				colIndex.put(name, c);
			}
		}

		if (!colIndex.containsKey("Date")
			|| !colIndex.containsKey("Party Name")
			|| !colIndex.containsKey("Item Name")) {
			// Doesn't look like an Item Details sheet we understand -
			// skip it rather than guessing.
			return;
		}

		for (int row = headerRow + 1; row < rows; row++) {

			Integer dateCol = colIndex.get("Date");
			String date = dateCol != null ? getIsoDate(sheet, dateCol, row) : "";

			if (date.length() == 0) {
				continue;
			}

			String party = getCell(sheet, colIndex, "Party Name", row);
			String invoiceNo = getCell(sheet, colIndex, "Invoice No.", row);
			String itemName = getCell(sheet, colIndex, "Item Name", row);
			String itemCode = getCell(sheet, colIndex, "Item code", row);
			String qtyStr = getCell(sheet, colIndex, "Quantity", row);
			String priceStr = getCell(sheet, colIndex, "Price/Unit", row);
			String amountStr = getCell(sheet, colIndex, "Amount", row);

			if (itemName.length() == 0 && itemCode.length() == 0) {
				continue;
			}

			ItemRow item = new ItemRow();
			item.code = itemCode;
			item.name = itemName;
			item.quantity = parseAmount(qtyStr);
			item.price = parseAmount(priceStr);
			item.amount = parseAmount(amountStr);

			if (item.quantity == 0) {
				item.quantity = 1;
			}

			if (invoiceNo.length() > 0) {

				String key = date + "|" + party + "|" + invoiceNo;

				ArrayList<ItemRow> list = salesItemsByKey.get(key);

				if (list == null) {
					list = new ArrayList<ItemRow>();
					salesItemsByKey.put(key, list);
				}

				list.add(item);

			} else {

				String key = date + "|" + party;

				ArrayDeque<ItemRow> queue = purchaseItemQueueByKey.get(key);

				if (queue == null) {
					queue = new ArrayDeque<ItemRow>();
					purchaseItemQueueByKey.put(key, queue);
				}

				queue.add(item);
			}
		}
	}

	// =====================
	// Purchases in the "All Transactions" sheet have no reference number,
	// so there's no direct way to tell which Item Details lines belong to
	// which purchase when the same date+party has several purchases (a
	// common case - each item is often recorded as its own purchase).
	//
	// Since both sheets are written in the same order, this hands out
	// items from the front of that date+party's queue, accumulating their
	// amounts, until the running total reaches this purchase's amount -
	// then leaves the rest in the queue for the next purchase in the same
	// group. A small tolerance absorbs rounding in the source data.
	// =====================
	private ArrayList<ItemRow> pickPurchaseItems(
		ArrayDeque<ItemRow> queue,
		double target) {

		ArrayList<ItemRow> chosen = new ArrayList<ItemRow>();

		if (queue == null || queue.isEmpty()) {
			return chosen;
		}

		double runningSum = 0;
		double tolerance = 0.01;

		while (!queue.isEmpty()) {

			if (!chosen.isEmpty() && runningSum >= target - tolerance) {
				break;
			}

			ItemRow next = queue.poll();
			chosen.add(next);
			runningSum += next.amount;
		}

		return chosen;
	}

	private String getIsoDate(Sheet sheet, int col, int row) {

		Cell cell = sheet.getCell(col, row);

		if (cell.getType() == CellType.DATE
			|| cell.getType() == CellType.DATE_FORMULA) {

			java.util.Date d = ((DateCell) cell).getDate();

			return new java.text.SimpleDateFormat(
				"yyyy-MM-dd",
				java.util.Locale.getDefault()
			).format(d);
		}

		// Not a real date-typed cell - fall back to parsing the text
		// (covers files where the date column is plain text like
		// "01/08/2024").
		return convertDate(cell.getContents().trim());
	}

	private String getCell(
		Sheet sheet,
		HashMap<String, Integer> colIndex,
		String columnName,
		int row) {

		Integer col = colIndex.get(columnName);

		if (col == null) {
			return "";
		}

		return sheet.getCell(col, row).getContents().trim();
	}

	private double parseAmount(String value) {

		if (value == null || value.trim().length() == 0) {
			return 0;
		}

		try {
			return Double.parseDouble(value.trim());
		} catch (Exception e) {
			return 0;
		}
	}

	private String convertDate(String date) {

		if (date == null) {
			return "";
		}

		date = date.trim();

		if (date.length() == 0) {
			return "";
		}

		try {

			java.text.SimpleDateFormat oldFormat =
                new java.text.SimpleDateFormat(
				"dd/MM/yyyy",
				java.util.Locale.getDefault());

			java.text.SimpleDateFormat newFormat =
                new java.text.SimpleDateFormat(
				"yyyy-MM-dd",
				java.util.Locale.getDefault());

			return newFormat.format(oldFormat.parse(date));

		} catch (Exception e) {

			return date;

		}
	}

	// =====================
	// Safely opens an Excel workbook from a content Uri's InputStream.
	//
	// Some content providers (cloud storage apps, some file pickers) can
	// hand back a stream that is empty or only partially materialized,
	// which makes the jxl library fail with a confusing low-level error
	// like "jxl.read.biff.BiffException: The input file was not found"
	// even though the picked file itself is fine. Reading the whole file
	// into memory first lets us detect and clearly report the common
	// causes (empty/undownloaded file, or a modern .xlsx file saved with
	// an .xls name - jxl only understands the older Excel 97-2003 format)
	// instead of surfacing that raw exception to the user.
	// =====================
	private Workbook openWorkbook(InputStream inputStream) throws Exception {

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int read;

		while ((read = inputStream.read(chunk)) != -1) {
			buffer.write(chunk, 0, read);
		}

		byte[] bytes = buffer.toByteArray();

		if (bytes.length == 0) {
			throw new Exception(
				"This file is empty or could not be fully read " +
				"(this can happen with cloud-storage files that " +
				"aren't fully downloaded yet). Please try again, or " +
				"re-export the file.");
		}

		// .xlsx/.xlsm files are zip archives and start with the "PK"
		// signature. jxl can only read the older .xls (BIFF/OLE2) format,
		// so give a clear, actionable message instead of letting jxl fail
		// with a cryptic error.
		if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
			throw new Exception(
				"This looks like an .xlsx file. Please re-save it as " +
				"an Excel 97-2003 (.xls) file and try again.");
		}

		try {

			return Workbook.getWorkbook(new ByteArrayInputStream(bytes));

		} catch (BiffException be) {

			throw new Exception(
				"This doesn't look like a valid Excel (.xls) file - " +
				"please re-export it and try again.");
		}
	}

}

