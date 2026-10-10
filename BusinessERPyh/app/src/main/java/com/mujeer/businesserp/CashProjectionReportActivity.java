package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Today's real cash balance projected across a user-picked period - see
// DatabaseHelper.getCashProjection() for exactly what's factored in
// (dues/bills already due, recurring expenses expected in the window,
// and separately, what restocking everything on the Reorder List would
// cost). Unlike Cash Flow Forecast (a fixed day-by-day list from a
// preset horizon), this is a single from/to summary over any period the
// user chooses.
// =====================
public class CashProjectionReportActivity extends Activity {

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	private EditText et_from_date;
	private EditText et_to_date;

	private CheckBox cb_include_sales;
	private CheckBox cb_include_expenses;
	private CheckBox cb_include_reorder;

	private TextView tv_starting_cash;
	private TextView tv_dues_in;
	private TextView tv_estimated_sales;
	private TextView tv_bills_out;
	private TextView tv_recurring_out;
	private TextView tv_projected_cash;
	private TextView tv_reorder_cost;
	private TextView tv_projected_after_reorder;

	private DatabaseHelper db;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cash_projection_report_activity);

		setTitle("Cash Projection");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Cash Projection",
			"Today's cash balance projected across whatever period you pick - factoring in Sales/Purchases already due in that window and recurring expenses expected to fall in it - plus, separately, what restocking everything currently on the Reorder List would cost, since that's not committed yet."
		);

		et_from_date = findViewById(R.id.et_from_date);
		et_to_date = findViewById(R.id.et_to_date);

		cb_include_sales = findViewById(R.id.cb_include_sales);
		cb_include_expenses = findViewById(R.id.cb_include_expenses);
		cb_include_reorder = findViewById(R.id.cb_include_reorder);

		tv_starting_cash = findViewById(R.id.tv_starting_cash);
		tv_dues_in = findViewById(R.id.tv_dues_in);
		tv_estimated_sales = findViewById(R.id.tv_estimated_sales);
		tv_bills_out = findViewById(R.id.tv_bills_out);
		tv_recurring_out = findViewById(R.id.tv_recurring_out);
		tv_projected_cash = findViewById(R.id.tv_projected_cash);
		tv_reorder_cost = findViewById(R.id.tv_reorder_cost);
		tv_projected_after_reorder = findViewById(R.id.tv_projected_after_reorder);

		db = new DatabaseHelper(this);

		Calendar today = Calendar.getInstance();
		et_from_date.setText(dateFormat.format(today.getTime()));

		Calendar in30Days = Calendar.getInstance();
		in30Days.add(Calendar.DAY_OF_MONTH, 30);
		et_to_date.setText(dateFormat.format(in30Days.getTime()));

		et_from_date.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_from_date);
				}
			});

		et_to_date.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_to_date);
				}
			});

		CompoundButton.OnCheckedChangeListener checkboxListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					loadProjection();
				}
			};

		cb_include_sales.setOnCheckedChangeListener(checkboxListener);
		cb_include_expenses.setOnCheckedChangeListener(checkboxListener);
		cb_include_reorder.setOnCheckedChangeListener(checkboxListener);

		loadProjection();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadProjection();
	}

	private void showDatePicker(final EditText target) {

		Calendar calendar = Calendar.getInstance();

		try {
			calendar.setTime(dateFormat.parse(target.getText().toString()));
		} catch (Exception e) {
		}

		new DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new DatePickerDialog.OnDateSetListener() {
				@Override
				public void onDateSet(
					android.widget.DatePicker view, int year, int month, int dayOfMonth) {

					target.setText(
						String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
					);

					loadProjection();
				}
			},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void loadProjection() {

		final long myGeneration = ++loadGeneration;
		final String fromDate = et_from_date.getText().toString();
		final String toDate = et_to_date.getText().toString();
		final android.content.Context appContext = getApplicationContext();
		final boolean includeSales = cb_include_sales.isChecked();
		final boolean includeExpenses = cb_include_expenses.isChecked();
		final boolean includeReorder = cb_include_reorder.isChecked();

		if (fromDate.isEmpty() || toDate.isEmpty() || fromDate.compareTo(toDate) > 0) {
			return;
		}

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> result = db.getCashProjection(
						appContext, fromDate, toDate, includeSales, includeExpenses, includeReorder
					);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyResult(result);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyResult(HashMap<String, Object> result) {

		double startingCash = (Double) result.get("starting_cash");
		double duesIn = (Double) result.get("dues_in");
		double estimatedSales = (Double) result.get("estimated_sales_revenue");
		double billsOut = (Double) result.get("bills_out");
		double recurringOut = (Double) result.get("recurring_expenses_out");
		double projectedCash = (Double) result.get("projected_cash");
		double reorderCost = (Double) result.get("reorder_cost_estimate");
		double projectedAfterReorder = (Double) result.get("projected_cash_after_reorder");

		tv_starting_cash.setText(AmountFormat.format(startingCash));
		tv_dues_in.setText(AmountFormat.format(duesIn));
		tv_estimated_sales.setText(AmountFormat.format(estimatedSales));
		tv_bills_out.setText(AmountFormat.format(billsOut));
		tv_recurring_out.setText(AmountFormat.format(recurringOut));
		tv_projected_cash.setText(AmountFormat.format(projectedCash));

		tv_projected_cash.setTextColor(
			getResources().getColor(projectedCash < 0 ? R.color.danger : R.color.text_primary)
		);

		tv_reorder_cost.setText("Estimated cost: " + AmountFormat.format(reorderCost));
		tv_projected_after_reorder.setText(
			"Projected cash after: " + AmountFormat.format(projectedAfterReorder)
		);

		// Unchecking a category still shows its own raw figure above
		// (so toggling it shows the swing) but dims it, since it no
		// longer counts toward the totals - see
		// DatabaseHelper.getCashProjection()'s own comment.
		int includedColor = getResources().getColor(R.color.mod_sales);
		int excludedColor = getResources().getColor(R.color.text_hint);

		tv_dues_in.setTextColor(cb_include_sales.isChecked() ? includedColor : excludedColor);
		tv_estimated_sales.setTextColor(cb_include_sales.isChecked() ? includedColor : excludedColor);

		int expenseIncludedColor = getResources().getColor(R.color.mod_purchase);
		int expenseIncludedColor2 = getResources().getColor(R.color.mod_expenses);

		tv_bills_out.setTextColor(cb_include_expenses.isChecked() ? expenseIncludedColor : excludedColor);
		tv_recurring_out.setTextColor(
			cb_include_expenses.isChecked() ? expenseIncludedColor2 : excludedColor
		);

		tv_reorder_cost.setTextColor(
			cb_include_reorder.isChecked() ?
			getResources().getColor(R.color.text_secondary) : excludedColor
		);

		tv_projected_after_reorder.setTextColor(
			getResources().getColor(projectedAfterReorder < 0 ? R.color.danger : R.color.text_primary)
		);
	}
}
