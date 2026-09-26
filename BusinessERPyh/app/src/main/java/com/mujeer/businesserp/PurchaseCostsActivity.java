package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

public class PurchaseCostsActivity extends Activity {

	private TextView tv_no_purchase_costs;
	private Button btn_add_purchase_cost;
	private ListView lv_purchase_costs;

	private DatabaseHelper db;

	private ArrayList<HashMap<String, Object>> purchaseCostList;
	private PurchaseCostAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.purchase_costs_activity);

		setTitle("Purchase Costs");

		tv_no_purchase_costs = findViewById(R.id.tv_no_purchase_costs);
		btn_add_purchase_cost = findViewById(R.id.btn_add_purchase_cost);
		lv_purchase_costs = findViewById(R.id.lv_purchase_costs);

		lv_purchase_costs.setEmptyView(tv_no_purchase_costs);

		db = new DatabaseHelper(this);

		btn_add_purchase_cost.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				Intent intent = new Intent(
					PurchaseCostsActivity.this, PurchaseCostEditActivity.class
				);

				intent.putExtra("purchase_cost_id", 0);

				startActivity(intent);
			}
		});

		lv_purchase_costs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				HashMap<String, Object> row =
					(HashMap<String, Object>) adapter.getItem(position);

				Intent intent = new Intent(
					PurchaseCostsActivity.this, PurchaseCostEditActivity.class
				);

				intent.putExtra("purchase_cost_id", (Integer) row.get("id"));

				startActivity(intent);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadPurchaseCosts();
	}

	private void loadPurchaseCosts() {

		purchaseCostList = db.getPurchaseCosts();

		adapter = new PurchaseCostAdapter(this, purchaseCostList);

		lv_purchase_costs.setAdapter(adapter);
	}
}
