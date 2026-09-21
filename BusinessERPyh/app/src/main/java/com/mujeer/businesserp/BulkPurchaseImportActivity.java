package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

// =====================
// Bulk Purchase Import
//
// Reached from the Import screen's "Bulk Purchase Import" button
// (Importexcelactivity), and also from Transactionviewactivity's
// "Bulk Import" button when viewing an existing Purchase.
//
// Workflow: the user picks an Excel (.xls) file where each row is one
// purchase item line:
//
//   Code, Type, Sole, Upper, Design, Color, Gender, Size,
//   Quantity, Purchase Price, Sale Price
//
// The ENTIRE file is validated before anything happens. If even one
// row has a problem, nothing is imported - every error found (row
// number, column, description) is shown in a list instead. Only when
// every row passes does this screen open Transactioneditactivity in
// purchase add mode with every row already added to the item list, so
// the user can pick the supplier, review, edit or remove rows, and
// only then actually save the purchase. Nothing is written to the
// database by this screen itself - not even the catalog products for
// the rows, which are created (if they don't already exist) by
// Transactioneditactivity.savePurchase() only once the user taps Save.
// =====================
public class BulkPurchaseImportActivity extends Activity {

	private Button btn_pick_file;
	private TextView tv_status;

	private static final int REQUEST_PICK_EXCEL = 4001;

	// Column names as they must appear in the Excel header row (row 1).
	// Matching is case-insensitive and trims surrounding whitespace, so
	// "color " (as in the sample template) still matches "Color".
	private static final String[] REQUIRED_COLUMNS = {
		"Code", "Type", "Sole", "Upper", "Design",
		"Color", "Gender", "Size",
		"Quantity", "Purchase Price", "Sale Price"
	};

	// =====================
	// One validation problem found while checking the file. Never
	// written to the database - only ever shown to the user so they can
	// fix the source file and re-import.
	// =====================
	public static class ImportError {

		int rowNumber;
		String column;
		String message;

		String toDisplayString() {

			return "Row " + rowNumber +
				(column != null && column.length() > 0 ? " - " + column : "") +
				"\n" + message;
		}
	}

	// One already-validated Excel row, ready to be written once the
	// whole file has been confirmed error-free.
	private static class PurchaseImportRow {

		int rowNumber;
		String code;
		String type;
		String sole;
		String upper;
		String design;
		String color;
		String gender;
		String size;
		double quantity;
		double purchasePrice;
		double salePrice;
	}

	// Holds the previous run's validation errors so
	// BulkPurchaseImportErrorsActivity can display them. In-memory only,
	// same convention as Importexcelactivity.lastSkippedRows - this is
	// just a "what just happened" report for right after an attempt.
	static ArrayList<ImportError> lastErrors = new ArrayList<ImportError>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bulk_purchase_import);

		btn_pick_file = (Button) findViewById(R.id.btn_bulk_import_pick_file);
		tv_status = (TextView) findViewById(R.id.tv_bulk_import_status);

		btn_pick_file.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					openExcelPicker();
				}
			});
	}

	private void openExcelPicker() {

		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

		intent.addCategory(Intent.CATEGORY_OPENABLE);

		intent.setType("*/*");

		intent.putExtra(
			Intent.EXTRA_MIME_TYPES,
			new String[]{"application/vnd.ms-excel"});

		startActivityForResult(intent, REQUEST_PICK_EXCEL);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode != REQUEST_PICK_EXCEL
			|| resultCode != RESULT_OK
			|| data == null
			|| data.getData() == null) {

			return;
		}

		final Uri uri = data.getData();

		btn_pick_file.setEnabled(false);
		tv_status.setText("Reading file...");

		new Thread(new Runnable() {
				@Override
				public void run() {
					runImport(uri);
				}
			}).start();
	}

	// =====================
	// Reads the whole workbook and validates every row. Any single
	// problem anywhere (a bad row, a missing column) means NOTHING is
	// imported - the errors are shown instead (showErrors()). Only if
	// the entire file is valid does this open Transactioneditactivity
	// in purchase add mode with every row already added
	// (openTransactionEditActivity()) - nothing is written to the
	// database by this method itself.
	// =====================
	private void runImport(Uri uri) {

		InputStream inputStream = null;
		Workbook workbook = null;

		try {

			inputStream = getContentResolver().openInputStream(uri);

			if (inputStream == null) {
				throw new Exception(
					"Could not open the selected file " +
					"(no permission, or the file was moved/deleted).");
			}

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

			HashMap<String, Integer> colIndex = new HashMap<String, Integer>();

			for (int c = 0; c < columns; c++) {

				String name = sheet.getCell(c, 0).getContents().trim();

				if (name.length() > 0) {
					colIndex.put(normalizeHeader(name), c);
				}
			}

			ArrayList<ImportError> errors = new ArrayList<ImportError>();

			for (String required : REQUIRED_COLUMNS) {

				if (!colIndex.containsKey(normalizeHeader(required))) {

					errors.add(buildError(1, required, "Missing required column: " + required));
				}
			}

			ArrayList<PurchaseImportRow> validRows = new ArrayList<PurchaseImportRow>();

			if (errors.isEmpty()) {

				boolean anyDataRow = false;

				for (int row = 1; row < rows; row++) {

					boolean emptyRow = true;

					for (int c = 0; c < columns; c++) {

						if (sheet.getCell(c, row).getContents().trim().length() > 0) {
							emptyRow = false;
							break;
						}
					}

					if (emptyRow) {
						continue;
					}

					anyDataRow = true;

					int humanRow = row + 1;

					String code = getCell(sheet, colIndex, "Code", row);
					String type = getCell(sheet, colIndex, "Type", row);
					String sole = getCell(sheet, colIndex, "Sole", row);
					String upper = getCell(sheet, colIndex, "Upper", row);
					String design = getCell(sheet, colIndex, "Design", row);
					String color = getCell(sheet, colIndex, "Color", row);
					String gender = getCell(sheet, colIndex, "Gender", row);
					String size = getCell(sheet, colIndex, "Size", row);
					String qtyStr = getCell(sheet, colIndex, "Quantity", row);
					String purchasePriceStr = getCell(sheet, colIndex, "Purchase Price", row);
					String salePriceStr = getCell(sheet, colIndex, "Sale Price", row);

					int errorsBefore = errors.size();

					requireField(errors, humanRow, "Type", type);
					requireField(errors, humanRow, "Sole", sole);
					requireField(errors, humanRow, "Upper", upper);
					requireField(errors, humanRow, "Design", design);
					requireField(errors, humanRow, "Color", color);
					requireField(errors, humanRow, "Gender", gender);
					requireField(errors, humanRow, "Size", size);

					Double quantity = requireNumber(errors, humanRow, "Quantity", qtyStr);
					Double purchasePrice = requireNumber(errors, humanRow, "Purchase Price", purchasePriceStr);
					Double salePrice = requireNumber(errors, humanRow, "Sale Price", salePriceStr);

					if (quantity != null && quantity.doubleValue() <= 0) {
						errors.add(buildError(humanRow, "Quantity", "Quantity must be greater than 0"));
						quantity = null;
					}

					if (purchasePrice != null && purchasePrice.doubleValue() < 0) {
						errors.add(buildError(humanRow, "Purchase Price", "Purchase Price cannot be negative"));
						purchasePrice = null;
					}

					if (salePrice != null && salePrice.doubleValue() < 0) {
						errors.add(buildError(humanRow, "Sale Price", "Sale Price cannot be negative"));
						salePrice = null;
					}

					boolean rowHasErrors = errors.size() > errorsBefore;

					if (rowHasErrors) {
						continue;
					}

					PurchaseImportRow r = new PurchaseImportRow();
					r.rowNumber = humanRow;
					r.code = code;
					r.type = type;
					r.sole = sole;
					r.upper = upper;
					r.design = design;
					r.color = color;
					r.gender = gender;
					r.size = size;
					r.quantity = quantity.doubleValue();
					r.purchasePrice = purchasePrice.doubleValue();
					r.salePrice = salePrice.doubleValue();

					validRows.add(r);
				}

				if (!anyDataRow) {
					throw new Exception("No data rows found in this file (all rows were empty).");
				}
			}

			if (!errors.isEmpty()) {

				showErrors(errors);
				return;
			}

			// Every row in the file is valid. Nothing is written to the
			// database here - build the item list Transactioneditactivity
			// expects and hand off to it (purchase add mode) so the user
			// picks the supplier, reviews the items, and only creates
			// anything by tapping Save there.
			final ArrayList<HashMap<String, Object>> prefillItems =
				buildPrefillItems(validRows);

			runOnUiThread(new Runnable() {
					@Override
					public void run() {

						openTransactionEditActivity(prefillItems);
					}
				});

		} catch (final Exception e) {

			runOnUiThread(new Runnable() {
					@Override
					public void run() {

						tv_status.setText(
							"Import failed - nothing was imported.\n\n" + e.getMessage());

						btn_pick_file.setEnabled(true);
					}
				});

		} finally {

			if (workbook != null) {
				workbook.close();
			}

			if (inputStream != null) {

				try {
					inputStream.close();
				} catch (Exception ignoreClose) {
					// already reading/parsing failed or finished -
					// nothing useful to do here
				}
			}
		}
	}

	private void showErrors(ArrayList<ImportError> errors) {

		BulkPurchaseImportActivity.lastErrors = errors;

		final int errorCount = errors.size();

		runOnUiThread(new Runnable() {
				@Override
				public void run() {

					tv_status.setText(
						errorCount + " validation error(s) found - " +
						"nothing was imported.");

					btn_pick_file.setEnabled(true);

					startActivity(new Intent(
						BulkPurchaseImportActivity.this,
						BulkPurchaseImportErrorsActivity.class
					));
				}
			});
	}

	// =====================
	// Turns every validated Excel row into the HashMap shape
	// Transactioneditactivity's transactionItemList expects (see
	// Transactioneditactivity.tryAddCurrentItemToTransaction()), except
	// "item_id" is deliberately left unset: product creation is
	// deferred until the user taps Save there. "code" and
	// "name_without_code" are carried along instead, so
	// Transactioneditactivity.savePurchase() can resolve/create the
	// real catalog item at that point (DatabaseHelper.
	// getOrCreateProductForPurchaseImport()) - same code/name/price
	// resolution the old bulk importer used to do immediately, just
	// deferred. "sale_price" only matters if that resolution ends up
	// creating a brand new product, to seed its initial sale price.
	// =====================
	private ArrayList<HashMap<String, Object>> buildPrefillItems(
		ArrayList<PurchaseImportRow> validRows) {

		ArrayList<HashMap<String, Object>> items =
			new ArrayList<HashMap<String, Object>>();

		for (PurchaseImportRow r : validRows) {

			String nameWithoutCode =
				"Shoe " + r.type + " " + r.sole + " " + r.upper + " " +
				r.design + " " + r.color + " " + r.gender + " " + r.size;

			String displayName =
				r.code.length() > 0 ?
				nameWithoutCode + " " + r.code :
				nameWithoutCode + " (code auto-generated on save)";

			HashMap<String, Object> map = new HashMap<String, Object>();

			// Left blank (not a placeholder string) when the row had no
			// code, so DatabaseHelper.getOrCreateProductForPurchaseImport()
			// still auto-generates a real code for it at Save time,
			// exactly as it would have during the old direct-write import.
			map.put("code", r.code);
			map.put("name_without_code", nameWithoutCode);
			map.put("name", displayName);
			map.put("quantity", Double.valueOf(r.quantity));
			map.put("purchase_price", Double.valueOf(r.purchasePrice));
			map.put("sale_price", Double.valueOf(r.salePrice));
			map.put("total", Double.valueOf(r.quantity * r.purchasePrice));

			items.add(map);
		}

		return items;
	}

	// Opens Transactioneditactivity in purchase add mode with every row
	// from the import already in the item list, and finishes this
	// screen - Back from the edit screen goes to wherever this screen
	// was opened from (e.g. the dashboard), not back here.
	private void openTransactionEditActivity(
		ArrayList<HashMap<String, Object>> prefillItems) {

		Intent intent = new Intent(this, Transactioneditactivity.class);

		intent.putExtra("transaction_type", 0); // TYPE_PURCHASE
		intent.putExtra("is_edit", false);
		intent.putExtra("prefill_items", prefillItems);

		startActivity(intent);
		finish();
	}

	private ImportError buildError(int rowNumber, String column, String message) {

		ImportError error = new ImportError();
		error.rowNumber = rowNumber;
		error.column = column;
		error.message = message;

		return error;
	}

	// Adds an error if the field is blank. Returns true if the field is
	// present (non-blank).
	private boolean requireField(
		ArrayList<ImportError> errors,
		int rowNumber,
		String column,
		String value) {

		if (value == null || value.trim().length() == 0) {

			errors.add(buildError(rowNumber, column, column + " is required"));
			return false;
		}

		return true;
	}

	// Parses value as a number, adding a validation error and returning
	// null if it is blank or not a valid number.
	private Double requireNumber(
		ArrayList<ImportError> errors,
		int rowNumber,
		String column,
		String value) {

		if (value == null || value.trim().length() == 0) {

			errors.add(buildError(rowNumber, column, column + " is required"));
			return null;
		}

		try {

			return Double.valueOf(Double.parseDouble(value.trim()));

		} catch (Exception e) {

			errors.add(buildError(rowNumber, column, "Invalid numeric value: " + value.trim()));
			return null;
		}
	}

	private String normalizeHeader(String name) {

		return name == null ? "" : name.trim().toLowerCase(Locale.US);
	}

	private String getCell(
		Sheet sheet,
		HashMap<String, Integer> colIndex,
		String columnName,
		int row) {

		Integer col = colIndex.get(normalizeHeader(columnName));

		if (col == null) {
			return "";
		}

		return sheet.getCell(col, row).getContents().trim();
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
	// instead of surfacing that raw exception to the user. (Same
	// approach as Importexcelactivity.openWorkbook().)
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
