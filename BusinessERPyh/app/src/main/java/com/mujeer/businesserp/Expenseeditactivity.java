package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
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

	// "+ Add New Cost Item" row - see loadCostItemAutoComplete()/
	// openAddCostItem()/onActivityResult().
	private static final int REQUEST_ADD_COST_ITEM = 6001;

	private TextView tv_code;

	private AutoCompleteTextView et_item;
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
	private Button btn_save_draft;

	private DatabaseHelper db;

	private int expenseId = 0;

	// Set when opened from the Drafts list - see saveDraft()/loadDraft().
	private int draftId = -1;

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

	// name -> id for the Cost Item field, same purpose as partyIdByName
	// above - saveExpense() requires the typed text to resolve to one of
	// these rather than silently creating a new Cost Item (see
	// loadCostItemAutoComplete()/openAddCostItem() for how a genuinely
	// new one gets added instead).
	private Map<String, Integer> costItemIdByName;
	private TwoLineAutoCompleteAdapter costItemAdapter;

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
		btn_save_draft = findViewById(R.id.btn_save_draft);

		db = new DatabaseHelper(this);

		// Created (once) before loadPartyAutoComplete() so it's already
		// in the in-memory list below - a new Expense defaults its party
		// to this, since most expenses aren't billed to a real party.
		db.getOrCreatePartyId("Cash Expenses");

		loadPartyAutoComplete();
		loadCostItemAutoComplete();

		et_item.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					if (costItemAdapter != null && costItemAdapter.isAddNewPosition(position)) {

						et_item.setText("", false);
						openAddCostItem();
					}
				}
			});

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

		draftId = getIntent().getIntExtra(
			"draft_id",
			-1
		);

		btn_save_draft.setVisibility(expenseId == 0 ? View.VISIBLE : View.GONE);

		btn_save_draft.setOnClickListener(
			new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					saveDraft();
				}
			}
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

			actv_party.setText("Cash Expenses", false);

			focusAndShowKeyboard(et_item);

			if (draftId != -1) {
				loadDraft(draftId);
			}

			loadCashBaseline(0);

		} else {

			HashMap<String, Object> expense =
				db.getExpenseById(expenseId);

			tv_code.setText(
				expense.get("code").toString()
			);

			et_item.setText(
				expense.get("item").toString(),
				false
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

	// Item is a reusable Cost Item (Petrol, Shipping, Packaging, ...) -
	// same list Purchase Costs draws from. The dropdown always carries
	// one extra "+ Add New Cost Item" row at the bottom (even when
	// nothing/only-wrong things match what's typed) - tapping it opens
	// CostItemEditActivity and, once a new one is actually created there,
	// selects it here (see openAddCostItem()/onActivityResult()).
	// Typing a name that doesn't exist and just hitting Save is NOT
	// enough on its own - see saveExpense()'s validation.
	private void loadCostItemAutoComplete() {

		ArrayList<HashMap<String, Object>> costItems = db.getCostItems();

		ArrayList<String> costItemNames = new ArrayList<String>();
		HashMap<String, String> costItemSubtitles = new HashMap<String, String>();

		costItemIdByName = new HashMap<String, Integer>();

		for (HashMap<String, Object> costItem : costItems) {

			String name = (String) costItem.get("name");
			int id = (Integer) costItem.get("id");

			costItemNames.add(name);
			costItemSubtitles.put(name, "");
			costItemIdByName.put(name, id);
		}

		costItemAdapter = new TwoLineAutoCompleteAdapter(
			this, costItemNames, costItemSubtitles, "+ Add New Cost Item"
		);

		et_item.setAdapter(costItemAdapter);
		et_item.setThreshold(1);
	}

	private void openAddCostItem() {

		Intent intent = new Intent(this, CostItemEditActivity.class);
		intent.putExtra("cost_item_id", 0);

		startActivityForResult(intent, REQUEST_ADD_COST_ITEM);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_ADD_COST_ITEM && resultCode == RESULT_OK && data != null) {

			String newName = data.getStringExtra("cost_item_name");

			loadCostItemAutoComplete();

			if (newName != null) {
				et_item.setText(newName, false);
			}
		}
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

	// =====================
	// Restores whatever was on screen when this expense was parked as a
	// draft - see saveDraft() for what gets written.
	// =====================
	private void loadDraft(int id) {

		HashMap<String, Object> draftRow = db.getDraftById(id);

		if (draftRow == null) {
			return;
		}

		HashMap<String, Object> data = DraftCodec.decode((String) draftRow.get("data"));

		if (data.get("item") != null) {
			et_item.setText((String) data.get("item"), false);
		}

		if (data.get("party_name") != null) {
			actv_party.setText((String) data.get("party_name"));
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

		if (data.get("amount_paid") != null) {
			et_amount_paid.setText((String) data.get("amount_paid"));
		}

		if (data.get("notes") != null) {
			et_notes.setText((String) data.get("notes"));
		}
	}

	// =====================
	// Parks whatever is currently on screen as a draft - none of
	// saveExpense()'s validation applies here, a draft is allowed to be
	// incomplete until it's actually saved for real.
	// =====================
	private void saveDraft() {

		HashMap<String, Object> data = new HashMap<String, Object>();

		data.put("item", et_item.getText().toString().trim());
		data.put("party_name", actv_party.getText().toString().trim());
		data.put("date", et_date.getText().toString());
		data.put("time", et_time.getText().toString());
		data.put("amount", et_amount.getText().toString());
		data.put("amount_paid", et_amount_paid.getText().toString());
		data.put("notes", et_notes.getText().toString());

		String encoded = DraftCodec.encode(data);

		if (encoded == null) {

			Toast.makeText(this, "Could not save draft", Toast.LENGTH_SHORT).show();
			return;
		}

		String itemLabel = et_item.getText().toString().trim();

		if (itemLabel.isEmpty()) {
			itemLabel = "Untitled";
		}

		String label = "Expense - " + itemLabel;

		db.insertDraft(
			DatabaseHelper.DRAFT_TYPE_EXPENSE,
			label,
			encoded,
			et_date.getText().toString(),
			et_time.getText().toString()
		);

		Toast.makeText(this, "Saved as draft", Toast.LENGTH_SHORT).show();

		finish();
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

		// Must resolve to an existing Cost Item, same requirement the
		// Party field enforces below - a typo or an unrecognized name
		// doesn't silently create a new category (see loadCostItemAutoComplete()'s
		// "+ Add New Cost Item" row for how to actually add one).
		if (costItemIdByName == null || !costItemIdByName.containsKey(item)) {

			Toast.makeText(
				this,
				"Select a valid cost item, or tap \"+ Add New Cost Item\" first",
				Toast.LENGTH_LONG
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

		if (!cashBaselineLoaded) {

			android.widget.Toast.makeText(
				this,
				"Still checking cash balance - try again in a moment",
				android.widget.Toast.LENGTH_SHORT
			).show();

			return;
		}

		if (paidAmount > 0 && cashBaseline - paidAmount < 0) {

			android.widget.Toast.makeText(
				this,
				"This would take cash balance below 0 - reduce Amount " +
				"Paid or add cash first",
				android.widget.Toast.LENGTH_LONG
			).show();

			return;
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

			if (expenseId == 0 && draftId != -1) {
				db.deleteDraft(draftId);
				draftId = -1;
			}

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
