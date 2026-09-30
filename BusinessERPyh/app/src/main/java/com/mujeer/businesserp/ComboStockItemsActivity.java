package com.mujeer.businesserp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Every item that carries a specific variety value (e.g. every model
// that comes in Size 9), opened by tapping a row on
// ComboStockReportActivity. See DatabaseHelper.getItemsForVarietyValue().
// =====================
public class ComboStockItemsActivity extends Activity {

	private TextView tv_page_title;
	private TextView tv_subtitle;
	private TextView tv_empty;
	private ListView lv_items;

	private DatabaseHelper db;

	private int valueId = -1;
	private String valueLabel = "";

	private final ArrayList<HashMap<String, Object>> itemList =
		new ArrayList<HashMap<String, Object>>();

	private ComboStockItemAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.combo_stock_items_activity);

		tv_page_title = findViewById(R.id.tv_page_title);
		tv_subtitle = findViewById(R.id.tv_subtitle);
		tv_empty = findViewById(R.id.tv_empty);
		lv_items = findViewById(R.id.lv_items);

		db = new DatabaseHelper(this);

		valueId = getIntent().getIntExtra("value_id", -1);
		valueLabel = getIntent().getStringExtra("value_label");

		if (valueLabel == null) {
			valueLabel = "";
		}

		setTitle(valueLabel);
		tv_page_title.setText(valueLabel);

		adapter = new ComboStockItemAdapter(this, itemList);
		lv_items.setAdapter(adapter);

		loadItems();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadItems();
	}

	private void loadItems() {

		if (valueId == -1) {
			return;
		}

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result =
						db.getItemsForVarietyValue(valueId);

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (isFinishing()) {
									return;
								}

								itemList.clear();
								itemList.addAll(result);

								adapter.notifyDataSetChanged();

								if (itemList.isEmpty()) {

									tv_empty.setVisibility(View.VISIBLE);
									lv_items.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_items.setVisibility(View.VISIBLE);
								}
							}
						});
				}
			}).start();
	}
}
