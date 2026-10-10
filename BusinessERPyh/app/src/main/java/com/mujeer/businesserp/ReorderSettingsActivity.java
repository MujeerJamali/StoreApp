package com.mujeer.businesserp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

// Every input ReorderListActivity's suggestion formula uses, plus the
// non-shoe turnover multiplier Slow-Moving Stock/Discount This Week/
// Dead Stock Aging share - see ReorderSettings for the defaults and
// why they live in SharedPreferences rather than a database table.
public class ReorderSettingsActivity extends Activity {

	private static final int REQUEST_SAVE_REORDER_HISTORY = 7001;

	private EditText et_velocity_window_days;
	private EditText et_safety_stock_percent;
	private EditText et_lead_time_days;
	private EditText et_min_order_qty;
	private EditText et_max_order_qty;
	private CheckBox cb_cash_aware;
	private CheckBox cb_manual_cash_override;
	private EditText et_manual_cash_amount;
	private EditText et_non_shoe_turnover_multiplier;

	// The .csv built in cache storage by exportReorderHistory(), waiting
	// to be copied to wherever the user picks - see writeCsvToDestination().
	private File pendingCsvFile;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reorder_settings_activity);

		setTitle("Reorder Settings");

		et_velocity_window_days = findViewById(R.id.et_velocity_window_days);
		et_safety_stock_percent = findViewById(R.id.et_safety_stock_percent);
		et_lead_time_days = findViewById(R.id.et_lead_time_days);
		et_min_order_qty = findViewById(R.id.et_min_order_qty);
		et_max_order_qty = findViewById(R.id.et_max_order_qty);
		cb_cash_aware = findViewById(R.id.cb_cash_aware);
		cb_manual_cash_override = findViewById(R.id.cb_manual_cash_override);
		et_manual_cash_amount = findViewById(R.id.et_manual_cash_amount);
		et_non_shoe_turnover_multiplier = findViewById(R.id.et_non_shoe_turnover_multiplier);

		Button btn_save = findViewById(R.id.btn_save_reorder_settings);
		Button btn_reset = findViewById(R.id.btn_reset_reorder_settings);
		Button btn_view_reorder_history = findViewById(R.id.btn_view_reorder_history);
		Button btn_export_reorder_history = findViewById(R.id.btn_export_reorder_history);
		Button btn_seasonal_calendar = findViewById(R.id.btn_seasonal_calendar);
		Button btn_reorder_threshold_checklist = findViewById(R.id.btn_reorder_threshold_checklist);

		loadCurrentValues();

		btn_save.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					save();
				}
			}
		);

		btn_reset.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					resetToDefaults();
				}
			}
		);

		btn_view_reorder_history.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						ReorderSettingsActivity.this, ReorderLearningHistoryActivity.class));
				}
			}
		);

		btn_export_reorder_history.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					exportReorderHistory();
				}
			}
		);

		btn_seasonal_calendar.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						ReorderSettingsActivity.this, SeasonalCalendarActivity.class));
				}
			}
		);

		btn_reorder_threshold_checklist.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(
						ReorderSettingsActivity.this, ReorderThresholdChecklistActivity.class));
				}
			}
		);
	}

	private void loadCurrentValues() {

		et_velocity_window_days.setText(
			String.valueOf(ReorderSettings.getVelocityWindowDays(this)));

		et_safety_stock_percent.setText(
			AmountFormat.formatPlain(ReorderSettings.getSafetyStockPercent(this)));

		et_lead_time_days.setText(
			String.valueOf(ReorderSettings.getDefaultLeadTimeDays(this)));

		et_min_order_qty.setText(
			AmountFormat.formatPlain(ReorderSettings.getMinOrderQty(this)));

		et_max_order_qty.setText(
			AmountFormat.formatPlain(ReorderSettings.getMaxOrderQty(this)));

		cb_cash_aware.setChecked(ReorderSettings.isCashAwareEnabled(this));
		cb_manual_cash_override.setChecked(ReorderSettings.isManualCashOverrideEnabled(this));

		et_manual_cash_amount.setText(
			AmountFormat.formatPlain(ReorderSettings.getManualCashOverrideAmount(this)));

		et_non_shoe_turnover_multiplier.setText(
			AmountFormat.formatPlain(ReorderSettings.getNonShoeTurnoverMultiplier(this)));
	}

	private void save() {

		try {

			int velocityWindowDays = Integer.parseInt(
				et_velocity_window_days.getText().toString().trim());

			double safetyStockPercent = Double.parseDouble(
				et_safety_stock_percent.getText().toString().trim());

			int leadTimeDays = Integer.parseInt(
				et_lead_time_days.getText().toString().trim());

			double minOrderQty = Double.parseDouble(
				et_min_order_qty.getText().toString().trim());

			double maxOrderQty = Double.parseDouble(
				et_max_order_qty.getText().toString().trim());

			double manualCashAmount = 0;

			String manualCashText = et_manual_cash_amount.getText().toString().trim();

			if (!manualCashText.isEmpty()) {
				manualCashAmount = Double.parseDouble(manualCashText);
			}

			double nonShoeTurnoverMultiplier = Double.parseDouble(
				et_non_shoe_turnover_multiplier.getText().toString().trim());

			if (velocityWindowDays <= 0 || leadTimeDays <= 0) {

				Toast.makeText(
					this, "Window and lead time must be greater than 0", Toast.LENGTH_SHORT
				).show();

				return;
			}

			if (nonShoeTurnoverMultiplier <= 0) {

				Toast.makeText(
					this, "Non-shoe turnover multiplier must be greater than 0", Toast.LENGTH_SHORT
				).show();

				return;
			}

			ReorderSettings.setVelocityWindowDays(this, velocityWindowDays);
			ReorderSettings.setSafetyStockPercent(this, safetyStockPercent);
			ReorderSettings.setDefaultLeadTimeDays(this, leadTimeDays);
			ReorderSettings.setMinOrderQty(this, minOrderQty);
			ReorderSettings.setMaxOrderQty(this, maxOrderQty);
			ReorderSettings.setCashAwareEnabled(this, cb_cash_aware.isChecked());
			ReorderSettings.setManualCashOverrideEnabled(this, cb_manual_cash_override.isChecked());
			ReorderSettings.setManualCashOverrideAmount(this, manualCashAmount);
			ReorderSettings.setNonShoeTurnoverMultiplier(this, nonShoeTurnoverMultiplier);

			Toast.makeText(this, "Reorder settings saved", Toast.LENGTH_SHORT).show();

			finish();

		} catch (NumberFormatException e) {

			Toast.makeText(this, "Check that every field is a valid number", Toast.LENGTH_SHORT)
				.show();
		}
	}

	private void resetToDefaults() {

		et_velocity_window_days.setText(
			String.valueOf(ReorderSettings.DEFAULT_VELOCITY_WINDOW_DAYS));

		et_safety_stock_percent.setText(
			AmountFormat.formatPlain(ReorderSettings.DEFAULT_SAFETY_STOCK_PERCENT));

		et_lead_time_days.setText(
			String.valueOf(ReorderSettings.DEFAULT_LEAD_TIME_DAYS));

		et_min_order_qty.setText(
			AmountFormat.formatPlain(ReorderSettings.DEFAULT_MIN_ORDER_QTY));

		et_max_order_qty.setText(
			AmountFormat.formatPlain(ReorderSettings.DEFAULT_MAX_ORDER_QTY));

		cb_cash_aware.setChecked(ReorderSettings.DEFAULT_CASH_AWARE_ENABLED);
		cb_manual_cash_override.setChecked(false);
		et_manual_cash_amount.setText("0");

		et_non_shoe_turnover_multiplier.setText(
			AmountFormat.formatPlain(ReorderSettings.DEFAULT_NON_SHOE_TURNOVER_MULTIPLIER));

		Toast.makeText(this, "Reset - tap Save to keep these", Toast.LENGTH_SHORT).show();
	}

	// =====================
	// REORDER LEARNING HISTORY EXPORT - writes the full
	// reorder_suggestion_log (see DatabaseHelper.
	// getReorderSuggestionLogForExport()) to a plain .csv, then hands
	// off to the system file picker to choose where it's saved, the
	// exact same pattern ExportVyaparActivity uses for its own manual
	// export (no storage permission needed, no new library - this is
	// plain text, not a real spreadsheet format). Device configuration/
	// automation memory, not business data, so this is deliberately
	// separate from the Vyapar backup round-trip.
	// =====================
	private void exportReorderHistory() {

		DatabaseHelper db = new DatabaseHelper(this);

		ArrayList<HashMap<String, Object>> rows = db.getReorderSuggestionLogForExport();

		if (rows.isEmpty()) {

			Toast.makeText(this, "No reorder history yet", Toast.LENGTH_SHORT).show();
			return;
		}

		StringBuilder csv = new StringBuilder();

		csv.append("Suggested Date,Item Code,Item Name,Variety,Suggested Qty,Outcome,Outcome Date\n");

		for (HashMap<String, Object> row : rows) {

			csv.append(csvField((String) row.get("suggested_date"))).append(",");
			csv.append(csvField((String) row.get("item_code"))).append(",");
			csv.append(csvField((String) row.get("item_name"))).append(",");
			csv.append(csvField((String) row.get("combo_label"))).append(",");
			csv.append(AmountFormat.formatPlain((Double) row.get("suggested_qty"))).append(",");
			csv.append(csvField((String) row.get("outcome"))).append(",");
			csv.append(csvField((String) row.get("outcome_date"))).append("\n");
		}

		try {

			pendingCsvFile = new File(getCacheDir(), "reorder_history_export.tmp");

			FileOutputStream out = new FileOutputStream(pendingCsvFile);
			out.write(csv.toString().getBytes("UTF-8"));
			out.close();

		} catch (Exception e) {

			Toast.makeText(this, "Export failed: " + e.toString(), Toast.LENGTH_SHORT).show();
			return;
		}

		String filename = "BusinessERP_reorder_history_" +
			new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) +
			".csv";

		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("text/csv");
		intent.putExtra(Intent.EXTRA_TITLE, filename);

		startActivityForResult(intent, REQUEST_SAVE_REORDER_HISTORY);
	}

	// Wraps a field in double quotes (doubling any embedded quote) only
	// when it actually needs it - a comma, quote, or newline in an item
	// name/outcome text, which would otherwise break the column split.
	private String csvField(String value) {

		if (value == null) {
			value = "";
		}

		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}

		return value;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode != REQUEST_SAVE_REORDER_HISTORY) {
			return;
		}

		if (resultCode != RESULT_OK || data == null || data.getData() == null) {

			Toast.makeText(this, "Save cancelled", Toast.LENGTH_SHORT).show();
			cleanupPendingCsv();
			return;
		}

		writeCsvToDestination(data.getData());
	}

	private void writeCsvToDestination(Uri destUri) {

		if (pendingCsvFile == null || !pendingCsvFile.exists()) {

			Toast.makeText(
				this, "Export failed: the file went missing before it could be saved",
				Toast.LENGTH_SHORT
			).show();

			return;
		}

		try {

			ContentResolver resolver = getContentResolver();
			OutputStream out = resolver.openOutputStream(destUri);

			if (out == null) {
				throw new Exception("Could not open the destination file for writing.");
			}

			FileInputStream in = new FileInputStream(pendingCsvFile);
			byte[] buffer = new byte[8192];
			int len;

			while ((len = in.read(buffer)) > 0) {
				out.write(buffer, 0, len);
			}

			in.close();
			out.close();

			Toast.makeText(this, "Reorder history exported", Toast.LENGTH_SHORT).show();

		} catch (Exception e) {

			Toast.makeText(this, "Export failed while saving: " + e.toString(), Toast.LENGTH_SHORT)
				.show();

		} finally {

			cleanupPendingCsv();
		}
	}

	private void cleanupPendingCsv() {

		if (pendingCsvFile != null && pendingCsvFile.exists()) {
			pendingCsvFile.delete();
		}

		pendingCsvFile = null;
	}
}
