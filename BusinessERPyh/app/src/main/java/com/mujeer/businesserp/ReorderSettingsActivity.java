package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

// Every input ReorderListActivity's suggestion formula uses - see
// ReorderSettings for the defaults and why they live in SharedPreferences
// rather than a database table.
public class ReorderSettingsActivity extends Activity {

	private EditText et_velocity_window_days;
	private EditText et_safety_stock_percent;
	private EditText et_lead_time_days;
	private EditText et_min_order_qty;
	private EditText et_max_order_qty;
	private CheckBox cb_cash_aware;
	private CheckBox cb_manual_cash_override;
	private EditText et_manual_cash_amount;

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

		Button btn_save = findViewById(R.id.btn_save_reorder_settings);
		Button btn_reset = findViewById(R.id.btn_reset_reorder_settings);

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

			if (velocityWindowDays <= 0 || leadTimeDays <= 0) {

				Toast.makeText(
					this, "Window and lead time must be greater than 0", Toast.LENGTH_SHORT
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

		Toast.makeText(this, "Reset - tap Save to keep these", Toast.LENGTH_SHORT).show();
	}
}
