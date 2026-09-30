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
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class Salesreportactivity extends Activity {

	private static final int RANGE_TODAY = 0;
	private static final int RANGE_YESTERDAY = 1;
	private static final int RANGE_WEEK = 2;
	private static final int RANGE_MONTH = 3;
	private static final int RANGE_QUARTER = 4;
	private static final int RANGE_YEAR = 5;
	private static final int RANGE_CUSTOM = 6;

	private Spinner spinner_range;

	// Labels are positioned to match the RANGE_* constants above
	// exactly (Today=0 ... Custom=6), so the Spinner's selected
	// position can be used as the range value directly.
	private static final String[] RANGE_LABELS = {
		"Today", "Yesterday", "This Week", "This Month",
		"This Quarter", "This Year", "Custom Range"
	};

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

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

		spinner_range = findViewById(R.id.spinner_range);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_range_label = findViewById(R.id.tv_range_label);
		tv_total_sales = findViewById(R.id.tv_total_sales);
		tv_date_range = findViewById(R.id.tv_date_range);
		tv_sales_count = findViewById(R.id.tv_sales_count);

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

	// Every range (other than Custom) ends "today" and starts at the
	// beginning of the relevant period, so each report answers "how much
	// so far in this Today/Week/Month/Quarter/Year" - Yesterday is the
	// one exception, a single day that isn't today.
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

		tv_total_sales.setText(
			AmountFormat.format(total)
		);

		tv_sales_count.setText(String.valueOf(count));

		tv_date_range.setText(
			fromDate.equals(toDate) ?
			fromDate :
			fromDate + " to " + toDate
		);
	}
}
