package com.mujeer.businesserp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

// =====================
// Every active item whose current stock is at or below its own
// Reorder Threshold (see Itemseditactivity's field and
// DatabaseHelper.getLowStockItems()) - always a snapshot of right now,
// no period selector. Tapping a row opens that item directly.
// =====================
public class LowStockReportActivity extends Activity {

	private TextView tv_empty;
	private ListView lv_low_stock;

	private DatabaseHelper db;

	private final ArrayList<HashMap<String, Object>> lowStockList =
		new ArrayList<HashMap<String, Object>>();

	private LowStockAdapter adapter;

	// A background result is only applied if it's still the most
	// recent request by the time it comes back.
	private long loadGeneration = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.low_stock_report_activity);

		setTitle("Low Stock");

		tv_empty = findViewById(R.id.tv_empty);
		lv_low_stock = findViewById(R.id.lv_low_stock);

		db = new DatabaseHelper(this);

		adapter = new LowStockAdapter(this, lowStockList);
		lv_low_stock.setAdapter(adapter);

		lv_low_stock.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

					HashMap<String, Object> row = lowStockList.get(position);

					Intent intent = new Intent(LowStockReportActivity.this, Itemviewactivity.class);
					intent.putExtra("item_id", (Integer) row.get("item_id"));

					startActivity(intent);
				}
			});

		loadReport();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadReport();
	}

	private void loadReport() {

		final long myGeneration = ++loadGeneration;

		new Thread(new Runnable() {
				@Override
				public void run() {

					final ArrayList<HashMap<String, Object>> result = db.getLowStockItems();

					runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (myGeneration != loadGeneration || isFinishing()) {
									return;
								}

								lowStockList.clear();
								lowStockList.addAll(result);

								adapter.notifyDataSetChanged();

								if (lowStockList.isEmpty()) {

									tv_empty.setVisibility(View.VISIBLE);
									lv_low_stock.setVisibility(View.GONE);

								} else {

									tv_empty.setVisibility(View.GONE);
									lv_low_stock.setVisibility(View.VISIBLE);
								}
							}
						});
				}
			}).start();
	}
}
