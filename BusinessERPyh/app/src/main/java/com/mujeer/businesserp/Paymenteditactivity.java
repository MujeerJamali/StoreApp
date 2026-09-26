package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class Paymenteditactivity extends Activity {

	private TextView tv_code;

	private Button btn_type_in;
	private Button btn_type_out;

	private int selectedType = DatabaseHelper.PAYMENT_IN;

	private AutoCompleteTextView et_party;

	private EditText et_date;
	private EditText et_time;
	private EditText et_amount;
	private EditText et_notes;

	private TextView tv_cash_before;
	private TextView tv_cash_after;

	private Button btn_save;
	private Button btn_save_draft;

	private DatabaseHelper db;

	private int paymentId = 0;

	// Set when opened from the Drafts list - see saveDraft()/loadDraft().
	private int draftId = -1;

	// See loadCashBaseline()/updateCashPreview() - the cash balance with
	// this payment's own (original, on-disk) cash effect excluded, so
	// "Cash After" can be recomputed locally as the user edits the
	// amount or flips Payment In/Out.
	private double cashBaseline = 0;
	private boolean cashBaselineLoaded = false;

	private ArrayList<HashMap<String, Object>> partyList =
	new ArrayList<HashMap<String, Object>>();

	private ArrayAdapter<String> partyAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.payment_edit_activity);

		setTitle("Payment");

		tv_code = findViewById(R.id.tv_code);
		btn_type_in = findViewById(R.id.btn_type_in);
		btn_type_out = findViewById(R.id.btn_type_out);
		et_party = findViewById(R.id.et_party);
		et_date = findViewById(R.id.et_date);
		et_time = findViewById(R.id.et_time);
		et_date.setFocusable(false);
		et_date.setClickable(true);
		et_time.setFocusable(false);
		et_time.setClickable(true);

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

		et_amount = findViewById(R.id.et_amount);
		et_notes = findViewById(R.id.et_notes);
		tv_cash_before = findViewById(R.id.tv_cash_before);
		tv_cash_after = findViewById(R.id.tv_cash_after);
		btn_save = findViewById(R.id.btn_save);
		btn_save_draft = findViewById(R.id.btn_save_draft);

		et_amount.addTextChangedListener(
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

		db = new DatabaseHelper(this);

		paymentId = getIntent().getIntExtra(
			"payment_id",
			0
		);

		selectedType = getIntent().getIntExtra(
			"payment_type",
			DatabaseHelper.PAYMENT_IN
		);

		draftId = getIntent().getIntExtra(
			"draft_id",
			-1
		);

		setSelectedType(selectedType);

		btn_save_draft.setVisibility(paymentId == 0 ? View.VISIBLE : View.GONE);

		btn_save_draft.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					saveDraft();
				}
			}
		);

		btn_type_in.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					setSelectedType(DatabaseHelper.PAYMENT_IN);
				}
			}
		);

		btn_type_out.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					setSelectedType(DatabaseHelper.PAYMENT_OUT);
				}
			}
		);

		partyList = db.getParties();

		ArrayList<String> partyNames =
			new ArrayList<String>();

		java.util.Map<String, String> partySubtitles =
			new java.util.HashMap<String, String>();

		for (HashMap<String, Object> party : partyList) {

			String name = party.get("name").toString();

			partyNames.add(name);

			partySubtitles.put(
				name,
				formatPartyBalanceSubtitle(party.get("balance"))
			);
		}

		partyAdapter =
			new TwoLineAutoCompleteAdapter(
			this,
			partyNames,
			partySubtitles
		);

		et_party.setAdapter(partyAdapter);

		et_party.setThreshold(1);

		et_party.setOnItemClickListener(
			new AdapterView.OnItemClickListener() {

				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {

					et_party.setText(
						parent.getItemAtPosition(position).toString()
					);

					et_party.setSelection(
						et_party.getText().length()
					);
				}
			}
		);

		if (paymentId == 0) {

			tv_code.setText(
				db.getNextPaymentCode()
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

			focusAndShowKeyboard(et_party);

			if (draftId != -1) {
				loadDraft(draftId);
			}

			loadCashBaseline(0);

		} else {

			HashMap<String, Object> payment =
				db.getPaymentById(paymentId);

			tv_code.setText(
				payment.get("code").toString()
			);

			setSelectedType(
				Integer.parseInt(
					payment.get("type").toString()
				)
			);

			et_party.setText(
				payment.get("party_name").toString()
			);

			et_date.setText(
				payment.get("date").toString()
			);

			et_time.setText(
				payment.get("time").toString()
			);

			et_amount.setText(
				payment.get("amount").toString()
			);

			if (payment.get("notes") != null) {

				et_notes.setText(
					payment.get("notes").toString()
				);
			}

			int originalType = Integer.parseInt(payment.get("type").toString());
			double originalAmount = Double.parseDouble(payment.get("amount").toString());

			loadCashBaseline(
				originalType == DatabaseHelper.PAYMENT_IN ? originalAmount : -originalAmount
			);
		}

		btn_save.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {

					savePayment();
				}
			}
		);
	}

	private void setSelectedType(int type) {

		selectedType = type;

		if (type == DatabaseHelper.PAYMENT_IN) {

			btn_type_in.setBackgroundResource(R.drawable.bg_button_primary);
			btn_type_in.setTextColor(getResources().getColor(R.color.text_on_primary));

			btn_type_out.setBackgroundResource(R.drawable.bg_button_outline);
			btn_type_out.setTextColor(getResources().getColor(R.color.primary));

		} else {

			btn_type_out.setBackgroundResource(R.drawable.bg_button_primary);
			btn_type_out.setTextColor(getResources().getColor(R.color.text_on_primary));

			btn_type_in.setBackgroundResource(R.drawable.bg_button_outline);
			btn_type_in.setTextColor(getResources().getColor(R.color.primary));
		}

		updateCashPreview();
	}

	// =====================
	// Cash before/after preview - see the matching comment in
	// Transactioneditactivity for the rationale (one background query at
	// load time, then pure local arithmetic as the form changes). A
	// payment is always fully cash, so its impact is just the signed
	// amount.
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

		double amount;

		try {

			amount = Double.parseDouble(et_amount.getText().toString().trim());

		} catch (Exception e) {

			amount = 0;
		}

		double impact = selectedType == DatabaseHelper.PAYMENT_IN ? amount : -amount;

		tv_cash_before.setText(
			AmountFormat.format(cashBaseline)
		);

		tv_cash_after.setText(
			AmountFormat.format(cashBaseline + impact)
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

	private int getSelectedPartyId() {

		String partyName =
			et_party.getText().toString().trim();

		for (HashMap<String, Object> party : partyList) {

			if (party.get("name").toString().equals(partyName)) {

				return Integer.parseInt(
					party.get("id").toString()
				);
			}
		}

		return -1;
	}

	private String formatPartyBalanceSubtitle(Object balanceObj) {

		double balance =
			balanceObj == null ?
			0 :
			Double.parseDouble(balanceObj.toString());

		if (balance > 0) {

			return "Balance: " + AmountFormat.format(balance) + " (Receivable)";

		} else if (balance < 0) {

			return "Balance: " + AmountFormat.format(Math.abs(balance)) + " (Payable)";
		}

		return "Balance: 0 (Settled)";
	}

	// =====================
	// Restores whatever was on screen when this payment was parked as a
	// draft - see saveDraft() for what gets written.
	// =====================
	private void loadDraft(int id) {

		HashMap<String, Object> draftRow = db.getDraftById(id);

		if (draftRow == null) {
			return;
		}

		HashMap<String, Object> data = DraftCodec.decode((String) draftRow.get("data"));

		if (data.get("type") != null) {
			setSelectedType((Integer) data.get("type"));
		}

		if (data.get("party_name") != null) {
			et_party.setText((String) data.get("party_name"));
		}

		if (data.get("date") != null) {
			et_date.setText((String) data.get("date"));
		}

		if (data.get("time") != null) {
			et_time.setText((String) data.get("time"));
		}

		if (data.get("amount") != null) {
			et_amount.setText((String) data.get("amount"));
		}

		if (data.get("notes") != null) {
			et_notes.setText((String) data.get("notes"));
		}
	}

	// =====================
	// Parks whatever is currently on screen as a draft - none of
	// savePayment()'s validation applies here, a draft is allowed to be
	// incomplete until it's actually saved for real.
	// =====================
	private void saveDraft() {

		HashMap<String, Object> data = new HashMap<String, Object>();

		data.put("type", selectedType);
		data.put("party_name", et_party.getText().toString().trim());
		data.put("date", et_date.getText().toString());
		data.put("time", et_time.getText().toString());
		data.put("amount", et_amount.getText().toString());
		data.put("notes", et_notes.getText().toString());

		String encoded = DraftCodec.encode(data);

		if (encoded == null) {

			Toast.makeText(this, "Could not save draft", Toast.LENGTH_SHORT).show();
			return;
		}

		String partyLabel = et_party.getText().toString().trim();

		if (partyLabel.isEmpty()) {
			partyLabel = "No party";
		}

		String label =
			(selectedType == DatabaseHelper.PAYMENT_IN ? "Payment In" : "Payment Out") +
			" - " + partyLabel;

		db.insertDraft(
			DatabaseHelper.DRAFT_TYPE_PAYMENT,
			label,
			encoded,
			et_date.getText().toString(),
			et_time.getText().toString()
		);

		Toast.makeText(this, "Saved as draft", Toast.LENGTH_SHORT).show();

		finish();
	}

	private void savePayment() {

		int partyId = getSelectedPartyId();

		if (partyId == -1) {

			android.widget.Toast.makeText(
				this,
				"Select a valid party",
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

		if (selectedType == DatabaseHelper.PAYMENT_OUT) {

			if (!cashBaselineLoaded) {

				android.widget.Toast.makeText(
					this,
					"Still checking cash balance - try again in a moment",
					android.widget.Toast.LENGTH_SHORT
				).show();

				return;
			}

			if (amount > 0 && cashBaseline - amount < 0) {

				android.widget.Toast.makeText(
					this,
					"This would take cash balance below 0 - reduce the " +
					"amount or add cash first",
					android.widget.Toast.LENGTH_LONG
				).show();

				return;
			}
		}

		boolean success;

		if (paymentId == 0) {

			success = db.insertPayment(

				selectedType,

				partyId,

				et_date.getText().toString().trim(),

				et_time.getText().toString().trim(),

				amount,

				et_notes.getText().toString().trim()

			) != -1;

		} else {

			success = db.updatePayment(

				paymentId,

				selectedType,

				partyId,

				et_date.getText().toString().trim(),

				et_time.getText().toString().trim(),

				amount,

				et_notes.getText().toString().trim()
			);
		}

		if (success) {

			if (paymentId == 0 && draftId != -1) {
				db.deleteDraft(draftId);
				draftId = -1;
			}

			Toast.makeText(
				this,
				"Payment saved successfully.",
				Toast.LENGTH_SHORT
			).show();

			finish();

		} else {

			Toast.makeText(
				this,
				"Failed to save payment.",
				Toast.LENGTH_SHORT
			).show();
		}
	}

	private void showDatePicker() {

		java.util.Calendar calendar = java.util.Calendar.getInstance();

		try {
			calendar.setTime(
				new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
				.parse(et_date.getText().toString())
			);
		} catch (Exception e) {
		}

		new android.app.DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new android.app.DatePickerDialog.OnDateSetListener() {

				@Override
				public void onDateSet(
					android.widget.DatePicker view, int year, int month, int dayOfMonth) {

					et_date.setText(
						String.format(
							Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth
						)
					);
				}
			},
			calendar.get(java.util.Calendar.YEAR),
			calendar.get(java.util.Calendar.MONTH),
			calendar.get(java.util.Calendar.DAY_OF_MONTH)
		).show();
	}

	private void showTimePicker() {

		java.util.Calendar calendar = java.util.Calendar.getInstance();

		try {
			calendar.setTime(
				new SimpleDateFormat("HH:mm", Locale.getDefault())
				.parse(et_time.getText().toString())
			);
		} catch (Exception e) {
		}

		new android.app.TimePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new android.app.TimePickerDialog.OnTimeSetListener() {

				@Override
				public void onTimeSet(
					android.widget.TimePicker view, int hourOfDay, int minute) {

					et_time.setText(
						String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
					);
				}
			},
			calendar.get(java.util.Calendar.HOUR_OF_DAY),
			calendar.get(java.util.Calendar.MINUTE),
			true
		).show();
	}
}
