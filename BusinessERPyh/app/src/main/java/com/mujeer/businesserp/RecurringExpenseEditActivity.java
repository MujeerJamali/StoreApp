package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RecurringExpenseEditActivity extends Activity {

	private static final String[] WEEKDAY_NAMES = {
		"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
	};

	private EditText et_item;
	private EditText et_amount;
	private AutoCompleteTextView actv_party;

	private Button btn_freq_daily;
	private Button btn_freq_weekly;
	private Button btn_freq_monthly;
	private Button btn_freq_specific;

	private View container_weekly;
	private Spinner spinner_day_of_week;

	private View container_monthly;
	private EditText et_day_of_month;

	private View container_specific;
	private EditText et_specific_dates;
	private Button btn_add_specific_date;

	private View container_start_date;
	private EditText et_start_date;

	private EditText et_notes;
	private CheckBox cb_active;

	private Button btn_save;
	private Button btn_delete;

	private DatabaseHelper db;

	private int recurringExpenseId = 0;
	private int selectedFrequency = DatabaseHelper.RECURRING_DAILY;

	private final SimpleDateFormat dateFormat =
		new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

	private Map<String, Integer> partyIdByName;
	private Map<Integer, String> partyNameById;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.recurring_expense_edit_activity);

		setTitle("Recurring Expense");

		et_item = findViewById(R.id.et_item);
		et_amount = findViewById(R.id.et_amount);
		actv_party = findViewById(R.id.actv_party);

		btn_freq_daily = findViewById(R.id.btn_freq_daily);
		btn_freq_weekly = findViewById(R.id.btn_freq_weekly);
		btn_freq_monthly = findViewById(R.id.btn_freq_monthly);
		btn_freq_specific = findViewById(R.id.btn_freq_specific);

		container_weekly = findViewById(R.id.container_weekly);
		spinner_day_of_week = findViewById(R.id.spinner_day_of_week);

		container_monthly = findViewById(R.id.container_monthly);
		et_day_of_month = findViewById(R.id.et_day_of_month);

		container_specific = findViewById(R.id.container_specific);
		et_specific_dates = findViewById(R.id.et_specific_dates);
		btn_add_specific_date = findViewById(R.id.btn_add_specific_date);

		container_start_date = findViewById(R.id.container_start_date);
		et_start_date = findViewById(R.id.et_start_date);
		et_start_date.setFocusable(false);
		et_start_date.setClickable(true);

		et_notes = findViewById(R.id.et_notes);
		cb_active = findViewById(R.id.cb_active);

		btn_save = findViewById(R.id.btn_save);
		btn_delete = findViewById(R.id.btn_delete);

		db = new DatabaseHelper(this);

		ArrayAdapter<String> weekdayAdapter = new ArrayAdapter<String>(
			this, android.R.layout.simple_spinner_item, Arrays.asList(WEEKDAY_NAMES)
		);

		weekdayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner_day_of_week.setAdapter(weekdayAdapter);

		loadPartyAutoComplete();

		btn_freq_daily.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectFrequency(DatabaseHelper.RECURRING_DAILY);
				}
			});

		btn_freq_weekly.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectFrequency(DatabaseHelper.RECURRING_WEEKLY);
				}
			});

		btn_freq_monthly.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectFrequency(DatabaseHelper.RECURRING_MONTHLY);
				}
			});

		btn_freq_specific.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					selectFrequency(DatabaseHelper.RECURRING_SPECIFIC_DATES);
				}
			});

		et_start_date.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePicker(et_start_date);
				}
			});

		btn_add_specific_date.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					showDatePickerAndAppend();
				}
			});

		btn_save.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					save();
				}
			});

		btn_delete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					confirmDelete();
				}
			});

		recurringExpenseId = getIntent().getIntExtra("recurring_expense_id", 0);

		if (recurringExpenseId == 0) {

			et_start_date.setText(dateFormat.format(new java.util.Date()));

			selectFrequency(DatabaseHelper.RECURRING_DAILY);

			db.getOrCreatePartyId("Cash Expenses");
			loadPartyAutoComplete();
			actv_party.setText("Cash Expenses", false);

		} else {

			btn_delete.setVisibility(View.VISIBLE);

			loadExistingRule();
		}
	}

	private void loadPartyAutoComplete() {

		ArrayList<HashMap<String, Object>> parties = db.getParties();

		ArrayList<String> partyNames = new ArrayList<String>();
		HashMap<String, String> partySubtitles = new HashMap<String, String>();

		partyIdByName = new HashMap<String, Integer>();
		partyNameById = new HashMap<Integer, String>();

		for (HashMap<String, Object> party : parties) {

			String name = (String) party.get("name");
			int id = (Integer) party.get("id");

			partyNames.add(name);
			partyIdByName.put(name, id);
			partyNameById.put(id, name);
			partySubtitles.put(name, "");
		}

		TwoLineAutoCompleteAdapter adapter =
			new TwoLineAutoCompleteAdapter(this, partyNames, partySubtitles);

		actv_party.setAdapter(adapter);
		actv_party.setThreshold(1);
	}

	private void loadExistingRule() {

		HashMap<String, Object> rule = db.getRecurringExpenseById(recurringExpenseId);

		if (rule == null) {
			finish();
			return;
		}

		et_item.setText(String.valueOf(rule.get("item")));
		et_amount.setText(AmountFormat.formatPlain((Double) rule.get("amount")));
		et_notes.setText(String.valueOf(rule.get("notes")));

		Integer partyId = (Integer) rule.get("party_id");

		if (partyId != null) {

			String partyName = partyNameById.get(partyId);

			if (partyName != null) {
				actv_party.setText(partyName, false);
			}
		}

		String startDate = (String) rule.get("start_date");
		et_start_date.setText(startDate == null ? "" : startDate);

		Integer dayOfWeek = (Integer) rule.get("day_of_week");

		if (dayOfWeek != null && dayOfWeek >= 1 && dayOfWeek <= 7) {
			spinner_day_of_week.setSelection(dayOfWeek - 1);
		}

		Integer dayOfMonth = (Integer) rule.get("day_of_month");
		et_day_of_month.setText(dayOfMonth == null ? "1" : String.valueOf(dayOfMonth));

		String specificDates = (String) rule.get("specific_dates");
		et_specific_dates.setText(specificDates == null ? "" : specificDates);

		cb_active.setChecked((Boolean) rule.get("active"));

		selectFrequency((Integer) rule.get("frequency"));
	}

	private void selectFrequency(int frequency) {

		selectedFrequency = frequency;

		Button[] buttons = {
			btn_freq_daily, btn_freq_weekly, btn_freq_monthly, btn_freq_specific
		};

		int[] frequencies = {
			DatabaseHelper.RECURRING_DAILY, DatabaseHelper.RECURRING_WEEKLY,
			DatabaseHelper.RECURRING_MONTHLY, DatabaseHelper.RECURRING_SPECIFIC_DATES
		};

		for (int i = 0; i < buttons.length; i++) {

			if (frequencies[i] == frequency) {

				buttons[i].setBackgroundResource(R.drawable.bg_button_primary);
				buttons[i].setTextColor(getResources().getColor(R.color.text_on_primary));

			} else {

				buttons[i].setBackgroundResource(R.drawable.bg_button_outline);
				buttons[i].setTextColor(getResources().getColor(R.color.primary));
			}
		}

		container_weekly.setVisibility(
			frequency == DatabaseHelper.RECURRING_WEEKLY ? View.VISIBLE : View.GONE
		);

		container_monthly.setVisibility(
			frequency == DatabaseHelper.RECURRING_MONTHLY ? View.VISIBLE : View.GONE
		);

		container_specific.setVisibility(
			frequency == DatabaseHelper.RECURRING_SPECIFIC_DATES ? View.VISIBLE : View.GONE
		);

		container_start_date.setVisibility(
			frequency == DatabaseHelper.RECURRING_SPECIFIC_DATES ? View.GONE : View.VISIBLE
		);
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
				}
			},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void showDatePickerAndAppend() {

		Calendar calendar = Calendar.getInstance();

		new DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new DatePickerDialog.OnDateSetListener() {
				@Override
				public void onDateSet(
					android.widget.DatePicker view, int year, int month, int dayOfMonth) {

					String picked = String.format(
						Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth
					);

					String existing = et_specific_dates.getText().toString().trim();

					if (existing.isEmpty()) {
						et_specific_dates.setText(picked);
					} else {
						et_specific_dates.setText(existing + ", " + picked);
					}
				}
			},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void save() {

		String item = et_item.getText().toString().trim();

		if (item.isEmpty()) {
			Toast.makeText(this, "Enter an expense item", Toast.LENGTH_SHORT).show();
			return;
		}

		double amount;

		try {

			amount = Double.parseDouble(et_amount.getText().toString().trim());

		} catch (Exception e) {

			Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
			return;
		}

		if (amount <= 0) {
			Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
			return;
		}

		String typedParty = actv_party.getText().toString().trim();

		if (typedParty.length() == 0) {
			Toast.makeText(this, "Please select a party", Toast.LENGTH_SHORT).show();
			return;
		}

		Integer partyId = partyIdByName.get(typedParty);

		if (partyId == null) {
			Toast.makeText(this, "Select a valid party", Toast.LENGTH_SHORT).show();
			return;
		}

		Integer dayOfWeek = null;
		Integer dayOfMonth = null;
		String specificDates = null;
		String startDate;

		if (selectedFrequency == DatabaseHelper.RECURRING_WEEKLY) {

			dayOfWeek = spinner_day_of_week.getSelectedItemPosition() + 1;
			startDate = et_start_date.getText().toString().trim();

		} else if (selectedFrequency == DatabaseHelper.RECURRING_MONTHLY) {

			try {

				dayOfMonth = Integer.parseInt(et_day_of_month.getText().toString().trim());

			} catch (Exception e) {

				Toast.makeText(this, "Enter a valid day of month", Toast.LENGTH_SHORT).show();
				return;
			}

			if (dayOfMonth < 1 || dayOfMonth > 31) {
				Toast.makeText(this, "Day of month must be 1-31", Toast.LENGTH_SHORT).show();
				return;
			}

			startDate = et_start_date.getText().toString().trim();

		} else if (selectedFrequency == DatabaseHelper.RECURRING_SPECIFIC_DATES) {

			specificDates = et_specific_dates.getText().toString().trim();

			if (specificDates.isEmpty()) {
				Toast.makeText(this, "Add at least one date", Toast.LENGTH_SHORT).show();
				return;
			}

			startDate = earliestDate(specificDates);

			if (startDate == null) {
				Toast.makeText(this, "Dates must be in yyyy-MM-dd form", Toast.LENGTH_SHORT).show();
				return;
			}

		} else {

			startDate = et_start_date.getText().toString().trim();
		}

		if (startDate.isEmpty()) {
			Toast.makeText(this, "Choose a start date", Toast.LENGTH_SHORT).show();
			return;
		}

		String notes = et_notes.getText().toString().trim();

		if (recurringExpenseId == 0) {

			db.insertRecurringExpense(
				item, amount, notes, partyId, selectedFrequency,
				dayOfWeek, dayOfMonth, specificDates, startDate
			);

		} else {

			db.updateRecurringExpense(
				recurringExpenseId, item, amount, notes, partyId, selectedFrequency,
				dayOfWeek, dayOfMonth, specificDates, startDate
			);

			db.setRecurringExpenseActive(recurringExpenseId, cb_active.isChecked());
		}

		Toast.makeText(this, "Recurring expense saved", Toast.LENGTH_SHORT).show();

		finish();
	}

	// The earliest of a comma-separated yyyy-MM-dd list, used as a
	// Specific Dates rule's own start_date so generateDueRecurringExpenses()
	// can still catch up on a past date the user deliberately added.
	private String earliestDate(String commaSeparated) {

		String earliest = null;

		for (String raw : commaSeparated.split(",")) {

			String date = raw.trim();

			try {
				dateFormat.parse(date);
			} catch (Exception e) {
				return null;
			}

			if (earliest == null || date.compareTo(earliest) < 0) {
				earliest = date;
			}
		}

		return earliest;
	}

	private void confirmDelete() {

		new AlertDialog.Builder(this)
			.setTitle("Delete Recurring Expense")
			.setMessage(
				"Delete this rule? Expenses it already generated stay in your Expenses list."
			)
			.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {

					db.deleteRecurringExpense(recurringExpenseId);

					Toast.makeText(
						RecurringExpenseEditActivity.this, "Deleted", Toast.LENGTH_SHORT
					).show();

					finish();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}
}
