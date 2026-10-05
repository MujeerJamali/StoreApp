package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// What share of sale/profit came from shoe items versus everything
// else, for a selectable period - same period-selector pattern as
// NetProfitReportActivity, same %-split card presentation as
// ProfitSplitReportActivity. See
// DatabaseHelper.getShoesVsNonShoesSummary() for the split itself,
// and ShoeIdentity for the shared "is this a shoe item" rule.
// =====================
public class ShoesVsNonShoesReportActivity extends Activity {

	private static final int RANGE_TODAY = 0;
	private static final int RANGE_YESTERDAY = 1;
	private static final int RANGE_WEEK = 2;
	private static final int RANGE_MONTH = 3;
	private static final int RANGE_QUARTER = 4;
	private static final int RANGE_YEAR = 5;
	private static final int RANGE_ALL_TIME = 6;
	private static final int RANGE_CUSTOM = 7;

	private static final int METRIC_SALES = 0;
	private static final int METRIC_PROFIT = 1;

	private Spinner spinner_metric;

	private static final String[] METRIC_LABELS = {"Sale", "Profit"};

	private Spinner spinner_range;

	// Positioned to match the RANGE_* constants above exactly.
	private static final String[] RANGE_LABELS = {
		"Today", "Yesterday", "This Week", "This Month",
		"This Quarter", "This Year", "All Time", "Custom Range"
	};

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private TextView tv_range_label;
	private TextView tv_date_range;
	private TextView tv_shoes_percent;
	private TextView tv_shoes_amount;
	private TextView tv_non_shoes_percent;
	private TextView tv_non_shoes_amount;
	private TextView tv_total_label;
	private TextView tv_total_amount;
	private SimpleBarChartView chart_shoes_split;

	private DatabaseHelper db;

	private int selectedRange = RANGE_TODAY;
	private int selectedMetric = METRIC_SALES;

	private long loadGeneration = 0;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.shoes_vs_non_shoes_report_activity);

		setTitle("Shoes vs Non-Shoes");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Shoes vs Non-Shoes", "What share of sales/profit came from shoe items versus everything else, for a selectable period."
		);

		spinner_metric = findViewById(R.id.spinner_metric);
		spinner_range = findViewById(R.id.spinner_range);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_range_label = findViewById(R.id.tv_range_label);
		tv_date_range = findViewById(R.id.tv_date_range);
		tv_shoes_percent = findViewById(R.id.tv_shoes_percent);
		tv_shoes_amount = findViewById(R.id.tv_shoes_amount);
		tv_non_shoes_percent = findViewById(R.id.tv_non_shoes_percent);
		tv_non_shoes_amount = findViewById(R.id.tv_non_shoes_amount);
		tv_total_label = findViewById(R.id.tv_total_label);
		tv_total_amount = findViewById(R.id.tv_total_amount);
		chart_shoes_split = findViewById(R.id.chart_shoes_split);

		db = new DatabaseHelper(this);

		String today = dateFormat.format(new java.util.Date());
		et_custom_from.setText(today);
		et_custom_to.setText(today);

		et_custom_from.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_custom_from);
				}
			});

		et_custom_to.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_custom_to);
				}
			});

		ArrayAdapter<String> metricAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, METRIC_LABELS
		);

		metricAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_metric.setAdapter(metricAdapter);

		spinner_metric.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectMetric(position);
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		ArrayAdapter<String> rangeAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, RANGE_LABELS
		);

		rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_range.setAdapter(rangeAdapter);

		spinner_range.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					selectRange(position);
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});

		loadReport();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
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

					if (selectedRange == RANGE_CUSTOM) {
						loadReport();
					}
				}
			},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void selectMetric(int metric) {

		selectedMetric = metric;

		tv_total_label.setText(metric == METRIC_SALES ? "Total Sale" : "Total Profit");

		loadReport();
	}

	private void selectRange(int range) {

		selectedRange = range;

		container_custom_range.setVisibility(range == RANGE_CUSTOM ? View.VISIBLE : View.GONE);

		loadReport();
	}

	// Every non-custom, non-all-time range ends "today" and starts at
	// the beginning of the relevant calendar period, matching
	// NetProfitReportActivity's convention. null/null means All Time.
	private String[] computeRange(int range) {

		if (range == RANGE_ALL_TIME) {
			return new String[]{null, null};
		}

		if (range == RANGE_CUSTOM) {
			return new String[]{
				et_custom_from.getText().toString().trim(),
				et_custom_to.getText().toString().trim()
			};
		}

		Calendar from = Calendar.getInstance();
		Calendar to = Calendar.getInstance();

		from.set(Calendar.HOUR_OF_DAY, 0);
		from.set(Calendar.MINUTE, 0);
		from.set(Calendar.SECOND, 0);
		from.set(Calendar.MILLISECOND, 0);

		switch (range) {

			case RANGE_YESTERDAY:
				from.add(Calendar.DAY_OF_YEAR, -1);
				to.add(Calendar.DAY_OF_YEAR, -1);
				break;

			case RANGE_WEEK:
				from.setFirstDayOfWeek(Calendar.MONDAY);
				from.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
				break;

			case RANGE_MONTH:
				from.set(Calendar.DAY_OF_MONTH, 1);
				break;

			case RANGE_QUARTER:
				int quarterStartMonth = (from.get(Calendar.MONTH) / 3) * 3;
				from.set(Calendar.MONTH, quarterStartMonth);
				from.set(Calendar.DAY_OF_MONTH, 1);
				break;

			case RANGE_YEAR:
				from.set(Calendar.DAY_OF_YEAR, 1);
				break;

			case RANGE_TODAY:
			default:
				break;
		}

		return new String[]{
			dateFormat.format(from.getTime()),
			dateFormat.format(to.getTime())
		};
	}

	private void loadReport() {

		final String[] range = computeRange(selectedRange);
		final int range_forLabel = selectedRange;
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final HashMap<String, Object> summary =
						db.getShoesVsNonShoesSummary(range[0], range[1]);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								applyReport(summary, range, range_forLabel);
							}
						});
				}
			}).start();
	}

	private void applyReport(
		HashMap<String, Object> summary,
		String[] range,
		int range_forLabel) {

		switch (range_forLabel) {

			case RANGE_YESTERDAY:
				tv_range_label.setText("Yesterday");
				break;

			case RANGE_WEEK:
				tv_range_label.setText("This Week");
				break;

			case RANGE_MONTH:
				tv_range_label.setText("This Month");
				break;

			case RANGE_QUARTER:
				tv_range_label.setText("This Quarter");
				break;

			case RANGE_YEAR:
				tv_range_label.setText("This Year");
				break;

			case RANGE_ALL_TIME:
				tv_range_label.setText("All Time");
				break;

			case RANGE_CUSTOM:
				tv_range_label.setText("Custom Range");
				break;

			case RANGE_TODAY:
			default:
				tv_range_label.setText("Today");
				break;
		}

		tv_date_range.setText(
			range[0] == null ?
			"All Time" :
			(range[0].equals(range[1]) ? range[0] : range[0] + " to " + range[1])
		);

		boolean isSales = selectedMetric == METRIC_SALES;

		double shoesAmount = (Double) summary.get(isSales ? "shoes_sales" : "shoes_profit");
		double nonShoesAmount = (Double) summary.get(isSales ? "non_shoes_sales" : "non_shoes_profit");
		double shoesPercent = (Double) summary.get(isSales ? "shoes_sales_percent" : "shoes_profit_percent");
		double nonShoesPercent =
			(Double) summary.get(isSales ? "non_shoes_sales_percent" : "non_shoes_profit_percent");

		double totalAmount = shoesAmount + nonShoesAmount;

		tv_shoes_percent.setText(AmountFormat.format(shoesPercent) + "%");
		tv_shoes_amount.setText(AmountFormat.format(shoesAmount));
		tv_non_shoes_percent.setText(AmountFormat.format(nonShoesPercent) + "%");
		tv_non_shoes_amount.setText(AmountFormat.format(nonShoesAmount));

		tv_total_amount.setText(AmountFormat.format(totalAmount));

		if (!isSales) {

			tv_total_amount.setTextColor(
				getResources().getColor(totalAmount >= 0 ? R.color.success : R.color.danger)
			);

		} else {

			tv_total_amount.setTextColor(getResources().getColor(R.color.text_primary));
		}

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();
		chartEntries.add(new SimpleBarChartView.Entry("Shoes", shoesAmount, getResources().getColor(R.color.mod_sales)));
		chartEntries.add(new SimpleBarChartView.Entry("Non-Shoes", nonShoesAmount, getResources().getColor(R.color.primary)));
		chart_shoes_split.setEntries(chartEntries);
	}
}
