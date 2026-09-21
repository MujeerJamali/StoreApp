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

	private Button btn_save;

	private DatabaseHelper db;

	private int paymentId = 0;

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
		et_amount = findViewById(R.id.et_amount);
		et_notes = findViewById(R.id.et_notes);
		btn_save = findViewById(R.id.btn_save);

		db = new DatabaseHelper(this);

		paymentId = getIntent().getIntExtra(
			"payment_id",
			0
		);

		selectedType = getIntent().getIntExtra(
			"payment_type",
			DatabaseHelper.PAYMENT_IN
		);

		setSelectedType(selectedType);

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

			return String.format(
				"Balance: %.2f (Receivable)",
				balance
			);

		} else if (balance < 0) {

			return String.format(
				"Balance: %.2f (Payable)",
				Math.abs(balance)
			);
		}

		return "Balance: 0.00 (Settled)";
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

		double amount = Double.parseDouble(
			et_amount.getText().toString().trim()
		);

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
	
	
	
	
}
