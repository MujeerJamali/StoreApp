package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

// Links an existing Expense (Petrol, Shipping, Packaging, ...) to one or
// more purchases as their landed cost - replaces the old separate
// Purchase Costs entity: the "cost event" is just the picked expense
// itself, already carrying its own item/amount/date/party/paid_amount.
//
// Two steps shown in the same list (its adapter swaps between them):
// pick an existing, not-yet-linked expense (or create a new one on the
// spot via "+ Add New Expense"), then - unless this was opened from a
// purchase that isn't saved yet, see singlePurchaseMode - choose which
// purchase(s) to split its amount across.
public class LinkExpenseActivity extends Activity {

	private static final int REQUEST_ADD_EXPENSE = 7001;

	private TextView tv_step_label;
	private View container_selected_expense;
	private TextView tv_selected_expense;
	private Button btn_change_expense;
	private EditText et_search;
	private TextView tv_empty;
	private ListView lv_picker;
	private Button btn_link;

	private DatabaseHelper db;

	// Only meaningful when opened from an already-saved purchase's own
	// edit screen - preselects it in step 2's purchase list.
	private int preselectPurchaseId = 0;

	// Set when opened from a purchase that isn't saved yet (no id to
	// preselect, or even link against yet) - skips step 2 entirely and
	// hands the whole picked expense back as a pending selection for the
	// caller to apply once that purchase is actually saved (see
	// Transactioneditactivity).
	private boolean singlePurchaseMode = false;

	private HashMap<String, Object> selectedExpense = null;

	private ExpensePickerAdapter expensePickerAdapter;
	private PurchaseSelectionAdapter purchaseSelectionAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.link_expense_activity);

		setTitle("Link Expense");

		tv_step_label = findViewById(R.id.tv_step_label);
		container_selected_expense = findViewById(R.id.container_selected_expense);
		tv_selected_expense = findViewById(R.id.tv_selected_expense);
		btn_change_expense = findViewById(R.id.btn_change_expense);
		et_search = findViewById(R.id.et_search);
		tv_empty = findViewById(R.id.tv_empty);
		lv_picker = findViewById(R.id.lv_picker);
		btn_link = findViewById(R.id.btn_link);

		db = new DatabaseHelper(this);

		preselectPurchaseId = getIntent().getIntExtra("preselect_purchase_id", 0);
		singlePurchaseMode = getIntent().getBooleanExtra("single_purchase_mode", false);

		lv_picker.setEmptyView(tv_empty);

		et_search.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

				// Both adapters can genuinely be null here, not just
				// transiently during setup: expensePickerAdapter is null
				// until resetToPickExpense()'s first call, and
				// purchaseSelectionAdapter is null until step 2 is reached
				// - which never happens at all in singlePurchaseMode, where
				// onExpensePicked() clears et_search (see its own comment)
				// while selectedExpense is already non-null. Guard both
				// rather than relying on call-site ordering elsewhere.
				if (selectedExpense == null) {

					if (expensePickerAdapter != null) {
						expensePickerAdapter.getFilter().filter(s);
					}

				} else if (purchaseSelectionAdapter != null) {

					purchaseSelectionAdapter.getFilter().filter(s);
				}
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		lv_picker.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				// Step 2's rows are self-contained checkboxes (see
				// PurchaseSelectionAdapter) - nothing to do here once an
				// expense is already picked.
				if (selectedExpense != null) {
					return;
				}

				if (expensePickerAdapter.isAddNewPosition(position)) {
					openAddExpense();
					return;
				}

				selectedExpense =
					(HashMap<String, Object>) expensePickerAdapter.getItem(position);

				onExpensePicked();
			}
		});

		btn_change_expense.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				resetToPickExpense();
			}
		});

		btn_link.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				link();
			}
		});

		resetToPickExpense();
	}

	private void resetToPickExpense() {

		selectedExpense = null;

		container_selected_expense.setVisibility(View.GONE);
		btn_link.setVisibility(View.GONE);

		tv_step_label.setText("Step 1: Pick an Expense");

		// Build and attach the adapter BEFORE clearing et_search below -
		// its TextWatcher (see onCreate()) fires on setText() and reads
		// expensePickerAdapter, which is null until this assignment. On
		// the very first call (from onCreate()) that order would otherwise
		// NPE before the adapter ever exists.
		ArrayList<HashMap<String, Object>> expenses = db.getUnlinkedExpenses(null);

		expensePickerAdapter = new ExpensePickerAdapter(this, expenses);
		lv_picker.setAdapter(expensePickerAdapter);

		et_search.setText("");
		et_search.setHint("Search expenses...");
	}

	private void openAddExpense() {

		startActivityForResult(
			new Intent(this, Expenseeditactivity.class), REQUEST_ADD_EXPENSE
		);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_ADD_EXPENSE && resultCode == RESULT_OK && data != null) {

			int newExpenseId = data.getIntExtra("expense_id", -1);

			if (newExpenseId != -1) {

				selectedExpense = db.getExpenseById(newExpenseId);
				onExpensePicked();
			}
		}
	}

	private void onExpensePicked() {

		container_selected_expense.setVisibility(View.VISIBLE);

		tv_selected_expense.setText(
			selectedExpense.get("item") + " - " +
			AmountFormat.format((Double) selectedExpense.get("amount")) +
			" - " + selectedExpense.get("date")
		);

		// selectedExpense is already non-null by this point, so the
		// TextWatcher's else-branch would run here - purchaseSelectionAdapter
		// isn't created yet (and in singlePurchaseMode never will be), which
		// is exactly why that branch is null-guarded (see onCreate()).
		et_search.setText("");

		if (singlePurchaseMode) {

			// Nothing left to pick - the whole expense goes to the one
			// purchase this was opened from, once it's actually saved.
			tv_step_label.setText("");
			lv_picker.setAdapter(null);
			btn_link.setText("Link to This Purchase");
			btn_link.setVisibility(View.VISIBLE);
			return;
		}

		tv_step_label.setText("Step 2: Split Across Purchase(s)");
		et_search.setHint("Search by party or invoice...");

		ArrayList<HashMap<String, Object>> purchases = db.getPurchases(null, null);

		purchaseSelectionAdapter = new PurchaseSelectionAdapter(this, purchases);
		purchaseSelectionAdapter.setTotalAmount((Double) selectedExpense.get("amount"));

		if (preselectPurchaseId != 0) {
			purchaseSelectionAdapter.preselect(preselectPurchaseId);
		}

		lv_picker.setAdapter(purchaseSelectionAdapter);

		btn_link.setText("Link Expense");
		btn_link.setVisibility(View.VISIBLE);
	}

	private void link() {

		if (selectedExpense == null) {
			return;
		}

		int expenseId = (Integer) selectedExpense.get("id");
		double amount = (Double) selectedExpense.get("amount");

		if (singlePurchaseMode) {

			Intent result = new Intent();
			result.putExtra("expense_id", expenseId);
			result.putExtra("amount", amount);
			result.putExtra("item", String.valueOf(selectedExpense.get("item")));
			result.putExtra("date", String.valueOf(selectedExpense.get("date")));
			setResult(RESULT_OK, result);

			finish();
			return;
		}

		ArrayList<HashMap<String, Object>> selections = purchaseSelectionAdapter.getSelections();

		if (selections.isEmpty()) {
			Toast.makeText(this, "Select at least one purchase", Toast.LENGTH_SHORT).show();
			return;
		}

		db.applyExpensePurchaseLinks(expenseId, amount, selections);

		Toast.makeText(this, "Expense linked", Toast.LENGTH_SHORT).show();

		setResult(RESULT_OK);
		finish();
	}
}
