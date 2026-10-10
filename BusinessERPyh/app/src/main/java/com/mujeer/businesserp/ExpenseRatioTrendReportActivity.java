package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Expenses as a % of Sales (default) or as a % of gross profit, for
// each of the last 6 calendar months (see
// DatabaseHelper.getExpenseRatioTrend() - both ratios are always
// computed there, so switching spinner_ratio_mode just re-renders the
// already-loaded list, no second query). A rising ratio means expenses
// are growing faster than sales/profit - worth watching even when both
// totals are individually growing. Each month's badge is colored by
// whether its ratio improved (fell) or worsened (rose) vs the month
// before it; the first month shown has nothing earlier to compare
// against, so it's shown neutrally.
// =====================
public class ExpenseRatioTrendReportActivity extends Activity {

	private static final int MONTHS = 6;

	private static final int MODE_VS_SALES = 0;
	private static final int MODE_VS_PROFIT = 1;

	private static final String[] MODE_LABELS = {"vs Sales", "vs Profit"};

	private static final String DESCRIPTION_VS_SALES =
		"Expenses as a % of Sales, last 6 calendar months. A rising ratio means expenses are growing faster than sales.";

	private static final String DESCRIPTION_VS_PROFIT =
		"Expenses as a % of gross profit (Sales minus item cost), last 6 calendar months. A rising ratio means expenses are eating a bigger share of the profit margin, even if profit itself is still growing.";

	private TextView tv_ratio_description;
	private Spinner spinner_ratio_mode;
	private SimpleLineChartView chart_expense_ratio;
	private LinearLayout container_expense_ratio;

	private DatabaseHelper db;

	private int selectedMode = MODE_VS_SALES;

	// Cached so switching modes re-renders instantly without a second
	// background query - both ratios are already in each row.
	private ArrayList<HashMap<String, Object>> lastLoadedList = null;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.expense_ratio_trend_report_activity);

		setTitle("Expense Ratio Trend");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Expense Ratio Trend",
			"Expenses as a % of Sales, or as a % of gross profit, last 6 calendar months (the current month is included even though it's partial). A rising ratio means expenses are growing faster than sales or profit - worth watching even when the totals are individually growing. Each month is colored by whether its ratio improved or worsened vs the month before it."
		);

		tv_ratio_description = findViewById(R.id.tv_ratio_description);
		spinner_ratio_mode = findViewById(R.id.spinner_ratio_mode);
		chart_expense_ratio = findViewById(R.id.chart_expense_ratio);
		container_expense_ratio = findViewById(R.id.container_expense_ratio);

		db = new DatabaseHelper(this);

		ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, MODE_LABELS
		);

		modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_ratio_mode.setAdapter(modeAdapter);

		selectedMode = FilterMemory.getInt(this, "ExpenseRatioTrend", "ratio_mode", MODE_VS_SALES);
		spinner_ratio_mode.setSelection(selectedMode);

		spinner_ratio_mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedMode = position;

					FilterMemory.setInt(
						ExpenseRatioTrendReportActivity.this,
						"ExpenseRatioTrend", "ratio_mode", position
					);

					if (lastLoadedList != null) {
						applyReport(lastLoadedList);
					} else {
						loadReport();
					}
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			}
		);

		loadReport();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	private void loadReport() {

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> list = db.getExpenseRatioTrend(MONTHS);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								lastLoadedList = list;
								applyReport(list);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyReport(ArrayList<HashMap<String, Object>> list) {

		tv_ratio_description.setText(
			selectedMode == MODE_VS_PROFIT ? DESCRIPTION_VS_PROFIT : DESCRIPTION_VS_SALES
		);

		container_expense_ratio.removeAllViews();

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

		Double previousRatio = null;

		for (HashMap<String, Object> row : list) {

			double ratio = (Double) (
				selectedMode == MODE_VS_PROFIT ? row.get("profit_ratio_percent") : row.get("ratio_percent")
			);

			chartEntries.add(new SimpleBarChartView.Entry(
				String.valueOf(row.get("month_label")), ratio, getResources().getColor(R.color.primary)
			));

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_expense_ratio, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("month_label")));

			double salesTotal = (Double) row.get("sales_total");
			double expensesTotal = (Double) row.get("expenses_total");
			double grossProfit = (Double) row.get("gross_profit");

			tv_detail.setText(
				selectedMode == MODE_VS_PROFIT ?
				"Profit " + AmountFormat.format(grossProfit) + " - Expenses " + AmountFormat.format(expensesTotal) :
				"Sales " + AmountFormat.format(salesTotal) + " - Expenses " + AmountFormat.format(expensesTotal)
			);

			tv_badge.setText(AmountFormat.formatPlain(ratio) + "%");

			if (previousRatio == null) {

				tv_badge.setTextColor(getResources().getColor(R.color.text_secondary));

			} else {

				boolean improved = ratio <= previousRatio;

				tv_badge.setTextColor(
					getResources().getColor(improved ? R.color.success : R.color.danger)
				);
			}

			previousRatio = ratio;

			container_expense_ratio.addView(view);
		}

		chart_expense_ratio.setEntries(chartEntries);
	}
}
