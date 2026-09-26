package com.mujeer.businesserp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class CostItemEditActivity extends Activity {

	private EditText et_cost_item_name;
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

			btn_delete_cost_item.setVisibility(View.VISIBLE);
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

		if (costItemId == 0) {

			db.getOrCreateCostItemId(name);

		} else {

			db.updateCostItem(costItemId, name);
		}

		Toast.makeText(this, "Cost item saved", Toast.LENGTH_SHORT).show();

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
				"Delete this cost item? Purchase costs and expenses already " +
				"recorded against it keep their own data."
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
