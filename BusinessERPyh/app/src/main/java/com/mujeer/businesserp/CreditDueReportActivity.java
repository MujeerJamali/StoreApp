package com.mujeer.businesserp;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

// =====================
// Every not-fully-paid Sale whose Due Date falls within the selected
// period (default Today) - see Transactioneditactivity's Due Date
// field and DatabaseHelper.getCreditDueSales(). Only a Sale carries a
// due date in this app, so this report has nothing to show for
// Purchases/Payments/Expenses. Tapping a row opens that Sale directly.
// =====================
public class CreditDueReportActivity extends Activity {

	private static final int TYPE_SALE = 1;

	// Deliberately NOT the app-wide "start of period through today"
	// range set every other report uses (Today/Yesterday/Week/Month/...)
	// - a due date is forward-looking (it's the whole point of this
	// screen: what's coming due so it can be collected), so a backward-
	// looking range would only ever be able to show things already due
	// today or earlier, never anything due tomorrow onward. These
	// options are its own, purpose-built set instead; the Spinner
	// WIDGET convention is unchanged, only its option semantics are.
	// Package-visible (not private) so CreditDueNotifier can request a
	// specific starting range via the "initial_range" intent extra below.
	static final int RANGE_OVERDUE = 0;
	static final int RANGE_TODAY = 1;
	static final int RANGE_TOMORROW = 2;
	static final int RANGE_NEXT_3_DAYS = 3;
	static final int RANGE_NEXT_7_DAYS = 4;
	static final int RANGE_THIS_MONTH = 5;
	static final int RANGE_ALL = 6;
	static final int RANGE_CUSTOM = 7;

	private Spinner spinner_range;

	// Positioned to match the RANGE_* constants above exactly.
	private static final String[] RANGE_LABELS = {
		"Overdue", "Today", "Tomorrow", "Next 3 Days", "Next 7 Days",
		"This Month", "All", "Custom Range"
	};

	private View container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private TextView tv_empty;
	private ListView lv_credit_due;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> creditDueList =
		new ArrayList<HashMap<String, Object>>();

	private CreditDueAdapter adapter;

	private int selectedRange = RANGE_TODAY;

	// Bumped on every loadReport() call; a background result is only
	// applied if it's still the most recent request by the time it
	// comes back - same convention as the other reports.
	private long loadGeneration = 0;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.credit_due_report_activity);

		setTitle("Credit Due");

		InfoBubbleView info_bubble = findViewById(R.id.info_bubble);
		info_bubble.setInfo(
			"Credit Due", "Unpaid or partially-paid Sales whose Due Date falls in the selected period - forward-looking, unlike every other report's period selector, since a due date is something still coming, not something that already happened."
		);

		spinner_range = findViewById(R.id.spinner_range);

		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		tv_empty = findViewById(R.id.tv_empty);
		lv_credit_due = findViewById(R.id.lv_credit_due);

		db = new DatabaseHelper(this);

		adapter = new CreditDueAdapter(this, creditDueList);
		lv_credit_due.setAdapter(adapter);

		lv_credit_due.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = creditDueList.get(position);

					Intent intent = new Intent(
						CreditDueReportActivity.this, Transactionviewactivity.class
					);

					intent.putExtra("transaction_type", TYPE_SALE);
					intent.putExtra("transaction_id", (Integer) row.get("sale_id"));

					startActivity(intent);
				}
			});

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

		// CreditDueNotifier opens straight to Overdue instead of the
		// plain Today default when that's what its notification was
		// actually about - everything else (a normal tap from Reports)
		// has no such extra and keeps defaulting to Today.
		int initialRange = getIntent().getIntExtra("initial_range", RANGE_TODAY);

		selectedRange = initialRange;
		spinner_range.setSelection(initialRange);

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

	// Forward-looking, unlike every other report's period selector -
	// "Overdue" is everything already past its due date, "Today" is due
	// today, "Tomorrow" is due exactly tomorrow, "Next 3/7 Days"/
	// "This Month" look ahead from today instead of back from it (each
	// an inclusive window starting today), and "All" is every
	// outstanding due date regardless of direction. See the RANGE_*
	// comment above for why this can't just reuse the app-wide range set.
	private String[] computeRange(int range) {

		if (range == RANGE_ALL) {
			return new String[]{"0000-01-01", "9999-12-31"};
		}

		if (range == RANGE_CUSTOM) {
			return new String[]{
				et_custom_from.getText().toString().trim(),
				et_custom_to.getText().toString().trim()
			};
		}

		// Same "0000-01-01" epoch sentinel RANGE_ALL uses, so "yesterday
		// or earlier" rather than fighting Calendar's BC/AD normalization
		// around year 0.
		if (range == RANGE_OVERDUE) {

			Calendar yesterday = Calendar.getInstance();
			yesterday.add(Calendar.DAY_OF_YEAR, -1);

			return new String[]{"0000-01-01", dateFormat.format(yesterday.getTime())};
		}

		Calendar from = Calendar.getInstance();
		Calendar to = Calendar.getInstance();

		from.set(Calendar.HOUR_OF_DAY, 0);
		from.set(Calendar.MINUTE, 0);
		from.set(Calendar.SECOND, 0);
		from.set(Calendar.MILLISECOND, 0);

		switch (range) {

			case RANGE_TOMORROW:
				from.add(Calendar.DAY_OF_YEAR, 1);
				to.add(Calendar.DAY_OF_YEAR, 1);
				break;

			case RANGE_NEXT_3_DAYS:
				to.add(Calendar.DAY_OF_YEAR, 2);
				break;

			case RANGE_NEXT_7_DAYS:
				to.add(Calendar.DAY_OF_YEAR, 6);
				break;

			case RANGE_THIS_MONTH:
				to.set(Calendar.DAY_OF_MONTH, to.getActualMaximum(Calendar.DAY_OF_MONTH));
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
		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getCreditDueSales(range[0], range[1]);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								creditDueList.clear();
								creditDueList.addAll(result);

								adapter.notifyDataSetChanged();

								if (creditDueList.isEmpty()) {

									tv_empty.setVisibility(View.VISIBLE);
									lv_credit_due.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_credit_due.setVisibility(View.VISIBLE);
								}
							}
						});
				}
			}).start();
	}
}
