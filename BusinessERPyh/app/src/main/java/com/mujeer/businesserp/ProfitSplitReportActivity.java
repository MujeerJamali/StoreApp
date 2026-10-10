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
// What share of sale profit came from unnamed Cash Sales versus real,
// named parties, for a selectable period - same period-selector pattern
// as Salesreportactivity. See
// DatabaseHelper.getProfitSplitByPartyType() for the split itself.
// =====================
public class ProfitSplitReportActivity extends Activity {

	private static final int RANGE_TODAY = 0;
	private static final int RANGE_YESTERDAY = 1;
	private static final int RANGE_WEEK = 2;
	private static final int RANGE_MONTH = 3;
	private static final int RANGE_QUARTER = 4;
	private static final int RANGE_YEAR = 5;
	private static final int RANGE_CUSTOM = 6;

	private Spinner spinner_range;

	private static final String[] RANGE_LABELS = {
		"Today", "Yesterday", "This Week", "This Month",
		"This Quarter", "This Year", "Custom Range"
	};

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private TextView tv_range_label;
	private TextView tv_date_range;
	private TextView tv_cash_percent;
	private TextView tv_cash_profit;
	private TextView tv_party_percent;
	private TextView tv_party_profit;
	private TextView tv_total_profit;
	private SimpleBarChartView chart_profit_split;
	private SimplePieChartView chart_profit_split_pie;

	private DatabaseHelper db;

	private int selectedRange = RANGE_TODAY;

	private long loadGeneration = 0;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.profit_split_report_activity);

		setTitle("Profit: Cash Sale vs Party");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Profit: Cash Sale vs Party", "What share of profit came from unnamed Cash Sales versus real, named parties."
		);

		spinner_range = findViewById(R.id.spinner_range);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_range_label = findViewById(R.id.tv_range_label);
		tv_date_range = findViewById(R.id.tv_date_range);
		tv_cash_percent = findViewById(R.id.tv_cash_percent);
		tv_cash_profit = findViewById(R.id.tv_cash_profit);
		tv_party_percent = findViewById(R.id.tv_party_percent);
		tv_party_profit = findViewById(R.id.tv_party_profit);
		tv_total_profit = findViewById(R.id.tv_total_profit);
		chart_profit_split = findViewById(R.id.chart_profit_split);
		chart_profit_split_pie = findViewById(R.id.chart_profit_split_pie);

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

	private void selectRange(int range) {

		selectedRange = range;

		container_custom_range.setVisibility(range == RANGE_CUSTOM ? View.VISIBLE : View.GONE);

		loadReport();
	}

	private String[] computeRange(int range) {

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
						db.getProfitSplitByPartyType(range[0], range[1]);

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

			case RANGE_CUSTOM:
				tv_range_label.setText("Custom Range");
				break;

			case RANGE_TODAY:
			default:
				tv_range_label.setText("Today");
				break;
		}

		tv_date_range.setText(
			range[0].equals(range[1]) ? range[0] : range[0] + " to " + range[1]
		);

		double cashPercent = (Double) summary.get("cash_percent");
		double partyPercent = (Double) summary.get("party_percent");
		double cashProfit = (Double) summary.get("cash_profit");
		double partyProfit = (Double) summary.get("party_profit");
		double totalProfit = (Double) summary.get("total_profit");

		tv_cash_percent.setText(AmountFormat.format(cashPercent) + "%");
		tv_party_percent.setText(AmountFormat.format(partyPercent) + "%");
		tv_cash_profit.setText(AmountFormat.format(cashProfit));
		tv_party_profit.setText(AmountFormat.format(partyProfit));
		tv_total_profit.setText(AmountFormat.format(totalProfit));

		tv_total_profit.setTextColor(
			getResources().getColor(totalProfit >= 0 ? R.color.success : R.color.danger)
		);

		ArrayList<SimpleBarChartView.Entry> chartEntries = new ArrayList<SimpleBarChartView.Entry>();
		chartEntries.add(new SimpleBarChartView.Entry("Cash Sale", cashProfit, getResources().getColor(R.color.mod_sales)));
		chartEntries.add(new SimpleBarChartView.Entry("Named Party", partyProfit, getResources().getColor(R.color.primary)));

		// A pie reads much more naturally as "share of profit" than a
		// bar pair, but a pie can only plot positive slices - so it's
		// only used when both sides actually turned a profit. The
		// moment either side is a loss, that bar chart below is what
		// still shows it (negative bars below baseline) instead of a
		// pie silently hiding it.
		boolean bothPositive = cashProfit > 0 && partyProfit > 0;

		chart_profit_split_pie.setVisibility(bothPositive ? View.VISIBLE : View.GONE);
		chart_profit_split.setVisibility(bothPositive ? View.GONE : View.VISIBLE);

		if (bothPositive) {
			chart_profit_split_pie.setEntries(chartEntries);
		} else {
			chart_profit_split.setEntries(chartEntries);
		}
	}
}
