package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;

import java.util.Calendar;

public class Expenseeditactivity extends Activity {

	private TextView tv_code;

	private EditText et_item;
	private AutoCompleteTextView actv_party;
	private EditText et_date;
	private EditText et_time;
	private EditText et_amount;
	private EditText et_amount_paid;
	private CheckBox cb_full_paid;
	private EditText et_notes;

	private TextView tv_cash_before;
	private TextView tv_cash_after;

	private Button btn_save;

	private DatabaseHelper db;

	private int expenseId = 0;

	// See loadCashBaseline()/updateCashPreview() - the cash balance with
	// this expense's own (original, on-disk) cash effect excluded.
	private double cashBaseline = 0;
	private boolean cashBaselineLoaded = false;

	// Guards against the "Full Paid" checkbox's own listener reacting to
	// a programmatic setText() the same way it would a real user tap -
	// same convention as Transactioneditactivity's cb_full_paid.
	private boolean updatingAmountPaidProgrammatically = false;

	// name -> id, for resolving whatever the user typed/picked in
	// actv_party back to a party row (the field is optional - blank is
	// a valid "no party" choice, only a non-blank value has to resolve).
	private Map<String, Integer> partyIdByName;
	private Map<Integer, String> partyNameById;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.expense_edit_activity);

		setTitle("Expense");

		tv_code = findViewById(R.id.tv_code);

		et_item = findViewById(R.id.et_item);
		actv_party = findViewById(R.id.actv_party);
		et_date = findViewById(R.id.et_date);
		et_time = findViewById(R.id.et_time);
		et_date.setFocusable(false);
		et_date.setClickable(true);

		et_time.setFocusable(false);
		et_time.setClickable(true);
		et_amount = findViewById(R.id.et_amount);
		et_amount_paid = findViewById(R.id.et_amount_paid);
		cb_full_paid = findViewById(R.id.cb_full_paid);
		et_notes = findViewById(R.id.et_notes);
		tv_cash_before = findViewById(R.id.tv_cash_before);
		tv_cash_after = findViewById(R.id.tv_cash_after);

		btn_save = findViewById(R.id.btn_save);

		db = new DatabaseHelper(this);

		loadPartyAutoComplete();

		cb_full_paid.setOnCheckedChangeListener(fullPaidCheckedChangeListener);

		et_amount.addTextChangedListener(
			new android.text.TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(android.text.Editable s) {

					if (cb_full_paid.isChecked()) {
						syncAmountPaidToAmount();
					}
				}
			}
		);

		et_amount_paid.addTextChangedListener(
			new android.text.TextWatcher() {

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}

				@Override
				public void afterTextChanged(android.text.Editable s) {
					updateCashPreview();
				}
			}
		);

		et_date.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					showDatePicker();
				}
			}
		);

		et_time.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					showTimePicker();
				}
			}
		);

		expenseId = getIntent().getIntExtra(
			"expense_id",
			0
		);

		if (expenseId == 0) {

			tv_code.setText(
				db.getNextExpenseCode()
			);

			et_date.setText(
				new SimpleDateFormat(
					"yyyy-MM-dd",
					Locale.getDefault()
				).format(new Date())
			);

			et_time.setText(
				new SimpleDateFormat(
					"HH:mm",
					Locale.getDefault()
				).format(new Date())
			);

			et_amount_paid.setText("0");

			focusAndShowKeyboard(et_item);

			loadCashBaseline(0);

		} else {

			HashMap<String, Object> expense =
				db.getExpenseById(expenseId);

			tv_code.setText(
				expense.get("code").toString()
			);

			et_item.setText(
				expense.get("item").toString()
			);

			et_date.setText(
				expense.get("date").toString()
			);

			et_time.setText(
				expense.get("time").toString()
			);

			et_amount.setText(
				expense.get("amount").toString()
			);

			double loadedAmount = (Double) expense.get("amount");
			double loadedPaidAmount = (Double) expense.get("paid_amount");

			et_amount_paid.setText(
				String.valueOf(loadedPaidAmount)
			);

			cb_full_paid.setOnCheckedChangeListener(null);
			cb_full_paid.setChecked(loadedPaidAmount >= loadedAmount - 0.01);
			cb_full_paid.setOnCheckedChangeListener(fullPaidCheckedChangeListener);

			if (expense.get("notes") != null) {

				et_notes.setText(
					expense.get("notes").toString()
				);
			}

			if (expense.get("party_id") != null) {

				int partyId = (Integer) expense.get("party_id");
				String partyName = partyNameById.get(partyId);

				if (partyName != null) {
					actv_party.setText(partyName, false);
				}
			}

			loadCashBaseline(-loadedPaidAmount);
		}

		btn_save.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					saveExpense();
				}
			}
		);
	}

	// Optional "who was this paid to" field - not every expense has one
	// worth tracking (e.g. a cash purchase from an untracked vendor), so
	// leaving it blank is valid; only a non-blank value has to resolve
	// to a real party (see saveExpense()).
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

	private final CompoundButton.OnCheckedChangeListener fullPaidCheckedChangeListener =
		new CompoundButton.OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

				if (isChecked) {
					syncAmountPaidToAmount();
				} else {
					updatingAmountPaidProgrammatically = true;
					et_amount_paid.setText("0");
					updatingAmountPaidProgrammatically = false;
				}
			}
		};

	private void syncAmountPaidToAmount() {

		updatingAmountPaidProgrammatically = true;

		et_amount_paid.setText(
			et_amount.getText().toString().trim()
		);

		updatingAmountPaidProgrammatically = false;
	}

	// =====================
	// Cash before/after preview - see the matching comment in
	// Transactioneditactivity for the rationale. An expense's cash
	// effect is always an outflow of its paid amount (the rest, if any,
	// stays owed on credit).
	// =====================
	private void loadCashBaseline(final double originalCashImpact) {

		new Thread(new Runnable() {

				@Override
				public void run() {

					final double balance = db.getCashBalance();

					runOnUiThread(new Runnable() {

							@Override
							public void run() {

								cashBaseline = balance - originalCashImpact;
								cashBaselineLoaded = true;

								updateCashPreview();
							}
						});
				}
			}).start();
	}

	private void updateCashPreview() {

		if (!cashBaselineLoaded || tv_cash_before == null || tv_cash_after == null) {
			return;
		}

		double paid;

		try {

			paid = Double.parseDouble(et_amount_paid.getText().toString().trim());

		} catch (Exception e) {

			paid = 0;
		}

		tv_cash_before.setText(
			AmountFormat.format(cashBaseline)
		);

		tv_cash_after.setText(
			AmountFormat.format(cashBaseline - paid)
		);
	}

	private void focusAndShowKeyboard(final View target) {

		target.requestFocus();

		target.postDelayed(
			new Runnable() {

				@Override
				public void run() {

					android.view.inputmethod.InputMethodManager imm =
						(android.view.inputmethod.InputMethodManager)
						getSystemService(INPUT_METHOD_SERVICE);

					if (imm != null) {

						imm.showSoftInput(
							target,
							android.view.inputmethod.InputMethodManager.SHOW_FORCED
						);
					}
				}
			},
			150
		);
	}

	private void saveExpense() {

		String item =
			et_item.getText().toString().trim();

		if (item.isEmpty()) {

			android.widget.Toast.makeText(
				this,
				"Enter expense item",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		if (et_amount.getText().toString().trim().isEmpty()) {

			android.widget.Toast.makeText(
				this,
				"Enter amount",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		double amount;

		try {

			amount = Double.parseDouble(
				et_amount.getText().toString().trim()
			);

		} catch (Exception e) {

			android.widget.Toast.makeText(
				this,
				"Enter a valid amount",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		double paidAmount = 0;

		try {

			paidAmount = Double.parseDouble(
				et_amount_paid.getText().toString().trim()
			);

		} catch (Exception e) {
		}

		String typedParty = actv_party.getText().toString().trim();
		Integer partyId = null;

		if (typedParty.length() > 0) {

			partyId = partyIdByName.get(typedParty);

			if (partyId == null) {

				Toast.makeText(
					this,
					"Select a valid party, or leave it blank",
					Toast.LENGTH_SHORT
				).show();

				return;
			}
		}

		boolean success;

		if (expenseId == 0) {

			success = db.insertExpense(

				item,

				et_date.getText().toString().trim(),

				et_time.getText().toString().trim(),

				amount,

				paidAmount,

				et_notes.getText().toString().trim(),

				partyId

			) != -1;

		} else {

			success = db.updateExpense(

				expenseId,

				item,

				et_date.getText().toString().trim(),

				et_time.getText().toString().trim(),

				amount,

				paidAmount,

				et_notes.getText().toString().trim(),

				partyId
			);
		}

		if (success) {

			Toast.makeText(
				this,
				"Expense saved successfully.",
				Toast.LENGTH_SHORT
			).show();

			finish();

		} else {

			Toast.makeText(
				this,
				"Failed to save expense.",
				Toast.LENGTH_SHORT
			).show();
		}
	}
	
	private void showDatePicker() {

		Calendar calendar = Calendar.getInstance();

		DatePickerDialog dialog =
			new DatePickerDialog(

			this,

			R.style.AppAlertDialogTheme,

			new DatePickerDialog.OnDateSetListener() {

				@Override
				public void onDateSet(
					android.widget.DatePicker view,
					int year,
					int month,
					int dayOfMonth) {

					et_date.setText(

						String.format(

							Locale.getDefault(),

							"%04d-%02d-%02d",

							year,

							month + 1,

							dayOfMonth
						)
					);
				}
			},

			calendar.get(Calendar.YEAR),

			calendar.get(Calendar.MONTH),

			calendar.get(Calendar.DAY_OF_MONTH)
		);

		dialog.show();
	}

	private void showTimePicker() {

		Calendar calendar = Calendar.getInstance();

		TimePickerDialog dialog =
			new TimePickerDialog(

			this,

			R.style.AppAlertDialogTheme,

			new TimePickerDialog.OnTimeSetListener() {

				@Override
				public void onTimeSet(
					android.widget.TimePicker view,
					int hourOfDay,
					int minute) {

					et_time.setText(

						String.format(

							Locale.getDefault(),

							"%02d:%02d",

							hourOfDay,

							minute
						)
					);
				}
			},

			calendar.get(Calendar.HOUR_OF_DAY),

			calendar.get(Calendar.MINUTE),

			true
		);

		dialog.show();
	}
	
}
