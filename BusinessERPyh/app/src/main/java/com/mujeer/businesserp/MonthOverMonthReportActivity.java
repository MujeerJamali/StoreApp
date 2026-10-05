package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// This Month (1st through today) vs Last Month (the full previous
// calendar month) for Sales/Net Profit/Expenses - reuses
// DatabaseHelper.getNetProfitSummary() for both periods, the same
// totals Net Profit's own "This Month" range already computes, just
// called twice and compared. Expenses is the one metric where "up"
// is colored as worse and "down" as better - the other two are the
// opposite.
// =====================
public class MonthOverMonthReportActivity extends Activity {

	private TextView tv_month_labels;

	private TextView tv_sales_this;
	private TextView tv_sales_last;
	private TextView tv_sales_delta;

	private TextView tv_profit_this;
	private TextView tv_profit_last;
	private TextView tv_profit_delta;

	private TextView tv_expenses_this;
	private TextView tv_expenses_last;
	private TextView tv_expenses_delta;

	private SimpleBarChartView chart_month_over_month;

	private DatabaseHelper db;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	private final SimpleDateFormat monthFormat =
		new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

	// A background result is only applied if it's still the most recent
	// request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.month_over_month_report_activity);

		setTitle("Month-over-Month");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Month-over-Month", "This Month (1st through today) vs the full previous calendar month, for Sales/Net Profit/Expenses - Expenses is the one metric where a decrease is the improvement, the other two are the opposite."
		);

		tv_month_labels = findViewById(R.id.tv_month_labels);

		tv_sales_this = findViewById(R.id.tv_sales_this);
		tv_sales_last = findViewById(R.id.tv_sales_last);
		tv_sales_delta = findViewById(R.id.tv_sales_delta);

		tv_profit_this = findViewById(R.id.tv_profit_this);
		tv_profit_last = findViewById(R.id.tv_profit_last);
		tv_profit_delta = findViewById(R.id.tv_profit_delta);

		tv_expenses_this = findViewById(R.id.tv_expenses_this);
		tv_expenses_last = findViewById(R.id.tv_expenses_last);
		tv_expenses_delta = findViewById(R.id.tv_expenses_delta);

		chart_month_over_month = findViewById(R.id.chart_month_over_month);

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

		Calendar thisMonthStart = Calendar.getInstance();
		thisMonthStart.set(Calendar.DAY_OF_MONTH, 1);
		clearTime(thisMonthStart);

		final String thisMonthLabel = monthFormat.format(thisMonthStart.getTime());
		final String thisFrom = dateFormat.format(thisMonthStart.getTime());
		final String thisTo = dateFormat.format(new java.util.Date());

		Calendar lastMonthEnd = (Calendar) thisMonthStart.clone();
		lastMonthEnd.add(Calendar.DAY_OF_MONTH, -1);

		Calendar lastMonthStart = (Calendar) lastMonthEnd.clone();
		lastMonthStart.set(Calendar.DAY_OF_MONTH, 1);

		final String lastMonthLabel = monthFormat.format(lastMonthStart.getTime());
		final String lastFrom = dateFormat.format(lastMonthStart.getTime());
		final String lastTo = dateFormat.format(lastMonthEnd.getTime());

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> thisSummary =
						db.getNetProfitSummary(thisFrom, thisTo);

					final HashMap<String, Object> lastSummary =
						db.getNetProfitSummary(lastFrom, lastTo);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								tv_month_labels.setText(thisMonthLabel + " vs " + lastMonthLabel);

								applyReport(thisSummary, lastSummary);
							}
						});
				}
			}).start();
	}

	private void clearTime(Calendar cal) {
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
	}

	private void applyReport(
		HashMap<String, Object> thisSummary, HashMap<String, Object> lastSummary) {

		double salesThis = (Double) thisSummary.get("sales_total");
		double salesLast = (Double) lastSummary.get("sales_total");

		double profitThis = (Double) thisSummary.get("net_profit");
		double profitLast = (Double) lastSummary.get("net_profit");

		double expensesThis = (Double) thisSummary.get("expenses_total");
		double expensesLast = (Double) lastSummary.get("expenses_total");

		tv_sales_this.setText("This Month: " + AmountFormat.format(salesThis));
		tv_sales_last.setText("Last Month: " + AmountFormat.format(salesLast));
		applyDelta(tv_sales_delta, salesThis, salesLast, true);

		tv_profit_this.setText("This Month: " + AmountFormat.format(profitThis));
		tv_profit_last.setText("Last Month: " + AmountFormat.format(profitLast));
		applyDelta(tv_profit_delta, profitThis, profitLast, true);

		tv_expenses_this.setText("This Month: " + AmountFormat.format(expensesThis));
		tv_expenses_last.setText("Last Month: " + AmountFormat.format(expensesLast));
		applyDelta(tv_expenses_delta, expensesThis, expensesLast, false);

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

		chartEntries.add(new SimpleBarChartView.Entry(
			"Sales (Last)", salesLast, getResources().getColor(R.color.mod_sales)));
		chartEntries.add(new SimpleBarChartView.Entry(
			"Sales (This)", salesThis, getResources().getColor(R.color.mod_sales)));

		chartEntries.add(new SimpleBarChartView.Entry(
			"Profit (Last)", profitLast, getResources().getColor(R.color.primary)));
		chartEntries.add(new SimpleBarChartView.Entry(
			"Profit (This)", profitThis, getResources().getColor(R.color.primary)));

		chartEntries.add(new SimpleBarChartView.Entry(
			"Expenses (Last)", expensesLast, getResources().getColor(R.color.mod_expenses)));
		chartEntries.add(new SimpleBarChartView.Entry(
			"Expenses (This)", expensesThis, getResources().getColor(R.color.mod_expenses)));

		chart_month_over_month.setEntries(chartEntries);
	}

	// higherIsBetter: true for Sales/Profit (an increase is good), false
	// for Expenses (a decrease is good).
	private void applyDelta(TextView target, double current, double previous, boolean higherIsBetter) {

		double delta = current - previous;

		if (Math.abs(delta) < 0.01 && Math.abs(previous) < 0.01) {

			target.setText("No change");
			target.setTextColor(getResources().getColor(R.color.text_secondary));
			return;
		}

		String sign = delta >= 0 ? "+" : "";

		String percentText;

		if (Math.abs(previous) < 0.01) {

			percentText = "";

		} else {

			double percent = delta / Math.abs(previous) * 100;
			percentText = " (" + (percent >= 0 ? "+" : "") + AmountFormat.formatPlain(percent) + "%)";
		}

		target.setText(sign + AmountFormat.format(delta) + percentText + " vs last month");

		boolean isImprovement = higherIsBetter ? delta >= 0 : delta <= 0;

		target.setTextColor(
			getResources().getColor(isImprovement ? R.color.success : R.color.danger)
		);
	}
}
