package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class PurchaseCostEditActivity extends Activity {

	private AutoCompleteTextView actv_cost_item;
	private EditText et_pc_amount;
	private EditText et_pc_date;
	private EditText et_pc_time;
	private EditText et_pc_notes;

	private CheckBox cb_pc_to_party;

	private LinearLayout container_purchase_picker;
	private EditText et_search_purchases;
	private ListView lv_purchase_picker;

	private LinearLayout container_existing_links;
	private LinearLayout container_existing_links_list;

	private Button btn_save_purchase_cost;
	private Button btn_delete_purchase_cost;

	private DatabaseHelper db;

	private int purchaseCostId = 0;

	// Only meaningful in new-entry mode - preselects one purchase when
	// opened via "+ Add Purchase Cost" from that purchase's own edit
	// screen (see Transactioneditactivity).
	private int preselectPurchaseId = 0;

	// Set when opened via "+ Add Purchase Cost" from a purchase that
	// isn't saved yet (so there's no purchase_id to preselect, or even
	// pick from a purchase list against - see Transactioneditactivity).
	// Hides the purchase picker entirely and, instead of writing to the
	// database, hands the entered amount/cost item/to_party back as an
	// activity result for the caller to apply once that purchase itself
	// is actually saved.
	private boolean singlePurchaseMode = false;

	private PurchaseSelectionAdapter pickerAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.purchase_cost_edit_activity);

		setTitle("Purchase Cost");

		actv_cost_item = findViewById(R.id.actv_cost_item);
		et_pc_amount = findViewById(R.id.et_pc_amount);
		et_pc_date = findViewById(R.id.et_pc_date);
		et_pc_time = findViewById(R.id.et_pc_time);
		et_pc_notes = findViewById(R.id.et_pc_notes);

		cb_pc_to_party = findViewById(R.id.cb_pc_to_party);

		container_purchase_picker = findViewById(R.id.container_purchase_picker);
		et_search_purchases = findViewById(R.id.et_search_purchases);
		lv_purchase_picker = findViewById(R.id.lv_purchase_picker);

		container_existing_links = findViewById(R.id.container_existing_links);
		container_existing_links_list = findViewById(R.id.container_existing_links_list);

		btn_save_purchase_cost = findViewById(R.id.btn_save_purchase_cost);
		btn_delete_purchase_cost = findViewById(R.id.btn_delete_purchase_cost);

		et_pc_date.setFocusable(false);
		et_pc_date.setClickable(true);
		et_pc_time.setFocusable(false);
		et_pc_time.setClickable(true);

		db = new DatabaseHelper(this);

		loadCostItemAutoComplete();

		et_pc_date.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showDatePicker();
			}
		});

		et_pc_time.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showTimePicker();
			}
		});

		btn_save_purchase_cost.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				save();
			}
		});

		btn_delete_purchase_cost.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				confirmDelete();
			}
		});

		purchaseCostId = getIntent().getIntExtra("purchase_cost_id", 0);
		preselectPurchaseId = getIntent().getIntExtra("preselect_purchase_id", 0);
		singlePurchaseMode = getIntent().getBooleanExtra("single_purchase_mode", false);

		if (purchaseCostId == 0) {

			et_pc_date.setText(
				new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date())
			);

			et_pc_time.setText(
				new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date())
			);

			if (singlePurchaseMode) {

				container_purchase_picker.setVisibility(View.GONE);
				cb_pc_to_party.setVisibility(View.VISIBLE);

			} else {

				setupPurchasePicker();
			}

		} else {

			container_purchase_picker.setVisibility(View.GONE);
			container_existing_links.setVisibility(View.VISIBLE);
			btn_delete_purchase_cost.setVisibility(View.VISIBLE);

			loadExistingCost();
		}
	}

	private void loadCostItemAutoComplete() {

		ArrayList<HashMap<String, Object>> costItems = db.getCostItems();

		ArrayList<String> names = new ArrayList<>();
		HashMap<String, String> subtitles = new HashMap<>();

		for (HashMap<String, Object> costItem : costItems) {

			String name = (String) costItem.get("name");
			names.add(name);
			subtitles.put(name, "");
		}

		TwoLineAutoCompleteAdapter adapter = new TwoLineAutoCompleteAdapter(this, names, subtitles);

		actv_cost_item.setAdapter(adapter);
		actv_cost_item.setThreshold(1);
	}

	private void setupPurchasePicker() {

		ArrayList<HashMap<String, Object>> purchases = db.getPurchases(null, null);

		pickerAdapter = new PurchaseSelectionAdapter(this, purchases);
		lv_purchase_picker.setAdapter(pickerAdapter);

		if (preselectPurchaseId != 0) {
			pickerAdapter.preselect(preselectPurchaseId);
		}

		et_search_purchases.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				pickerAdapter.getFilter().filter(s);
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		et_pc_amount.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {

				double amount;

				try {
					amount = Double.parseDouble(s.toString().trim());
				} catch (Exception e) {
					amount = 0;
				}

				pickerAdapter.setTotalAmount(amount);
			}
		});
	}

	// Existing entries only allow changing the cost item/date/time/notes -
	// the amount and its split across purchases already had their cash/
	// party-balance/landed-cost effects applied, and (same simplification
	// applyExtraCostToItem()'s own comment documents) aren't retroactively
	// corrected here. See DatabaseHelper#deletePurchaseCost() for the same
	// rule on delete.
	private void loadExistingCost() {

		HashMap<String, Object> costItem = db.getPurchaseCostById(purchaseCostId);

		if (costItem == null) {
			finish();
			return;
		}

		setTitle("Purchase Cost");

		int costItemId = (Integer) costItem.get("cost_item_id");
		HashMap<String, Object> costItemRow = db.getCostItemById(costItemId);

		if (costItemRow.get("name") != null) {
			actv_cost_item.setText(String.valueOf(costItemRow.get("name")), false);
		}

		et_pc_amount.setText(AmountFormat.formatPlain((Double) costItem.get("amount")));
		et_pc_amount.setEnabled(false);

		et_pc_date.setText(String.valueOf(costItem.get("date")));
		et_pc_time.setText(String.valueOf(costItem.get("time")));

		if (costItem.get("notes") != null) {
			et_pc_notes.setText(String.valueOf(costItem.get("notes")));
		}

		ArrayList<HashMap<String, Object>> links = db.getPurchaseCostLinks(purchaseCostId);

		container_existing_links_list.removeAllViews();

		for (HashMap<String, Object> link : links) {

			TextView tv = new TextView(this);

			boolean toParty = (Boolean) link.get("to_party");

			tv.setText(
				link.get("party_name") + " - " +
				(link.get("invoice_no") == null ? "no invoice" : link.get("invoice_no")) +
				" - " + AmountFormat.format((Double) link.get("allocated_amount")) +
				" (" + (toParty ? "added to party balance" : "cash outflow") + ")"
			);

			tv.setTextColor(getResources().getColor(R.color.text_secondary));
			tv.setTextSize(13);
			tv.setPadding(0, 6, 0, 6);

			container_existing_links_list.addView(tv);
		}
	}

	private void save() {

		String costItemName = actv_cost_item.getText().toString().trim();

		if (costItemName.isEmpty()) {
			Toast.makeText(this, "Enter a cost item", Toast.LENGTH_SHORT).show();
			return;
		}

		String date = et_pc_date.getText().toString().trim();
		String time = et_pc_time.getText().toString().trim();

		if (date.isEmpty() || time.isEmpty()) {
			Toast.makeText(this, "Choose a date and time", Toast.LENGTH_SHORT).show();
			return;
		}

		String notes = et_pc_notes.getText().toString().trim();

		int costItemId = db.getOrCreateCostItemId(costItemName);

		if (purchaseCostId != 0) {

			// Only the non-financial fields are editable once an entry
			// already exists - see loadExistingCost().
			db.updatePurchaseCostFields(purchaseCostId, costItemId, date, time, notes);

			Toast.makeText(this, "Purchase cost updated", Toast.LENGTH_SHORT).show();

			finish();
			return;
		}

		double amount;

		try {
			amount = Double.parseDouble(et_pc_amount.getText().toString().trim());
		} catch (Exception e) {
			Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show();
			return;
		}

		if (amount <= 0) {
			Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
			return;
		}

		if (singlePurchaseMode) {

			Intent result = new Intent();
			result.putExtra("cost_item_name", costItemName);
			result.putExtra("amount", amount);
			result.putExtra("to_party", cb_pc_to_party.isChecked());
			result.putExtra("date", date);
			result.putExtra("time", time);
			result.putExtra("notes", notes);
			setResult(RESULT_OK, result);

			finish();
			return;
		}

		ArrayList<HashMap<String, Object>> selections = pickerAdapter.getSelections();

		if (selections.isEmpty()) {
			Toast.makeText(this, "Select at least one purchase", Toast.LENGTH_SHORT).show();
			return;
		}

		long newId = db.insertPurchaseCost(costItemId, amount, date, time, notes);

		db.applyPurchaseCostLinks((int) newId, amount, selections);

		Toast.makeText(this, "Purchase cost saved", Toast.LENGTH_SHORT).show();

		Intent result = new Intent();
		result.putExtra("purchase_cost_id", (int) newId);
		result.putExtra("cost_item_name", costItemName);
		result.putExtra("amount", amount);
		setResult(RESULT_OK, result);

		finish();
	}

	private void confirmDelete() {

		new AlertDialog.Builder(this)
			.setTitle("Delete Purchase Cost")
			.setMessage(
				"Delete this entry? Its earlier effect on cash, party " +
				"balances and landed cost is NOT reversed - only the " +
				"record itself is removed."
			)
			.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {

					db.deletePurchaseCost(purchaseCostId);

					Toast.makeText(
						PurchaseCostEditActivity.this, "Deleted", Toast.LENGTH_SHORT
					).show();

					finish();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showDatePicker() {

		Calendar calendar = Calendar.getInstance();

		new DatePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new DatePickerDialog.OnDateSetListener() {
				@Override
				public void onDateSet(
					android.widget.DatePicker view, int year, int month, int dayOfMonth) {

					et_pc_date.setText(
						String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
					);
				}
			},
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH),
			calendar.get(Calendar.DAY_OF_MONTH)
		).show();
	}

	private void showTimePicker() {

		Calendar calendar = Calendar.getInstance();

		new TimePickerDialog(
			this,
			R.style.AppAlertDialogTheme,
			new TimePickerDialog.OnTimeSetListener() {
				@Override
				public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {

					et_pc_time.setText(
						String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
					);
				}
			},
			calendar.get(Calendar.HOUR_OF_DAY),
			calendar.get(Calendar.MINUTE),
			true
		).show();
	}
}
