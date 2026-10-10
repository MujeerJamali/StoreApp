package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class CostItemEditActivity extends Activity {

	private EditText et_cost_item_name;
	private EditText et_cost_item_budget;
	private Button btn_save_cost_item;
	private Button btn_delete_cost_item;

	private DatabaseHelper db;

	private int costItemId = 0;
	private String originalName = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.cost_item_edit_activity);

		setTitle("Cost Item");

		et_cost_item_name = findViewById(R.id.et_cost_item_name);
		et_cost_item_budget = findViewById(R.id.et_cost_item_budget);
		btn_save_cost_item = findViewById(R.id.btn_save_cost_item);
		btn_delete_cost_item = findViewById(R.id.btn_delete_cost_item);

		db = new DatabaseHelper(this);

		costItemId = getIntent().getIntExtra("cost_item_id", 0);

		if (costItemId != 0) {

			HashMap<String, Object> costItem = db.getCostItemById(costItemId);

			if (costItem.get("name") != null) {
				originalName = String.valueOf(costItem.get("name"));
				et_cost_item_name.setText(originalName);
			}

			double existingBudget = 0;

			if (costItem.get("monthly_budget") != null) {
				existingBudget = (Double) costItem.get("monthly_budget");
			}

			if (existingBudget > 0) {
				et_cost_item_budget.setText(AmountFormat.formatPlain(existingBudget));
			}

			btn_delete_cost_item.setVisibility(View.VISIBLE);

		} else {

			// Lets a caller (the Cost Item picker's "+ Add New Cost Item"
			// row) pre-fill the name when the user typed something that
			// didn't match any existing cost item, so they don't have to
			// retype it here.
			String prefillName = getIntent().getStringExtra("cost_item_name");

			if (prefillName != null && !prefillName.trim().isEmpty()) {

				et_cost_item_name.setText(prefillName.trim());
				et_cost_item_name.setSelection(et_cost_item_name.getText().length());
			}
		}

		btn_save_cost_item.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				save();
			}
		});

		btn_delete_cost_item.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				confirmDelete();
			}
		});
	}

	private void save() {

		String name = et_cost_item_name.getText().toString().trim();

		if (name.isEmpty()) {
			Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show();
			return;
		}

		if (!name.equalsIgnoreCase(originalName) && nameExists(name)) {
			Toast.makeText(this, "A cost item with this name already exists", Toast.LENGTH_SHORT)
				.show();
			return;
		}

		double budget = 0;

		try {
			budget = Double.parseDouble(et_cost_item_budget.getText().toString().trim());
		} catch (Exception e) {
		}

		if (budget < 0) {
			budget = 0;
		}

		int savedId;

		if (costItemId == 0) {

			savedId = db.getOrCreateCostItemId(name);

		} else {

			db.updateCostItem(costItemId, name);
			savedId = costItemId;
		}

		db.updateCostItemBudget(savedId, budget);

		Toast.makeText(this, "Cost item saved", Toast.LENGTH_SHORT).show();

		// Harmless when opened via plain startActivity() (e.g. from
		// CostItemsActivity's own list/FAB) - nothing is waiting on a
		// result then. A caller that opened this via startActivityForResult
		// (the "+ Add New Cost Item" row in a Cost Item autocomplete - see
		// TwoLineAutoCompleteAdapter) uses this to select the item it just
		// created once control returns to it.
		Intent result = new Intent();
		result.putExtra("cost_item_id", savedId);
		result.putExtra("cost_item_name", name);
		setResult(RESULT_OK, result);

		finish();
	}

	private boolean nameExists(String name) {

		ArrayList<HashMap<String, Object>> costItems = db.getCostItems();

		for (HashMap<String, Object> costItem : costItems) {

			if (name.equalsIgnoreCase(String.valueOf(costItem.get("name")))) {
				return true;
			}
		}

		return false;
	}

	private void confirmDelete() {

		new AlertDialog.Builder(this)
			.setTitle("Delete Cost Item")
			.setMessage(
				"Delete this cost item? Expenses already recorded against " +
				"it keep their own data."
			)
			.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {

					db.deleteCostItem(costItemId);

					Toast.makeText(
						CostItemEditActivity.this, "Deleted", Toast.LENGTH_SHORT
					).show();

					finish();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}
}
