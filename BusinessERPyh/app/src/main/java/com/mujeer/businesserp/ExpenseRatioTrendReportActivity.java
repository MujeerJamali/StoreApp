package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Expenses as a % of Sales for each of the last 6 calendar months (see
// DatabaseHelper.getExpenseRatioTrend()) - a rising ratio means
// expenses are growing faster than sales, worth watching even when
// both totals are individually growing. Each month's badge is colored
// by whether its ratio improved (fell) or worsened (rose) vs the month
// before it; the first month shown has nothing earlier to compare
// against, so it's shown neutrally.
// =====================
public class ExpenseRatioTrendReportActivity extends Activity {

	private static final int MONTHS = 6;

	private SimpleBarChartView chart_expense_ratio;
	private LinearLayout container_expense_ratio;

	private DatabaseHelper db;

	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.expense_ratio_trend_report_activity);

		setTitle("Expense Ratio Trend");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Expense Ratio Trend",
			"Expenses as a % of Sales, last 6 calendar months (the current month is included even though it's partial). A rising ratio means expenses are growing faster than sales - worth watching even when both totals are individually growing. Each month is colored by whether its ratio improved or worsened vs the month before it."
		);

		chart_expense_ratio = findViewById(R.id.chart_expense_ratio);
		container_expense_ratio = findViewById(R.id.container_expense_ratio);

		db = new DatabaseHelper(this);

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

								applyReport(list);
							}
						}
					);
				}
			}
		).start();
	}

	private void applyReport(ArrayList<HashMap<String, Object>> list) {

		container_expense_ratio.removeAllViews();

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

		Double previousRatio = null;

		for (HashMap<String, Object> row : list) {

			double ratio = (Double) row.get("ratio_percent");

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

			tv_detail.setText(
				"Sales " + AmountFormat.format(salesTotal) + " - Expenses " +
				AmountFormat.format(expensesTotal)
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
