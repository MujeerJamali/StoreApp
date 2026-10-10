package com.mujeer.businesserp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class Expensesactivity extends Activity {

	private static final int RANGE_ALL = 0;
	private static final int RANGE_TODAY = 1;
	private static final int RANGE_YESTERDAY = 2;
	private static final int RANGE_WEEK = 3;
	private static final int RANGE_MONTH = 4;
	private static final int RANGE_QUARTER = 5;
	private static final int RANGE_YEAR = 6;
	private static final int RANGE_CUSTOM = 7;

	// Position in this array matches the Spinner's own display order,
	// same convention as every report's own RANGE_VALUES/RANGE_LABELS
	// pair - "All" (no date filter at all) replaces "Today" as the
	// default here, since this is a plain list screen, not a report.
	private static final int[] RANGE_VALUES = {
		RANGE_ALL, RANGE_TODAY, RANGE_YESTERDAY, RANGE_WEEK,
		RANGE_MONTH, RANGE_QUARTER, RANGE_YEAR, RANGE_CUSTOM
	};

	private static final String[] RANGE_LABELS = {
		"All", "Today", "Yesterday", "This Week",
		"This Month", "This Quarter", "This Year", "Custom Range"
	};

	private int selectedRange = RANGE_ALL;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	private EditText et_search;
	private TextView tv_empty;

	private Button btn_add;
	private Spinner spinner_filter;
	private LinearLayout container_custom_range;
	private EditText et_custom_from;
	private EditText et_custom_to;

	private ListView lv_expenses;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> expenseList;

	private ExpenseAdapter adapter;

	private final ExpenseAdapter.RowActionListener rowActionListener =
		new ExpenseAdapter.RowActionListener() {

			@Override
			public void onRowEdit(HashMap<String, Object> expense) {

				Intent intent = new Intent(
					Expensesactivity.this,
					Expenseeditactivity.class
				);

				intent.putExtra(
					"expense_id",
					Integer.parseInt(expense.get("id").toString())
				);

				startActivity(intent);
			}

			@Override
			public void onRowDelete(final HashMap<String, Object> expense) {

				final int expenseId = Integer.parseInt(expense.get("id").toString());

				new android.app.AlertDialog.Builder(Expensesactivity.this)
					.setTitle("Delete Expense")
					.setMessage("Are you sure you want to delete this expense?")
					.setPositiveButton(
						"Delete",
						new android.content.DialogInterface.OnClickListener() {

							@Override
							public void onClick(android.content.DialogInterface dialog, int which) {

								if (db.deleteExpense(expenseId)) {

									Toast.makeText(
										Expensesactivity.this,
										"Expense deleted successfully.",
										Toast.LENGTH_SHORT
									).show();

									loadExpenses();

								} else {

									Toast.makeText(
										Expensesactivity.this,
										"Failed to delete expense.",
										Toast.LENGTH_SHORT
									).show();
								}
							}
						}
					)
					.setNegativeButton("Cancel", null)
					.show();
			}

			// Plain tap on the row (open the expense) - see
			// SwipeRevealLayout.onTouchEvent()'s own comment for why this
			// can no longer be left to lv_expenses.setOnItemClickListener
			// alone now that swipe is enabled on every row here.
			@Override
			public void onRowTap(HashMap<String, Object> expense) {

				Intent intent = new Intent(
					Expensesactivity.this,
					Expenseviewactivity.class
				);

				intent.putExtra("expense_id", Integer.parseInt(expense.get("id").toString()));

				startActivity(intent);
			}
		};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.expensesactivity);

		setTitle("Expenses");

		et_search = findViewById(R.id.et_search);
		tv_empty = findViewById(R.id.tv_empty);

		btn_add = findViewById(R.id.btn_add);
		spinner_filter = findViewById(R.id.spinner_filter);
		container_custom_range = findViewById(R.id.container_custom_range);
		et_custom_from = findViewById(R.id.et_custom_from);
		et_custom_to = findViewById(R.id.et_custom_to);

		lv_expenses = findViewById(R.id.lv_expenses);

		db = new DatabaseHelper(this);

		expenseList =
			new ArrayList<HashMap<String, Object>>();

		btn_add.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					Intent intent = new Intent(
						Expensesactivity.this,
						Expenseeditactivity.class
					);

					intent.putExtra(
						"expense_id",
						0
					);

					startActivity(intent);
				}
			}
		);

		ArrayAdapter<String> rangeAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, RANGE_LABELS
		);

		rangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_filter.setAdapter(rangeAdapter);

		spinner_filter.setOnItemSelectedListener(
			new AdapterView.OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

					selectedRange = RANGE_VALUES[position];

					container_custom_range.setVisibility(
						selectedRange == RANGE_CUSTOM ? View.VISIBLE : View.GONE
					);

					applyRangeFilter();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			}
		);

		et_custom_from.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_custom_from);
				}
			}
		);

		et_custom_to.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_custom_to);
				}
			}
		);

		lv_expenses.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					Intent intent = new Intent(
						Expensesactivity.this,
						Expenseviewactivity.class
					);

					intent.putExtra(
						"expense_id",
						Integer.parseInt(
							((HashMap<String, Object>)
							adapter.getItem(position))
							.get("id")
							.toString()
						)
					);

					startActivity(intent);
				}
			}
		);

		et_search.addTextChangedListener(
			new TextWatcher() {

				@Override
				public void beforeTextChanged(
					CharSequence s,
					int start,
					int count,
					int after) {
				}

				@Override
				public void onTextChanged(
					CharSequence s,
					int start,
					int before,
					int count) {
				}

				@Override
				public void afterTextChanged(
					Editable s) {

					if (adapter != null) {
						applyRangeFilter();
					}
				}
			}
		);
	}

	@Override
	protected void onResume() {
		super.onResume();

		loadExpenses();
	}

	private void loadExpenses() {

		expenseList.clear();

		expenseList.addAll(
			db.getExpenses()
		);

		adapter = new ExpenseAdapter(
			this,
			expenseList
		);

		lv_expenses.setAdapter(adapter);

		lv_expenses.setEmptyView(tv_empty);

		adapter.setSwipeEnabled(SwipeGestureSettings.isEnabled(this));
		adapter.setRowActionListener(rowActionListener);

		// Set only when launched from DayCloseReportActivity's Expenses
		// card - pins the list to that one day. Reflected in the Spinner
		// itself (Custom Range, both fields set to that date) rather than
		// filtering underneath it, so the screen's own filter state stays
		// honest about what's actually being shown.
		String filterDate = getIntent().getStringExtra("date");

		if (filterDate != null) {

			et_custom_from.setText(filterDate);
			et_custom_to.setText(filterDate);

			int customPosition = indexOfRange(RANGE_CUSTOM);
			spinner_filter.setSelection(customPosition);

			// setSelection() above only triggers onItemSelected() (and so
			// applyRangeFilter()) when it actually changes the current
			// position - harmless to call again directly so the very
			// first load (already sitting on that position) still
			// applies this date instead of silently showing "All."
			selectedRange = RANGE_CUSTOM;
			container_custom_range.setVisibility(View.VISIBLE);
			applyRangeFilter();

		} else {

			applyRangeFilter();
		}
	}

	private int indexOfRange(int rangeValue) {

		for (int i = 0; i < RANGE_VALUES.length; i++) {

			if (RANGE_VALUES[i] == rangeValue) {
				return i;
			}
		}

		return 0;
	}

	private void showDatePicker(final EditText target) {

		Calendar calendar = Calendar.getInstance();

		try {

			String existing = target.getText().toString();

			if (existing.length() > 0) {
				calendar.setTime(dateFormat.parse(existing));
			}

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

						applyRangeFilter();
					}
				},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	// Mirrors every report screen's own range-Spinner arithmetic
	// (NetProfitReportActivity etc.) - Monday-start weeks, calendar
	// month/quarter/year boundaries, today as the end of every range
	// except Yesterday. Empty strings for RANGE_ALL since
	// ExpenseAdapter.filter(query, from, to) already treats an empty
	// from/to as "no bound on that side."
	private String[] computeRangeDates(int range) {

		Calendar from = Calendar.getInstance();
		Calendar to = Calendar.getInstance();

		switch (range) {

			case RANGE_TODAY:
				break;

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

			case RANGE_ALL:
			default:
				return new String[]{"", ""};
		}

		return new String[]{dateFormat.format(from.getTime()), dateFormat.format(to.getTime())};
	}

	private void applyRangeFilter() {

		if (adapter == null) {
			return;
		}

		String fromDate;
		String toDate;

		if (selectedRange == RANGE_CUSTOM) {

			fromDate = et_custom_from.getText().toString().trim();
			toDate = et_custom_to.getText().toString().trim();

		} else {

			String[] range = computeRangeDates(selectedRange);
			fromDate = range[0];
			toDate = range[1];
		}

		adapter.filter(et_search.getText().toString(), fromDate, toDate);
	}

}
