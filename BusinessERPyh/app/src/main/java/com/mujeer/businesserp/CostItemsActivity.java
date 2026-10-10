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

import java.util.ArrayList;
import java.util.HashMap;

public class CostItemsActivity extends Activity {

	private EditText et_search_cost_item;
	private TextView tv_no_cost_items;
	private Button btn_add_cost_item;
	private ListView lv_cost_items;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> costItemList;
	private CostItemAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.cost_items_activity);

		setTitle("Cost Items");

		et_search_cost_item = findViewById(R.id.et_search_cost_item);
		tv_no_cost_items = findViewById(R.id.tv_no_cost_items);
		btn_add_cost_item = findViewById(R.id.btn_add_cost_item);
		lv_cost_items = findViewById(R.id.lv_cost_items);

		lv_cost_items.setEmptyView(tv_no_cost_items);

		db = new DatabaseHelper(this);

		loadCostItems();

		et_search_cost_item.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

				if (adapter != null) {
					adapter.getFilter().filter(s);
				}
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		btn_add_cost_item.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				Intent intent = new Intent(
					CostItemsActivity.this, CostItemEditActivity.class
				);

				intent.putExtra("cost_item_id", 0);

				startActivity(intent);
			}
		});

		lv_cost_items.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				HashMap<String, Object> row =
					(HashMap<String, Object>) adapter.getItem(position);

				Intent intent = new Intent(
					CostItemsActivity.this, CostItemEditActivity.class
				);

				intent.putExtra("cost_item_id", (Integer) row.get("id"));

				startActivity(intent);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadCostItems();
	}

	private void loadCostItems() {

		costItemList = db.getCostItems();

		// Merges in this month's spend for every category that has a
		// budget set, so the list can show a status badge without
		// CostItemAdapter needing its own DB access.
		ArrayList<HashMap<String, Object>> budgetStatus = db.getCategoryBudgetStatus();

		for (HashMap<String, Object> costItem : costItemList) {

			for (HashMap<String, Object> status : budgetStatus) {

				if (costItem.get("id").equals(status.get("id"))) {

					costItem.put("spent", status.get("spent"));
					costItem.put("percent_used", status.get("percent_used"));
					costItem.put("over_budget", status.get("over_budget"));
					break;
				}
			}
		}

		adapter = new CostItemAdapter(this, costItemList);

		lv_cost_items.setAdapter(adapter);

		if (et_search_cost_item != null) {
			adapter.getFilter().filter(et_search_cost_item.getText().toString());
		}
	}
}
