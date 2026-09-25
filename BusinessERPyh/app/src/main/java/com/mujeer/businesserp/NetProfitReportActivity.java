package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Net Profit = Sales Total - Item Cost - Expenses, for a selectable
// period (Today/Week/Month/Quarter/Year/All Time/Custom Range). See
// DatabaseHelper.getNetProfitSummary() for the cost-basis
// simplification (current items.purchase_price, not a historical
// snapshot per sale).
// =====================
public class NetProfitReportActivity extends Activity {

	private static final int RANGE_TODAY = 0;
	private static final int RANGE_WEEK = 1;
	private static final int RANGE_MONTH = 2;
	private static final int RANGE_QUARTER = 3;
	private static final int RANGE_YEAR = 4;
	private static final int RANGE_ALL_TIME = 5;
	private static final int RANGE_CUSTOM = 6;
	private static final int RANGE_YESTERDAY = 7;

	private Button btn_range_today;
	private Button btn_range_yesterday;
	private Button btn_range_week;
	private Button btn_range_month;
	private Button btn_range_quarter;
	private Button btn_range_year;
	private Button btn_range_all_time;
	private Button btn_range_custom;

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private TextView tv_range_label;
	private TextView tv_date_range;
	private TextView tv_sales_total;
	private TextView tv_item_cost;
	private TextView tv_expenses_total;
	private TextView tv_net_profit;

	private DatabaseHelper db;

	private int selectedRange = RANGE_TODAY;

	// Bumped on every loadReport() call; a background result is only
	// applied if it's still the most recent request by the time it
	// comes back - same convention as Salesreportactivity.
	private long loadGeneration = 0;

	private final SimpleDateFormat dateFormat =
	new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.net_profit_report_activity);

		setTitle("Net Profit");

		btn_range_today = findViewById(R.id.btn_range_today);
		btn_range_yesterday = findViewById(R.id.btn_range_yesterday);
		btn_range_week = findViewById(R.id.btn_range_week);
		btn_range_month = findViewById(R.id.btn_range_month);
		btn_range_quarter = findViewById(R.id.btn_range_quarter);
		btn_range_year = findViewById(R.id.btn_range_year);
		btn_range_all_time = findViewById(R.id.btn_range_all_time);
		btn_range_custom = findViewById(R.id.btn_range_custom);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_range_label = findViewById(R.id.tv_range_label);
		tv_date_range = findViewById(R.id.tv_date_range);
		tv_sales_total = findViewById(R.id.tv_sales_total);
		tv_item_cost = findViewById(R.id.tv_item_cost);
		tv_expenses_total = findViewById(R.id.tv_expenses_total);
		tv_net_profit = findViewById(R.id.tv_net_profit);

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

		btn_range_today.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_TODAY);
				}
			});

		btn_range_yesterday.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_YESTERDAY);
				}
			});

		btn_range_week.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_WEEK);
				}
			});

		btn_range_month.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_MONTH);
				}
			});

		btn_range_quarter.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_QUARTER);
				}
			});

		btn_range_year.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_YEAR);
				}
			});

		btn_range_all_time.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_ALL_TIME);
				}
			});

		btn_range_custom.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_CUSTOM);
				}
			});

		selectRange(RANGE_TODAY);
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

		Button[] buttons = {
			btn_range_today, btn_range_yesterday, btn_range_week, btn_range_month,
			btn_range_quarter, btn_range_year, btn_range_all_time, btn_range_custom
		};

		int[] ranges = {
			RANGE_TODAY, RANGE_YESTERDAY, RANGE_WEEK, RANGE_MONTH,
			RANGE_QUARTER, RANGE_YEAR, RANGE_ALL_TIME, RANGE_CUSTOM
		};

		for (int i = 0; i < buttons.length; i++) {

			if (ranges[i] == range) {

				buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
				buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

			} else {

				buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
				buttons[i].setTextColor(getResources().getColor(R.color.primary));
			}
		}

		container_custom_range.setVisibility(range == RANGE_CUSTOM ? View.VISIBLE : View.GONE);

		loadReport();
	}

	// Every non-custom, non-all-time range ends "today" and starts at
	// the beginning of the relevant calendar period, matching
	// Salesreportactivity's convention. null/null means All Time.
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
						db.getNetProfitSummary(range[0], range[1]);

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

		double salesTotal = (Double) summary.get("sales_total");
		double itemCost = (Double) summary.get("item_cost");
		double expensesTotal = (Double) summary.get("expenses_total");
		double netProfit = (Double) summary.get("net_profit");

		tv_sales_total.setText(AmountFormat.format(salesTotal));
		tv_item_cost.setText(AmountFormat.format(itemCost));
		tv_expenses_total.setText(AmountFormat.format(expensesTotal));

		tv_net_profit.setText(AmountFormat.format(netProfit));

		tv_net_profit.setTextColor(
			getResources().getColor(netProfit >= 0 ? R.color.success : R.color.danger)
		);
	}
}
