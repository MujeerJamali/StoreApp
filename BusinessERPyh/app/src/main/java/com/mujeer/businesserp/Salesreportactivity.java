package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class Salesreportactivity extends Activity {

	private static final int RANGE_TODAY = 0;
	private static final int RANGE_WEEK = 1;
	private static final int RANGE_MONTH = 2;
	private static final int RANGE_QUARTER = 3;
	private static final int RANGE_YEAR = 4;

	private Button btn_range_today;
	private Button btn_range_week;
	private Button btn_range_month;
	private Button btn_range_quarter;
	private Button btn_range_year;

	private TextView tv_range_label;
	private TextView tv_total_sales;
	private TextView tv_date_range;
	private TextView tv_sales_count;

	private DatabaseHelper db;

	private int selectedRange = RANGE_TODAY;

	// Bumped on every loadReport() call; a background result is only
	// applied if it's still the most recent request by the time it
	// comes back, so a slow "Year" query can't clobber a later "Today"
	// tap that finished first.
	private long loadGeneration = 0;

	private final SimpleDateFormat dateFormat =
	new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.salesreportactivity);

		setTitle("Total Sales");

		btn_range_today = findViewById(R.id.btn_range_today);
		btn_range_week = findViewById(R.id.btn_range_week);
		btn_range_month = findViewById(R.id.btn_range_month);
		btn_range_quarter = findViewById(R.id.btn_range_quarter);
		btn_range_year = findViewById(R.id.btn_range_year);

		tv_range_label = findViewById(R.id.tv_range_label);
		tv_total_sales = findViewById(R.id.tv_total_sales);
		tv_date_range = findViewById(R.id.tv_date_range);
		tv_sales_count = findViewById(R.id.tv_sales_count);

		db = new DatabaseHelper(this);

		btn_range_today.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectRange(RANGE_TODAY);
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

		selectRange(RANGE_TODAY);
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	private void selectRange(int range) {

		selectedRange = range;

		Button[] buttons = {
			btn_range_today,
			btn_range_week,
			btn_range_month,
			btn_range_quarter,
			btn_range_year
		};

		int[] ranges = {
			RANGE_TODAY,
			RANGE_WEEK,
			RANGE_MONTH,
			RANGE_QUARTER,
			RANGE_YEAR
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

		loadReport();
	}

	// Every range ends "today" and starts at the beginning of the
	// relevant period, so each report answers "how much so far in
	// this Today/Week/Month/Quarter/Year".
	private String[] computeRange(int range) {

		Calendar from = Calendar.getInstance();
		Calendar to = Calendar.getInstance();

		from.set(Calendar.HOUR_OF_DAY, 0);
		from.set(Calendar.MINUTE, 0);
		from.set(Calendar.SECOND, 0);
		from.set(Calendar.MILLISECOND, 0);

		switch (range) {

			case RANGE_WEEK:

				from.setFirstDayOfWeek(Calendar.MONDAY);
				from.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

				break;

			case RANGE_MONTH:

				from.set(Calendar.DAY_OF_MONTH, 1);

				break;

			case RANGE_QUARTER:

				int quarterStartMonth =
					(from.get(Calendar.MONTH) / 3) * 3;

				from.set(Calendar.MONTH, quarterStartMonth);
				from.set(Calendar.DAY_OF_MONTH, 1);

				break;

			case RANGE_YEAR:

				from.set(Calendar.DAY_OF_YEAR, 1);

				break;

			case RANGE_TODAY:
			default:

				// 'from' is already today at midnight.

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
						db.getSalesSummary(range[0], range[1]);

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

		String fromDate = range[0];
		String toDate = range[1];

		double total = (Double) summary.get("total");
		int count = (Integer) summary.get("count");

		switch (range_forLabel) {

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

			case RANGE_TODAY:
			default:
				tv_range_label.setText("Today");
				break;
		}

		tv_total_sales.setText(
			String.format(Locale.getDefault(), "%.2f", total)
		);

		tv_sales_count.setText(String.valueOf(count));

		tv_date_range.setText(
			fromDate.equals(toDate) ?
			fromDate :
			fromDate + " to " + toDate
		);
	}
}
