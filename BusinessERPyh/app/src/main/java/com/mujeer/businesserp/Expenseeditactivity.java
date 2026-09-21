package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;

import java.util.Calendar;

public class Expenseeditactivity extends Activity {

	private TextView tv_code;

	private EditText et_item;
	private EditText et_date;
	private EditText et_time;
	private EditText et_amount;
	private EditText et_notes;

	private Button btn_save;

	private DatabaseHelper db;

	private int expenseId = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.expense_edit_activity);

		setTitle("Expense");

		tv_code = findViewById(R.id.tv_code);

		et_item = findViewById(R.id.et_item);
		et_date = findViewById(R.id.et_date);
		et_time = findViewById(R.id.et_time);
		et_date.setFocusable(false);
		et_date.setClickable(true);

		et_time.setFocusable(false);
		et_time.setClickable(true);
		et_amount = findViewById(R.id.et_amount);
		et_notes = findViewById(R.id.et_notes);

		btn_save = findViewById(R.id.btn_save);

		db = new DatabaseHelper(this);
		
		
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

			focusAndShowKeyboard(et_item);

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

			if (expense.get("notes") != null) {

				et_notes.setText(
					expense.get("notes").toString()
				);
			}
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

		double amount = Double.parseDouble(
			et_amount.getText().toString().trim()
		);

		boolean success;

		if (expenseId == 0) {

			success = db.insertExpense(

				item,

				et_date.getText().toString().trim(),

				et_time.getText().toString().trim(),

				amount,

				et_notes.getText().toString().trim()

			) != -1;

		} else {

			success = db.updateExpense(

				expenseId,

				item,

				et_date.getText().toString().trim(),

				et_time.getText().toString().trim(),

				amount,

				et_notes.getText().toString().trim()
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
