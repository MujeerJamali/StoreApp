package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// One day's Sales/Purchases/Expenses/Payments totals plus how much that
// day actually moved the running cash balance (see
// DatabaseHelper.getDayCloseSummary()) - defaults to today, but any past
// day can be picked to review it after the fact. Every row taps through
// to that category's existing list, pre-filtered to this one date.
// =====================
public class DayCloseReportActivity extends Activity {

	private static final int TYPE_PURCHASE = 0;
	private static final int TYPE_SALE = 1;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	private EditText et_date;

	private TextView tv_sales_total;
	private TextView tv_sales_count;
	private TextView tv_purchases_total;
	private TextView tv_purchases_count;
	private TextView tv_expenses_total;
	private TextView tv_payments_in_total;
	private TextView tv_payments_out_total;
	private TextView tv_net_cash;
	private SimpleBarChartView chart_day_close;

	private View card_reorder_prep;
	private TextView tv_reorder_prep;

	private DatabaseHelper db;

	private String selectedDate;

	// A background result is only applied if it's still the most recent
	// request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.day_close_report_activity);

		setTitle("Day Close");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Day Close", "One day's Sales, Purchases, Expenses and Payments at a glance, plus how much that day actually moved the cash balance. Tap any row to see what's behind it."
		);

		et_date = findViewById(R.id.et_date);

		tv_sales_total = findViewById(R.id.tv_sales_total);
		tv_sales_count = findViewById(R.id.tv_sales_count);
		tv_purchases_total = findViewById(R.id.tv_purchases_total);
		tv_purchases_count = findViewById(R.id.tv_purchases_count);
		tv_expenses_total = findViewById(R.id.tv_expenses_total);
		tv_payments_in_total = findViewById(R.id.tv_payments_in_total);
		tv_payments_out_total = findViewById(R.id.tv_payments_out_total);
		tv_net_cash = findViewById(R.id.tv_net_cash);
		chart_day_close = findViewById(R.id.chart_day_close);

		card_reorder_prep = findViewById(R.id.card_reorder_prep);
		tv_reorder_prep = findViewById(R.id.tv_reorder_prep);

		findViewById(R.id.row_reorder_prep).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					startActivity(new Intent(DayCloseReportActivity.this, ReorderListActivity.class));
				}
			});

		db = new DatabaseHelper(this);

		selectedDate = dateFormat.format(new java.util.Date());
		et_date.setText(selectedDate);

		et_date.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker();
				}
			});

		findViewById(R.id.row_sales).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						DayCloseReportActivity.this, Transactionactivity.class
					);

					intent.putExtra("transaction_type", TYPE_SALE);
					intent.putExtra("from_date", selectedDate);
					intent.putExtra("to_date", selectedDate);

					startActivity(intent);
				}
			});

		findViewById(R.id.row_purchases).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						DayCloseReportActivity.this, Transactionactivity.class
					);

					intent.putExtra("transaction_type", TYPE_PURCHASE);
					intent.putExtra("from_date", selectedDate);
					intent.putExtra("to_date", selectedDate);

					startActivity(intent);
				}
			});

		findViewById(R.id.row_expenses).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						DayCloseReportActivity.this, Expensesactivity.class
					);

					intent.putExtra("date", selectedDate);

					startActivity(intent);
				}
			});

		findViewById(R.id.row_payments_in).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						DayCloseReportActivity.this, Paymentactivity.class
					);

					intent.putExtra("payment_type", DatabaseHelper.PAYMENT_IN);
					intent.putExtra("date", selectedDate);

					startActivity(intent);
				}
			});

		findViewById(R.id.row_payments_out).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						DayCloseReportActivity.this, Paymentactivity.class
					);

					intent.putExtra("payment_type", DatabaseHelper.PAYMENT_OUT);
					intent.putExtra("date", selectedDate);

					startActivity(intent);
				}
			});

		findViewById(R.id.row_net_cash).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						DayCloseReportActivity.this, CashActivity.class
					);

					intent.putExtra("date", selectedDate);

					startActivity(intent);
				}
			});

		loadSummary();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadSummary();
	}

	private void showDatePicker() {

		Calendar calendar = Calendar.getInstance();

		try {
			calendar.setTime(dateFormat.parse(selectedDate));
		} catch (Exception e) {
		}

		new DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new DatePickerDialog.OnDateSetListener() {
				@Override
				public void onDateSet(
					android.widget.DatePicker view, int year, int month, int dayOfMonth) {

					selectedDate = String.format(
						Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth
					);

					et_date.setText(selectedDate);

					loadSummary();
				}
			},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void loadSummary() {

		final long myGeneration = ++loadGeneration;
		final String date = selectedDate;

		final boolean isToday = date.equals(dateFormat.format(new java.util.Date()));

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> summary = db.getDayCloseSummary(date);

					final java.util.ArrayList<HashMap<String, Object>> reorderSuggestions =
						isToday ? db.getReorderSuggestions(getApplicationContext()) : null;

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applySummary(summary);
								applyReorderPrep(isToday, reorderSuggestions);
							}
						});
				}
			}).start();
	}

	// "Tomorrow's Reorder Prep" is only meaningful for today (it's a
	// forward-looking suggestion, not something to show when reviewing a
	// past day's close) - see DatabaseHelper.getReorderSuggestions().
	private void applyReorderPrep(
		boolean isToday, java.util.ArrayList<HashMap<String, Object>> suggestions) {

		if (!isToday) {

			card_reorder_prep.setVisibility(View.GONE);
			return;
		}

		card_reorder_prep.setVisibility(View.VISIBLE);

		int count = suggestions == null ? 0 : suggestions.size();

		tv_reorder_prep.setText(
			count == 0
			? "Nothing needs reordering right now"
			: count + (count == 1 ? " item worth restocking" : " items worth restocking")
		);
	}

	private void applySummary(HashMap<String, Object> summary) {

		int salesCount = (Integer) summary.get("sales_count");
		double salesTotal = (Double) summary.get("sales_total");
		int purchasesCount = (Integer) summary.get("purchases_count");
		double purchasesTotal = (Double) summary.get("purchases_total");
		double expensesTotal = (Double) summary.get("expenses_total");
		double paymentsInTotal = (Double) summary.get("payments_in_total");
		double paymentsOutTotal = (Double) summary.get("payments_out_total");
		double netCashMovement = (Double) summary.get("net_cash_movement");

		tv_sales_total.setText(AmountFormat.format(salesTotal));
		tv_sales_count.setText(salesCount + (salesCount == 1 ? " sale" : " sales"));

		tv_purchases_total.setText(AmountFormat.format(purchasesTotal));
		tv_purchases_count.setText(purchasesCount + (purchasesCount == 1 ? " purchase" : " purchases"));

		tv_expenses_total.setText(AmountFormat.format(expensesTotal));
		tv_payments_in_total.setText(AmountFormat.format(paymentsInTotal));
		tv_payments_out_total.setText(AmountFormat.format(paymentsOutTotal));

		tv_net_cash.setText(
			(netCashMovement > 0 ? "+" : "") + AmountFormat.format(netCashMovement)
		);

		tv_net_cash.setTextColor(
			getResources().getColor(
				netCashMovement < 0 ? R.color.danger : R.color.mod_sales
			)
		);

		// Same distinct per-category colors already used for each total
		// above, reused on the chart rather than a new palette; Net Cash
		// keeps the same sign-based color as its own TextView since it can
		// go negative - the Bar chart's baseline naturally draws that as a
		// bar below zero instead of hiding it (unlike a Pie, which could
		// only plot positive shares).
		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();
		chartEntries.add(new SimpleBarChartView.Entry("Sales", salesTotal, getResources().getColor(R.color.mod_sales)));
		chartEntries.add(new SimpleBarChartView.Entry("Purchases", purchasesTotal, getResources().getColor(R.color.mod_purchase)));
		chartEntries.add(new SimpleBarChartView.Entry("Expenses", expensesTotal, getResources().getColor(R.color.mod_expenses)));
		chartEntries.add(new SimpleBarChartView.Entry("Payments In", paymentsInTotal, getResources().getColor(R.color.mod_payments)));
		chartEntries.add(new SimpleBarChartView.Entry("Payments Out", paymentsOutTotal, getResources().getColor(R.color.mod_payments)));
		chartEntries.add(new SimpleBarChartView.Entry(
			"Net Cash", netCashMovement,
			getResources().getColor(netCashMovement < 0 ? R.color.danger : R.color.mod_sales)
		));

		chart_day_close.setEntries(chartEntries);
	}
}
