package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// This Period (1st-today, aligned to the chosen granularity) vs the
// prior period of the same length, at item granularity - defaults to
// This Month vs Last Month (the same pair Month-over-Month compares
// store-wide), but spinner_comparison_period can switch it to
// Week/Quarter/Year. Two lists: Margin Erosion
// (DatabaseHelper.getMarginErosionAlerts() - items that sold in both
// periods whose margin % dropped by 5+ points) and Biggest Profit
// Swings (getBiggestProfitSwings() - top 10 items by the size of their
// profit change, up or down). Both DB methods already take arbitrary
// from/to date pairs, so the period selector only changes which dates
// get passed in - no DB change needed. MarginErosionNotifier runs the
// same erosion check once a day (always Month vs Month) and opens here
// when there's more than one item to show.
// =====================
public class MarginProfitAlertReportActivity extends Activity {

	private static final int PROFIT_SWING_TOP_N = 10;

	private static final int PERIOD_MONTH = 0;
	private static final int PERIOD_WEEK = 1;
	private static final int PERIOD_QUARTER = 2;
	private static final int PERIOD_YEAR = 3;

	private static final String[] PERIOD_LABELS = {
		"This Month vs Last Month", "This Week vs Last Week",
		"This Quarter vs Last Quarter", "This Year vs Last Year"
	};

	private static final String[] PERIOD_THIS_LABELS = {
		"This Month", "This Week", "This Quarter", "This Year"
	};

	private static final String[] PERIOD_LAST_LABELS = {
		"Last Month", "Last Week", "Last Quarter", "Last Year"
	};

	private Spinner spinner_comparison_period;
	private int selectedComparisonPeriod = PERIOD_MONTH;

	private TextView tv_month_labels;

	private TextView tv_margin_avg_this;
	private TextView tv_margin_avg_last;
	private TextView tv_margin_avg_delta;

	private SimpleBarChartView chart_margin_profit;

	private Spinner spinner_shoes_filter;

	private TextView tv_margin_erosion_empty;
	private LinearLayout container_margin_erosion;

	private TextView tv_profit_swing_empty;
	private LinearLayout container_profit_swing;

	private DatabaseHelper db;

	private int selectedShoesFilter = DatabaseHelper.SHOES_FILTER_ALL;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	// A background result is only applied if it's still the most
	// recent request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.margin_profit_alert_report_activity);

		setTitle("Margin & Profit Alerts");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Margin & Profit Alerts",
			"This Period vs the prior period, at the item level - pick the period below (Month/Week/Quarter/Year). Margin Erosion: items that sold in both periods whose margin % dropped by 5 or more points. Biggest Profit Swings: the top 10 items by the size of their profit change vs the prior period, whether up or down."
		);

		tv_month_labels = findViewById(R.id.tv_month_labels);

		tv_margin_avg_this = findViewById(R.id.tv_margin_avg_this);
		tv_margin_avg_last = findViewById(R.id.tv_margin_avg_last);
		tv_margin_avg_delta = findViewById(R.id.tv_margin_avg_delta);

		chart_margin_profit = findViewById(R.id.chart_margin_profit);

		spinner_comparison_period = findViewById(R.id.spinner_comparison_period);
		spinner_shoes_filter = findViewById(R.id.spinner_shoes_filter);

		ArrayAdapter<String> periodAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, PERIOD_LABELS
		);

		periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_comparison_period.setAdapter(periodAdapter);

		selectedComparisonPeriod =
			FilterMemory.getInt(this, "MarginProfitAlertReport", "comparison_period", PERIOD_MONTH);

		spinner_comparison_period.setSelection(selectedComparisonPeriod);

		spinner_comparison_period.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedComparisonPeriod = position;

					FilterMemory.setInt(
						MarginProfitAlertReportActivity.this,
						"MarginProfitAlertReport", "comparison_period", position
					);

					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			}
		);

		tv_margin_erosion_empty = findViewById(R.id.tv_margin_erosion_empty);
		container_margin_erosion = findViewById(R.id.container_margin_erosion);

		tv_profit_swing_empty = findViewById(R.id.tv_profit_swing_empty);
		container_profit_swing = findViewById(R.id.container_profit_swing);

		ArrayAdapter<String> shoesFilterAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item,
			new String[]{"All Items", "Shoes Only", "Non-Shoes Only"}
		);

		shoesFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_shoes_filter.setAdapter(shoesFilterAdapter);

		int rememberedShoesFilterPosition =
			FilterMemory.getInt(this, "MarginProfitAlertReport", "shoes_filter", 0);

		spinner_shoes_filter.setSelection(rememberedShoesFilterPosition);

		selectedShoesFilter =
			rememberedShoesFilterPosition == 1 ? DatabaseHelper.SHOES_FILTER_SHOES_ONLY :
			rememberedShoesFilterPosition == 2 ? DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY :
			DatabaseHelper.SHOES_FILTER_ALL;

		spinner_shoes_filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedShoesFilter =
						position == 1 ? DatabaseHelper.SHOES_FILTER_SHOES_ONLY :
						position == 2 ? DatabaseHelper.SHOES_FILTER_NON_SHOES_ONLY :
						DatabaseHelper.SHOES_FILTER_ALL;

					FilterMemory.setInt(
						MarginProfitAlertReportActivity.this,
						"MarginProfitAlertReport", "shoes_filter", position
					);

					loadReport();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			}
		);

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

		final int period = selectedComparisonPeriod;

		Calendar thisPeriodStart = Calendar.getInstance();
		clearTime(thisPeriodStart);

		switch (period) {

			case PERIOD_WEEK:
				thisPeriodStart.setFirstDayOfWeek(Calendar.MONDAY);
				thisPeriodStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
				break;

			case PERIOD_QUARTER:
				int quarterStartMonth = (thisPeriodStart.get(Calendar.MONTH) / 3) * 3;
				thisPeriodStart.set(Calendar.MONTH, quarterStartMonth);
				thisPeriodStart.set(Calendar.DAY_OF_MONTH, 1);
				break;

			case PERIOD_YEAR:
				thisPeriodStart.set(Calendar.DAY_OF_YEAR, 1);
				break;

			case PERIOD_MONTH:
			default:
				thisPeriodStart.set(Calendar.DAY_OF_MONTH, 1);
				break;
		}

		final String thisFrom = dateFormat.format(thisPeriodStart.getTime());
		final String thisTo = dateFormat.format(new java.util.Date());

		Calendar lastPeriodEnd = (Calendar) thisPeriodStart.clone();
		lastPeriodEnd.add(Calendar.DAY_OF_MONTH, -1);

		Calendar lastPeriodStart = (Calendar) thisPeriodStart.clone();

		switch (period) {

			case PERIOD_WEEK:
				lastPeriodStart.add(Calendar.DAY_OF_YEAR, -7);
				break;

			case PERIOD_QUARTER:
				lastPeriodStart.add(Calendar.MONTH, -3);
				break;

			case PERIOD_YEAR:
				lastPeriodStart.add(Calendar.YEAR, -1);
				break;

			case PERIOD_MONTH:
			default:
				lastPeriodStart.add(Calendar.MONTH, -1);
				break;
		}

		final String lastFrom = dateFormat.format(lastPeriodStart.getTime());
		final String lastTo = dateFormat.format(lastPeriodEnd.getTime());

		final String thisPeriodLabel = PERIOD_THIS_LABELS[period];
		final String lastPeriodLabel = PERIOD_LAST_LABELS[period];

		final int shoesFilter = selectedShoesFilter;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> erosionList =
						db.getMarginErosionAlerts(thisFrom, thisTo, lastFrom, lastTo, shoesFilter);

					final ArrayList<HashMap<String, Object>> swingList =
						db.getBiggestProfitSwings(
							thisFrom, thisTo, lastFrom, lastTo, shoesFilter, PROFIT_SWING_TOP_N
						);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								tv_month_labels.setText(PERIOD_LABELS[period]);

								applyAverageMargin(erosionList, swingList, thisPeriodLabel, lastPeriodLabel);
								applyMarginErosion(erosionList);
								applyProfitSwings(swingList);
							}
						}
					);
				}
			}
		).start();
	}

	private void clearTime(Calendar cal) {
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
	}

	// The store-wide average margin % is derived from the same two
	// per-item lists already fetched (swingList covers every item that
	// sold in either period - erosionList is a strict subset) rather
	// than a third query, so it stays consistent with the shoes filter
	// applied to the rest of this screen.
	private void applyAverageMargin(
		ArrayList<HashMap<String, Object>> erosionList, ArrayList<HashMap<String, Object>> swingList,
		String thisPeriodLabel, String lastPeriodLabel) {

		double salesThisTotal = 0;
		double profitThisTotal = 0;
		double salesLastTotal = 0;
		double profitLastTotal = 0;

		for (HashMap<String, Object> row : swingList) {

			salesThisTotal += (Double) row.get("sales_this");
			profitThisTotal += (Double) row.get("profit_this");
			salesLastTotal += (Double) row.get("sales_last");
			profitLastTotal += (Double) row.get("profit_last");
		}

		double marginThis = salesThisTotal > 0.01 ? profitThisTotal / salesThisTotal * 100 : 0;
		double marginLast = salesLastTotal > 0.01 ? profitLastTotal / salesLastTotal * 100 : 0;
		double delta = marginThis - marginLast;

		tv_margin_avg_this.setText(thisPeriodLabel + ": " + AmountFormat.formatPlain(marginThis) + "%");
		tv_margin_avg_last.setText(lastPeriodLabel + ": " + AmountFormat.formatPlain(marginLast) + "%");

		if (salesThisTotal < 0.01 && salesLastTotal < 0.01) {

			tv_margin_avg_delta.setText("No sales in either period");
			tv_margin_avg_delta.setTextColor(getResources().getColor(R.color.text_secondary));

		} else {

			tv_margin_avg_delta.setText(
				(delta >= 0 ? "+" : "") + AmountFormat.formatPlain(delta) + " points vs " + lastPeriodLabel.toLowerCase(Locale.getDefault())
			);

			tv_margin_avg_delta.setTextColor(
				getResources().getColor(delta >= 0 ? R.color.success : R.color.danger)
			);
		}

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();

		chartEntries.add(new SimpleBarChartView.Entry(
			"Margin % (Last)", marginLast, getResources().getColor(R.color.primary)));
		chartEntries.add(new SimpleBarChartView.Entry(
			"Margin % (This)", marginThis, getResources().getColor(R.color.primary)));

		chart_margin_profit.setEntries(chartEntries);
	}

	private void applyMarginErosion(ArrayList<HashMap<String, Object>> list) {

		container_margin_erosion.removeAllViews();

		if (list.isEmpty()) {

			tv_margin_erosion_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_margin_erosion_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_margin_erosion, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("item_name")));

			double marginThis = (Double) row.get("margin_this");
			double marginLast = (Double) row.get("margin_last");

			tv_detail.setText(
				"Margin " + AmountFormat.formatPlain(marginLast) + "% -> " +
				AmountFormat.formatPlain(marginThis) + "%"
			);

			double erosion = (Double) row.get("erosion_points");
			tv_badge.setText("-" + AmountFormat.formatPlain(erosion) + "pp");

			final int itemId = (Integer) row.get("item_id");

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							MarginProfitAlertReportActivity.this, Itemviewactivity.class
						);

						intent.putExtra("item_id", itemId);
						startActivity(intent);
					}
				}
			);

			container_margin_erosion.addView(view);
		}
	}

	private void applyProfitSwings(ArrayList<HashMap<String, Object>> list) {

		container_profit_swing.removeAllViews();

		if (list.isEmpty()) {

			tv_profit_swing_empty.setVisibility(View.VISIBLE);
			return;
		}

		tv_profit_swing_empty.setVisibility(View.GONE);

		for (final HashMap<String, Object> row : list) {

			View view = LayoutInflater.from(this).inflate(
				R.layout.discount_stop_restock_row, container_profit_swing, false
			);

			TextView tv_name = view.findViewById(R.id.tv_row_name);
			TextView tv_detail = view.findViewById(R.id.tv_row_detail);
			TextView tv_badge = view.findViewById(R.id.tv_row_badge);

			tv_name.setText(String.valueOf(row.get("item_name")));

			double profitThis = (Double) row.get("profit_this");
			double profitLast = (Double) row.get("profit_last");

			tv_detail.setText(
				"Profit " + AmountFormat.format(profitLast) + " -> " + AmountFormat.format(profitThis)
			);

			double swing = (Double) row.get("profit_swing");
			tv_badge.setText((swing >= 0 ? "+" : "") + AmountFormat.format(swing));
			tv_badge.setTextColor(getResources().getColor(swing >= 0 ? R.color.success : R.color.danger));

			final int itemId = (Integer) row.get("item_id");

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						Intent intent = new Intent(
							MarginProfitAlertReportActivity.this, Itemviewactivity.class
						);

						intent.putExtra("item_id", itemId);
						startActivity(intent);
					}
				}
			);

			container_profit_swing.addView(view);
		}
	}
}
